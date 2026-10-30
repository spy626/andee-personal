# Phase 10B.6 — LYRA-style Groq text chat for Andee

Base: Phase 10B.5 `b3890f8cc3eb37bc43106eaa65f6c5b73a62a047`. This is a separate branch; `main` is untouched.

## Why

The phone recording showed Groq returning HTTP 413. The previous Andee route sent the full LocalPrompt, long history, notebook/plan indexes and the device's large tool catalogue on every request. A one-word greeting could therefore carry a very large payload. Groq documents 413 as a request body that is too large; GPT-OSS Free limits are also tight, so this route must not inherit Andee's full agent context.

## Behavior

Groq now follows the same deliberate boundary as LYRA's Groq integration:

- Text chat only, using `openai/gpt-oss-120b` by default or `openai/gpt-oss-20b` when selected.
- A compact Andee system prompt and only the newest plain-text user/assistant turns; hard cap 4,500 characters and 512 completion tokens.
- No device tool schemas or tool execution, screenshots, photos, files, source lookup, notebook/personal memory, scene/plan indexes, scrollback rebuild, internal notes, or background autonomous turns.
- Character persona is retained as tone only. The prompt explicitly forbids claiming device actions or inventing private memories.
- Groq-selected image requests are refused locally before any API call. Non-user turns (notifications, peer tasks, wakes, background work and reviews) are skipped rather than pretending they were performed.
- Existing Andee agent/autonomy/tool behavior remains unchanged for legacy, xKiro and OpenRouter routes. Voice/ASR/TTS remains separately configured and is not routed through Groq.

## Safety

- The user's Groq API key remains encrypted by the existing Android Keystore AES-GCM store.
- Groq Free confirmation remains OFF by default and must be explicitly enabled by the user.
- No paid fallback, automatic provider switch, automatic retry, billing change or new service is introduced.
- The request still uses the fixed official Groq OpenAI-compatible endpoint and only the two allowlisted GPT-OSS model IDs.
- HTTP 413 now has a safe, actionable diagnostic and does not expose the raw response body, prompt or key.

## Verification

Added pure JVM tests for history compaction, role filtering, multimodal/tool exclusion, newest-message retention, hard size bounds and the chat-only prompt. Existing free-provider and Phase 10B.5 source gates remain active.

CI is the next verification step. A green build proves compile/tests and source contracts only; it does not prove the user's live Groq key works. After CI, phone acceptance must send a harmless text greeting with Groq Free enabled and record the selected model plus HTTP/result only — never the API key.

## Explicit limitation

Selecting Groq intentionally changes Andee into a conversational text-only route. For device actions, personal memory, screenshots, files, notifications or autonomous work, select a provider whose Andee tool route is verified (for example xKiro Free). This is a provider capability boundary, not a global removal of Andee features.
