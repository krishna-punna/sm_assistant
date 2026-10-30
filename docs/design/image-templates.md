# Image prompt templates

Status: **plan, waiting for Krishna's approval** (2026-10-30). Mode decided: Both (app-drawn and AI-painted side by side). Nothing here is implemented yet.
Source: Krishna's template file, kept as [requirements 15](../requirements/15-image-prompt-templates.md).
Tasks: [docs/tasks/10-image-templates.md](../tasks/10-image-templates.md).

## What Krishna asked for

When the app generates an image, the AI agent looks at the content, picks the best template from the
template file (the "Choosing the right template" table, with the Universal Master as the default), and
fills it with the content and the other inputs (audience, style, brand, aspect ratio). The image is
then generated from the filled template.

## How the image pipeline works today

From [image-video-pipeline.md](image-video-pipeline.md), PR #57/#58/#60/#70/#71:

```
prompt ──► MediaContentWriter (text agent, no-limit content, media_content.md)
              │
              ▼
        InfographicWriter ×4 (text agent, infographic.md + 3 few-shot examples + a fixed layout idea each)
              │  JSON InfographicSpec (format, palette, sections…)
              ▼
        InfographicRenderer (SVG, all text drawn by the app) ──► AI illustrations (image agent, no text)
              │
              ▼
        AndroidInfographicComposer (PNG at 2x) ──► checks (spec, pixels, vision review + retry)
```

* With no text agent, `GenerateImagesUseCase` falls back to asking the image agent to paint whole
  images from `image.md` + few-shot examples.
* Video (`GenerateVideoUseCase`) is the same pipeline: one `SlideDeckWriter` call plans all square
  slides, each slide is rendered, then `Media3SlideshowMaker` encodes the MP4.
* The scheduler (`AutoPostRun`) calls `appGenerateImages` / `appGenerateVideo` with the prompt, so it
  gets whatever these use cases do.
* Agents left: Gemini, Groq, Cloudflare and Pollinations. Groq has no image model; Gemini,
  Cloudflare (FLUX schnell) and Pollinations (FLUX schnell) paint.

What the templates add that the pipeline doesn't have today: an explicit **content classification**
(the four layout ideas are fixed strings, whatever the content is), **template-specific content and
design rules** (for example "no declared winner" for comparisons, "never invent numbers" for data),
and **inputs the user controls** (audience, style guide, brand name, aspect ratio).

## Proposed flow

```
prompt ──► MediaContentWriter (unchanged: this is the template file's section 11 "adapter")
              │ technicalContent
              ▼
        TemplateChooser (text agent, choose_template.md)  ──►  {"primary": 5, "alternates": [3, 2], "reason": "…"}
              │                                                 (fallback: 1 Universal Master)
              ▼
        ImageBrief = template + technicalContent + audience + styleGuide + brandName + format
              │
      ┌───────┴────────────────────────────┐
      ▼                                    ▼
 App-drawn (default)                  AI-painted (optional)
 InfographicWriter gets the           The filled template goes to the
 template's rules as its brief        image agent (Gemini as is; FLUX
 → JSON → renderer → PNG              agents get a condensed prompt)
      │                                    │
      └───────────────┬────────────────────┘
                      ▼
        Checks + vision review (with the template's own rules)
```

### 1. Templates as an asset

* `assets/prompts/image_templates.md`: an adapted copy of Krishna's file, one block per template with
  a stable id (`universal`, `concept`, `architecture`, `comparison`, `workflow`, `code`,
  `checklist`, `data`, `learning`, `series`), its name, its "Use when" line and its body.
* The bracketed placeholders in the file are written differently in each template
  (`[PASTE MY TECHNICAL CONTENT…]`, `[OPTIONAL: …]`). The asset replaces them with fixed slots:
  `{{content}}`, `{{audience}}`, `{{style}}`, `{{brand}}`, `{{format}}`. Optional slots that the
  user left empty are filled with "not specified", so the template's own defaults apply.
