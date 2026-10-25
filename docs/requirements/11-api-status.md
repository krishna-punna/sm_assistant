# 11. API connection status

Source: product owner request on 2026-10-25, not part of the original slide deck. No wireframe yet.

## Purpose

Let the user confirm that the configured APIs and keys actually work before using the generators or posting, so a wrong or expired key shows up straight away instead of as a failed generation.

## Entry point

Home → **API Status** *(S15 in the [screen inventory](02-screens-and-navigation.md))*.

## Layout

| Element | Type | Notes |
|---------|------|-------|
| "SM Assistant" | App bar | |
| "API Status" | Section chip (yellow) | |
| One card per service | Status card | LinkedIn, Claude, Gemini, Groq. Shows the service name, a coloured status dot (spinner while checking), a status label and a one-line detail. |
| Test again | Button (green) | Re-runs every check. |
| Back to Home | Footer button (black) | |

## Functional requirements

- **AS-1** Opening the screen tests every service immediately; the checks run in parallel.
- **AS-2** Each check uses the service's current settings: the value saved on its settings screen, or the default from `app.yml` when nothing is saved.
- **AS-3** Each check is a cheap, read-only request that generates nothing, posts nothing, and normally costs no tokens:

  | Service | Request |
  |---------|---------|
  | Claude | `GET /v1/models` with `x-api-key` |
  | Gemini | `GET /v1beta/models` with `x-goog-api-key` |
  | Groq | `GET /openai/v1/models` with a Bearer key |
  | LinkedIn | `GET /v2/userinfo` with the access token as Bearer |

  The request goes to the host of the configured API URL.
- **AS-4** Results map to these statuses:

  | Status | When | Colour |
  |--------|------|--------|
  | Connected | HTTP 2xx | Green |
  | Not set up | API URL or key is empty (no request is sent) | Grey |
  | Invalid key | HTTP 400 or 401 | Red |
  | No access | HTTP 403: the key is recognised but lacks permission for the check | Amber |
  | Unreachable | Network error, timeout (10 s), or an invalid API URL | Red |
  | Error | Any other HTTP status (code shown) | Red |

- **AS-5** **Test again** resets every card to "Checking" and re-runs all checks.
- **AS-6** Keys are never shown on this screen.

## Open questions

| ID | Question |
|----|----------|
| Q-21 | LinkedIn `/v2/userinfo` needs the `openid`/`profile` scopes. A token that only has `w_member_social` (enough to post) will show **No access**. Is that acceptable, or should the check use a different endpoint once the LinkedIn auth flow (Q-03) is decided? |
| Q-22 | Should each settings screen also get its own **Test** button next to SAVE? |
| Q-23 | Should the generators warn or block when their agent's last status was not Connected? |
