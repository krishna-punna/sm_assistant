# 20. Pipeline status: where a post is, from prompt to LinkedIn

Source: Krishna, project thread, 2026-10-31. Design: [docs/design/pipeline-status.md](../design/pipeline-status.md).
Tasks: [16-pipeline-status.md](../tasks/16-pipeline-status.md).

> "Add Pipelines from Prompt Submission to till Image Posting in UI screen. This Heps users to understand where it is.
> It should be simpl piple with stages and status. It is application for ll screens where we post to linked in. Like
> Text Generation, Image Genration, Video Generation, Text and Image generation, Text and Video Generation. Addition to
> this, In schedular also a piple stage shoudl show the statsu. Add this for schedular also"

## Requirements

| ID | Requirement |
|----|-------------|
| PS-1 | Every screen that posts to LinkedIn shows a pipeline for each prompt: Generate Technical Content, Generate Image, Generate Video, Content & Image, Content & Video. |
| PS-2 | The pipeline is a simple row of stages, from the prompt being submitted to the post being on LinkedIn. Each stage has one status: waiting, running, done, failed or skipped. |
| PS-3 | One line under the stages says, in words, what is happening now ("Drawing the image…", "Ready: tap Post in LinkedIn", "Posted"). A failed stage's line gives the error. |
| PS-4 | The stages follow the real work: a stage is running only while the app is doing that step, and the next one starts when it finishes. Steps that run at the same time (the post text and the image in Content & Image) can both be running. |
| PS-5 | The last stage, Post, follows the Post in LinkedIn flow for that result: running while the confirm dialog is open or the post is being sent, done once it is on LinkedIn, failed when LinkedIn refused it. Closing the dialog without posting puts it back to waiting. |
| PS-6 | A pipeline that was running when the user left the screen keeps going and shows its current stage when the screen opens again. After the app is restarted, a stage that was running shows as failed ("Stopped"), like the result does today. |
| PS-7 | Scheduled Posts: each scheduler card shows the pipeline of its run: live while a run is going (Run now or a planned run), and the last run's stages after it ends, with the stage it stopped at. |
| PS-8 | The scheduler's stages depend on its post type: Topic, Prompt, Write post (when the type has text), Make image or Make video (when it has media), Check length, then Post, or Hold for review when "Review before posting" is on. |
| PS-9 | Stages use the theme's colours and are readable in every theme, in light and dark; TalkBack reads each stage with its status, and the status line is announced when it changes. |
| PS-10 | It fits a phone: stage labels are one or two short words and the row scrolls sideways instead of wrapping when a pipeline has many stages. |

## Stages by screen

| Screen | Stages |
|--------|--------|
| Generate Technical Content | Prompt · Write post · Post |
| Generate Image | Prompt · Write content · Draw image · Post |
| Generate Video | Prompt · Write slides · Draw slides · Make video · Post |
| Content & Image | Prompt · Write post · Write content · Draw image · Post |
| Content & Video | Prompt · Write post · Write slides · Draw slides · Make video · Post |
| Scheduler | Topic · Prompt · Write post · Make image / Make video · Check length · Post / Hold for review |
