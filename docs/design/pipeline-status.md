# Pipeline status

Status: **planned**, implementation in progress (tasks [16](../tasks/16-pipeline-status.md)).
Requirements: [20](../requirements/20-pipeline-status.md).

## What the user sees

Under each prompt card in a generation screen, before the result, a pipeline row:

```
 ●───────●───────◐───────○
Prompt  Write   Draw    Post
        content image
Drawing the image…
```

| Status | Mark | Colour (from `MaterialTheme.colorScheme`) |
|--------|------|-------------------------------------------|
| Waiting | hollow circle | `outline` |
| Running | small spinner in the circle | `primary` |
| Done | filled circle with a tick | `primary` |
| Failed | filled circle with a cross | `error` |
| Skipped | hollow circle with a dash, label dimmed | `outlineVariant` |

The line between two stages is filled once the stage on its left is done. The status line under the row is
one sentence from the stage that is running (or the last one that finished or failed), announced politely to
TalkBack. Each stage's content description is "<label>: <status>". The row scrolls sideways when it doesn't fit.

The same `PipelineRow` composable is used on every screen and on the scheduler card, so the look is the same
everywhere. It takes only a `Pipeline` value, so it has no knowledge of where the stages come from.

## The model (pure Kotlin, `pipeline/`)

```kotlin
enum class StageStatus { WAITING, RUNNING, DONE, FAILED, SKIPPED }

enum class StageId(val label: String, val running: String) {
    TOPIC("Topic", "Choosing the topic…"),
    PROMPT("Prompt", "Prompt submitted"),           // a manual screen's prompt is done as soon as it is sent
    PREPARE_PROMPT("Prompt", "Preparing the prompt…"),
    WRITE_POST("Write post", "Writing the post…"),
    WRITE_CONTENT("Write content", "Writing the image content…"),
    DRAW_IMAGE("Draw image", "Drawing the image…"),
    MAKE_IMAGE("Make image", "Making the image…"),   // the scheduler's single media stage
    WRITE_SLIDES("Write slides", "Writing the slides…"),
    DRAW_SLIDES("Draw slides", "Drawing the slides…"),
    MAKE_VIDEO("Make video", "Making the video…"),
    CHECK_LENGTH("Check length", "Checking the length…"),
    POST("Post", "Posting to LinkedIn…"),
    HOLD("Hold for review", "Waiting for your review"),
}

data class Stage(val id: StageId, val status: StageStatus = WAITING, val note: String? = null)

data class Pipeline(val stages: List<Stage>) {
    fun start(id), fun done(id), fun fail(id, note), fun skip(id), fun reset(id)
    val statusLine: String   // the words under the row (PS-3)
    fun failRunning(note)    // a cancelled or stopped generation
    fun finishGeneration()   // every generation stage that isn't failed is done; Post is waiting ("Ready: tap Post in LinkedIn")
}

object Pipelines { val text; val image; val video; val contentAndImage; val contentAndVideo; fun scheduler(type, reviewFirst) }
```

`Pipeline` is immutable; every change returns a new value, so it sits in a `StateFlow` and Compose redraws it.
It is encoded to JSON (`id` and `status` per stage, plus a note) to be saved with the conversation and with a
scheduler's last run.

### Reporting stages without changing every signature

The generation code runs deep (`GenerateImagesUseCase` → infographic writer → renderer → checks). Rather than
add a callback to every function, the stage reporter rides in the coroutine context:

```kotlin
class StageReporter(val report: (StageId, StageStatus, String?) -> Unit) : AbstractCoroutineContextElement(Key)

/**
 * Runs [block] as stage [id]: running before, done after. When [failed] says the result is a failure (an
 * `Outcome.Failed` or `ApiResult.Failed`, which the generation code returns rather than throws), or the block
 * throws, the stage fails. Does nothing without a reporter.
 */
suspend fun <T> stage(id: StageId, failed: (T) -> String? = { null }, block: suspend () -> T): T
suspend fun reportStage(id: StageId, status: StageStatus, note: String? = null)
```

A use case marks its steps with `stage(StageId.DRAW_IMAGE) { … }`. Called from a test or from code without a
reporter, nothing happens, so existing tests and callers are unchanged. A stage id that isn't in the screen's
pipeline is ignored, so the image use case can report `WRITE_CONTENT` whether it is called from Generate Image
or from the scheduler (which folds it into `MAKE_IMAGE`, see below).

Where each step is marked:

| Step | Code | Stage |
|------|------|-------|
| Post text | `GenerateTextUseCase.invoke` | `WRITE_POST` |
| Image content | `GenerateImagesUseCase.infographics` content step, template chooser, and the painted path's prompt | `WRITE_CONTENT` |
| Image drawing | infographic write-draw-review loop, painted images, carousel pages, upscale, size guard | `DRAW_IMAGE` |
| Slide deck | `GenerateVideoUseCase.deckSlides` writing (deck writer, template chooser) | `WRITE_SLIDES` |
| Slide drawing | drawing each slide, painted fallback slides, size guard | `DRAW_SLIDES` |
| Encoding | timeline + `SlideshowMaker.make` | `MAKE_VIDEO` |

