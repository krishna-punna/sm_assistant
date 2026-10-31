# 15. Fully isolated image and video pipelines

Requirements: [19](../requirements/19-isolated-image-video-pipelines.md) and [18](../requirements/18-separate-image-and-video.md).
Replaces the order in [14](14-separate-image-video.md). Stacked PRs into `f-mobile-app`, merged in order (Krishna,
2026-10-31: don't wait for CI, fix failures as they come).

## Implementation

| ID | Task | Req | Status |
|----|------|-----|--------|
| IP-1 | Docs 19 and 15. True size on every result ("2:3 · 2160×3240", "9:16 · 1080×1920"), thumbnails at the image's ratio, not cropped. Title words keep their spaces (non-breaking space between `<tspan>`s) and code keeps `<`, `>`, `&` (CDATA instead of entities, which AndroidSVG skips). | SV-10, SV-13, SP-10 | PR #129 |
| IP-2 | One poster per image post: Layout Auto never splits into a carousel or switches to landscape; content too long is condensed by the writer. Carousel only when Layout is Carousel. | SV-3, SV-5 | PR #130 |
| IP-3 | A size on every agent call: Auto resolved to a concrete image size; illustrations asked at their slot's shape; the no-text-agent video fallback paints at the video size. A final size guard pads any image or frame off by more than 2%. | SV-8, SV-9 | PR #131 |
| IP-4 | Separate prompts: `prompts/image/` and `prompts/video/` with their own content, chooser, illustration, review and examples files (new `video_examples.md` of slide decks); neither folder names the other medium (`PromptIsolationTest`). | SP-5, SP-7, SP-11, SP-14 | PR #132 |
| IP-5 | Separate packages: `image/` and `video/` with `ImageSize` / `VideoSize`, their own templates (`VideoTemplate`), chooser, reviewer, examples, content writer, pipelines and a copy of the renderer each (`image/render/` draws 2:3, 1:1, 3:2 only; `video/render/` 9:16, 1:1, 16:9 only). Video gets its own style inputs (`video_style` in app.yml, `VideoStyleStore`; the Image Style values carry over until changed). `PipelineBoundaryTest` fails on any import across. | SP-1–SP-4, SP-6, SP-13, SP-15, SP-16 | PR #133 |
| IP-6 | Separate settings: "Image agent" and "Video slide agent", "Image text agent" and "Video text agent", Image Style and a new Video Style (`video_style`), separate cache folders. Saved choices carry over: the image and video text agents and models follow the old text agent and model until changed; generated files move to `generated/images/` and `generated/videos/`. | SP-8, SP-12, SP-15, SP-16 | in progress |
| IP-7 | Painted poster with drawn fallback (mode `auto`), denser drawn poster, the six new samples in the image examples. | SV-6, SV-7 | todo |

## Validation (on the phone)

| ID | Check | Status |
|----|-------|--------|
| IP-V1 | Generate Image and Content & Image give one poster labelled 2:3, 1:1 or 3:2 with its pixels; never 9:16 or 16:9. | todo |
| IP-V2 | Titles read with spaces; code shows generics and arrows. | todo |
| IP-V3 | Generate Video gives a 9:16 (or 16:9) slideshow labelled with its pixels. | todo |
| IP-V4 | Changing a video prompt or setting doesn't change image output, and the other way round. | todo |
