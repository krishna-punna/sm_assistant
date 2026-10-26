# SM Assistant — Requirements Knowledge Base

**SM Assistant** (Social Media Personal Assistant) is a mobile application that helps a user turn a short topic idea into a polished LinkedIn post (text, image, video, or a mix) using AI agents, and publish it to LinkedIn from the app.

This knowledge base is derived from the wireframe deck [`requirements/SM Assistant.pptx`](../../requirements/SM%20Assistant.pptx) (12 slides, no speaker notes). Rendered copies of every wireframe slide are in [`wireframes/`](wireframes/).

> Where the wireframes are silent or ambiguous, the docs say so explicitly and the item is listed in [Open questions](09-open-questions.md). Statements marked *(inferred)* are reasonable readings of the wireframes, not something the slides state directly.

## Contents

| # | Document | What it covers | Source slides |
|---|----------|----------------|---------------|
| 1 | [Product overview](01-product-overview.md) | Purpose, user, end-to-end workflow, scope | 1, 5, 12 |
| 2 | [Screens and navigation](02-screens-and-navigation.md) | Screen inventory, navigation map, common layout | 2–11 |
| 3 | [LinkedIn API settings](03-linkedin-settings.md) | Configuring LinkedIn credentials | 2 |
| 4 | [AI agent settings](04-ai-agent-settings.md) | Configuring Claude, Gemini and Groq | 3, 4 |
| 5 | [Prepare LinkedIn Post Prompt](05-prepare-post-prompt.md) | Turning a topic into a detailed prompt | 5 |
| 6 | [Content generation features](06-content-generation.md) | Technical content, image, video, content & image, content & video | 6–11 |
| 7 | [Posting to LinkedIn](07-post-to-linkedin.md) | "Post in LinkedIn" action and the target post format | 6–12 |
| 8 | [UI conventions](08-ui-conventions.md) | Colours, components and layout rules used across wireframes | all |
| 9 | [Open questions](09-open-questions.md) | Gaps and ambiguities to resolve with the product owner | — |
| 10 | [Default hashtags](10-default-hashtags.md) | Default hashtag list, enable/disable toggle, appending tags when posting | [extra wireframe](wireframes/default-hashtags.png) |
| 11 | [API connection status](11-api-status.md) | Screen that tests each API and key and shows whether it connects | — (request 2026-10-25) |
| 12 | [LinkedIn post length](12-post-length.md) | Generating posts within 3,000 characters including default hashtags, checking, shortening and cutting to fit; Trim post | — (requests 2026-10-25, 2026-10-26) |
| 13 | [Scheduled LinkedIn posts](13-scheduled-posts.md) | Schedulers that pick a technical topic, prepare a prompt, generate a text, image or video post, check its length and publish it on a schedule | — (request 2026-10-26) |

## Slide index

| Slide | Content | Image |
|-------|---------|-------|
| 1 | Title: "SM Assistant — Social Media Personal Assistant Mobile Application Requirements" | — |
| 2 | Home menu; LinkedIn API settings screen | [slide-02](wireframes/slide-02.jpg) |
| 3 | AI Agent list; Claude AI Agent settings | [slide-03](wireframes/slide-03.jpg) |
| 4 | Gemini and Groq AI Agent settings | [slide-04](wireframes/slide-04.jpg) |
| 5 | Social Media Assistant hub; Prepare LinkedIn Post Prompt (input → output) | [slide-05](wireframes/slide-05.jpg) |
| 6 | Generate Technical Content (3 states) | [slide-06](wireframes/slide-06.jpg) |
| 7 | Generate Image (3 states) | [slide-07](wireframes/slide-07.jpg) |
| 8 | Generate Image — full image preview with "Post in LinkedIn" | [slide-08](wireframes/slide-08.jpg) |
| 9 | Generate Video (3 states) | [slide-09](wireframes/slide-09.jpg) |
| 10 | Generate Content & Video (3 states) | [slide-10](wireframes/slide-10.jpg) |
| 11 | Generate Content & Image (3 states) | [slide-11](wireframes/slide-11.jpg) |
| 12 | Screenshot of a published LinkedIn post (reference for the desired output) | [slide-12](wireframes/slide-12.jpg) |
| — | Default Hash Tags screen (added 2026-10-25, not in the deck) | [default-hashtags](wireframes/default-hashtags.png) |
| — | API Status screen (added 2026-10-25, no wireframe) | — |

## Glossary

| Term | Meaning |
|------|---------|
| **SM Assistant** | The app; also the name of the hub screen that lists the content-generation features. |
| **AI Agent** | A configured LLM provider (Claude, Gemini, Groq) used to generate prompts and content. |
| **Post prompt** | A detailed, expert-style prompt the app builds from a short topic (e.g. "Design a system for Short URL"). |
| **Feature screen** | Any of the six chat-style screens launched from the hub (Prepare prompt, Technical Content, Image, Video, Content & Image, Content & Video). |
| **Default hashtags** | User-maintained list of hashtags appended to every LinkedIn post when the toggle is on. |
| **Post in LinkedIn** | Action that publishes the generated result to the user's LinkedIn account. |
