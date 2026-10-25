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

## AI Agent list (S3)

- Entry: Home → **AI Agent**.
- Section chip "AI Agent".
- Three full-width rows/buttons: *Claude AI Agent*, *Gemini AI Agent*, *Groq AI Agent*, each opening its settings screen.
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

- **AG-1** The user can open settings for each of Claude, Gemini and Groq from the AI Agent list.
- **AG-2** For each agent, the user can enter and edit the API value and API key, and save them.
- **AG-3** Saved values persist across restarts and are pre-filled when the screen is reopened.
- **AG-4** Agents are configured independently; configuring one does not require the others.
- **AG-5** Generation features use the configured agent(s). *How the app chooses which agent to use for each feature is not specified (Q-05).*

## Non-functional requirements *(inferred)*

- API keys stored in secure storage, masked in the UI, never logged or sent anywhere except the provider.
- A way to verify a key (e.g. a test call on SAVE) would help the user catch mistakes early.
- The settings screen pattern should be generic so more agents can be added later.

## Notes on capabilities *(inferred)*

Not every provider can produce every output type. Text is available on all three; image generation is available from Gemini (Imagen/Gemini image models); Claude and Groq do not generate images or video natively. Video generation needs a video-capable model (e.g. Google Veo). This affects how features map to agents (Q-05, Q-08).
