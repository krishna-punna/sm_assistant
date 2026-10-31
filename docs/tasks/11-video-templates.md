# 11. Video prompt templates

Requirements: [16](../requirements/16-video-prompt-templates.md). Design: [video-templates.md](../design/video-templates.md).
Status: implemented 2026-10-30 (PRs #105–#112); device checks VT-V1 and VT-V2 left. One PR per implementation task, in order.
Replaces IT-I12 in [10-image-templates.md](10-image-templates.md).

## Implementation

| ID | Task | Depends on | Status |
|----|------|-----------|--------|
| VT-I1 | `assets/prompts/video_templates.md`: templates 1–10 with the image template ids, "Use when" lines and `{{content}}`, `{{audience}}`, `{{duration}}`, `{{format}}`, `{{style}}`, `{{brand}}` slots; narration fixed to on-screen text, rendering lines pointed at the app's renderer, video sizes only (9:16, 1:1, 16:9). Same parser; `fill` takes the duration. No change in the app yet. | — | done |
| VT-I2 | Generate Video, Content & Video and scheduled videos run `TemplateChooser` on the technical content (the prompt when there is none); `VideoBrief(template, content, seconds, format, style)`; `SlideDeckWriter` gets the filled template; `video_deck.md` gets the content-to-video adapter rules and the Series consistency rules. Universal on any failure. | VT-I1 | done |
| VT-I3 | Storyboard fields: deck `takeaway`, scene `purpose` and `seconds`; times scaled to the picked length (at least 3 s a scene); `SlideshowMaker` takes a duration per slide. | VT-I2 | done |
| VT-I4 | Video size per template, separate from image sizes (Krishna, 2026-10-30): `VideoFormat` vertical 9:16 1080×1920 (default), square 1:1 1080×1080, landscape 16:9 1920×1080; slides drawn at that size, the MP4 at its size. | VT-I2 | done |
| VT-I5 | Step-by-step reveal: a `reveal` scene is drawn once per section, adding one each time, sharing the scene's time. | VT-I3 | done |
| VT-I6 | Short fades between scenes from blended frames. | VT-I3 | done |
| VT-I7 | Template checks over the storyboard (`templateProblems` across scenes), scene times add up, and the vision review of each scene with the template's rules (one re-plan). | VT-I2 | done |
| VT-I8 | The template and shape in the video's file name; the Video screen shows "Workflow · 9:16"; scheduled runs record it ("Video · Workflow"). | VT-I2, IT-I13 | done |

## Tests

| ID | Kind | Test | Status |
|----|------|------|--------|
| VT-T1 | unit | Asset parses into 10 templates with the image ids, each with `{{content}}`; filling leaves no `{{…}}` or `[PASTE…]`; only 9:16, 1:1 and 16:9 mentioned (`VideoTemplatesTest`). | done |
| VT-T2 | unit | Generate Video calls content → chooser → deck writer with the filled template; a failed choice gives Universal; the prompt never reaches the slides (`GenerateVideoTest`). | done |
| VT-T3 | unit | Scene times scale to the length with the minimum; a reveal scene becomes one picture per section (`SlideTimingTest`). | done |
| VT-T4 | unit | Template checks find invented numbers and missing workflow steps in a deck (`TemplateCheckTest`). | done |

## Validation

| ID | Check | Status |
|----|-------|--------|
| VT-V1 | On a device, video prompts of each kind (a concept, a system design, X vs Y, a lifecycle, a code snippet, best practices, benchmark numbers, an interview topic) pick the matching template and the scenes follow it. | todo |
| VT-V2 | A scheduled video post uses a template without any setting changed. | todo |
