# 05 — Prepare LinkedIn Post Prompt (P3)

Requirement: [05 Prepare LinkedIn Post Prompt](../requirements/05-prepare-post-prompt.md).
Depends on the agent clients (AGC-*) and shared chat screen (GEN-I1) in [06](06-content-generation.md).

## Status by requirement

| ID | Requirement | Status | Gap |
|----|-------------|--------|-----|
| PP-1 | Type a topic and submit | partial | Input + Submit drawn; Submit does nothing |
| PP-2 | Send topic to the agent with instructions to produce a detailed LinkedIn-post prompt | todo | |
| PP-3 | Show result as a titled card (title from topic) | todo | |
| PP-4 | Copy action copies full prompt | todo | |
| PP-5 | Expand action shows prompt full-screen | todo | |
| PP-6 | Conversation stays; user can submit another topic | todo | |
| PP-7 | Back to Home | done | |
| PP-8 *(new, Q-06)* | "Use this prompt in…" hand-off to a generator | todo (optional) | Waiting on Q-06 |

## Implementation

| ID | Task | Depends on | Status |
|----|------|-----------|--------|
| PP-I1 | System prompt asset `assets/prompts/prepare_prompt.md` reproducing the observed recipe (persona with seniority → task with concrete stack → sections to cover → diagrams/code/trade-offs → scale targets). Ask for JSON `{ "title": "...", "prompt": "..." }` so the card title is reliable (PP-3). | — | todo |
| PP-I2 | `PreparePromptUseCase(topic) → Result<PreparedPrompt>` using the selected text agent (AGC-4). Parse JSON; if parsing fails, fall back to title = topic in title case, prompt = raw text. | AGC-1…4, PP-I1 | todo |
| PP-I3 | Screen on the shared chat scaffold: user bubble with the topic, assistant `PromptCard` with title, copy (clipboard + "Copied" snackbar) and expand (full-screen dialog). Input cleared after submit; history kept in ViewModel (PP-6). No Post in LinkedIn button here (HT-16, 07). | GEN-I1, UI-2-I | todo |
| PP-8-I | "Use in…" menu on the card opens the chosen generator with the prompt pre-filled (nav argument or shared `PromptHandoff` holder). | Q-06, GEN-I1 | todo (optional) |

## Validation

| ID | Check | Status |
|----|-------|--------|
| PP-V1 | Topic "Design a system for Short URL" with each text agent (Claude, Gemini, Groq) gives a card titled like "URL Shortener System Design Prompt" whose text follows the five-part recipe (compare with the slide 5 example). | todo |
| PP-V2 | Copy, then paste into the Technical Content input: full text, no title. Expand shows the whole prompt and closes with back. | todo |
| PP-V3 | Submit three topics in a row: all three exchanges stay in order and scroll. | todo |
| PP-V4 | No agent configured → message with a link to AI Agent settings (GEN-1). | todo |

## Tests

| ID | Kind | Test | Status |
|----|------|------|--------|
| PP-T1 | unit | Use case with a fake agent: sends system prompt + topic; parses JSON; falls back on non-JSON; propagates `ApiError`. | todo |
| PP-T2 | unit | Title fallback from topic. | todo |
| PP-T3 | unit | ViewModel: submit appends user + loading item, then replaces loading with card; blank input ignored; second submit while busy ignored. | todo |
| PP-T4 | UI | Card shows title and text; copy writes clipboard; expand opens dialog; no "Post in LinkedIn" node on this screen. | todo |
| PP-T5 | unit | Prompt asset contains each recipe section (guards against accidental edits). | todo |
