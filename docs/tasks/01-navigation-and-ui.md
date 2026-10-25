# 01 — Navigation and UI conventions (P1)

Requirements: [02 Screens and navigation](../requirements/02-screens-and-navigation.md), [08 UI conventions](../requirements/08-ui-conventions.md).

## Navigation

| ID | Requirement | Status | Notes |
|----|-------------|--------|-------|
| NAV-1 | Home (S1) shows LinkedIn API, AI Agent, SM Assistant, Default Hash Tags, API Status | done | One AI Agent button (Q-01 interim) |
| NAV-2 | All screens S1–S15 exist and are reachable per the navigation map | partial | S8–S13 share one placeholder `FeatureScreen`; S10 full image preview missing (see IM-3) |
| NAV-3 | Back to Home on every screen except Home returns to Home and clears the back stack | done | Hub also has it (Q-02 interim); system back pops one level |
| NAV-4 | Survive rotation / process death without losing typed input or results | partial | Inputs use `rememberSaveable`; results will need ViewModel + `SavedStateHandle` |

| ID | Type | Task | Depends on | Status |
|----|------|------|-----------|--------|
| NAV-2-I | Impl | Split `FeatureScreen` into the chat screen (GEN-I1) and add the image preview route `hub/IMAGE/preview/{index}`. | GEN-I1 | partial — Prepare Prompt, Technical Content and Image have their own screens; the preview is a full-screen dialog rather than a route, so the generated files don't need to go through nav arguments |
| NAV-4-I | Impl | Keep chat history and results in the ViewModel; restore input from `SavedStateHandle`. | FND-3 | todo |
| NAV-V1 | Val | Walk the navigation map: every arrow in [02](../requirements/02-screens-and-navigation.md#navigation-map) works, every Back to Home lands on S1, system back from S1 exits. | — | todo |
| NAV-T1 | UI | Nav test with `TestNavHostController`: each Home button opens the right route; Back to Home from each route returns to `home` with a one-entry back stack. | FND-1 | todo |
| NAV-T2 | UI | Rotation test on a feature screen keeps typed input and results. | NAV-4-I | todo |

## UI conventions

| ID | Requirement | Status | Notes |
|----|-------------|--------|-------|
| UI-1 | App bar "SM Assistant", yellow section chip, black Back to Home footer on every screen | done | `SmScreen` |
| UI-2 | Colour roles: blue menu/Post buttons, green SAVE/Submit, dark prompt card | partial | Colours defined; prompt card and Post in LinkedIn button not built |
| UI-3 | Header and footer fixed; body scrolls | partial | API Status scrolls; settings and chat screens don't yet |
| UI-4 | Components: menu button, labelled field, primary button, chat input bar, prompt card, result view, toggle | partial | Prompt card, chat input bar (growing), result view missing |
| UI-5 | Final visual design beyond Office theme colours | blocked | No design yet; not needed for MVP |

| ID | Type | Task | Depends on | Status |
|----|------|------|-----------|--------|
| UI-2-I | Impl | `PostInLinkedInButton` (blue, full width) and `PromptCard` (dark, title, copy + expand icons) in `ChatComponents.kt`. | — | done |
| UI-3-I | Impl | Make the body slot of `SmScreen` scrollable by default (opt-out for chat screens that use a `LazyColumn`). Keep IME padding so the input bar stays above the keyboard. | — | todo |
| UI-4-I | Impl | `ChatInputBar`: multi-line field that grows to ~6 lines then scrolls, green Submit on the right, disabled when blank or busy. | — | done |
| UI-V1 | Val | Compare each screen with its wireframe slide (colours, order chip → body → action → footer). List differences in the PR. | UI-2-I, UI-4-I | todo |
| UI-T1 | UI | `SmScreen` shows title, chip text and Back to Home; omits footer when `onBackToHome` is null. | FND-1 | todo |
| UI-T2 | UI | `PromptCard` copy puts the full text on the clipboard; expand opens a full-screen dialog. | UI-2-I | done (`PromptCardTest`) |
| UI-T3 | UI | `ChatInputBar` Submit disabled for blank input and while busy. | UI-4-I | done (`ChatScreenTest`) |
| UI-T4 | UI | Screenshot tests (Roborazzi or Paparazzi) for Home, a settings screen, API Status, hashtags, a chat screen with a result. | FND-1 | todo |