* `ImageTemplates.kt` parses the asset (like `parseImageExamples` does for `image_examples.md`) and
  is unit-tested: 10 templates, every id unique, every body has `{{content}}`, no placeholder left
  after filling.
* Section 11 (the adapter) is not a separate template: the app already writes the content first with
  `MediaContentWriter`, which is exactly what the adapter describes. Sections 12 and 13 become the
  chooser prompt and this design.

### 2. Aspect ratio conflict (needs attention)

> **Conflict:** the templates default to **portrait 4:5** and offer **landscape 16:9**. Krishna's
> rule (2026-10-26) is that images are **2:3, 1:1 or 3:2**, never 4:5 or 9:16.

The plan keeps Krishna's rule and adapts the templates:

* In the asset, "portrait 4:5" becomes "portrait 2:3", "landscape 16:9" becomes "landscape 3:2",
  and "default to portrait 4:5" becomes "default to portrait 2:3".
* The app always fills `{{format}}` itself, with one of the three allowed values, so the model is
  never left to pick a size.
* A unit test fails if any template asset contains `4:5`, `16:9` or `9:16`.
* The original file stays unchanged in `docs/requirements/15-image-prompt-templates.md` for reference.

Two existing gaps against the same rule, fixed as part of this work:

* The renderer makes a portrait image **taller than 2:3** when the content is long
  (`InfographicRenderer`, "long content makes a portrait image taller"). Templates say "do not cram",
  so portrait is capped at exactly 2:3: the writer is told the card budget, and if the layout still
  overflows, the cards are shrunk or the spec is sent back with "too much content".
* Image agents don't ask for a size today. Gemini 2.5 Flash Image takes `imageConfig.aspectRatio`
  (`2:3`, `1:1`, `3:2`), Pollinations takes `size` (now always `1024x1024`), and Cloudflare FLUX
  schnell takes only a prompt and returns a square. This matters for illustrations (which are
  centre-cropped to their slot) and even more for AI-painted images. **Painted sizes:** Gemini's 2:3
  is about 832 x 1248, below the 1024 x 1536 minimum, and Cloudflare can only give 1:1. The plan
  upscales painted images 2x on the device so they meet the minimum and only offers 1:1 painted
  images from Cloudflare (unless a model with width/height is configured).

### 3. Choosing the template

* `prompts/choose_template.md` holds the "Choosing the right template" table and the "Use when" line
  of each template, and asks for JSON only:
  `{"primary": "workflow", "alternates": ["architecture", "concept"], "reason": "…"}`.
* `TemplateChooser` (in `generate/`, like `InfographicWriter`) sends the technical content to the text
  agent. An unreadable reply, an unknown id or a failed call gives `universal` and never fails the
  submit (the same "a check can't stop Generate Image" rule as the image checks).
* `series` is never picked by the classifier: it is chosen when the user turns on "Keep a series
  style" (see section 5), and then wraps the chosen template.
* Cost: one extra small text-agent call per submit. It could be folded into the content call later,
  but a separate call is easier to test and to retry.
* The user can override the choice: the Image screen gets a **Template** picker with "Auto (AI picks)"
  as the default and the 10 templates by name.

### 4. Four images from the templates

Today each of the 4 images gets a fixed layout idea (`INFOGRAPHIC_LAYOUT_IDEAS`). They become
template-driven:

| Image | Template | Why |
|---|---|---|
| 1 | primary | best fit |
| 2 | primary, in its other allowed format | same content, different shape |
| 3 | first alternate | a different way to see it |
| 4 | Universal Master | the template file's general default |

With a template picked by the user, all 4 use it with different formats and layout ideas.
Under each image the result shows the template it came from ("Workflow · 2:3").

### 5. Inputs: audience, style, brand, format

The template file's "If you are automating" list maps onto the app like this:

