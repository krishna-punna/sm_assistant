# 06 — Content generation (P3, P5, P6)

Requirement: [06 Content generation](../requirements/06-content-generation.md), plus AG-5 from [04](../requirements/04-ai-agent-settings.md).
Current code: one placeholder `FeatureScreen` shared by all six hub features; Submit is a TODO.

## Agent clients (P3, prerequisite for every generator)

| ID | Task | Depends on | Status |
|----|------|-----------|--------|
| AGC-1 | `TextAgent` interface `generate(system, user): Result<String>` with implementations: Claude (`POST /v1/messages`, `x-api-key`, `anthropic-version`), Gemini (`POST /v1beta/models/{model}:generateContent`), Groq (`POST /openai/v1/chat/completions`). Model from settings/`app.yml`. | FND-5, FND-6 | done — `agents/TextAgent.kt` (JSON via kotlinx-serialization-json, no compiler plugin) |
| AGC-2 | `ImageAgent` interface `generateImages(prompt, count): Result<List<ImageBytes>>`, Gemini implementation (Imagen / Gemini image model). Save results to app cache files; return URIs. | FND-5, FND-6 | todo (P5) |
| AGC-3 | `VideoAgent` interface, implementation per Q-08 answer (e.g. Veo long-running operation with polling). | **Q-08** | blocked (P6) |
| AGC-4 | Agent selection: a "Default text agent" setting (on the AI Agent list screen) used by PP/TC/CI/CV text; image always Gemini; if none configured → GEN-1. Interim answer to Q-05. | Q-05 (interim), AGC-1 | todo |
| AGC-V1 | Each text agent returns a reply for a short prompt with a real key; wrong key shows Invalid key error in the chat. | AGC-1 | todo |
| AGC-T1 | unit: MockWebServer per provider — request path, headers, JSON body (model, system, messages), response parsing, error mapping (401, 429 rate limit, 500, timeout). | AGC-1 | done (`TextAgentTest`) |
| AGC-T2 | unit: Gemini image response (inline base64) decoded and written to cache; count respected. | AGC-2 | todo |
| AGC-T3 | unit: selection picks default agent; falls back to any configured; none → NotConfigured. | AGC-4 | todo |

## Cross-cutting (GEN)

| ID | Requirement | Status |
|----|-------------|--------|
| GEN-1 | Use configured agent(s); prompt to set one up if none | todo |
| GEN-2 | Prompt cards support copy and expand | todo |
| GEN-3 | Results scroll; header, input and footer fixed | partial — frame is fixed, no scrolling result list yet |
| GEN-4 | Progress while generating; clear error with Retry (Q-09) | todo |
| GEN-5 *(new, Q-13)* | Where conversations/media are stored | todo — interim: in memory, media in cache dir |
| GEN-6 *(new, Q-23)* | Warn when the agent's last status isn't Connected | todo (optional) |

| ID | Type | Task | Depends on | Status |
|----|------|------|-----------|--------|
| GEN-I1 | Impl | `ChatScreen` scaffold replacing `FeatureScreen`: `LazyColumn` of items (`UserPrompt` shown as `PromptCard`, `Loading`, `Error(retry)`, `Result(content)`), `ChatInputBar`, Back to Home. Each feature supplies a `Generator` and a result renderer. Covers GEN-2, GEN-3. | UI-2-I, UI-4-I, FND-3 | todo |
| GEN-I2 | Not-configured state: if `AGC-4` returns NotConfigured, show a card "Set up an AI agent" with a button to AI Agent settings. Covers GEN-1. | AGC-4, GEN-I1 | todo |
| GEN-I3 | Loading item with spinner + "Generating…" and Cancel; error item with message from `ApiError` and Retry. Covers GEN-4. | GEN-I1, FND-6 | todo |
| GEN-I4 | Media files written under `cacheDir/generated/`, cleared on app start older than 7 days (GEN-5 interim). | AGC-2 | todo |
| GEN-V1 | Val | On each feature: submit shows prompt card + spinner, then result; airplane mode shows error with Retry that works once online; no agent configured shows the setup card. | GEN-I1…3 | todo |
| GEN-T1 | UI | Chat scaffold renders each item type; Retry calls the generator again; input/footer remain visible while the list scrolls. | GEN-I1 | todo |
| GEN-T2 | unit | Generic chat ViewModel state machine (idle → loading → result/error → retry), cancel. | GEN-I1 | todo |

## 6.1 Generate Technical Content (P3; posting in P4)

| ID | Requirement | Status |
|----|-------------|--------|
| TC-1 | Render Markdown: headings, lists, tables, inline code, code blocks | todo |
| TC-2 | Result scrolls for long output | todo |
| TC-3 | Post in LinkedIn publishes the text (see 07) | todo |

| ID | Type | Task | Depends on | Status |
|----|------|------|-----------|--------|
| TC-I1 | Impl | Technical content system prompt (`assets/prompts/technical_content.md`). Per Q-11 interim: produce a LinkedIn-ready post (hook line with emoji, emoji sections, "📌 In short:", ≤ 3,000 chars) like slide 12. | Q-11 (interim), AGC-1 | todo |
| TC-I2 | Impl | Markdown renderer composable (e.g. `com.mikepenz:multiplatform-markdown-renderer-m3`) with table and code-block support. | GEN-I1 | todo |
| TC-I3 | Impl | `MarkdownToLinkedIn` converter (pure): headings → bold-ish lines/emoji, lists → "• ", code blocks kept as plain text, tables → text rows (until tables become images). Used by TC-3. | — | todo |
| TC-I4 | Impl | Post in LinkedIn under the result → posting use case (LP-1-I) with converted text. | LP-1-I, TC-I3 | todo (P4) |
| TC-V1 | Val | "System Design: Short URL Service" prompt renders headings, numbered list and the NFR table like slide 6; long output scrolls. | TC-I2 | todo |
| TC-V2 | Val | Posted result on LinkedIn reads cleanly (no `#`/`|` Markdown artefacts) and is ≤ 3,000 chars. | TC-I4 | todo |
| TC-T1 | unit | `MarkdownToLinkedIn`: each Markdown construct; output never contains `**`, `##`, table pipes; length preserved within limits. | TC-I3 | todo |
| TC-T2 | UI | Renderer shows a heading, list item and table cell from sample Markdown. | TC-I2 | todo |

