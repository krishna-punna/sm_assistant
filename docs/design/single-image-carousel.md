# One image or a carousel

Status: **approved by Krishna 2026-10-31**, implemented in PRs #117, #119 and #122–#127; device checks left.
Requirements: [17](../requirements/17-single-image-or-carousel.md). Tasks: [docs/tasks/13-single-image-carousel.md](../tasks/13-single-image-carousel.md).
Krishna approved without picking the open choices, so the recommended ones are used: 1:1 carousel pages,
PDF document posts with a multi-image fallback, and app-drawn images.

## Today

Each request makes 4 images (`IMAGES_PER_REQUEST = 4`): images 1 and 3 drawn by the app, 2 and 4
painted by the image agent (`ImageMode.BOTH`). Sizes:

| Image | Format | Layout | Finished PNG |
|-------|--------|--------|--------------|
| Drawn | 2:3 portrait | 1080 x 1620 | 2160 x 3240 |
| Drawn | 1:1 square | 1080 x 1080 | 2160 x 2160 |
| Drawn | 3:2 landscape | 1620 x 1080 | 3240 x 2160 |
| Painted | 2:3 / 1:1 / 3:2 | from the agent | 1024 x 1536 / 1536 x 1536 / 1536 x 1024 |

Content too long for its format is laid out on a page up to 2x wider (`MAX_WIDEN`), so the text is
drawn up to half size; past that the page is padded. The shape is kept but the text gets too small
to read on a phone.

## LinkedIn limits

| Post | Limit |
|------|-------|
| Image | JPG, GIF or PNG under 36,152,320 pixels |
| Multi-image | 2 to 20 images |
| Document (PDF) | up to 100 MB and 300 pages; `w_member_social`, the owner must be the caller |

Sources: [Documents API](https://learn.microsoft.com/en-us/linkedin/marketing/community-management/shares/documents-api),
[MultiImage API](https://learn.microsoft.com/en-us/linkedin/marketing/community-management/shares/multiimage-post-api),
[Images API](https://learn.microsoft.com/en-us/linkedin/marketing/community-management/shares/images-api).

## The decision

```
technical content ──► infographic definition (the format the template plans, budgets below)
        │
        ▼
 fits portrait/square at full text size? ── yes ──► one image
        │ no (and the format wasn't picked by the user)
        ▼
 same definition fits 3:2 landscape? ── yes ──► one landscape image
        │ no
        ▼
 carousel: the writer splits the content into pages ──► cover + pages + summary, 1:1
```

- "Fits" is the renderer's own measure (`InfographicRenderer.fits`): the layout fits the format's
  height at its base width, with no widening.
- A format the user picked is kept for a single image: it is not switched to landscape. When it
  doesn't fit, the result is a carousel (pages are always 1:1).
- Painting: with one image, `ImageMode.BOTH` gives the drawn image (image 1 was always drawn);
  `ImageMode.PAINTED` still paints the one image whole and is never a carousel.
- Image Style gets a **Layout** setting: Auto (default), Single image (today's widening, never a
  carousel), Carousel (always).
- Budgets in the writer prompts: single portrait up to 6 sections (~180 words), square up to 4,
  landscape up to 6; a carousel page holds one section (two if both are short, ~50 words), code up
  to 12 lines, table up to 5 rows, flow up to 6 nodes.

## Carousel

- Pages are 1:1, 2160 x 2160 PNG (layout 1080 x 1080), all with one palette, the same header strip
  and the brand footer, and a page number "3 / 7".
- Page 1 is the cover (title, highlight, subtitle, hero or icon, "Swipe →"). The last page is the
  summary (the takeaway and up to 5 key points). Between them, 3 to 8 content pages. At most 10.
- A `Carousel` is written in one text-agent call (`CarouselWriter`, `prompts/carousel.md`), like the
  video's `SlideDeck`: the cover, the pages as infographic definitions and the summary, one palette.
  The carousel is checked as a whole (`carouselProblems`: page count, cover title, at most 2
  sections a page, every page fits its square) and drawn by `InfographicPipeline.drawPages` (at
  most 1 illustration per page, 2 on the cover). The vision review is not run per page, to keep a
  10-page carousel from costing 10 more vision calls. If no carousel can be made (the writer fails,
  or fewer than 2 pages draw), the long content is drawn as one image as before.
- The cover has no cards: its header fills the page with the title centred. Content pages centre
  their cards below the header.
- Files: `carousel-<id>-p01.png` … so a result, a saved result and a scheduled run keep the pages
  together and in order.

## Showing and posting

- The image screens show one result: a single image, or a swipeable pager with "Page 2 of 7".
  Regenerate replaces the 4 picks. Post in LinkedIn posts what is shown.
- A carousel is posted as a PDF document: the pages are written into a PDF (Android `PdfDocument`,
  pages as JPEG at 90 quality, about 2 to 4 MB for 10 pages), uploaded with the Documents API
  (`/rest/documents?action=initializeUpload`, PUT, poll until AVAILABLE) and posted with
  `content.media` `{id: urn:li:document:…, title: <carousel title>}`. If the document upload fails,
  the post is retried as a multi-image post of the pages.
- Scheduled image posts make one result the same way and post it the same way.

## Not changed

Video, the image templates and their chooser, painted mode (`ImageMode.PAINTED` still paints one
image whole), the checks and the vision review.
