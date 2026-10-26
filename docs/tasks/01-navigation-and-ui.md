# 01 — Navigation and UI conventions (P1)

Requirements: [02 Screens and navigation](../requirements/02-screens-and-navigation.md), [08 UI conventions](../requirements/08-ui-conventions.md).

## Navigation

| ID | Requirement | Status | Notes |
|----|-------------|--------|-------|
| NAV-1 | Home (S1) shows LinkedIn API, AI Agent, SM Assistant, Default Hash Tags, API Status | done | One AI Agent button (Q-01 interim) |
| NAV-2 | All screens S1–S15 exist and are reachable per the navigation map | done | S8–S13 each have their own screen; the S10 preview is a full-screen dialog |
| NAV-3 | Back to Home on every screen except Home returns to Home and clears the back stack | done | Hub also has it (Q-02 interim); system back pops one level |
| NAV-4 | Survive rotation / process death without losing typed input or results | done | `ChatViewModel` keeps the typed input and the conversation in `SavedStateHandle`; media results come back while their files are still in the cache, otherwise as an error with Retry |

| ID | Type | Task | Depends on | Status |
|----|------|------|-----------|--------|
| NAV-2-I | Impl | Split `FeatureScreen` into the chat screen (GEN-I1) and add the image preview route `hub/IMAGE/preview/{index}`. | GEN-I1 | done — every feature has its own screen and `FeatureScreen` is gone; the preview is a full-screen dialog rather than a route, so the generated files don't need to go through nav arguments |
| NAV-4-I | Impl | Keep chat history and results in the ViewModel; restore input from `SavedStateHandle`. | FND-3 | done (`ResultSaver` per feature) |
| NAV-V1 | Val | Walk the navigation map: every arrow in [02](../requirements/02-screens-and-navigation.md#navigation-map) works, every Back to Home lands on S1, system back from S1 exits. | — | todo |
| NAV-T1 | UI | Nav test with `TestNavHostController`: each Home button opens the right route; Back to Home from each route returns to `home` with a one-entry back stack. | FND-1 | done (`NavigationTest`, with the app's own `NavHostController`; API Status is left out because it calls the services) |
| NAV-T2 | UI | Rotation test on a feature screen keeps typed input and results. | NAV-4-I | done (`RotationTest`; process death is covered in `ChatViewModelTest`) |

## UI conventions

| ID | Requirement | Status | Notes |
|----|-------------|--------|-------|
| UI-1 | App bar "SM Assistant", yellow section chip, black Back to Home footer on every screen | done | `SmScreen` |
| UI-2 | Colour roles: blue menu/Post buttons, green SAVE/Submit, dark prompt card | done | |
| UI-3 | Header and footer fixed; body scrolls | done | `SmScreen` scrolls its body; chat screens scroll their list |
| UI-4 | Components: menu button, labelled field, primary button, chat input bar, prompt card, result view, toggle | done | `ChatComponents`, `MarkdownText`, `ImageComponents`, `VideoPlayer` |
| UI-5 | Final visual design beyond Office theme colours | blocked | No design yet; not needed for MVP |
| UI-6 | Themes: at least 5 themes plus the current look, each with its own font, button shape/style, icon style and colours; a Themes screen to pick one; a Home button to open it; the choice is saved | done | `SmThemes` (Classic + Midnight Neon, Sunset Pop, Forest Calm, Mono Brutalist, Royal Serif, Ocean Breeze), `ThemesScreen`, `ThemeStore`; fonts bundled from Google Fonts (OFL) |

| ID | Type | Task | Depends on | Status |
|----|------|------|-----------|--------|
| UI-2-I | Impl | `PostInLinkedInButton` (blue, full width) and `PromptCard` (dark, title, copy + expand icons) in `ChatComponents.kt`. | — | done |
| UI-3-I | Impl | Make the body slot of `SmScreen` scrollable by default (opt-out for chat screens that use a `LazyColumn`). Keep IME padding so the input bar stays above the keyboard. | — | done — `scrollable` parameter; Chat and API Status opt out; settings SAVE now sits under the fields |
| UI-4-I | Impl | `ChatInputBar`: multi-line field that grows to ~6 lines then scrolls, green Submit on the right, disabled when blank or busy. | — | done |
| UI-V1 | Val | Compare each screen with its wireframe slide (colours, order chip → body → action → footer). List differences in the PR. | UI-2-I, UI-4-I | todo |
| UI-T1 | UI | `SmScreen` shows title, chip text and Back to Home; omits footer when `onBackToHome` is null. | FND-1 | done (`SmScreenTest`, also checks a long body scrolls) |
| UI-T2 | UI | `PromptCard` copy puts the full text on the clipboard; expand opens a full-screen dialog. | UI-2-I | done (`PromptCardTest`) |
| UI-T3 | UI | `ChatInputBar` Submit disabled for blank input and while busy. | UI-4-I | done (`ChatScreenTest`) |
| UI-6-I | Impl | `SmTheme` in `LocalSmTheme`; `SmScreen`, `SmButton`, `SectionChip`, `PostInLinkedInButton` read colours, shapes and icons from it instead of fixed colours. | — | done |
| UI-T5 | UI | Themes screen: Classic selected and in use at first, Apply disabled until another theme is picked, Apply switches the app theme; store saves and restores the choice. | UI-6-I | done (`ThemesScreenTest`, `ThemeStoreTest`) |
| UI-T4 | UI | Screenshot tests (Roborazzi or Paparazzi) for Home, a settings screen, API Status, hashtags, a chat screen with a result. | FND-1 | todo |