## 6.2 Generate Image (P5)

| ID | Requirement | Status |
|----|-------------|--------|
| IM-1 | Generate one or more images | todo |
| IM-2 | Thumbnail strip / carousel with count badge (Q-10) | todo |
| IM-3 | Tap opens full preview (slide 8) | todo |
| IM-4 | Post in LinkedIn from result and preview | todo |
| IM-5 | Choose which image(s) to post (Q-10) | todo — interim: all selected, user can deselect |

| ID | Type | Task | Depends on | Status |
|----|------|------|-----------|--------|
| IM-I1 | Impl | Image generator: optional text agent step turns the prompt into an image prompt (diagram style), then `ImageAgent` with count = 4 (Q-10 interim). | AGC-2, AGC-1 | todo |
| IM-I2 | Impl | `ImageStrip`: horizontal pager of thumbnails (Coil), count badge "n/N", selection checkmarks (IM-5). | IM-I1 | todo |
| IM-I3 | Impl | Full preview route (NAV-2-I): zoomable image, swipe between images, Post in LinkedIn + Back to Home. | IM-I2, NAV-2-I | todo |
| IM-I4 | Impl | Post selected images (LP-2 image path). | LP-2-I | todo |
| IM-V1 | Val | Short URL prompt yields architecture-style diagrams; badge count matches; preview opens the tapped image; posting 1 and 3 images both work on LinkedIn. | IM-I1…4 | todo |
| IM-T1 | unit | Generator: requested count, cache files created, error when Gemini not configured. | IM-I1 | todo |
| IM-T2 | UI | Strip shows N thumbnails and badge; deselecting updates selection; tap navigates to preview with index. | IM-I2 | todo |
| IM-T3 | UI | Preview shows Post in LinkedIn and swipes between images. | IM-I3 | todo |

## 6.3 Generate Video (P6) — blocked on Q-08

| ID | Requirement | Status |
|----|-------------|--------|
| VI-1 | Generate a short video from the prompt | **blocked (Q-08)** |
| VI-2 | Inline playback with standard controls | todo (independent of provider) |
| VI-3 | Post in LinkedIn publishes the video | blocked (needs VI-1 + LP-2 video path) |

| ID | Type | Task | Depends on | Status |
|----|------|------|-----------|--------|
| VI-I1 | Impl | Video generator via AGC-3 (long-running; show progress, allow leaving the screen and returning). | AGC-3, Q-08 | blocked |
| VI-I2 | Impl | Inline player (Media3 ExoPlayer) for a local file URI. Can be built with a sample MP4 before Q-08. | GEN-I1 | todo |
| VI-I3 | Impl | Post video (LP-2 video path: register upload, upload parts, finalize). | LP-2-I, VI-I1 | blocked |
| VI-V1 | Val | Generated video plays inline, length/format within LinkedIn limits, posts successfully. | VI-I1…3 | blocked |
| VI-T1 | unit | Polling generator: pending → done → file saved; timeout → error. | VI-I1 | blocked |
| VI-T2 | UI | Player shows controls for a sample file. | VI-I2 | todo |

## 6.4 Generate Content & Image (P5)

| ID | Requirement | Status |
|----|-------------|--------|
| CI-1 | Text and image(s) from one submit | todo |
| CI-2 | Post text as body with image(s) attached | todo |

| ID | Type | Task | Depends on | Status |
|----|------|------|-----------|--------|
| CI-I1 | Impl | Composite generator: run TC text generation and image generation in parallel from the same prompt; render Markdown result then `ImageStrip`, then Post in LinkedIn. Partial failure shows the part that worked + Retry for the other. | TC-I1, TC-I2, IM-I1, IM-I2 | todo |
| CI-I2 | Impl | Post converted text + selected images in one post. | LP-2-I, TC-I3 | todo |
| CI-V1 | Val | Matches slide 11 order (text, strip, Post); LinkedIn post has body + images + default hashtags. | CI-I1, CI-I2 | todo |
| CI-T1 | unit | Composite: both succeed; text fails/image succeeds; image fails/text succeeds; retry re-runs only the failed part. | CI-I1 | todo |
| CI-T2 | UI | Result shows text then strip then Post button. | CI-I1 | todo |

## 6.5 Generate Content & Video (P6) — blocked on Q-08

| ID | Requirement | Status |
|----|-------------|--------|
| CV-1 | Text and video from one submit | **blocked (Q-08)** |
| CV-2 | Post text as body with video attached | blocked |

| ID | Type | Task | Depends on | Status |
|----|------|------|-----------|--------|
| CV-I1 | Impl | Composite generator like CI-I1 with video. | VI-I1, TC-I1 | blocked |
| CV-I2 | Impl | Post text + video. | VI-I3 | blocked |
| CV-V1 | Val | Matches slide 10; LinkedIn post has body + video. | CV-I1, CV-I2 | blocked |
| CV-T1 | unit | Composite partial-failure cases as CI-T1. | CV-I1 | blocked |
