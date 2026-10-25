# 3. LinkedIn API settings

Source: slide 2 ([wireframe](wireframes/slide-02.jpg)).

## Purpose

Lets the user connect the app to their LinkedIn account so generated content can be published with **Post in LinkedIn**.

## Entry point

Home → **LinkedIn API**.

## Layout

| Element | Type | Notes |
|---------|------|-------|
| "SM Assistant" | App bar | |
| "LinkedIn" | Section chip (yellow) | |
| "LinkedIn APIs" | Label + text box | Wireframe label reads "Linked APIs" (typo). Likely the LinkedIn API endpoint / app identifier *(inferred, see Q-03)*. |
| "LinkedIn API Key" | Label + text box | Wireframe label reads "Linked API Key" (typo). Secret credential. |
| SAVE | Primary button (green) | Persists both values. |
| Back to Home | Footer button (black) | Returns to Home. |

## Functional requirements

- **LI-1** The user can enter and edit the LinkedIn API value and LinkedIn API key.
- **LI-2** Tapping **SAVE** persists both values on the device so they survive app restarts.
- **LI-3** Saved values are pre-filled when the screen is reopened.
- **LI-4** Posting to LinkedIn (see [07](07-post-to-linkedin.md)) uses these saved values.

## Non-functional requirements *(inferred)*

- The API key is a secret: store it in secure storage (Keychain / Android Keystore), mask it in the UI, and never log it.
- Show a confirmation on save and validation errors for empty fields.
- LinkedIn's posting API normally uses OAuth 2.0 member tokens rather than a static key; an OAuth sign-in flow may be needed instead of, or in addition to, a key field (Q-03).
