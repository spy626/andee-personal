# Signed Remote Hub Adapter — experimental self-hosted bridge

**Only for a test computer and trusted existing Hub. Not main-phone ready.**
This adds no new AI model or paid API. Andee's original SMS, WhatsApp,
notification suggestions, voice, local AI, floating mascot and screen actions
remain unchanged. The bridge is optional, never automatically deployed.

## What it does

An Andee Phase 3 phone connects to a TLS \`wss://\` endpoint on a computer.
The adapter requires a 32-byte random secret access path (64 lowercase hex),
then waits for the phone's \`auth.challenge\`. It checks the exact expected
device ID and 32-byte nonce, signs the canonical v1 payload using the Hub's
P-256 signing key, and sends \`auth.proof\`.

Only then does the bridge connect/relay **unchanged** original Hub WebSocket
frames (\`register/request/response/event/progress/message\`) to the *owner's
existing Hub*, which MUST listen on numeric loopback
\`ws://127.0.0.1:<port>\` or \`ws://[::1]:<port>\`.

**The adapter is not itself an AI backend.** No existing Hub was discovered or
modified in this repository. A trusted legacy Hub must already be working.

## Run on a trusted computer (Python 3.11+, free OSS only)

1. Configure your existing Hub on \`127.0.0.1\` (not publicly exposed).
2. Install dependencies in your own virtual environment:

   \`\`\`bash
   python3 -m venv .venv
   . .venv/bin/activate
   python -m pip install -r tools/requirements-hub-adapter.txt
   \`\`\`

3. Generate a private P-256 signing identity **on the Hub computer only**:

   \`\`\`bash
   openssl ecparam -name prime256v1 -genkey -noout -out hub-private.pem
   chmod 600 hub-private.pem
   openssl pkey -in hub-private.pem -pubout -outform DER | openssl base64 -A
   \`\`\`

   The last command prints the **public** SPKI identity. Verify it by a
   separate trusted channel, then paste ONLY that public key into Andee's
   original Cloud backend → **Trusted Hub identity key** setting.

4. Generate a random *secret URL path* locally, and protect the file:

   \`\`\`bash
   python -c 'import secrets; print(secrets.token_hex(32))' > hub-access-token.txt
   chmod 600 hub-access-token.txt
   \`\`\`

   Treat the token like a password; never paste it into this chat, a GitHub
   issue, a public config or phone screenshots. It lives in a private file,
   not a CLI flag, to avoid exposing it in process arguments.

5. Obtain a **proper TLS certificate trusted by Android** and private key
   for the endpoint hostname. Let's Encrypt offers free certificates.
   \`chmod 600\` the TLS private key. This bridge **refuses plaintext TLS
   listeners**. The CI tests use a temporary self-signed TLS certificate
   trusted by the test client only—not suitable for an ordinary Android phone.

6. On your own machine:

   \`\`\`bash
   python tools/hub_signed_adapter.py \
     --listen-host 127.0.0.1 --listen-port 9443 \
     --legacy-url ws://127.0.0.1:9000 \
     --identity-key hub-private.pem --token-file hub-access-token.txt \
     --device-id body-example1234 \
     --tls-cert fullchain.pem --tls-key privkey.pem
   \`\`\`

   If the phone needs external access, the owner must separately configure a
   trusted firewall/TLS reverse proxy. Disable **URL access logging** because
   the bearer capability is embedded in the path. Never expose the old
   unauthenticated Hub port to the public LAN/internet.

7. The phone Hub URL has the form
   \`wss://trusted-host.example:9443/<your-secret-64-hex-token>\`.
   The user must enter it privately on the phone. The phone verifies the Hub
   P-256 signature before revealing its tool schemas or user events.
   Clearing/rotating the identity pin or bearer path revokes future sessions.

## Automated testing without phone access

\`\`\`bash
python -m unittest discover -s tools/tests -p 'test_hub_signed_adapter.py' -v
python tools/check_hub_adapter.py
\`\`\`

Tests run real local WSS/TLS and a **fake legacy Hub** to verify P-256 proof,
exact two-way protocol payload preservation (text + binary), rejection of
missing/wrong capability paths, bad/foreign-device challenges, simultaneous
session denial, loopback-only Hub URLs, and private-token permission checks.
No APK install, API key, SMS, WhatsApp, real Hub, or phone permissions needed.

## Remaining security/feature acceptance gates

- A bearer URL is **not cryptographic client pairing**. If stolen, another
  party may reach the legacy Hub through this bridge. Keep it secret, rotate
  on exposure, rate-limit/firewall the listener, and don't leak it in logs.
- A trusted or compromised Hub can still send powerful device commands;
  neither TLS nor its signed identity replaces prompt-injection and
  user-intent safeguards. No security-ready or daily-phone status is claimed.
- The bridge itself is independently self-hosted and not deployed.
  Real Hub protocol compatibility, TLS operation, reconnection, Android
  overlay/voice/WhatsApp/SMS behavior and separate test-phone acceptance
  remain required before merging/releasing.
