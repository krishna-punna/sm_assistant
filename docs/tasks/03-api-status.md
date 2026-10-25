# 03 — API Status (P1)

Requirement: [11 API connection status](../requirements/11-api-status.md).
Current code: `ApiStatusScreen.kt`, `data/ApiConnectionCheck.kt` (PR #4).

## Status by requirement

| ID | Requirement | Status | Gap |
|----|-------------|--------|-----|
| AS-1 | Test every service on open, in parallel | done | |
| AS-2 | Use saved settings, falling back to `app.yml` | done | |
| AS-3 | Cheap read-only request per service (models list / userinfo) to the configured host | done | LinkedIn endpoint pending Q-21 |
| AS-4 | Status mapping: Connected / Not set up / Invalid key / No access / Unreachable / Error(code) | done | |
| AS-5 | Test again resets to Checking and re-runs | partial | Works, but a slow earlier run can overwrite a newer result (no cancellation of the previous round) |
| AS-6 | Keys never shown | done | |
| AS-7 *(new, Q-22)* | Test button on each settings screen | todo (optional) | Waiting on Q-22 |

## Implementation

| ID | Task | Depends on | Status |
|----|------|-----------|--------|
| AS-5-I | Move checks into `ApiStatusViewModel`; cancel the previous round's jobs when Test again is tapped; disable the button while all four are Checking. | FND-3 | todo |
| AS-I2 | Port `ApiConnectionCheck` to the shared HTTP layer and error model (FND-5/6) with injectable base URL. | FND-5, FND-6 | done |
| AS-3-I | If Q-21/Q-03 changes the LinkedIn auth, switch the LinkedIn check endpoint (e.g. token introspection or `/rest/me`). | Q-21 | blocked |
| AS-7-I | Reuse the check on settings screens: "Test" next to SAVE shows the status inline. | Q-22, LI-2-I | todo (optional) |

## Validation

| ID | Check | Status |
|----|-------|--------|
| AS-V1 | With valid keys for all four: all Connected within ~2 s. | todo |
| AS-V2 | Wrong key → Invalid key; empty key → Not set up with no network request (check with a proxy or logcat); airplane mode → Unreachable; bad URL `htp:/x` → Unreachable "Invalid API URL". | todo |
| AS-V3 | LinkedIn token with only `w_member_social` shows No access (documents Q-21 behaviour). | todo |
| AS-V4 | Tap Test again repeatedly: cards end in the latest result, never stuck on Checking. | todo |

## Tests

| ID | Kind | Test | Status |
|----|------|------|--------|
| AS-T1 | unit | MockWebServer per service: 200 → Connected, 400/401 → InvalidKey, 403 → NoPermission, 500 → Error(500), timeout → Unreachable. | done (`ApiConnectionCheckTest`) |
| AS-T2 | unit | Correct path and auth header per service (`/v1/models` + `x-api-key` + `anthropic-version`, `/v1beta/models` + `x-goog-api-key`, `/openai/v1/models` + Bearer, `/v2/userinfo` + Bearer). | done (`ApiConnectionCheckTest`) |
| AS-T3 | unit | Blank API or key → NotConfigured with zero requests recorded. | done (`ApiConnectionCheckTest`) |
| AS-T4 | unit | Origin extraction: path dropped, port kept, invalid URL → Unreachable. | done (`ApiConnectionCheckTest`) |
| AS-T5 | unit | ViewModel: Test again cancels the previous round; final state matches the second round. | todo |
| AS-T6 | UI | Status cards render label, detail and colour for each `ConnectionStatus`; no key text appears in the tree. | todo |
