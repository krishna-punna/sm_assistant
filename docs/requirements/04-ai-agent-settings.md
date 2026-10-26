# 4. AI agent settings

Source: slides 3–4 ([slide 3](wireframes/slide-03.jpg), [slide 4](wireframes/slide-04.jpg)).

## Purpose

Lets the user configure the AI providers ("AI agents") the app uses to prepare prompts and generate content.

## Supported agents

| Agent | Settings screen | Fields |
|-------|-----------------|--------|
| **Claude AI Agent** (Anthropic) | S4, slide 3 right | *Claude APIs*, *Claude API Key* |
| **Gemini AI Agent** (Google) | S5, slide 4 left | *Gemini APIs*, *Gemini API Key* |
| **Groq AI Agent** (Groq) | S6, slide 4 right | *Groq APIs*, *Groq API Key* |
| **Hugging Face AI Agent** ([Inference Providers](https://huggingface.co/docs/inference-providers/en/index)) | added 2026-10-26 | *Hugging Face APIs*, *Hugging Face API Key* (an access token), model, image model |
| **Cloudflare AI Agent** ([Workers AI](https://developers.cloudflare.com/workers-ai/)) | added 2026-10-26 | *Cloudflare APIs*, *Cloudflare Account ID*, *Cloudflare API Key* (an API token with Workers AI permission), model, image model |
| **Pollinations.AI AI Agent** ([gen.pollinations.ai](https://enter.pollinations.ai)) | added 2026-10-26 | *Pollinations.AI APIs*, *Pollinations.AI API Key* (a secret `sk_` key), model, image model |

The three newer agents write text and make images:

| Agent | Text | Images | Model lists |
|-------|------|--------|-------------|
| Hugging Face | `POST https://router.huggingface.co/v1/chat/completions` (OpenAI-compatible) | The first live provider for the image model, from `GET https://huggingface.co/api/models/{image model}?expand[]=inferenceProviderMapping`: fal-ai (`POST /fal-ai/{provider model}?_subdomain=queue`, then poll and download `images[0].url`), together (`POST /together/v1/images/generations`, base64) or hf-inference (`POST /hf-inference/models/{image model}`, image bytes). Default image model: `Qwen/Qwen-Image` (served by fal-ai) | Router `GET /v1/models`; image models from the Hub, `GET https://huggingface.co/api/models?pipeline_tag=text-to-image&inference_provider=hf-inference,fal-ai,together` |
| Cloudflare | `POST {api}/v1/chat/completions` (OpenAI-compatible), with `{api}` = `https://api.cloudflare.com/client/v4/accounts/{account_id}/ai` | `POST {api}/run/{image model}` with `{"prompt": prompt}`; FLUX answers base64 JSON, Stable Diffusion the PNG | `GET {api}/models/search`, split by task (*Text Generation*, *Text-to-Image*) |
| Pollinations.AI | `POST https://gen.pollinations.ai/v1/chat/completions` (OpenAI-compatible) | `POST https://gen.pollinations.ai/v1/images/generations` with `response_format: b64_json` | `GET /v1/models`, split by `output_modalities` |

**AI video clips (Hugging Face).** When Hugging Face is the video agent and its video model is a text-to-video model (default `Wan-AI/Wan2.2-T2V-A14B`, Wan 2.2), Generate Video asks that model for a short clip instead of making a slideshow. The clip prompt comes from `assets/prompts/video_clip.md`, and the request runs on fal-ai through the router: the same queue as Qwen-Image, with the clip downloaded from `video.url`. The video model list holds text-to-video models served by fal-ai (`GET https://huggingface.co/api/models?pipeline_tag=text-to-video&inference_provider=fal-ai`), for example Wan 2.2, LTX-Video and HunyuanVideo. If the chosen video model isn't a text-to-video model, Generate Video makes a slideshow instead. Stable Video Diffusion XT animates an image rather than following a text prompt, so it isn't offered.

Diffusion models read only the start of a prompt (and Cloudflare's FLUX takes at most 2,048 characters), so prompts over 2,000 characters keep their start (house style) and end (topic) and drop the few-shot examples in the middle.

## AI Agent list (S3)

- Entry: Home → **AI Agent**.
- Section chip "AI Agent".
- One full-width row/button per agent: *Claude*, *Gemini*, *Groq*, *Hugging Face*, *Cloudflare* and *Pollinations.AI AI Agent*, each opening its settings screen.
- **Back to Home** footer.

## Agent settings screen (S4–S6)

All three screens share one layout:

| Element | Type | Notes |
|---------|------|-------|
| "SM Assistant" | App bar | |
| "<Agent> AI Agent" | Section chip (yellow) | |
| "<Agent> APIs" | Label + text box | Likely API base URL and/or model name *(inferred, see Q-04)*. |
| "<Agent> API Key" | Label + text box | Secret credential. |
| SAVE | Primary button (green) | |
| Back to Home | Footer button (black) | |

## Functional requirements

- **AG-1** The user can open settings for each of Claude, Gemini, Groq, Hugging Face, Cloudflare and Pollinations.AI from the AI Agent list.
- **AG-2** For each agent, the user can enter and edit the API value and API key, and save them.
- **AG-3** Saved values persist across restarts and are pre-filled when the screen is reopened.
- **AG-4** Agents are configured independently; configuring one does not require the others.
- **AG-5** Generation features use the configured agent(s). *How the app chooses which agent to use for each feature is not specified (Q-05).*

## Non-functional requirements *(inferred)*

- API keys stored in secure storage, masked in the UI, never logged or sent anywhere except the provider.
- A way to verify a key (e.g. a test call on SAVE) would help the user catch mistakes early.
- The settings screen pattern should be generic so more agents can be added later.

## Notes on capabilities *(inferred)*

Not every provider can produce every output type. Text is available on all six; image generation is available from Gemini (Imagen/Gemini image models), Hugging Face, Cloudflare and Pollinations.AI (FLUX and Stable Diffusion models), and Claude draws SVG infographics; Groq does not generate images. Video generation needs a video-capable model (e.g. Google Veo). This affects how features map to agents (Q-05, Q-08).
