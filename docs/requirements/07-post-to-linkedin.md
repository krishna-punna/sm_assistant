# 7. Posting to LinkedIn

Source: slides 6–12 ([slide 12](wireframes/slide-12.jpg) is the reference output).

## Where the action appears

A blue **Post in LinkedIn** button appears directly below every generated result in:

- Generate Technical Content (slide 6)
- Generate Image — result and full preview (slides 7, 8)
- Generate Video (slide 9)
- Generate Content & Video (slide 10)
- Generate Content & Image (slide 11)

It does **not** appear on Prepare LinkedIn Post Prompt — that feature's output is a prompt, not a post.

## Reference output (slide 12)

Slide 12 is a screenshot of a real LinkedIn post by the product owner, showing the kind of post the app should produce:

- **Hook line** with an emoji: "🔧 YAML vs .properties in Spring Boot — Quick Comparison".
- **Short intro** question: "Choosing between application.yml and application.properties? Here's a crisp breakdown:".
- **Emoji-marked sections** (✅ YAML (.yml), 📄 .properties) each with short one-line points.
- **"📌 In short:"** summary line.
- **Attached image**: a comparison table (S.No / YAML(.yml) / .properties, 7 rows).
- Reached 12,914 impressions, 42 reactions — i.e. this format is the proven baseline.

## Functional requirements

- **LP-1** Tapping **Post in LinkedIn** publishes the current result to the user's LinkedIn account using the saved LinkedIn settings ([03](03-linkedin-settings.md)).
- **LP-2** Text-only results post as a text post; results with images/video post the text as commentary with the media attached.
- **LP-3** If LinkedIn settings are missing or invalid, tell the user and link to the LinkedIn settings screen *(inferred)*.
- **LP-4** Show success (ideally with a link to the post) or a clear error *(inferred)*.

## Content constraints *(inferred, to confirm)*

- LinkedIn post commentary is limited to about 3,000 characters, so long technical write-ups (6.1) must be condensed into post format like slide 12, not posted verbatim. Generation prompts should target that format (Q-11).
- LinkedIn renders plain text only: Markdown headings/tables must be converted (emoji headers, line breaks), and tables turned into an image as in slide 12.
- Multi-image posts and video posts have their own size/count limits on LinkedIn.
- A preview/edit step before publishing is recommended (Q-12).
