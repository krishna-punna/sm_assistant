# 12. App improvements (P2: user experience)

Source: the review doc "SM Assistant app: improvement tasks" (2026-10-29), tasks #6–#12. P1 (#1–#5) shipped in PR #81.
Status: implemented 2026-10-31 (PRs #113–#116, #118, #120 and this one); device checks left. One PR per task, in order.

## Implementation

| # | Task | Status |
|---|------|--------|
| 6 | Share and save generated media: Share (system sheet through a `FileProvider` over `cacheDir/generated/`) and Save to Gallery (MediaStore, `Pictures/SM Assistant` or `Movies/SM Assistant`; the storage permission only on Android 8–9) under image results, the image preview and video results (`MediaExport`, `MediaActions`). | done |
| 7 | Post history screen: every published post (date, text, media type, post link, manual or scheduled) kept in a local list, shown from Home (Post History: open, copy, remove, clear); kept on the phone, latest 200 (`PostHistoryStore`). Recorded by `PostToLinkedInUseCase` for every button, approved reviews and scheduled runs. Scheduled prompts now also avoid the latest 25 scheduled titles from the history, across all schedulers. | done |
| 8 | Saved drafts: Save draft in the Post to LinkedIn dialog, which every result's Post button opens (the final text, hashtags included, and the selected media, copied to `filesDir/drafts/`); Drafts on Home lists them to Edit & post (same dialog; posting removes the draft, Save draft updates it) or Delete (`DraftStore`, `DraftsScreen`). | done |
| 9 | Agent-not-connected warning (GEN-6, Q-23, answered as "warn, don't block"): API Status remembers each result (`ApiSettingsStore.lastStatus`, cleared when the settings are saved again), and the text and media agent pickers show a warning when the picked agent's last check failed (`AgentStatusHint`). | done |
| 10 | Test button on settings screens (AS-7, Q-22): Test under SAVE on the LinkedIn token form and every AI agent screen runs the API Status check (saving unsaved changes first) and shows the result inline; the result also feeds the #9 warnings. | done |
| 11 | Sign-in note for Google-login LinkedIn accounts: the WebView can't do "Continue with Google". The settings screen and the sign-in browser say so (the note turns red once the page goes to accounts.google.com), suggest setting a LinkedIn password, and "Paste a token instead" closes the browser and scrolls to the token form. Custom Tabs + an App Link redirect stays a later option. | done |
| 12 | Character count and preview while editing: in the confirm dialog, a live count above the text (amber with how many are left from 90% of the limit, red over it), the appended hashtags with their characters, and Preview: the post as LinkedIn shows it, hashtags as links and a "…see more" marker where the feed folds it (3 lines or ~210 characters; `PostPreview.kt`). | done |

## Tests

| # | Kind | Test | Status |
|---|------|------|--------|
| 7 | unit | Entries, titles, JSON and the store's order, cap and scheduled titles (`PostHistoryTest`); published posts recorded with their source, failed ones not (`PostToLinkedInTest`); scheduled runs recorded and history titles avoided (`AutoPostRunTest`); the list, Remove and Clear (`PostHistoryScreenTest`); Home opens it (`NavigationTest`). | done |
| 8 | unit | Media copied out of the cache, updates, titles, delete removes media, JSON (`DraftStoreTest`); save, reopen, re-save and post a draft (`PostToLinkedInTest`); the list, Edit & post and Delete (`DraftsScreenTest`); Home opens it (`NavigationTest`). | done |
| 9 | unit | Last status stored, cleared on save, every status round-trips (`ApiSettingsStoreTest`); warning text and when it shows (`AgentStatusHintTest`). | done |
| 10 | unit | Test saves typed values, checks, remembers and clears the result; stops at field errors (`ApiSettingsViewModelTest`); the result line and button (`ApiSettingsFieldsTest`). | done |
| 11 | unit | Google sign-in pages recognised; the note and its button; the hint on the settings screen (`GoogleSignInNoteTest`, `LinkedInSignInFieldsTest`). | done |
| 12 | unit | Feed fold by lines and characters, hashtags found (`PostPreviewTest`); near-limit count, added hashtags line, Preview and back to Edit (`PostDialogsTest`). | done |
| 6 | unit | Gallery names, folders and MIME types; share sends content links with a read grant (`MediaExportTest`); Share opens the system sheet, buttons off with nothing selected (`MediaActionsTest`). | done |

## Validation

| # | Check | Status |
|---|-------|--------|
| 7-V | On a device: post manually and from a scheduler, then see both in Post History with working Open post links. | todo |
| 8-V | On a device: save an image post as a draft, close the app, reopen Drafts a day later, edit and post it. | todo |
| 9-V | On a device: break an agent's key, run API Status, open a generator and see the warning; fix the key and see it go. | todo |
| 10-V | On a device: type a wrong key, tap Test, see Invalid key; fix it, tap Test, see Connected. | todo |
| 11-V | On a device: tap Continue with Google in the sign-in browser, see the red note, tap Paste a token instead and land on the token form. | todo |
| 12-V | On a device: open a long post's confirm dialog, check the count turns amber near 2,700, Preview shows the fold, and the post on LinkedIn folds at about the same place. | todo |
| 6-V | On a device: share an image to a messaging app, save an image and a video, and find them in the Photos app under "SM Assistant" (and on Android 8–9 after granting storage). | todo |
