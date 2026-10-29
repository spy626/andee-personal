#!/usr/bin/env python3
"""Self-hosted signed Hub adapter. Never commit private keys or bearer tokens.

Phone WSS -> [bearer path + P-256 signed challenge] -> this adapter ->
original Hub on numeric loopback. Original Hub frames pass through unchanged.
NOT a main-phone security approval: a compromised authenticated Hub is powerful.
"""
from __future__ import annotations
import argparse
import asyncio
import base64
import binascii
import hmac
import json
import logging
import os
import ssl
import stat
from dataclasses import dataclass
from http import HTTPStatus
from pathlib import Path
from urllib.parse import urlsplit

from cryptography.hazmat.primitives.asymmetric import ec
from cryptography.hazmat.primitives import hashes, serialization
from websockets.asyncio.client import connect
from websockets.asyncio.server import serve
from websockets.exceptions import ConnectionClosed

LOG = logging.getLogger("andee.hub_adapter")
DOMAIN = "andee-hub-auth-v1"
MAX_FRAME_BYTES = 262_144


def check_legacy_url(url: str) -> str:
    parts = urlsplit(url)
    if parts.scheme != "ws" or parts.hostname not in {"127.0.0.1", "::1"}:
        raise ValueError("Legacy Hub must be ws://127.0.0.1:<port> or ws://[::1]:<port>")
    if parts.username or parts.password or parts.fragment:
        raise ValueError("Legacy Hub cannot contain credentials/fragment")
    try:
        if parts.port is None or not 1 <= parts.port <= 65535:
            raise ValueError("Legacy Hub requires a valid explicit port")
    except ValueError as exc:
        raise ValueError("Invalid legacy Hub port") from exc
    return url


def read_private_file(path: Path) -> bytes:
    info = path.stat()
    if os.name == "posix" and stat.S_IMODE(info.st_mode) & 0o077:
        raise ValueError(f"Private file must have chmod 600: {path.name}")
    if not path.is_file():
        raise ValueError("Not a regular file")
    return path.read_bytes()


def load_identity(path: Path) -> ec.EllipticCurvePrivateKey:
    key = serialization.load_pem_private_key(read_private_file(path), password=None)
    if not isinstance(key, ec.EllipticCurvePrivateKey) or not isinstance(key.curve, ec.SECP256R1):
        raise ValueError("Hub identity must be a P-256 EC private key")
    return key


def read_access_token(path: Path) -> str:
    token = read_private_file(path).decode("ascii").strip()
    if len(token) != 64 or not all(c in "0123456789abcdef" for c in token):
        raise ValueError("Token must be 32 random bytes encoded as 64 lowercase hex characters")
    return token


def parse_challenge(frame: str | bytes, expected_device_id: str) -> tuple[str, str]:
    if not isinstance(frame, str) or len(frame) > 4096:
        raise ValueError("Invalid challenge frame")
    try:
        msg = json.loads(frame)
        data = msg["data"]
        if not isinstance(msg, dict) or msg.get("type") != "auth.challenge":
            raise ValueError("Expected auth.challenge")
        if not isinstance(data, dict) or type(data.get("version")) is not int or data["version"] != 1:
            raise ValueError("Unsupported protocol version")
        device_id, nonce = data["device_id"], data["nonce"]
        if not isinstance(device_id, str) or device_id != expected_device_id:
            raise ValueError("Unknown phone identity")
        if not isinstance(nonce, str) or len(nonce) != 44:
            raise ValueError("Malformed nonce")
        raw = base64.b64decode(nonce, validate=True)
        if len(raw) != 32 or base64.b64encode(raw).decode("ascii") != nonce:
            raise ValueError("Nonce must be canonical Base64 of 32 bytes")
        return device_id, nonce
    except (KeyError, IndexError, TypeError, json.JSONDecodeError, binascii.Error) as exc:
        raise ValueError("Invalid challenge data") from exc


def sign_challenge(identity: ec.EllipticCurvePrivateKey, device_id: str, nonce: str) -> str:
    body = f"{DOMAIN}\n{device_id}\n{nonce}".encode("utf-8")
    signature = identity.sign(body, ec.ECDSA(hashes.SHA256()))
    return base64.b64encode(signature).decode("ascii")


