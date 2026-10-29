"""Live TLS WSS + fake legacy Hub acceptance tests; no real Android permissions."""
import asyncio
import base64
import ipaddress
import json
import ssl
import sys
import tempfile
import unittest
from datetime import datetime, timedelta, timezone
from pathlib import Path

from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.x509.oid import NameOID
from websockets.asyncio.client import connect
from websockets.asyncio.server import serve
from websockets.exceptions import InvalidStatus, ConnectionClosed

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from hub_signed_adapter import (
    AdapterConfig, SignedHubAdapter, check_legacy_url,
    parse_challenge, read_access_token,
)


class HubAdapterIntegration(unittest.IsolatedAsyncioTestCase):
    async def asyncSetUp(self):
        self.tmp = tempfile.TemporaryDirectory()
        self.folder = Path(self.tmp.name)
        self.identity = ec.generate_private_key(ec.SECP256R1())
        self.device_id = "body-test-1234"
        self.token = "a" * 64
        self.legacy_frames = []
        self.upstream_connections = 0
        self.inbound = asyncio.Queue()
        self.backend = await serve(self.fake_legacy, "127.0.0.1", 0)
        self.backend_url = f"ws://127.0.0.1:{self.backend.sockets[0].getsockname()[1]}"
        cert, tls_key = self.make_cert()
        tls = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
        tls.load_cert_chain(str(cert), str(tls_key))
        self.client_tls = ssl.create_default_context(cafile=str(cert))
        adapter = SignedHubAdapter(AdapterConfig(
            self.device_id, self.token, self.backend_url, self.identity,
        ))
        self.server = await serve(
            adapter.handler, "127.0.0.1", 0, ssl=tls, compression=None,
            process_request=adapter.check_path,
        )
        self.port = self.server.sockets[0].getsockname()[1]

    async def asyncTearDown(self):
        self.server.close()
        self.backend.close()
        await self.server.wait_closed()
        await self.backend.wait_closed()
        self.tmp.cleanup()

    def make_cert(self):
        key = ec.generate_private_key(ec.SECP256R1())
        now = datetime.now(timezone.utc)
        subject = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, "127.0.0.1")])
        cert = (
            x509.CertificateBuilder().subject_name(subject).issuer_name(subject)
            .public_key(key.public_key()).serial_number(x509.random_serial_number())
            .not_valid_before(now - timedelta(days=1))
            .not_valid_after(now + timedelta(days=1))
            .add_extension(x509.SubjectAlternativeName([
                x509.IPAddress(ipaddress.ip_address("127.0.0.1"))
            ]), critical=False).sign(key, hashes.SHA256())
        )
        cp, kp = self.folder / "cert.pem", self.folder / "tls-key.pem"
        cp.write_bytes(cert.public_bytes(serialization.Encoding.PEM))
        kp.write_bytes(key.private_bytes(
            serialization.Encoding.PEM, serialization.PrivateFormat.PKCS8,
            serialization.NoEncryption(),
        ))
        kp.chmod(0o600)
        return cp, kp

    async def fake_legacy(self, ws):
        self.upstream_connections += 1
        async def push():
            while True:
                await ws.send(await self.inbound.get())
        task = asyncio.create_task(push())
        try:
            async for msg in ws:
                self.legacy_frames.append(msg)
                await ws.send(msg)
        finally:
            task.cancel()
            await asyncio.gather(task, return_exceptions=True)

    def url(self, token=None):
        return f"wss://127.0.0.1:{self.port}/{self.token if token is None else token}"

    def challenge(self, device=None, nonce=None, version=1):
        return json.dumps({"type": "auth.challenge", "data": {
            "version": version, "device_id": device or self.device_id,
            "nonce": nonce or base64.b64encode(b"z" * 32).decode(),
        }})

    async def test_signed_proof_and_original_protocol_both_directions(self):
        async with connect(self.url(), ssl=self.client_tls) as phone:
            await phone.send(self.challenge())
            proof = json.loads(await asyncio.wait_for(phone.recv(), 2))
            self.assertEqual(proof["type"], "auth.proof")
            self.assertEqual(proof["data"]["version"], 1)
            signature = base64.b64decode(proof["data"]["signature"], validate=True)
            payload = b"andee-hub-auth-v1\nbody-test-1234\n" + base64.b64encode(b"z" * 32)
            self.identity.public_key().verify(signature, payload, ec.ECDSA(hashes.SHA256()))
            for kind in ["register", "request", "response", "event", "progress", "message"]:
                frame = json.dumps({"type": kind, "id": 10, "data": [1, 2]},
                                   separators=(",", ":"))
                await phone.send(frame)
                self.assertEqual(await asyncio.wait_for(phone.recv(), 2), frame)
            raw = b"\x00\x01HEY\xff"
            await phone.send(raw)
            self.assertEqual(await asyncio.wait_for(phone.recv(), 2), raw)
            upstream = b'{"type":"request","method":"screen.tap"}'
            await self.inbound.put(upstream)
            self.assertEqual(await asyncio.wait_for(phone.recv(), 2), upstream)
        self.assertEqual(self.upstream_connections, 1)
        self.assertEqual(len(self.legacy_frames), 7)

    async def test_wrong_or_missing_secret_path_rejected_before_backend(self):
        for token in ["b" * 64, "", "a" * 63]:
            with self.assertRaises(InvalidStatus):
                async with connect(self.url(token), ssl=self.client_tls):
                    pass
        self.assertEqual(self.upstream_connections, 0)

    async def test_bad_challenges_denied_without_connecting_backend(self):
        invalid = [
            "not json", "{}", '{"type":"request"}',
            self.challenge(device="body-untrusted"),
            self.challenge(nonce=base64.b64encode(b"short").decode()),
            self.challenge(version=99),
        ]
        for item in invalid:
            async with connect(self.url(), ssl=self.client_tls) as phone:
                await phone.send(item)
                with self.assertRaises(ConnectionClosed):
                    await asyncio.wait_for(phone.recv(), 2)
        self.assertEqual(self.upstream_connections, 0)

    async def test_second_session_cannot_observe_or_take_over_first(self):
        async with connect(self.url(), ssl=self.client_tls) as first:
            await first.send(self.challenge())
            self.assertEqual(json.loads(await first.recv())["type"], "auth.proof")
            async with connect(self.url(), ssl=self.client_tls) as second:
                with self.assertRaises(ConnectionClosed):
                    await asyncio.wait_for(second.recv(), 2)
            frame = '{"type":"register","data":{"tools":[]}}'
            await first.send(frame)
            self.assertEqual(await asyncio.wait_for(first.recv(), 2), frame)
        self.assertEqual(self.upstream_connections, 1)

    def test_rejects_lan_backend_and_world_readable_capability(self):
        for url in ["ws://127.0.0.1:9000", "ws://[::1]:9000/path"]:
            self.assertEqual(check_legacy_url(url), url)
        for url in ["ws://localhost:9000", "ws://192.168.1.5:9000",
                    "wss://example.com:443", "ws://127.0.0.1",
                    "ws://127.0.0.1:99999", "ws://u:p@127.0.0.1:9000"]:
            with self.assertRaises(ValueError):
                check_legacy_url(url)
        p = self.folder / "secret-path"
        p.write_text(self.token + "\n")
        p.chmod(0o600)
        self.assertEqual(read_access_token(p), self.token)
        p.chmod(0o644)
        with self.assertRaises(ValueError):
            read_access_token(p)

    def test_invalid_challenge_version_and_nonce_fail_closed(self):
        for msg in [
            self.challenge().replace('"version": 1', '"version": true'),
            self.challenge().replace('"version": 1', '"version": 2'),
        ]:
            with self.assertRaises(ValueError):
                parse_challenge(msg, self.device_id)
        with self.assertRaises(ValueError):
            parse_challenge('{"type":"auth.challenge","data":null}', self.device_id)


if __name__ == "__main__":
    unittest.main()
