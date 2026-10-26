# 04 — Default hashtags (P2, posting part in P4)

Requirement: [10 Default hashtags](../requirements/10-default-hashtags.md).
Current code: `DefaultHashtagsScreen.kt` — layout only; nothing is saved, and the toggle starts on (D-1).

Fully local, so it can be built and tested in parallel with P1. The appending logic (HT-10…14) is a pure function that can be finished in P2; wiring it into posting happens in P4.

## Status by requirement

| ID | Requirement | Status | Gap |
|----|-------------|--------|-----|
| HT-1 | Home button opens the screen | done | |
| HT-2 | Tags separated by spaces, commas, new lines | done | |
| HT-3 | Save persists; pre-filled on reopen | done | |
| HT-4 | Normalise on save: add `#`, reject invalid characters inline, de-dupe case-insensitively, keep order | done | |
| HT-5 | Clear empties the box only; saved list changes on Save (Q-18) | done | |
| HT-6 | Confirmation after save | done | Toast |
| HT-7 | Toggle on/off, persisted (Q-17: on this screen) | done | |
| HT-8 | Off on first install; auto-on at first non-empty save | done | D-1 fixed |
| HT-9 | Turning off keeps the list | done | |
| HT-10 | Append after one blank line, space-separated, when on and non-empty | done | |
| HT-11 | Skip tags already in the text (case-insensitive) | done | |
| HT-12 | Applies to all five posting features; media-only posts get tags as the text | done | Image-only and video-only posts get the tags as text |
| HT-13 | Applied at posting time, not stored in content | done | |
| HT-14 | Counts towards 3,000 chars; keep tags, ask user to shorten body | done | Generation also leaves room for the tags and shortens long posts: PL-1…PL-8 in [12 Post length](../requirements/12-post-length.md) (`PostLengthGuard`) |
| HT-15 | Preview/confirmation shows the tags (Q-12) | done | |
| HT-16 | Never added to Prepare Prompt output | done | Prepare Prompt has no Post button (`PreparePromptScreenTest`) |
| HT-NFR | Warn (not block) above 5 tags | done | |

## Implementation

| ID | Task | Depends on | Status |
|----|------|-----------|--------|
| HT-I1 | `HashtagParser` (pure Kotlin): split on whitespace/commas/new lines, add `#`, validate `^#[\p{L}\p{N}_]+$`, de-dupe case-insensitively keeping first occurrence, return `ParseResult(tags, invalid)`. Covers HT-2, HT-4. | — | done — `Hashtags.parse` |
| HT-I2 | `HashtagSettingsStore` (SharedPreferences; not secret): `tags: List<String>`, `enabled: Boolean` (default false), `everSavedNonEmpty: Boolean`. Covers HT-3, HT-7, HT-8, HT-9. | — | done |
| HT-I3 | `DefaultHashtagsViewModel`: load into box on open; Save → parse; if invalid show inline message and don't save; else store, auto-enable on first non-empty save, toast "Saved N hashtags"; show warning when > 5. Clear empties the box only. Toggle writes immediately. Fixes D-1. | HT-I1, HT-I2 | done |
| HT-I4 | `HashtagAppender.apply(body, tags, enabled): PostText` (pure): HT-10 format, HT-11 skip existing (match whole tags, case-insensitive), HT-12 media-only → tags only, HT-14 returns `overLimitBy` when body + tags > 3,000. | HT-I1 | done — `Hashtags.appendTo` |
| HT-I5 | Call `HashtagAppender` inside the posting use case for every posting feature, never on Prepare Prompt; show over-limit message; feed the final text to the preview (LP-6). Covers HT-12…16. | HT-I4, LP-1-I, LP-6-I | done — `PostToLinkedInUseCase.prepare` |
| HT-17-I *(Q-19)* | Per-post "Skip hashtags" in the preview. | Q-19, LP-6-I | blocked (optional) |
| HT-20-I *(Q-20)* | "AI-suggested hashtags" toggle (`HashtagSettingsStore.aiEnabled`); `PostLengthGuard` asks the agent for up to `linkedin_post.ai_hashtags` topic hashtags on a last line; `Hashtags.appendTo` merges the defaults into that line (dedupe, sanitise). Covers HT-20…23. | HT-I4, PL | done |

## Validation

| ID | Check | Status |
|----|-------|--------|
| HT-V1 | Fresh install: toggle off, box empty. Type `JavaDeveloper, #AIEngineer #javadeveloper` → Save: box shows `#JavaDeveloper #AIEngineer`, toast shown, toggle switches on. | todo |
| HT-V2 | Type `#C++ #ok` → Save: inline error names `#C++`, nothing saved. | todo |
| HT-V3 | Toggle off, restart: still off, list kept. Clear → Save: list empty, toggle unchanged. | todo |
| HT-V4 | Six tags → warning shown, save still succeeds. | todo |
| HT-V5 (P4) | Post technical content with toggle on: LinkedIn post ends with a blank line + tags; a tag already in the body isn't repeated. Toggle off: no tags. Image-only post: text is just the tags. | todo |
| HT-V6 (P4) | Body of 2,990 chars + tags: user is told to shorten, nothing posted. | todo |
| HT-V7 | AI-suggested hashtags on: generated post ends with ≤ 3 topic tags; posting merges defaults onto that line, no duplicates, still ≤ 3,000. Off: no AI tags. | todo |

## Tests

| ID | Kind | Test | Status |
|----|------|------|--------|
| HT-T1 | unit | Parser: separators (space, comma, newline, mixed), `#` added, invalid chars reported (`C++`, `hello-world`, emoji), underscore and non-Latin letters allowed, case-insensitive de-dupe keeps first spelling and order, empty input → empty list. | done (`HashtagsParseTest`) |
| HT-T2 | unit | Store/ViewModel: default off; first non-empty save turns on; later saves don't change toggle; user-off stays off after save; empty save keeps toggle; restart persistence. | done (`HashtagSettingsTest`) |
| HT-T3 | unit | Appender: format (`body\n\n#A #B`), skip existing (`#ai` in body skips `#AI`, but `#AIEngineer` in body doesn't skip `#AI`), disabled/empty → body unchanged, media-only → tags only, over-limit detection at 3,000. | done (`HashtagsAppendTest`) |
| HT-T4 | UI | Prepare Prompt screen has no Post in LinkedIn button, so its output can never get hashtags (HT-16). | done (`PreparePromptScreenTest`) |
| HT-T5 | UI | Screen: pre-fills saved tags, Clear empties box without changing store, Save shows error text for invalid input, toggle reflects store. | done (`DefaultHashtagsScreenTest`, Robolectric) |
| HT-T6 | unit | AI hashtags: toggle persistence, prompt asks for N tags and lists defaults only when on, shorten request keeps the line, merge order/dedupe/sanitise, merged line counts to the limit, `ai_hashtags` config. | done (`AiHashtagsTest`, `GenerateTextTest`, `HashtagSettingsTest`, `AppConfigTest`) |