@dataclass(frozen=True)
class AdapterConfig:
    device_id: str
    access_token: str
    legacy_url: str
    identity: ec.EllipticCurvePrivateKey

    def __post_init__(self):
        if not self.device_id or len(self.device_id) > 128 or any(c.isspace() for c in self.device_id):
            raise ValueError("Device ID required without whitespace")
        if len(self.access_token) != 64 or not all(c in "0123456789abcdef" for c in self.access_token):
            raise ValueError("Capability token must be 64 lowercase hex characters")
        check_legacy_url(self.legacy_url)
        if not isinstance(self.identity.curve, ec.SECP256R1):
            raise ValueError("Signing curve must be P-256")


class SignedHubAdapter:
    def __init__(self, config: AdapterConfig):
        self.cfg = config
        self._active = asyncio.Lock()

    def check_path(self, connection, request):
        # Never echo the token or invalid user-supplied URL in any error.
        if not hmac.compare_digest(request.path, "/" + self.cfg.access_token):
            return connection.respond(HTTPStatus.FORBIDDEN, "Forbidden\n")
        return None

    async def handler(self, phone):
        # One device session at a time. A second client cannot steal messages.
        if self._active.locked():
            await phone.close(1008, "device already connected")
            return
        async with self._active:
            try:
                frame = await asyncio.wait_for(phone.recv(), timeout=10)
                device_id, nonce = parse_challenge(frame, self.cfg.device_id)
                # Don't claim to be the Hub if its backend is unavailable.
                async with connect(self.cfg.legacy_url, proxy=None, compression=None,
                                   open_timeout=5, max_size=MAX_FRAME_BYTES, max_queue=8) as legacy:
                    proof = json.dumps({"type": "auth.proof", "data": {
                        "version": 1,
                        "signature": sign_challenge(self.cfg.identity, device_id, nonce)
                    }}, separators=(",", ":"))
                    await phone.send(proof)
                    await self._relay(phone, legacy)
            except (ValueError, ConnectionClosed, OSError, asyncio.TimeoutError) as exc:
                LOG.warning("Hub session rejected or disconnected (%s)", type(exc).__name__)
                await phone.close(1008, "Hub session unavailable")
            except Exception:
                LOG.exception("Unexpected adapter failure")
                await phone.close(1011, "internal error")

    @staticmethod
    async def _relay(phone, legacy):
        async def forward(source, destination):
            async for frame in source:
                await destination.send(frame)  # Preserve original JSON/binary frames exactly.

        tasks = [asyncio.create_task(forward(phone, legacy)),
                 asyncio.create_task(forward(legacy, phone))]
        try:
            await asyncio.wait(tasks, return_when=asyncio.FIRST_COMPLETED)
        finally:
            for task in tasks:
                task.cancel()
            await asyncio.gather(*tasks, return_exceptions=True)


async def run(config: AdapterConfig, host: str, port: int, certificate: Path, tls_key: Path):
    read_private_file(tls_key)
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    context.minimum_version = ssl.TLSVersion.TLSv1_2
    context.load_cert_chain(str(certificate), str(tls_key))
    adapter = SignedHubAdapter(config)
    async with serve(adapter.handler, host, port, ssl=context,
                     process_request=adapter.check_path, compression=None,
                     max_size=MAX_FRAME_BYTES, max_queue=8, open_timeout=10) as server:
        LOG.info("Signed Hub adapter TLS listener active on %s:%s",
                 host, server.sockets[0].getsockname()[1])
        await server.serve_forever()


def main():
    p = argparse.ArgumentParser(description=__doc__)
    p.add_argument("--listen-host", default="127.0.0.1")
    p.add_argument("--listen-port", type=int, default=9443)
    p.add_argument("--legacy-url", required=True)
    p.add_argument("--identity-key", type=Path, required=True)
    p.add_argument("--token-file", type=Path, required=True)
    p.add_argument("--device-id", required=True)
    p.add_argument("--tls-cert", type=Path, required=True)
    p.add_argument("--tls-key", type=Path, required=True)
    a = p.parse_args()
    logging.basicConfig(level=logging.INFO, format="%(levelname)s: %(message)s")
    if not 1 <= a.listen_port <= 65535:
        raise SystemExit("Invalid listen-port")
    config = AdapterConfig(a.device_id, read_access_token(a.token_file),
                           check_legacy_url(a.legacy_url), load_identity(a.identity_key))
    asyncio.run(run(config, a.listen_host, a.listen_port, a.tls_cert, a.tls_key))


if __name__ == "__main__":
    main()
