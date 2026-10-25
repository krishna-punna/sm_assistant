# 6. Content generation features

Source: slides 6–11.

The hub (S7) offers five generators. All five use the **same chat-style screen** and the **same three-state flow**; they differ only in what the result contains.

| Feature | Result | Slides |
|---------|--------|--------|
| Generate Technical Content | Long-form technical text (Markdown-style: headings, lists, tables, code) | [6](wireframes/slide-06.jpg) |
| Generate Image | One or more images (e.g. architecture diagrams) | [7](wireframes/slide-07.jpg), [8](wireframes/slide-08.jpg) |
| Generate Video | Video *(wireframe reuses image placeholders, see Q-08)* | [9](wireframes/slide-09.jpg) |
| Generate Content & Image | Technical text followed by image(s) | [11](wireframes/slide-11.jpg) |
| Generate Content & Video | Technical text followed by video | [10](wireframes/slide-10.jpg) |

## Shared screen layout

| Element | Notes |
|---------|-------|
| App bar "SM Assistant" | |
| Section chip | Feature name, e.g. "Generate Image" |
| Chat / result area | Scrollable (a scrollbar is drawn in state 3) |
| Input bar + **Submit** | Pinned above the footer |
| **Post in LinkedIn** | Appears under the generated result |
| **Back to Home** | Footer |

## Shared three-state flow

| State | Description |
|-------|-------------|
| 1. Compose | Chat area is empty. The user pastes the prepared prompt (from [Prepare LinkedIn Post Prompt](05-prepare-post-prompt.md)) into the input and taps **Submit**. In the wireframe the pasted prompt card overlaps the input bar, i.e. the input grows to fit long text. |
| 2. Submitted | The prompt appears as a message card ("URL Shortener System Design Prompt", with copy/expand icons) at the top of the chat. Input bar is cleared. *(A loading indicator is implied but not drawn, Q-09.)* |
| 3. Result | The generated output appears below the prompt, with a **Post in LinkedIn** button under it. The input bar stays available for follow-up instructions *(inferred)*. |

## Feature details

### 6.1 Generate Technical Content (slide 6)

Result is a rich-text document. The wireframe example, "System Design: Short URL Service", contains:

- **1. Problem Statement** with an example original URL and short URL (`https://sho.rt/aB91xZ`).
- **Functional Requirements** — numbered list: Create Short URL, Redirect, Custom Alias, Expiration, Analytics, Management.
- **Non-Functional Requirements** — table of Requirement / Target: Availability 99.99%, Redirect latency < 100 ms, Scalability billions of URLs, Durability, Uniqueness, Security, Performance (read-heavy).

Requirements:
- **TC-1** Render Markdown (headings, numbered/bulleted lists, tables, inline code / code blocks).
- **TC-2** Result area scrolls for long output.
- **TC-3** **Post in LinkedIn** publishes the text (see [07](07-post-to-linkedin.md) for length/format handling).

### 6.2 Generate Image (slides 7–8)

Result shows the prompt card, then an image strip with diagrams of the system (e.g. "System Design: URL Shortener Architecture" and "URL Shortener — End-to-End Architecture"). A badge on the strip shows an image count ("10") *(inferred: multiple images, swipeable gallery)*.

Slide 8 shows a dedicated **full image preview**: the selected image fills the body, with **Post in LinkedIn** and **Back to Home** beneath.

Requirements:
- **IM-1** Generate one or more images from the prompt.
- **IM-2** Show images as a thumbnail strip / carousel with a count indicator.
- **IM-3** Tapping an image opens a full preview (slide 8).
- **IM-4** **Post in LinkedIn** is available from both the result and the preview.
- **IM-5** *(inferred)* Allow choosing which image(s) to post (Q-10).

The example architecture image depicts: Users → Client (web/mobile) → Load Balancer (NGINX) → API Servers → Database (MongoDB) and ID Generation Service (KGS); Cache (Redis, LRU) for redirect lookups; optional async Analytics worker; redirect to Target Website.

### 6.3 Generate Video (slide 9)

Identical flow and layout to Generate Image; the result area in the wireframe reuses the image thumbnails.

- **VI-1** Generate a short video from the prompt (e.g. an animated explainer of the architecture) *(inferred)*.
- **VI-2** Play the video inline with standard controls *(inferred)*.
- **VI-3** **Post in LinkedIn** publishes the video.

### 6.4 Generate Content & Image (slide 11)

Result combines 6.1 and 6.2: the technical write-up ("System Design: Short URL Service") followed by the image strip, then **Post in LinkedIn**.

- **CI-1** Generate both text and image(s) from a single submit.
- **CI-2** **Post in LinkedIn** publishes the text as the post body with the image(s) attached.

### 6.5 Generate Content & Video (slide 10)

Same as 6.4 with video instead of images.

- **CV-1** Generate both text and a video from a single submit.
- **CV-2** **Post in LinkedIn** publishes the text as the post body with the video attached.

## Cross-cutting requirements

- **GEN-1** Each feature uses the configured AI agent(s); if none is configured, prompt the user to set one up *(inferred)*.
- **GEN-2** Prompt cards support copy and expand.
- **GEN-3** Long results scroll within the chat area; header, input bar and footer stay fixed.
- **GEN-4** *(inferred)* Show progress while generating and a clear error with retry on failure (Q-09).
