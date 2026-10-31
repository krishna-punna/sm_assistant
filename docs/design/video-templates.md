# Video prompt templates

Status: **plan, waiting for Krishna's approval** (2026-10-30). Nothing here is implemented yet.
Source: Krishna's template file, kept as [requirements 16](../requirements/16-video-prompt-templates.md).
Tasks: [docs/tasks/11-video-templates.md](../tasks/11-video-templates.md). Replaces IT-I12 of the image templates.

## What Krishna asked for

"Use the attached templates while generating technical content videos." "Agent should auto decide
which template should pick based on the prompt and technical content asked by user."

So when the app makes a video (Generate Video, Content & Video, and scheduled video posts), the AI
agent reads the prompt and the technical content written for it, picks the best video template, and
plans the video by that template. There is no template picker: the choice is always the agent's.

## How a video is made today

From [image-video-pipeline.md](image-video-pipeline.md) and Requirement 9:

```
prompt ──► MediaContentWriter (text agent, no-limit content, media_content.md)
              │
              ▼
        SlideDeckWriter (one text-agent call, video_deck.md): N square slides as JSON, N from the length
              │
              ▼
        InfographicRenderer per slide (all text drawn by the app) ──► AI illustration per slide (image agent, no text)
              │
              ▼
        Media3SlideshowMaker: 1080 x 1080 MP4, every slide on screen for the same time, no audio
```

There is no video model and no audio. Without a text agent the image agent paints each slide whole
(`video.md`); that fallback stays as it is.

## How the templates fit

The templates are written for "a video-generation model or a workflow that uses a language model to
create a storyboard and a video model to render scenes", and they warn that video models garble
labels, code and diagrams: "generate a storyboard first, validate it, then render scenes", and "add
exact code, labels, captions, and diagrams using a video editor or deterministic renderer".

The app already is that workflow, with the deterministic renderer drawing every scene. So:

| Template in the file | What the app does with it |
|---|---|
| 1 Universal Master, 2–10 (concept, architecture, workflow, comparison, code, checklist, data, learning, series) | The 10 video templates. One is picked per video and its rules shape the storyboard. |
| 11 Content-to-video adapter | Already the flow: the content step writes the technical content, then the storyboard is planned from it, and the prompt itself is never shown on screen. Its rules go into `video_deck.md`. |
| 12 Scene-by-scene prompts for video models | Not used: the app has no video model. Each scene's illustration prompt keeps the rule "no labels in generated footage". Kept for a future text-to-video agent. |
| 13 Output specification | Becomes the storyboard JSON (section 4): the fields the app can use. |
| 14 Workflow, "fields to store" | Matches the app's steps; the template id is kept with the video (section 7). |

The template ids are the image template ids (`universal`, `concept`, `architecture`, `workflow`,
`comparison`, `code`, `checklist`, `data`, `learning`, `series`), so the chooser and its
`choose_template.md` table are shared.

## Plan

### 1. The template asset

`assets/prompts/video_templates.md` holds templates 1–10 in the same block format as
`image_templates.md` (`Id:`, `Name:`, `Use when:`, `Template:`), read by the same parser. The
`[PASTE …]` and `[OPTIONAL …]` fields become slots: `{{content}}`, `{{audience}}`, `{{duration}}`,
`{{format}}`, `{{style}}`, `{{brand}}`. Lines that don't fit the app are adapted, and nothing else
changes:

* **Narration.** The app has no audio, so the Narration input reads "on-screen text only; the video
  plays without sound". Subtitles aren't needed, since every word is on screen.
* **Rendering.** "Render the video" and "add the code in post-production" become "the app draws each
  scene from your storyboard, with exact labels, code and charts". "Do not claim that a finished
  video has been rendered" stays.
* **Format.** Videos use the standard video sizes only, separate from the image sizes (Krishna,
  2026-10-30): vertical 9:16 (1080×1920, the default), square 1:1 (1080×1080) and landscape 16:9
  (1920×1080), picked per template. Images keep 2:3, 1:1 and 3:2.

### 2. Choosing the template (automatic)

After the content step, the same `TemplateChooser` call reads the technical content (or the prompt
when there is none) and returns a primary template and alternates. The video follows the primary
template. A failed or unusable choice gives the Universal Master, so a video never fails because of
templates. There is no picker on the Video screens, and the scheduler needs no setting.

The Series template is never picked. Its consistency rules (one palette, typography, icon and motion
style, no facts carried over from earlier videos) are always added, because a video is a series of
scenes by nature.

