# 14. Separate image and video; image posts like the samples

Requirements: [18](../requirements/18-separate-image-and-video.md).
Status: proposed 2026-10-31, waiting for Krishna's review. One PR per implementation task, in order, into `f-mobile-app`.

## Where things are today

| Shared part | File | Problem |
|-------------|------|---------|
| One size list for both media | `infographic/InfographicSpec.kt` (`InfographicFormat` has VERTICAL, WIDE) | Image code can be handed a video size |
| 9:16 → 2:3 and 16:9 → 3:2 mapping | `generate/TemplatePlan.kt` (`InfographicFormat.image`) | A wrong size is relabelled, not rejected |
| Video templates parsed as image templates | `generate/GenerateVideo.kt` (`parseImageTemplates("video_templates")`) | Same type and `{{format}}` slot for both |
| One chooser prompt | `prompts/choose_template.md` (`{{media}}`) | Same rules for both media |
| Image, slide deck and carousel writers in one file | `generate/InfographicWriter.kt` | 2:3 image examples steer video slides |
| "Image agent" with a "video model" | `agents/AgentSelector.kt`, `data/ApiConfig.kt` (`forMedia`) | Hard to tell which agent makes what |
| Automatic carousel and landscape switch | `generate/GenerateImages.kt` (`splitter`, `fitFormat`) | Long content becomes square slides |
| No size sent with Auto | `GenerateImages.generate` fallback, `agents/ImageAgent.kt` | The model picks the shape |
| Square thumbnails | `ui/components/ImageComponents.kt` (`ImageStrip`) | Every image looks 1:1 |

## Implementation

| ID | Task | Req | Depends on | Status |
|----|------|-----|-----------|--------|
| SV-I1 | Show the true shape: thumbnails use `ContentScale.Fit` in a box at the image's ratio; each image and video result shows ratio and pixels ("Workflow · 2:3 · 2160×3240", "9:16 · 1080×1920"). | SV-10 | — | todo |
| SV-I2 | Single poster by default: Layout Auto never splits or switches format. Too-long content is condensed by the infographic writer (shorter card text, at most 11 cards, asked again with the overflow) instead of the carousel. Carousel only when Layout is Carousel. Update app.yml comments and the Image Style screen. | SV-3, SV-5 | SV-I1 | todo |
| SV-I3 | Separate size types: `image/ImageSize` (PORTRAIT 2:3, SQUARE 1:1, LANDSCAPE 3:2) and `video/VideoSize` (VERTICAL 9:16, SQUARE 1:1, LANDSCAPE 16:9). The renderer takes a `PageSize(width, height)`. Remove VERTICAL and WIDE from `InfographicFormat` and delete `InfographicFormat.image`. | SV-2 | SV-I2 | todo |
| SV-I4 | Always send a size: Auto is resolved to a concrete `ImageSize` (portrait default, landscape for architecture/comparison/data flows, square for a grid of ≤ 6 cards) before any agent call, on every path including the no-text-agent fallback and scheduled posts. | SV-4, SV-8 | SV-I3 | todo |
| SV-I5 | Painted poster with drawn fallback: the single image is AI-painted from the filled template at its size; the vision review checks text against the content (misspelt, missing, invented) and score ≥ `min_score`; on failure after retries the app-drawn poster is used. Image Style mode Drawn/Painted still forces one. Default `image_style.mode` becomes `auto`. | SV-6 | SV-I4 | todo |
| SV-I6 | Denser app-drawn poster: up to 11 cards in 2–3 columns on 2:3, a two-colour title, card header tabs, footer tip strip; add the six new samples (Stream API, Collections, Optional, Angular 21, ConcurrentHashMap, Spring transactions) to `prompts/image_examples.md`. | SV-7 | SV-I5 | todo |
| SV-I7 | Final size guard: `ImagePipeline` checks every finished image's ratio (2% tolerance) and pads to the nearest `ImageSize`; `VideoPipeline` checks each slide and the MP4 frame size against its `VideoSize`. Each fix is logged. | SV-9 | SV-I3 | todo |
| SV-I8 | Separate templates and prompts: `VideoTemplate` type and parser; `choose_image_template.md` and `choose_video_template.md`; image and video few-shot examples apart; split `InfographicWriter.kt` into image, video and carousel writer files. | SV-1 | SV-I3 | todo |
| SV-I9 | Two pipelines: `image/ImagePipeline` (Generate Image, Content & Image, scheduled image posts) and `video/VideoPipeline` (Generate Video, Content & Video, scheduled video posts). Settings and screens say "Image agent" and "Video slide agent", each with its own model. | SV-1, SV-11, SV-12 | SV-I8 | todo |
| SV-I10 | Docs: update `docs/design/image-video-pipeline.md`, mark [17](../requirements/17-single-image-or-carousel.md) SC-2/SC-3 replaced, add the phone checklist below. | — | SV-I9 | todo |

## Tests

| ID | Kind | Test | Status |
|----|------|------|--------|
| SV-T1 | unit | Long content gives one poster, not a carousel, with Layout Auto; Layout Carousel still splits (`GenerateImagesTest`). | todo |
| SV-T2 | unit | Every image path sends a concrete size to Gemini, Pollinations and Cloudflare (`ImageAgentTest`, `GenerateImagesTest`). | todo |
| SV-T3 | unit | A 9:16 picture through the image guard comes out 2:3; a 2:3 picture through the video guard comes out 9:16 (`SizeGuardTest`). | todo |
| SV-T4 | unit | Painted poster that fails the text review falls back to the drawn poster (`GenerateImagesTest`). | todo |
| SV-T5 | unit | The `image` package imports nothing from `video` and back (`PipelineBoundaryTest`, reads the source tree). | todo |
| SV-T6 | unit | Drawn 2:3 poster with 11 cards fits at full text size (`InfographicRendererTest`). | todo |

## Validation (on the phone)

| ID | Check | Status |
|----|-------|--------|
| SV-V1 | Generate Image and Content & Image on a long topic (e.g. "ArrayList internals") give one 2:3 poster like the sample, labelled "2:3 · …". | todo |
| SV-V2 | An architecture topic gives a 3:2 poster; a "top 6 tips" topic gives a square poster. | todo |
| SV-V3 | Generate Video still gives a 9:16 slideshow; nothing on the image screens shows 9:16 or 16:9. | todo |
| SV-V4 | A scheduled image post posts one poster. | todo |
