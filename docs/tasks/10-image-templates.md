# 10. Image prompt templates

Requirements: [15](../requirements/15-image-prompt-templates.md). Design: [image-templates.md](../design/image-templates.md).
Status: done 2026-10-30 (PRs #85 to #104), one PR per task. IT-I12 was replaced by the video templates. Only the device checks below are left.

## Implementation

| ID | Task | Depends on | Status |
|----|------|-----------|--------|
| IT-I1 | `assets/prompts/image_templates.md`: the 10 templates with ids, "Use when" lines and fixed `{{content}}`, `{{audience}}`, `{{style}}`, `{{brand}}`, `{{format}}` slots; sizes adapted to 2:3 / 1:1 / 3:2 (no 4:5, 16:9 or 9:16). `ImageTemplates.kt` parser and `fill(brief)`. No change in the app yet. | — | done |
| IT-I2 | `prompts/choose_template.md` (the "Choosing the right template" table) and `TemplateChooser`: the text agent classifies the technical content and returns a primary template and up to 2 alternates; anything unusable gives Universal Master. | IT-I1 | done |
| IT-I3 | `ImageBrief` (template, content, format, style) and `filled()`, `ImageFormat` (2:3 / 1:1 / 3:2 only) and `ImageMode`, `image_style` in app.yml read by `AppConfig.imageStyle`, `ImageRequest(prompt, template, format)` taken by `GenerateImagesUseCase.generate`. Behaviour unchanged. Results as `GeneratedImage(file, template, format)` moved to IT-I4, where the label is first shown. | IT-I1 | done |
| IT-I4 | Generate Image (and Content & Image, scheduler) runs the chooser after the content step; `InfographicWriter` gets the filled template as its brief (the content isn't repeated); the 4 images follow `planImages` (primary, primary in its other format, first alternate, Universal); the brief's format replaces the model's. All 4 are still app-drawn until IT-I11. | IT-I2, IT-I3 | done |
| IT-I5 | Template picker (Auto + 10 names) and Format picker (Auto, Portrait 2:3, Square 1:1, Landscape 3:2) on Image and Content & Image, saved in `AgentPreferences`; each image shows its template and ratio ("Workflow · 2:3"), kept in the file name by `labelImage` so saved results and the scheduler need no new types. | IT-I4 | done |
| IT-I6 | Renderer: every image exactly 2:3, 1:1 or 3:2. Long content is laid out on a wider page with more columns (up to 2x the format's width); content too long even for that is centred on a page widened to the ratio. Brand name drawn in the footer, set from the brief, never by the model. | IT-I3 | done |
| IT-I7 | Renderer: `chart` section (horizontal bars with exact values and units) for the Data template; parser, limits, `infographic.md` docs. | IT-I4 | done |
| IT-I8 | Renderer: `diagram` section (components with optional groups, labelled arrows, layered by the longest chain of arrows; left to right when the layers fit, top to bottom otherwise; arrows that skip layers or point back run along lanes beside the boxes) for the Architecture template and for workflows that branch or loop. Branches are diagram edges, so `flow` stays linear. | IT-I4 | done |
| IT-I9 | Template-aware checks: `templateProblems` (Comparison needs a compare/table/pros_cons card, Workflow steps/flow/diagram, Architecture a diagram or flow, Code a code card whose lines are in the content, Data only numbers from the content), added to the writer's checks; the vision review gets the template's "- " rules as problem 7 in `image_review.md`. | IT-I4 | done |
| IT-I10 | `ImageAgent.generate` takes a format: Gemini `imageConfig.aspectRatio`, Pollinations `size` 1024x1536 / 1024x1024 / 1536x1024, Cloudflare square only; painted images under the minimum are upscaled 2x on the device. | IT-I3 | done |
| IT-I11 | AI-painted and Both modes (`image_style.mode`): the filled template is the image prompt (in full for Gemini, condensed to ≤1,500 characters by the text agent for FLUX on Cloudflare and Pollinations), then pixel checks and the vision review. Both is the default mode (Krishna, 2026-10-30): images 1 and 3 drawn, 2 and 4 painted. | IT-I9, IT-I10 | done |
| IT-I12 | Video: replaced by Krishna's own video templates (2026-10-30), see [11-video-templates.md](11-video-templates.md). | IT-I4 | replaced |
| IT-I13 | Scheduler: run history shows the template used; a schedule can fix a template and format (default Auto) in `SchedulerStore`. | IT-I5 | done |
| IT-I14 | `image_examples.md`: a `template:` tag on each of the 21 examples; `pickImageExamples` prefers examples of the chosen template. | IT-I4 | done |
| IT-I15 | Image Style settings screen (audience, brand name, style guide, series on/off, mode), overriding app.yml `image_style`. Split out of IT-I5. | IT-I5 | done |

## Tests

| ID | Kind | Test | Status |
|----|------|------|--------|
| IT-T1 | unit | Asset parses into 10 unique templates, each with `{{content}}`; filling leaves no `{{…}}` or `[PASTE…]`; no asset has `4:5`, `16:9` or `9:16` (`ImageTemplatesTest`). | done |
| IT-T2 | unit | Chooser reads ids, ignores unknown ones, falls back to Universal on a bad reply or failed call (`TemplateChooserTest`). | done |
| IT-T3 | unit | Generate Image calls content → chooser → 4 writers with the planned templates and formats; a user-picked template skips the chooser (`GenerateImagesTest`). | done |
| IT-T4 | unit | Portrait and landscape render at exactly their ratio for long content, square too after widening; the brand name is drawn; chart and diagram sections (IT-I7, IT-I8) render inside their cards in every format (`InfographicRendererTest`, with `ChartSectionTest` and `DiagramSectionTest`). | done |
| IT-T5 | unit | Each agent sends the right size field per format: Gemini's aspect ratio (`ImageAgentTest`), Pollinations' size (`ProviderImageAgentsTest`); Cloudflare's models take no size. | done |
| IT-T6 | unit | Template checks find invented numbers, made-up code and missing cards; the review request lists the template's rules (`TemplateCheckTest`, `ImageReviewerTest`). | done |
| IT-T7 | UI | Template and Format pickers list every option and save the choice; the strip shows labels (`ImageTemplatePickersTest`, `ImageLabelTest`). | done |

## Validation

| ID | Check | Status |
|----|-------|--------|
| IT-V1 | On a device, prompts of each kind (a concept, a system design, X vs Y, a lifecycle, a code snippet, best practices, benchmark numbers, an interview topic) pick the matching template and the images follow it. | todo |
| IT-V2 | Every image is 2:3, 1:1 or 3:2 and at least 1024 x 1536 / 1536 x 1536 / 1536 x 1024. | todo |
| IT-V3 | A scheduled image post uses the templates without any setting changed (videos: VT-V2). | todo |
