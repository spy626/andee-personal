# Andee Remote Hub: signed identity v1 (draft interoperability contract)

This protocol is **required** by `recovery/phase3-signed-hub-identity` for the
optional remote Hub backend. **No remote Hub adapter ships in this fork.**
The existing local/on-device AI, notifications, messaging, voice and overlay
continue without remote Hub configuration.

## Manual public-key pinning (free, no hosted service required)

The Hub administrator generates a long-term **P-256** signing keypair using
local OS tools and securely stores the **private key only on the Hub**.
For a local OpenSSL host, for example:

```bash
openssl ecparam -name prime256v1 -genkey -noout -out hub-private.pem
chmod 600 hub-private.pem
openssl pkey -in hub-private.pem -pubout -outform DER | openssl base64 -A
```

The resulting one-line Base64 is a **public** X.509 SubjectPublicKeyInfo (SPKI).
Verify this key **out of band** with the real Hub owner and paste it into
Andee Settings -> Backend -> Cloud -> **Trusted Hub identity key**.
Do **not** paste a private key into Andee or a chat. Use a trusted `wss://`
Hub URL. A wrong or empty key fails closed and does not dial out.

This is manual **identity pinning**, not automatic QR/link pairing. A new
private signing key requires the owner to update the public-key pin.

## On every WebSocket connection

1. Android completes normal WSS/TLS validation but sends **no** registration,
   tool schemas or user events.
2. Android sends:

   ```json
   {"type":"auth.challenge","data":{"version":1,"device_id":"body-abcdef12","nonce":"base64-32-random-bytes"}}
   ```

3. The trusted Hub signs the **exact UTF-8 bytes, with no trailing newline**:

   ```text
   andee-hub-auth-v1\n<device_id>\n<nonce>
   ```

   using ECDSA P-256 SHA-256, returning a DER-encoded signature as Base64:

   ```json
   {"type":"auth.proof","data":{"version":1,"signature":"base64-DER-signature"}}
   ```

4. Android verifies the signature against the manually pinned public key,
   the **same** device ID and this connection's fresh random nonce. Incorrect
   signature, malformed proof, wrong version, wrong pin, replay or timeout
   (10 seconds) closes the connection. There is **no legacy auth bypass**.
5. Only after verification, Android sends its **unchanged** original
   `register` JSON with tool schemas; after that, original
   `request/response/event/progress/message` formats continue unchanged.
   Authentication is **per live socket**, not transferable across reconnects.

One way to reproduce the Hub signature with OpenSSL (adjust device ID and
nonce from the live challenge; do **not** put a private key on the phone):

```bash
printf 'andee-hub-auth-v1\n%s\n%s' "$DEVICE_ID" "$NONCE" |
  openssl dgst -sha256 -sign hub-private.pem | openssl base64 -A
```

## Security and acceptance boundaries

- A Hub may not issue `request` or `progress` before it proves identity.
- This proves possession of a trusted signing key. It does not protect against
  a malicious/compromised **authenticated** Hub, provider prompt injection,
  owner account compromise, or a user pinning the wrong public key.
- Remote Hub servers **must be updated** to sign challenges. Until then, the
  optional remote Hub backend is intentionally unavailable; do not mark its
  feature parity as passing.
- Do not enable phone-level Accessibility/SMS/notifications on the user's
  main device until remote integration, per-action authorization and complete
  real-device acceptance have all passed.
