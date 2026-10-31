# 18. Separate image and video generation, and image posts like the samples

Source: Krishna, project thread, 2026-10-31. Tasks: [14-separate-image-video.md](../tasks/14-separate-image-video.md).

> "the current project image generation confusing. I have installed this app in mobile. Image functionality is broken.
> Images are generated in Video Size formate for Text and Image Generation.
> I want to differentiate clearly both Image and Video Genration Process and Pipelines."

> "WHen i am referring to images to post those should be like attached samples"

> "can you plan and propose requirement docuemnts with new changes"

Build checked: CI run #361 (PR #98, f-mobile-app at 79eba54), the APK installed on Krishna's phone.

## What is wrong today

- Long content is split into square 1:1 carousel pages ([17](17-single-image-or-carousel.md) SC-2), or switched to a 3:2
  landscape (SC-3). Each page holds a few large cards, so on the phone it looks like a video slide, not a post image.
- The result strip crops every thumbnail to a square, so even a 2:3 image looks 1:1.
- With one image per request and Image Style mode "both", the image is always app-drawn. The samples' look (icons,
  mini-diagrams, code panels, rich colour) is never tried.
- With format Auto and no text agent, no size is sent to the AI agent, and nothing checks the shape that comes back.
- Image and video share one renderer size list (2:3, 1:1, 3:2, 9:16, 16:9), one template type, one template chooser
  prompt, one set of few-shot examples and one agent setting. A change for one medium silently affects the other.

### Test evidence (Krishna's phone, 2026-10-31)

Generate Image on "CompletableFuture vs Reactive architecture" gave an **8-page square carousel**, not one image:

- Each page holds a single card with 4 bullets, centred on a mostly empty square. It looks like a video slide.
- The cover is a title on an empty dark square.
- Words in two-colour titles run together: "Completablevs", "StackExecution", "ErrorFaultTolerance", "KeyTakeaways",
  "RESTAPI". The space at the start of each highlighted `<tspan>` is dropped when the SVG is drawn.
- Code loses characters: `Mono<ResponseEntity<OrderDto>>` shows as "MonoResponseEntityOrderDto", and `tuple ->` as
  "tuple -". The cause is not yet traced; it could be the writer's output or the drawing.

## The samples

All 22 samples Krishna sent (the 16 in `prompts/image_examples.md` plus six more in project files: Stream API,
Collections, Optional, Angular 21, ConcurrentHashMap, Spring transactions) are **one dense poster each**. None is a
carousel or a slide.

| Shape | Count | Pixels | Used for |
|-------|-------|--------|----------|
| Portrait 2:3 (about 0.67 to 0.7) | 13 | 1024×1536, 800×1135 | Cheat sheets and internals with 6 to 11 numbered cards |
| Landscape 3:2 | 3 | 1536×1024, 1280×851 | Wide flows and architectures (Retry, RAG, Kafka) |
| Square 1:1 | 6 | 1536², 1254², 800² | Grids of a few equal cards (SQL, CompletableFuture, Feign, Angular 21, CHM, Spring tx) |

What every sample has: a dark or gradient header band with a big two-colour title, a subtitle and an icon; 4 to 11
numbered cards, each with a coloured header tab; inside the cards, small diagrams (arrays, trees, flows), tables, code
panels and icon bullets; a footer strip with a takeaway or tip. Text is small but sharp at the full size.

## Requirements

| ID | Requirement |
|----|-------------|
| SV-1 | Image and video are two separate pipelines with their own entry point, size type, templates, chooser prompt, examples and agent setting. They share only low-level drawing (renderer, palette, icons, text layout), which takes a pixel size, not a medium. |
| SV-2 | An image is always 2:3 portrait, 1:1 square or 3:2 landscape. A video is always 9:16 vertical (default), 1:1 square or 16:9 landscape. Neither pipeline can produce or name the other's sizes. |
| SV-3 | Every image post (Generate Image, Content & Image, scheduled image posts) is **one dense poster** like the samples: header band, 4 to 11 numbered cards with diagrams, tables, code and icons, and a footer. |
| SV-4 | Format Auto picks portrait by default, landscape for a wide flow or architecture, and square for a grid of up to 6 equal cards. A format the user picks is always kept. |
| SV-5 | Content too long for one poster is condensed to fit (fewer words per card, at most 11 cards), not split. The carousel is made only when the user sets Layout to Carousel. Replaces [17](17-single-image-or-carousel.md) SC-2 and SC-3. |
| SV-6 | The poster is AI-painted from the filled template at the chosen size (Gemini 2:3 / 1:1 / 3:2, Pollinations 1024×1536 / 1024² / 1536×1024). If the vision review finds misspelt, missing or invented text, or a low score, the app draws the poster itself instead. Image Style mode can still force Drawn or Painted. (Recommended option; Krishna's choice on the decision card is pending.) |
| SV-7 | The app-drawn poster matches the samples' density: up to 11 cards in 2 to 3 columns on a 2:3 page, small diagrams and code panels, and text that stays readable at 2160×3240. |
| SV-8 | A size is always sent to the AI agent; Auto is resolved to a concrete size before any call. |
| SV-9 | The last step of each pipeline checks every output's pixel ratio. An image off by more than 2% is padded to the nearest image size; a video frame to its video size. Each fix is logged. |
| SV-10 | Results show their true shape: thumbnails are not cropped, and each result shows its size, e.g. "Workflow · 2:3 · 2160×3240" or "9:16 · 1080×1920". |
| SV-11 | Settings name the agents by purpose: "Image agent" and "Video slide agent", each with its own model. |
| SV-12 | The video pipeline keeps working as today (slides at the video's size, MP4 slideshow, video templates). |
| SV-13 | Text is drawn exactly: spaces between title words are kept in two-colour titles, and code keeps every character (`<`, `>`, `->`, generics). |

## Out of scope

- AI video generation (Q-08 stays: video is a slideshow of drawn slides).
- New image templates; the ten from [15](15-image-prompt-templates.md) stay.

## Open questions

| ID | Question | Recommendation |
|----|----------|----------------|
| Q-SV1 | How should the poster be made: AI-painted, app-drawn, or painted with drawn fallback? | Painted with drawn fallback (SV-6). |
| Q-SV2 | Should the six new samples be added to `prompts/image_examples.md`? | Yes, in SV-I6. |