## Generation screens

`ChatUiState` gets `pipelines: Map<Long, Pipeline>`, keyed by the input's id. `ChatViewModel` takes the
feature's `Pipeline` template:

1. `submit()` (and Retry / Regenerate) puts a fresh copy for the input with `PROMPT` done.
2. `run()` runs `generate` inside `withContext(StageReporter { … })`, which updates that input's pipeline.
3. On `Outcome.Ok`, `finishGeneration()`: anything still waiting or running in the generation stages is done
   (a stage the use case didn't report, e.g. text from an agent without content step). On `Failed`, the running
   stage (or the first waiting one) fails with the error. On `NoAgent`, the first waiting stage fails with
   "No agent set up". On Cancel, the running stage fails with "Cancelled".
4. Content & Image / Content & Video: text and media run at the same time, so `WRITE_POST` and `WRITE_CONTENT`
   can both be running. A half that fails fails only its own stages; Retry text / Retry images (`updateResult`)
   run the same reporter, so the failed stage goes back to running.

Posting (PS-5): `PostViewModel.start` gets an optional `key` (the input id), kept in a `postingKey` field.
`ChatScreen` gets an `onPostState` hook from the screen: on `Confirm` / `Posting` the keyed pipeline's `POST`
is running, `Posted` done, `Failed` failed with the message, `Idle` / `DraftSaved` after Confirm back to waiting.
`ChatViewModel.markPost(inputId, status, note)` applies it.

Saving (PS-6): the pipeline is written with its input in the saved conversation (`"stages": […]`); a restored
running stage becomes failed "Stopped", like a restored `Loading` item. Old saved conversations without
`stages` show no pipeline for those inputs.

Placement: `ChatScreen` draws `PipelineRow(pipeline)` right under each input's prompt card, so the pipeline
and the result it leads to are read together, and each earlier prompt keeps its own finished pipeline.

## Scheduler

`AutoPostRun.invoke` runs inside a `StageReporter` given by its caller and marks its own steps:
`TOPIC` (choose topic, sign-in checks), `PREPARE_PROMPT`, `WRITE_POST` and `MAKE_IMAGE` / `MAKE_VIDEO` (in
parallel), `CHECK_LENGTH`, then `POST` or `HOLD`. The image and video use cases' own finer stages
(`WRITE_CONTENT`, `DRAW_IMAGE`, …) aren't in the scheduler's pipeline, so they are ignored there; the run wraps the
whole media step in `stage(MAKE_IMAGE)` / `stage(MAKE_VIDEO)`.
A post type without text skips `WRITE_POST`; a text post skips the media stage (PS-8).

Live progress (PS-7): `AutoPostWorker` gives the run a reporter that calls `setProgress(workDataOf("pipeline" to json))`
(WorkManager progress data, under the 10 KB limit). `SchedulersViewModel` collects
`WorkManager.getWorkInfosForUniqueWorkFlow` for each scheduler's planned and Run now work names and shows the
running one's progress. Progress outlives the screen and works when the run is in the worker's own process.

After a run: `RunRecord` gets `stages` (the final pipeline, JSON, saved with `lastRun`), so the card shows where
the last run stopped, e.g. "Make image" failed with the error, or "Hold for review" running ("Waiting for your
review") until it is approved, then `POST` done. A skipped run ("still waiting for your review") keeps the
earlier run's pipeline.

`SchedulerCard` shows `PipelineRow` between the schedule line and the last-run message.

## Out of scope

- No timings or percentages per stage: the steps' lengths vary too much to be honest about.
- No pipeline on the Drafts or Post History screens; their posts are already made.
- No change to notifications; the scheduler's foreground notification keeps its indeterminate progress.

## Tests

- `PipelineTest` (JVM): transitions, `finishGeneration`, `statusLine`, JSON round trip, restored running → failed.
- `StageReporterTest`: `stage {}` reports running/done/failed, nested reporters, no reporter = no-op.
- `ChatViewModelTest` additions: submit → `PROMPT` done; Ok → generation done and `POST` waiting; Failed /
  NoAgent / Cancel mark the right stage; `markPost`; save and restore.
- Use case tests: fake agents check `GenerateImagesUseCase` / `GenerateVideoUseCase` / `GenerateTextUseCase`
  report their stages in order.
- `AutoPostRunTest` additions: stages per post type, review-first ends at `HOLD`, failure stage, skipped stages.
- Compose UI test (`PipelineRowTest`): labels and content descriptions per status.
