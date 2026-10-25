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
| Signed-in status | Text + "Sign out" | "Signed in as <name>" and the token's expiry date (or "Token expired… Sign in again"). Shown only while the saved token is the one sign-in produced. *(Q-03)* |
| "Client ID" | Text box | From the user's LinkedIn developer app. *(Q-03)* |
| "Client Secret" | Text box, masked | From the same app; stored encrypted on the device. *(Q-03)* |
| "Redirect URL" | Text box | Defaults to `https://www.linkedin.com/developers/tools/oauth/redirect`; must be listed under the app's Authorized redirect URLs. *(Q-03)* |
| Sign in with LinkedIn | Primary button (green) | Opens LinkedIn's sign-in page in an in-app browser. Reads "Sign in again" when signed in. *(Q-03)* |
| "Or paste an access token" | Heading | Fallback for a token obtained elsewhere (e.g. LinkedIn's OAuth token tool). |
| "LinkedIn APIs" | Label + text box | Wireframe label reads "Linked APIs" (typo). The Posts API endpoint (`https://api.linkedin.com/rest/posts`). |
| "LinkedIn API Key" | Label + text box | Wireframe label reads "Linked API Key" (typo). The access token; filled in by sign-in, or pasted. |
| SAVE | Primary button (green) | Persists both values. |
| Back to Home | Footer button (black) | Returns to Home. |

## Functional requirements

- **LI-1** The user can enter and edit the LinkedIn API value and LinkedIn API key.
- **LI-2** Tapping **SAVE** persists both values on the device so they survive app restarts.
- **LI-3** Saved values are pre-filled when the screen is reopened.
- **LI-4** Posting to LinkedIn (see [07](07-post-to-linkedin.md)) uses these saved values.
- **LI-5** *(Q-03)* The user can sign in with LinkedIn using their own developer app: enter Client ID, Client Secret and redirect URL, tap **Sign in with LinkedIn**, approve on LinkedIn, and the app saves the access token (scopes `openid profile w_member_social`) as the LinkedIn key, then shows the member's name and the token's expiry.
- **LI-6** *(Q-03)* **Sign out** removes the token and the browser's LinkedIn login; the Client ID and Secret stay saved.
- **LI-7** *(Q-03)* Pasting an access token stays available as a fallback.

## Setting up the LinkedIn developer app (Q-03)

1. In the [LinkedIn Developer Portal](https://www.linkedin.com/developers/apps), create an app (or open yours) and add the products **Sign In with LinkedIn using OpenID Connect** and **Share on LinkedIn**.
2. Under **Auth → Authorized redirect URLs for your app**, add exactly the redirect URL shown on the app's LinkedIn screen (default `https://www.linkedin.com/developers/tools/oauth/redirect`).
3. Copy the **Client ID** and **Primary Client Secret** into the app and tap **Sign in with LinkedIn**.

Why an in-app browser and not a Custom Tab: LinkedIn only accepts http(s) redirect URLs, so it can't redirect to an app scheme, and an https App Link needs a verified domain the app doesn't have. The in-app browser catches the redirect URL before it loads, checks the `state` value, and swaps the code for a token. The default redirect URL is LinkedIn's own page, so the code never goes to a third-party site.

Caveat: the authorization-code flow needs the Client Secret, so it lives on the phone (encrypted with the Android Keystore). Anyone with full access to the unlocked device could extract it; use a LinkedIn app made only for SM Assistant. Tokens last 60 days, then the user signs in again (LinkedIn only issues refresh tokens to approved partners).

## Non-functional requirements *(inferred)*

- The API key is a secret: store it in secure storage (Keychain / Android Keystore), mask it in the UI, and never log it.
- Show a confirmation on save and validation errors for empty fields.
- LinkedIn's posting API uses OAuth 2.0 member tokens rather than a static key; the app gets one through Sign in with LinkedIn (Q-03, answered).
