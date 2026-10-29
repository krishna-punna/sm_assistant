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
| **Cloudflare AI Agent** ([Workers AI](https://developers.cloudflare.com/workers-ai/)) | added 2026-10-26 | *Cloudflare APIs*, *Cloudflare Account ID*, *Cloudflare API Key* (an API token with Workers AI permission), model, image model |
| **Pollinations.AI AI Agent** ([gen.pollinations.ai](https://enter.pollinations.ai)) | added 2026-10-26 | *Pollinations.AI APIs*, *Pollinations.AI API Key* (a secret `sk_` key), model, image model |

The two newer agents write text and make images. A Hugging Face agent was added on 2026-10-26 and removed on 2026-10-28 at Krishna's request, because its Inference Providers aren't free (they answer 402 once the small monthly credit is used up).

| Agent | Text | Images | Model lists |
|-------|------|--------|-------------|
| Cloudflare | `POST {api}/v1/chat/completions` (OpenAI-compatible), with `{api}` = `https://api.cloudflare.com/client/v4/accounts/{account_id}/ai` | `POST {api}/run/{image model}` with `{"prompt": prompt}`; FLUX answers base64 JSON, Stable Diffusion the PNG | `GET {api}/models/search`, split by task (*Text Generation*, *Text-to-Image*) |
| Pollinations.AI | `POST https://gen.pollinations.ai/v1/chat/completions` (OpenAI-compatible) | `POST https://gen.pollinations.ai/v1/images/generations` with `response_format: b64_json` | `GET /v1/models`, split by `output_modalities` |

Diffusion models read only the start of a prompt (and Cloudflare's FLUX takes at most 2,048 characters), so prompts over 2,000 characters keep their start (house style) and end (topic) and drop the few-shot examples in the middle.

## AI Agent list (S3)

- Entry: Home → **AI Agent**.
- Section chip "AI Agent".
- One full-width row/button per agent: *Claude*, *Gemini*, *Groq*, *Cloudflare* and *Pollinations.AI AI Agent*, each opening its settings screen.
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

- **AG-1** The user can open settings for each of Claude, Gemini, Groq, Cloudflare and Pollinations.AI from the AI Agent list.
- **AG-2** For each agent, the user can enter and edit the API value and API key, and save them.
- **AG-3** Saved values persist across restarts and are pre-filled when the screen is reopened.
- **AG-4** Agents are configured independently; configuring one does not require the others.
- **AG-5** Generation features use the configured agent(s). *How the app chooses which agent to use for each feature is not specified (Q-05).*

## Non-functional requirements *(inferred)*

- API keys stored in secure storage, masked in the UI, never logged or sent anywhere except the provider.
- A way to verify a key (e.g. a test call on SAVE) would help the user catch mistakes early.
- The settings screen pattern should be generic so more agents can be added later.

## Notes on capabilities *(inferred)*

Not every provider can produce every output type. Text is available on all five; image generation is available from Gemini (Imagen/Gemini image models), Cloudflare and Pollinations.AI (FLUX and Stable Diffusion models), and Claude draws SVG infographics; Groq does not generate images. Video generation needs a video-capable model (e.g. Google Veo). This affects how features map to agents (Q-05, Q-08).
