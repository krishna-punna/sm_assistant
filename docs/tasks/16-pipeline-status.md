# 16. Pipeline status on the generation screens and the scheduler

Requirements: [20](../requirements/20-pipeline-status.md). Design: [docs/design/pipeline-status.md](../design/pipeline-status.md).
Stacked PRs into `f-mobile-app`, one per task, merged in order.

## Implementation

| ID | Task | Req | Status |
|----|------|-----|--------|
| PL-1 | Docs 20, design and these tasks. `pipeline/` model: `StageId`, `StageStatus`, `Stage`, `Pipeline` (transitions, `statusLine`, JSON), `Pipelines` per screen and scheduler post type, `StageReporter` + `stage {}`. JVM tests. | PS-2, PS-3, PS-8 | PR #136 |
| PL-2 | `PipelineRow` composable (marks, connector line, status line, TalkBack, sideways scroll) in `ui/components/`. Compose test. | PS-2, PS-3, PS-9, PS-10 | PR #137 |
| PL-3 | `ChatViewModel` keeps a pipeline per input (submit, Ok, Failed, NoAgent, Cancel, Retry, half retries, save/restore); `ChatScreen` shows it under each prompt; the five screens pass their pipeline. | PS-1, PS-4, PS-6 | PR #138 |
| PL-4 | Post stage: `PostViewModel` keyed posts; the five screens mark `POST` from the Post in LinkedIn flow. | PS-5 | PR #139 |
| PL-5 | Real stages from the use cases: `stage {}` in text, image (content, drawing) and video (slides, drawing, encoding). | PS-4 | PR #140 |
| PL-6 | Scheduler: `AutoPostRun` stages per post type, worker progress, live and last-run pipeline on the scheduler card, `RunRecord.stages`. | PS-7, PS-8 | in progress |

## Validation (on the phone)

| ID | Check | Status |
|----|-------|--------|
| PL-V1 | On each of the five screens, a prompt shows its stages moving one after another, then "Ready: tap Post in LinkedIn", then Posted after posting. | todo |
| PL-V2 | A failing agent (wrong key) fails the stage it was on, with the error. Cancel fails the running stage with "Cancelled". | todo |
| PL-V3 | Leaving the screen during a video and coming back shows the current stage. | todo |
| PL-V4 | Run now on a scheduler shows its stages moving on the card; after the run the card shows where it ended. | todo |
| PL-V5 | Stages read well in every theme, light and dark, and with TalkBack. | todo |
