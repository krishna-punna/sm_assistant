# 14. Prompt types and the image/video content workflow

Source: "SM Assistant Requirements 2" deck from the product owner, 2026-10-27.

## Purpose

Images and videos were drawn straight from the topic, so they were only as deep as one short planning reply. The owner wants every image and video to start from full technical content with no length limit, and wants Prepare Prompt to write the prompt for that content too.

## Prepare Prompt: Prompt Type

- **PT-1** Prepare Prompt has a **Prompt Type** picker next to the Model picker: Text, Image, Video, Text & Image, Text & Video. The choice is remembered.
- **PT-2** **Text**: no change. One prompt for a LinkedIn post, as today.
- **PT-3** **Image** and **Video**: one prompt, labelled "Text Generation Prompt [No Limit]", asking for in-depth technical content with no length limit that will be shown as an image (infographic cheat sheet) or a video (slideshow).
- **PT-4** **Text & Image** and **Text & Video**: two prompts, "Text Generation Prompt [Within LinkedIn Post Limit]" (the Text prompt) and "Text Generation Prompt [No Limit]" (the Image or Video prompt).
- **PT-5** Each prompt card keeps Copy and Expand.

## Image and video generation workflow

- **MW-1** Generate Image: the text agent first writes technical content on the prompt with no length limit; that content, not the bare prompt, is passed to the image pipeline, which represents it as the image(s).
- **MW-2** Generate Video: the same, with the content passed to the video pipeline (the slide deck).
- **MW-3** Content & Image / Content & Video: the LinkedIn post is generated exactly as today, within the post limit. The image or video is generated from separate no-limit content, as in MW-1 and MW-2. The screen shows the post and the image or video.
- **MW-4** Post in LinkedIn is unchanged.
