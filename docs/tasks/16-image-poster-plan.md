# 16. Image poster plan: AI-decided content, poster series, richer design

Plan: [Image poster generation plan](https://claude.ai/artifact/8xczzQmaxY59v1gamUitMC), written from Krishna's Q&A on the image
prompts (2026-10-31). Stacked PRs into `f-mobile-app`, merged in order (Krishna, 2026-10-31: implement all, don't wait for CI,
fix failures as they come).

Decisions:

- The AI decides how many cards a poster has; the app only checks that it is readable. Readable means drawn at the
  format's own width, so body text is about 12 px when LinkedIn shows the image at its feed width (about 550 px on every
  device). Not phone-specific: the same rule for every viewer.
- Images are single-page posters. The square carousel (Layout = Carousel) is removed.
- Nothing is shortened to fit a prompt limit. Content too long for one poster becomes a numbered poster series (1/N),
  each a full poster, posted as one LinkedIn multi-image post.
- The AI decides how much clip art a poster carries; the header and footer get a modern infographic design; one text
  standard for every word.

## Implementation

| ID | Task | Status |
|----|------|--------|
| AC-1 | Readability rule: `InfographicRenderer.measure` reports fits, cards, needed and available height, and the feed text size; portrait and landscape posters of 12+ narrow cards may take one more column (the fewest columns that fit win). | PR #142 |
| AC-2 | Card cap 16 (`Limits.SECTIONS`), cards over it logged instead of dropped silently. The spec checks report dropped cards, so the writer merges them. | PR #143 |
| AC-3 | `infographic.md` without fixed card ranges, with the feed-width readability rule. | PR #144 |
| AC-4 | Retry with measured numbers instead of "at most 11 sections"; a poster series when the retry still doesn't fit. | PR #145; series fallback in AC-7 |
| AC-5 | Remove the image carousel (Layout = Carousel, `carousel.md`, `CarouselWriter`, PDF posting); keep the multi-image post. Shared grouping becomes "series" (`SeriesFiles`, `SeriesPager`, `RunRecord.seriesParts`), ready for AC-7. | PR #146 |
| AC-6 | Painted series: `split_image_prompt.md` replaces `condense_image_prompt.md`; per-agent prompt budgets in app.yml. A series passes the review only when every poster does. | PR #147 |
| AC-7 | Drawn series: the writer may return N posters; results, scheduled posts and history handle N images. Series posters are not sent to the image review (each is drawn from a checked definition). | PR (this) |
| AC-8 | AI-decided clip art: an optional `art` picture on any card, `MAX_ILLUSTRATIONS` 2 → 12. | todo |
| AC-10 | Text standard: shared text writer, typography rules, word-gap test, prompt rules, review text check. | todo |
| AC-9 | Richer header and footer: ribbon, badges, highlight pill, breakout hero, takeaway card, recap strip, handle, 1/N. | todo |

## Validation (on a phone and a desktop)

| ID | Check | Status |
|----|-------|--------|
| AC-V1 | A long topic gives a readable poster or a numbered series in the LinkedIn feed; nothing is cut. | todo |
| AC-V2 | A short topic gives one poster with clip art and the new header and footer; titles keep their spaces. | todo |