### 3. The filled template goes to the storyboard writer

`SlideDeckWriter` gets the filled video template after the technical content, the way
`InfographicWriter` gets the image template: the template decides the story's shape (hook,
mechanism, clarification, takeaway for a concept; components revealed in order for an architecture;
one step per scene for a workflow; equal criteria per side for a comparison). The content isn't
repeated. The Image Style inputs (audience, style guide, brand name) fill the matching slots, and
the length picked on the screen fills `{{duration}}`.

### 4. The storyboard

The deck JSON gains the fields from template 13 that the app can use:

| Field | Use |
|---|---|
| `title`, `takeaway` (deck) | The video's title and central takeaway, also checked against the content. |
| `purpose` (scene) | One line on what the scene is for; helps the review and keeps one idea per scene. |
| `seconds` (scene) | How long the scene stays up. The app scales them to the picked length, with at least 3 s per scene, so a dense scene stays up longer than a title scene. |
| `reveal` (scene) | For workflow and architecture scenes: the scene is shown in steps, one more section each step (section 5). |
| scene content (`title`, `sections`, `hero`, …) | As today; "exact on-screen text" is what the app draws. |

Fields the app can't use (narration, transition, camera, continuity notes) are left out, which keeps
the reply small enough for 10 scenes. `video_deck.md` keeps the length rules and gets the template-11
rules: pick one coherent learning objective and a subset of the content, never put the prompt on
screen, don't invent facts.

### 5. Motion the app can draw

* **Step-by-step reveal.** A `reveal` scene is drawn once per section, each picture adding one more
  section, and the scene's time is shared between them. This is the "animate the process
  progressively" and "reveal the components in a readable order" that the workflow and architecture
  templates ask for, drawn exactly.
* **Fades.** A short fade between scenes, made from a few blended frames on the device.

### 6. Checks

* Template checks run over the whole storyboard, as for images (IT-I9): Data numbers must come from
  the content, Code must be the supplied code, Comparison needs a compare or table scene, Workflow
  needs steps or a flow, Architecture a diagram or flow.
* The scene times must add up to the length, and no scene repeats another.
* The vision review (`image_checks` in app.yml) looks at each finished scene with the template's
  rules. A scene it scores too low is planned again once.

### 7. What the user sees

* The Video screen shows the template under the video ("Workflow · 9:16"). The video file name carries
  the template and shape, as images do, so saved results keep it.
* A scheduled video's run history shows it ("Video · Workflow"), with IT-I13.

## Decided: video shape

Krishna (2026-10-30): image and video sizes must differ, and videos use standard video sizes only.
A new `VideoFormat` (vertical 9:16, square 1:1, landscape 16:9, at 1080 px on the short side) is
used for videos; `ImageFormat` stays for images. Each template has a default size: landscape for
architecture, comparison and data; vertical for the rest.

## Not in this plan

* Voice-over and subtitles: the app makes silent videos. Android's text-to-speech could read a
  narration track later; that would be a separate request.
* A text-to-video model (template 12): none of the app's agents makes video clips.

## Refactoring needed

| Area | Change |
|---|---|
| `ImageTemplate` / parser | Shared by both assets; `fill` gets a `{{duration}}` slot. |
| `GenerateVideoUseCase` | Runs the chooser after the content step; builds a `VideoBrief(template, content, seconds, format, style)`. |
| `SlideDeckWriter.write` | Takes the brief; adds the filled template. |
| `parseSlideDeck` / `SlideDeck` | Deck `takeaway`, scene `purpose`, `seconds` and `reveal`. |
| `SlideshowMaker.make` | Takes a duration per slide and the video's size. |
| `GeneratedVideo` | Its file name carries the template and shape. |

## Rollout

One PR per task, in this order (details in [docs/tasks/11](../tasks/11-video-templates.md)). VT-I1
changes nothing the user sees; from VT-I2 videos follow the chosen template.

| PR | Task | Content |
|---|---|---|
| 1 | VT-I1 | Video template asset and parser |
| 2 | VT-I2 | The agent picks the template; the storyboard follows it |
| 3 | VT-I3 | Storyboard fields: scene times, purpose, takeaway; per-scene durations |
| 4 | VT-I4 | Video shape per template |
| 5 | VT-I5 | Step-by-step reveal |
| 6 | VT-I6 | Fades between scenes |
| 7 | VT-I7 | Template checks and the vision review for scenes |
| 8 | VT-I8 | Template label on the Video screen and in run history |
