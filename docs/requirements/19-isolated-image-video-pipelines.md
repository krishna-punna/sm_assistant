# 19. Image and video as fully isolated pipelines

Source: Krishna, project thread, 2026-10-31. Reviewed document: "Requirements: separate image and video pipelines"
(https://claude.ai/artifact/151wLLyCwp77MTUARVZs6S). Tasks: [15-isolated-pipelines.md](../tasks/15-isolated-pipelines.md).
Builds on [18](18-separate-image-and-video.md), which also sets how the image poster looks.

> "I want to differentiate clearly both Image and Video Genration Process and Pipelines."

> "I dont want to share anything, isolate COmpletely starting from Prompts to till last final image results"

> "Can you Implement the changes all, dont wait for CI status, if any PR fails and notifies fix them in loop untill success."

## Requirements

Each pipeline owns every step from the user's prompt to the finished file. Nothing is shared.

| ID | Requirement | Image pipeline | Video pipeline |
|----|-------------|----------------|----------------|
| SP-1 | Own entry point | `ImagePipeline` | `VideoPipeline` |
| SP-2 | Own screens and posts call it | Generate Image, Content & Image, scheduled image posts | Generate Video, Content & Video, scheduled video posts |
| SP-3 | Own size type; the other medium's sizes can't be named | `ImageSize`: 2:3 portrait (default), 1:1, 3:2 | `VideoSize`: 9:16 vertical (default), 1:1, 16:9 |
| SP-4 | Own templates and template type | `ImageTemplate` from `prompts/image/image_templates.md` | `VideoTemplate` from `prompts/video/video_templates.md` |
| SP-5 | Own template chooser prompt | `choose_image_template.md` | `choose_video_template.md` |
| SP-6 | Own writer and prompts | Poster writer (`infographic.md`, `image.md`, `carousel.md`) | Slide deck writer (`video_deck.md`, `video.md`) |
| SP-7 | Own few-shot examples | `image_examples.md` (the sample posters) | `video_examples.md` (slides only) |
| SP-8 | Own AI agent and model setting | "Image agent" + image model | "Video slide agent" + video model |
| SP-9 | Own output and checks | One PNG poster (carousel pages only when picked), ratio checked against `ImageSize` | One MP4; every slide and the MP4 frame checked against `VideoSize` |
| SP-10 | Own labels on results | "Workflow · 2:3 · 2160×3240" | "Workflow · 9:16 · 1080×1920" |
| SP-11 | Own content step and prompt | `ImageContentWriter` + `image_content.md` | `VideoContentWriter` + `video_content.md` |
| SP-12 | Own text agent and model setting | "Image text agent" + model | "Video text agent" + model |
| SP-13 | Own drawing code | `image/render/` (posters), own palette, icons, text layout | `video/render/` (slides), own palette, icons, text layout |
| SP-14 | Own vision review prompt and checks | `image_review.md`, `image_checks` | `video_review.md`, `video_checks` |
| SP-15 | Own style settings | Image Style + `image_style` | Video Style + `video_style` |
| SP-16 | Own files and folders | `image/` package, `prompts/image/`, `generated/images/` | `video/` package, `prompts/video/`, `generated/videos/` |

Only plain platform code stays common, because it knows nothing about images or video: the HTTP client, secure key
storage, the LinkedIn client and the screens framework. An AI provider's key is entered once; each pipeline makes its
own agent and calls with it.

The drawing code exists twice, so a fix to text drawing is made in both copies. A boundary test makes sure neither
pipeline calls the other.

## Current implementation (verified 2026-10-31 at f-mobile-app 4fa84f7)

### Images (Generate Image and the image half of Content & Image, `GenerateImagesUseCase`)

1. No-limit technical content from the prompt (`media_content.md`, shared with video).
2. Template chosen by the text agent (`choose_template.md`, `{{media}}` = image, shared with video) from `image_templates.md`.
3. Format: the one picked, or the template's first format on Auto.
4. Image Style mode "both" with one image per request always draws; it paints only in "painted" mode.
5. JSON plan of the infographic (`infographic.md` + 3 of the sample descriptions in `image_examples.md` + the filled template).
6. A plan too long for portrait or square is tried as 3:2 landscape; still too long becomes a square carousel (`carousel.md`).
7. The SVG is drawn with up to 2 illustrations from the image agent (`illustration.md`, shared, **no size sent**), saved at 2×.
8. Vision review (`image_review.md`, shared): below 7/10 the plan is rewritten once.

Without a text agent the image agent paints the whole image from `image.md` (no size sent on Auto).

| Output | Ratio | Pixels |
|--------|-------|--------|
| Drawn portrait / square / landscape | 2:3 / 1:1 / 3:2 | 2160×3240 / 2160×2160 / 3240×2160 |
| Carousel page | 1:1 | 2160×2160, up to 10 pages |
| Painted, Gemini | 2:3, 1:1, 3:2 | about 832×1248 upscaled 2× (square 1024² → 2048²) |
| Painted, Pollinations | 2:3, 1:1, 3:2 | 1024×1536, 1024² (→ 2048²), 1536×1024 |
| Painted, Cloudflare FLUX | 1:1 only | 1024² → 2048² |
| Illustrations inside a drawn image | none sent | the agent's default, cropped into the slot |

### Videos (Generate Video and the video half of Content & Video, `GenerateVideoUseCase`)

1. Length (30 s to 5 min, default 30 s) sets the slides: one per 8 s, 4 to 10.
2. No-limit technical content (`media_content.md`, shared).
3. Video template chosen by the text agent (`choose_template.md`, `{{media}}` = video) from `video_templates.md`, parsed as image templates.
4. Size from the template: architecture, comparison and data are 16:9; every other template is 9:16.
5. One call plans every slide (`video_deck.md`), with few-shot examples from `image_examples.md` (2:3 posters).
6. Each slide is redrawn at the video's size with the image renderer, with 1 illustration from the video agent (`illustration.md`, no size sent), saved at 1×; "reveal" slides are drawn in steps.
7. Every slide is reviewed (`image_review.md`); a slide below 7/10 re-plans the deck once.
8. Media3 encodes the MP4 at the video's size with fades.

Without a text agent the video agent paints each slide from `video.md`, which says "Square format", and the square
pictures are encoded into a 9:16 1080×1920 video.

| Video size | Pixels | Used today |
|------------|--------|------------|
| 9:16 vertical (default) | 1080×1920 | All templates except architecture, comparison and data; the no-text-agent fallback |
| 16:9 landscape | 1920×1080 | Architecture, comparison and data templates |
| 1:1 square | 1080×1080 | Defined in code; nothing picks it |

### Found

- Images and video share 5 prompt files (`media_content`, `choose_template`, `illustration`, `image_review`,
  `technical_content`) and the image examples.
- Illustrations are requested with no size.
- The no-text-agent video fallback paints square slides into a 9:16 video.
- Square video can't be chosen.
- Long image content becomes a square carousel (what Krishna saw on the phone: 8 pages, one card each).
- Two-colour titles lose their spaces and code loses `<`, `>` and `&`. AndroidSVG reads with an XmlPullParser,
  drops a plain space at the start of a `<tspan>`, and skips entity references such as `&lt;`.