| Template field | In the app |
|---|---|
| `contentPrompt` | the prompt typed or prepared (unchanged) |
| `technicalContent` | `MediaContentWriter`'s output (unchanged) |
| `imageTemplate` | chosen by `TemplateChooser` or the Template picker |
| `audience` | new setting, default from app.yml |
| `styleGuide` | new setting; empty means the template's "restrained professional palette" |
| `aspectRatio` | Format picker: Auto, Portrait 2:3, Square 1:1, Landscape 3:2 |
| `brandName` | new setting, optional |
| `approvalStatus` | already covered: nothing is posted until the user taps Post (or the scheduler's own rules) |

New `app.yml` section, overridable from a new **Image Style** settings screen (under Settings, next to
Themes):

```yaml
image_style:
  audience: "developers, senior engineers and architects"
  brand_name: ""
  style_guide: ""        # e.g. "navy and teal, rounded cards, brand name bottom right"
  series: false          # true: wrap every image in the Series template with the style guide
  format: auto           # auto | portrait | square | landscape
  mode: both             # drawn | painted | both (see section 6); Krishna chose both
```

In app-drawn mode, `style_guide` is turned into renderer choices where it can be (palette, dark
background); the rest is passed to the writer as text. `brand_name` is drawn by the renderer as a
small footer label (new), so it is always spelled right.

### 6. App-drawn and AI-painted (Krishna chose Both, 2026-10-30)

The templates are written for an image model that paints the whole picture. The app moved away from
that in PR #57/#58 because diffusion models misspell and blur the text a technical cheat sheet needs,
and the template file says the same in its final recommendations ("for exact labels, code, charts and
architecture diagrams, use AI for the visual draft and a deterministic renderer for final text and
geometry"). So the app supports all three modes. Krishna chose **Both** as the default (2026-10-30), so real posts can compare them:

* **App-drawn.** The filled template becomes the brief for `InfographicWriter`:
  its TASK, CONTENT RULES and DESIGN RULES are added to the user message, and the template decides
  the section types the writer should lean on (table below). The renderer draws all text, so it stays
  exact; AI illustrations stay text-free.
* **AI-painted.** The filled template is the image prompt. Gemini 2.5 Flash Image (an LLM-based image
  model) gets it in full. FLUX schnell on Cloudflare and Pollinations reads only a short prompt
  (Cloudflare caps it at 2,048 characters and FLUX reads a few hundred tokens, while the filled
  Universal template is about 2,700 characters before the content is added), so for them the text
  agent first condenses the filled template into one image prompt of at most 1,500 characters. Every
  painted image goes through the pixel checks and the vision review, which is stricter about text.
* **Both (default).** Images 1 and 3 drawn, 2 and 4 painted, so the user can compare on real posts. With no image agent that can paint, all 4 are drawn. Video stays drawn (section 9).

How each template guides the app-drawn plan:

| Template | Format (Auto) | Section types the writer leans on | New renderer support needed |
|---|---|---|---|
| Universal Master | writer's choice | any | none |
| Concept Explainer | square or portrait | bullets, one illustration, takeaway | none |
| Architecture | landscape | **diagram** (components, groups, labelled arrows), bullets | **diagram section** |
| Comparison | landscape or portrait | compare, table, pros_cons, "when to choose" takeaway | none |
| Workflow | portrait or landscape | steps, flow (with branches) | branches in **flow** |
| Code | portrait | code (exact, short), bullets explaining it | none (code panel exists) |
| Checklist | portrait | pros_cons (do/don't), bullets with check/cross | none |
| Data | landscape or square | **chart** (bars), stats, table | **chart section** |
| Learning | portrait | concept → explanation → example → takeaway | none |
| Series | the wrapped template's | the wrapped template's, fixed palette | none |

The new sections draw numbers and arrows in SVG, which the Data and Architecture templates
explicitly prefer over image-model output.

### 7. Checks and review

* `infographicProblems` gets template-aware checks: the Data template must not show a number that is
  not in the content, the Comparison template needs a compare/table with at least two sides, the Code
  template's code must appear in the content, the Workflow template needs steps or a flow.
* `image_review.md` gets the chosen template's CONTENT RULES as "also check" points, so the vision
  review catches an invented benchmark or a declared winner, and a "facts not in the content" item.
* Painted images: the review also checks for gibberish text, which is the main risk of that mode.

### 8. Few-shot examples

The 21 examples in `image_examples.md` get a `template:` tag each, and `pickImageExamples` prefers
examples of the chosen template before keyword matches. They keep working as layout ideas.

### 9. Video slides

* Slides stay square 1:1 and app-drawn: a video needs the same style on every slide and readable text
  at video size, and painting 4 to 9 slides is slow and inconsistent.
* The deck is classified once (the same `TemplateChooser` on the content), and the Series template's
  consistency rules are always added to `video_deck.md`, since a video is a series of images by
  nature. Each slide may name its own template from the primary and alternates (for example a
  Concept intro, a Workflow middle and a Checklist end), so the slides follow the content's shape.
* The Image Style settings (audience, style guide, brand name) apply to videos too.

### 10. Schedulers

* `AutoPostRun` needs no new code path: it calls the same use cases, so scheduled image and video
  posts get Auto template choice and the saved Image Style settings.
* Small additions: each run's history line records the template used ("Image · Workflow"), and a
  schedule can optionally fix a template and format (default Auto), stored in `SchedulerStore`.
* A failed chooser call falls back to Universal, so a scheduled post can't fail because of templates.

## Refactoring needed

| Area | Change |
|---|---|
| `GenerateImagesUseCase.invoke(prompt)` | Takes an `ImageRequest(prompt, template = Auto, format = Auto)`; the screens and the scheduler pass their pickers' values. |
| `INFOGRAPHIC_LAYOUT_IDEAS` | Replaced by template-driven plans (section 4). |
| `InfographicWriter.write(…)` | Takes an `ImageBrief` instead of `topic, layoutIdea, content`; `format` from the brief overrides the model's. |
| `ImageAgent.generate(prompt, count)` | Gets a `format` (or `ImageSize`) argument; each agent maps it to its API (section 2). |
| Result type | `Outcome<List<File>>` becomes a list of `GeneratedImage(file, template, format)` so the screen and history can show the template. `ResultSaver` keeps reading old saved results. |
| `InfographicRenderer` | Portrait capped at 2:3; new `diagram` and `chart` sections; `flow` branches; brand label in the footer. |
| `GenerateVideoUseCase` / `SlideDeckWriter` | Same `ImageBrief` inputs; per-slide template. |
| Design doc | `image-video-pipeline.md` points here for the template step. |

## Rollout

One PR per task, in this order (details in [docs/tasks/10](../tasks/10-image-templates.md)). PRs 1 to
3 change nothing the user sees; from PR 4 Generate Image uses templates.

| PR | Task | Content |
|---|---|---|
| 1 | IT-I1 | Templates asset (sizes adapted), parser, tests |
| 2 | IT-I2 | `TemplateChooser` + `choose_template.md`, tests |
| 3 | IT-I3 | `ImageBrief`, `image_style` in app.yml, `ImageRequest` / `GeneratedImage` refactor |
| 4 | IT-I4 | App-drawn Generate Image uses the template (writer brief, 4-image plan, template label) |
| 5 | IT-I5 | Template and Format pickers on Image and Content & Image; Image Style settings screen |
| 6 | IT-I6 | Renderer: portrait capped at 2:3, brand label |
| 7 | IT-I7 | Renderer: `chart` section (Data template) |
| 8 | IT-I8 | Renderer: `diagram` section and flow branches (Architecture, Workflow) |
| 9 | IT-I9 | Template-aware spec checks and vision review |
| 10 | IT-I10 | Image agents take a size; painted upscale to the minimum |
| 11 | IT-I11 | AI-painted and Both modes from the filled template |
| 12 | IT-I12 | Video: template per deck and slide, series rules |
| 13 | IT-I13 | Scheduler: template in run history, optional per-schedule template and format |
| 14 | IT-I14 | Few-shot examples tagged by template |

PRs 7, 8, 13 and 14 can be dropped or reordered without breaking the rest. Until PR 11 lands, all 4 images are app-drawn.
