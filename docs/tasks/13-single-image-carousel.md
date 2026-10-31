# 12. One image or a carousel

Requirements: [17](../requirements/17-single-image-or-carousel.md). Design: [single-image-carousel.md](../design/single-image-carousel.md).
Status: approved 2026-10-31. One PR per implementation task, in order.

## Implementation

| ID | Task | Depends on | Status |
|----|------|-----------|--------|
| SC-I1 | One image per request: `IMAGES_PER_REQUEST = 1`; the image screens show one result with Regenerate; Both mode gives the drawn image. | — | done |
| SC-I2 | Fit check: `InfographicRenderer.fits(spec)`; an unpicked portrait or square that doesn't fit is drawn as 3:2 landscape when that fits. Layout setting (Auto, Single image, Carousel) in Image Style and app.yml. | SC-I1 | done |
| SC-I3 | `Carousel` model, `CarouselWriter` and `prompts/carousel.md` (cover, 3–8 pages, summary, one palette, page budgets); carousel checks. | SC-I2 | done |
| SC-I4 | Drawing a carousel: page number and "Swipe" on the cover, each page through the pipeline and the review; Generate Image returns the pages when content doesn't fit one image (or Layout is Carousel); files `carousel-<id>-pNN.png`. | SC-I3 | done |
| SC-I5 | Image screens and Content & Image show a carousel as a swipeable pager with "Page N of M"; Post in LinkedIn posts all pages. | SC-I4 | done |
| SC-I6 | Posting a carousel as a PDF document: PDF from the pages, Documents API upload and status poll, `content.media` document post with the title; multi-image fallback. | SC-I5 | done |
| SC-I7 | Scheduled image posts post a carousel as a document; run history says "Carousel · 7 pages". | SC-I6 | todo |

## Tests

| ID | Kind | Test | Status |
|----|------|------|--------|
| SC-T1 | unit | One image per request; Both mode draws it (`GenerateImagesTest`). | done |
| SC-T2 | unit | Too-long portrait becomes landscape; too long for both becomes a carousel; a picked format is never switched (`ImageLayoutTest`). | todo |
| SC-T3 | unit | Carousel JSON parses; page limits and the 10-page cap are enforced; page numbers drawn (`CarouselTest`). | done |
| SC-T4 | unit | A carousel post uploads a document and posts `content.media` with a document URN; a failed document upload falls back to multi-image (`LinkedInClientTest`, `PostToLinkedInTest`). | done |

## Validation

| ID | Check | Status |
|----|-------|--------|
| SC-V1 | On a device, a short topic gives one image, a wide topic a landscape image, and a long topic a carousel that reads well on a phone. | todo |
| SC-V2 | A carousel posted to LinkedIn shows as a swipeable document. | todo |
