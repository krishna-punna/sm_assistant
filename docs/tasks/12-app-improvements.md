# 12. App improvements (P2: user experience)

Source: the review doc "SM Assistant app: improvement tasks" (2026-10-29), tasks #6–#12. P1 (#1–#5) shipped in PR #81.
Status: in progress. One PR per task, in order.

## Implementation

| # | Task | Status |
|---|------|--------|
| 6 | Share and save generated media: Share (system sheet through a `FileProvider` over `cacheDir/generated/`) and Save to Gallery (MediaStore, `Pictures/SM Assistant` or `Movies/SM Assistant`; the storage permission only on Android 8–9) under image results, the image preview and video results (`MediaExport`, `MediaActions`). | done |
| 7 | Post history screen: every published post (date, text, media type, post link, manual or scheduled) kept in a local list, shown from Home (Post History: open, copy, remove, clear); kept on the phone, latest 200 (`PostHistoryStore`). Recorded by `PostToLinkedInUseCase` for every button, approved reviews and scheduled runs. Scheduled prompts now also avoid the latest 25 scheduled titles from the history, across all schedulers. | done |
| 8 | Saved drafts: "Save draft" on results, a Drafts list to edit, post or delete them later. | todo |
| 9 | Agent-not-connected warning (GEN-6, Q-23): a hint by the agent dropdown when the agent has no key. | todo |
| 10 | Test button on settings screens (AS-7, Q-22): check a key right after saving it with the API Status check. | todo |
| 11 | Sign-in note for Google-login LinkedIn accounts: the WebView can't do "Continue with Google", so the dialog says so and points to the paste-token route. | todo |
| 12 | Character count and preview while editing: live count against LinkedIn's 3,000 limit with the hashtags that will be appended, in the confirm dialog. | todo |

## Tests

| # | Kind | Test | Status |
|---|------|------|--------|
| 7 | unit | Entries, titles, JSON and the store's order, cap and scheduled titles (`PostHistoryTest`); published posts recorded with their source, failed ones not (`PostToLinkedInTest`); scheduled runs recorded and history titles avoided (`AutoPostRunTest`); the list, Remove and Clear (`PostHistoryScreenTest`); Home opens it (`NavigationTest`). | done |
| 6 | unit | Gallery names, folders and MIME types; share sends content links with a read grant (`MediaExportTest`); Share opens the system sheet, buttons off with nothing selected (`MediaActionsTest`). | done |

## Validation

| # | Check | Status |
|---|-------|--------|
| 7-V | On a device: post manually and from a scheduler, then see both in Post History with working Open post links. | todo |
| 6-V | On a device: share an image to a messaging app, save an image and a video, and find them in the Photos app under "SM Assistant" (and on Android 8–9 after granting storage). | todo |
