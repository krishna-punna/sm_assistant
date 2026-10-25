# 9. Open questions

Gaps and ambiguities found while reading the wireframes. Each should be resolved with the product owner before implementation.

| ID | Area | Question |
|----|------|----------|
| Q-01 | Home (slide 2) | The Home screen shows **AI Agent** twice. Is the second button a mistake, or is it meant to be something else (e.g. a default-agent selector or another integration)? |
| Q-02 | Navigation | The Social Media Assistant hub (slide 5) has no **Back to Home** button, unlike every other screen. Should it? Should screens also have a normal "back" (one level up)? |
| Q-03 | LinkedIn settings | What exactly goes in "LinkedIn APIs" (endpoint, client ID, access token)? LinkedIn posting normally requires OAuth 2.0 sign-in with the `w_member_social` scope; should the app implement a "Sign in with LinkedIn" flow instead of a pasted key? **Answered 2026-10-25 (Krishna): in-app LinkedIn OAuth sign-in with the user's own developer app (Client ID + Client Secret typed into the app); the pasted token stays as a fallback.** See [03](03-linkedin-settings.md). |
| Q-04 | AI agent settings | What does "<Agent> APIs" hold: base URL, model name, or both? Should the user be able to pick a model per agent? |
| Q-05 | Agent selection | When several agents are configured, which one does each feature use? Is there a default agent, a per-feature choice, or a picker on each feature screen? |
| Q-06 | Prompt hand-off | Should a prepared prompt be sendable directly to a generator (button on the prompt card) instead of copy/paste? Should generators also accept a raw topic and prepare the prompt internally? |
| Q-07 | Hub | Is the hub a separate screen (Home → SM Assistant → hub) or should its six features sit directly on Home? |
| Q-08 | Video | The video wireframes reuse image placeholders. What kind of video is expected (animated diagram, narrated explainer, slideshow of generated images), what length, and which provider/model generates it? |
| Q-09 | States | Loading, error, empty and "not configured" states are not drawn. Expected behaviour? |
| Q-10 | Images | How many images per generation? Can the user pick which to post? What does the "10" badge on the image strip mean? |
| Q-11 | Post format | Should generators produce LinkedIn-ready text (≤ ~3,000 chars, emoji sections like slide 12) or a long article that is condensed at posting time? |
| Q-12 | Posting | Is there a preview/edit step before publishing? Should drafts or post history be kept? |
| Q-13 | Storage | Where are conversations and generated media stored, and for how long (device only vs. backend)? |
| Q-14 | Architecture | Does the mobile app call AI/LinkedIn APIs directly, or through a backend service (which would keep API keys off the device)? |
| Q-15 | Platforms | Target platforms (Android, iOS, both) and framework? |
| Q-16 | Accounts | Is there any app login, or is it a single-user personal tool? |
| Q-17 | Default hashtags | The wireframe has no enable/disable control. The docs put an **Add default hashtags to LinkedIn posts** toggle on the Default Hash Tags screen. Should it live there, or on a separate settings screen? |
| Q-18 | Default hashtags | Should **Clear** only empty the text box (the documented behaviour) or also delete the saved list immediately? |
| Q-19 | Default hashtags | Should the user be able to skip or edit the default hashtags for a single post, or is the global toggle enough? |
| Q-20 | Default hashtags | Should the AI also suggest topic-specific hashtags (e.g. `#SystemDesign`) in addition to the defaults? |
| Q-21 | API status | LinkedIn check uses `/v2/userinfo`, which needs `openid`/`profile` scopes; a post-only token shows "No access". Acceptable? See [11](11-api-status.md). |
| Q-22 | API status | Should each settings screen also have its own **Test** button? |
| Q-23 | API status | Should generators warn or block when their agent is not Connected? |
