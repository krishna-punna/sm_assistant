# 07 — Post in LinkedIn (P4, media in P5/P6)

Requirement: [07 Posting to LinkedIn](../requirements/07-post-to-linkedin.md), with LI-4 from [03](../requirements/03-linkedin-settings.md) and HT-10…16 from [10](../requirements/10-default-hashtags.md).
Nothing is built yet. The auth model (**Q-03**) decides how the token is obtained; the posting calls are the same either way.

## Status by requirement

| ID | Requirement | Status | Gap |
|----|-------------|--------|-----|
| LP-1 | Post current result using saved LinkedIn settings | **blocked (Q-03)** | Can start with a pasted access token |
| LP-2 | Text-only → text post; media → text as commentary with media attached | blocked (Q-03); media parts wait for P5/P6 | |
| LP-3 | Missing/invalid settings → tell user, link to LinkedIn settings | todo | |
| LP-4 | Success (with link) or clear error | todo | |
| LP-5 | Append default hashtags when enabled | todo | Logic in HT-I4, wiring HT-I5 |
| LP-6 *(new, Q-12)* | Preview/confirm before publishing | blocked (Q-12) — interim: confirmation dialog with final text | |
| LP-NFR | ≤ 3,000 chars, plain text (no Markdown), media size/count limits | todo | |

## Implementation

| ID | Task | Depends on | Status |
|----|------|-----------|--------|
| LP-1-I | `LinkedInClient`: get member URN (`GET /v2/userinfo` → `sub`, or from OAuth sign-in), then `POST /rest/posts` with headers `Authorization: Bearer`, `LinkedIn-Version: YYYYMM`, `X-Restli-Protocol-Version: 2.0.0`; body `author: urn:li:person:{id}`, `commentary`, `visibility: PUBLIC`, `distribution`, `lifecycleState: PUBLISHED`. Returns the post URN from `x-restli-id`. | FND-5, FND-6, Q-03 (interim: pasted token) | blocked |
| LP-1-I2 | `PostToLinkedInUseCase(content: PostContent)`: builds final text (MarkdownToLinkedIn → HashtagAppender), checks length, uploads media, creates post. Single entry point for every Post in LinkedIn button. | LP-1-I, TC-I3, HT-I4 | todo |
| LP-2-I | Media upload: images via `POST /rest/images?action=initializeUpload` → PUT bytes → `content.media.id` (single) or `content.multiImage` (2–20). Video via `/rest/videos?action=initializeUpload`, multipart PUT, `finalizeUpload`. | LP-1-I | todo (images P5, video P6) |
| LP-3-I | Before posting: if token/settings missing → snackbar "Set up LinkedIn" with action to S2. On 401 → "LinkedIn token expired or invalid" with the same action. | LP-1-I2 | todo |
| LP-4-I | Progress on the button while posting; success snackbar with "Open" (`https://www.linkedin.com/feed/update/{urn}`); errors from `ApiError` (403 missing `w_member_social`, 422 validation, 429 rate limit). Prevent double posting. | LP-1-I2 | todo |
| LP-5-I | Same as HT-I5. | HT-I4 | todo |
| LP-6-I | Confirmation dialog: final post text (incl. hashtags, HT-15), media thumbnails, char count, Post / Cancel. Editing the text here is Q-12. | LP-1-I2, Q-12 (interim) | todo |

## Validation

| ID | Check | Status |
|----|-------|--------|
| LP-V1 | Text post from Technical Content appears on the LinkedIn profile, formatted like slide 12, with default hashtags. Success snackbar "Open" goes to it. | todo |
| LP-V2 | No token → "Set up LinkedIn" takes you to S2. Revoked token → expired message. | todo |
| LP-V3 | Tapping Post twice quickly makes one post. | todo |
| LP-V4 (P5) | Single-image and multi-image posts show the images. | todo |
| LP-V5 (P6) | Video post plays on LinkedIn. | blocked |
| LP-V6 | Prepare Prompt screen has no Post button (07 "does not appear"). | todo |

## Tests

| ID | Kind | Test | Status |
|----|------|------|--------|
| LP-T1 | unit | MockWebServer: create text post — headers (`LinkedIn-Version`, Restli), JSON body, URN parsed from `x-restli-id`. | todo |
| LP-T2 | unit | Error mapping: 401 → InvalidKey, 403 → NoAccess, 422 → validation message, 429 → RateLimited. | todo |
| LP-T3 | unit | Use case: Markdown converted, hashtags appended (or not), over-limit stops before any request, media uploaded before post, media-only post text = hashtags. | todo |
| LP-T4 | unit | Image upload sequence (initialize → PUT → post with media id); multi-image uses `multiImage`. | todo |
| LP-T5 | UI | Post button shows progress and is disabled while posting; confirmation dialog shows hashtags and char count. | todo |
