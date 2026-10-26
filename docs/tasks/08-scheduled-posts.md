# 08 — Scheduled LinkedIn posts

Requirement: [13 Scheduled LinkedIn posts](../requirements/13-scheduled-posts.md). Built in three PRs: the scheduler model, the background run, then the screens.

## Status by requirement

| ID | Requirement | Status | Gap |
|----|-------------|--------|-----|
| SC-1 | Topic → Prepare Prompt, avoiding recent titles | done | `AutoPostRun.topicRequest` |
| SC-2 | Prompt → Generate Technical Content (+ images / video) | done | `AutoPostRun`, with the screens' generators |
| SC-3 | Searchable multi-select of 200 technical content types | done | `TechTopics`; picker on the editor |
| SC-3a / 3b | Validate length with default hashtags, then post | done | `AutoPostRun`; media failure posts nothing |
| SC-4 | Every few hours / daily / weekly / monthly | done | `Schedule`; options on the editor |
| SC-5 | Post type Text / Text with image / Text with video | done | |
| SC-6 | Topics take turns | done | `AutoPostScheduler.currentTopic` / `advanced` |
| SC-7 | Result on the scheduler and in a notification | done | `lastRun` on the list with Open post; `RunNotifier`; Allow notifications prompt on Android 13+ |
| SC-8 | Several schedulers, each on or off | done | Scheduled Posts list |
| SC-9 | Optional name | done | |
| SC-10 | Run now | done | |
| SC-NFR1…4 | WorkManager, no catch-up bursts, retries, existing secrets | done | `SchedulerEngine`, `AutoPostWorker`; Doze note on the editor |

## Implementation

| ID | Task | Depends on | Status |
|----|------|-----------|--------|
| SC-I1 | `Schedule`: frequency, times, days, day of month; `nextRun(after)` in local time; `describe()` | — | done |
| SC-I2 | `AutoPostScheduler`, `PostType`, `RunRecord`; JSON (`SchedulerJson`) and `SchedulerStore` (SharedPreferences, one lock); `TechTopics` catalog with search | SC-I1 | done |
| SC-I3 | `AutoPostRun`: topic → `PreparePromptUseCase` → `GenerateTextUseCase` (+ images / video) → `PostToLinkedInUseCase.prepare` check → `post`; returns a `RunRecord` | SC-I2, PP, TC, IM, VI, LP | done — `AutoPostRun`, `appAutoPostRun` |
| SC-I4 | WorkManager: `AutoPostWorker` (network constraint, retry on network errors), `SchedulerEngine` (one unique one-time work per scheduler at `nextRun`, re-planned after each run, on save, on toggle and at app start), Run now, result notification | SC-I3 | done — `SchedulerEngine`, `AutoPostWorker`, `RunNotifier`; `MainActivity` calls `ensurePlanned` at start |
| SC-I5 | Schedulers list screen (Home → Scheduled Posts): each scheduler's name, schedule, post type, next run, last result; on/off switch; Add, Edit, Delete, Run now | SC-I4 | done — `SchedulersScreen`, `SchedulersViewModel` |
| SC-I6 | Scheduler editor: name, searchable multi-select of topics with chips, post type, frequency with its options, save validation (`problem()`), Doze note | SC-I5 | done — `SchedulerEditorScreen`, `SchedulerEditorViewModel` |

## Tests

| ID | Test | Type | Status |
|----|------|------|------|
| SC-T1 | Next-run times for each frequency, windows, weekdays only, short months, clock change | unit | done — `ScheduleTest` |
| SC-T2 | Topic rotation, recent titles, validation, JSON round trip and bad JSON | unit | done — `AutoPostSchedulerTest` |
| SC-T3 | Store add / replace / delete / update | Robolectric | done — `SchedulerStoreTest` |
| SC-T4 | 200 distinct topics incl. the samples; search | unit | done — `TechTopicsTest` |
| SC-T5 | Run pipeline: happy path per post type, no agent, over-limit post not posted, media failure not posted, LinkedIn errors | unit | done — `AutoPostRunTest` |
| SC-T7 | Planning: next run, off/no topics cancel, one unique work per scheduler, app-start planning | Robolectric + work-testing | done — `SchedulerEngineTest` |
| SC-T6 | Screens: list, editor search/multi-select, save validation; view models | Robolectric UI | done — `SchedulerScreensTest`, `SchedulerViewModelsTest`; Home button in `NavigationTest` |

## Validation

| ID | Check | Status |
|----|-------|--------|
| SC-V1 | On a device: a daily scheduler a few minutes ahead posts once, with the app closed; the list shows the link | todo |
| SC-V2 | Restart the phone: the next run still happens | todo |
