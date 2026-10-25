# 02 — LinkedIn and AI agent settings (P1)

Requirements: [03 LinkedIn settings](../requirements/03-linkedin-settings.md), [04 AI agent settings](../requirements/04-ai-agent-settings.md).
Current code: `SettingsScreens.kt` (`ApiSettingsForm`), `data/ApiSettings.kt` (`ApiSettingsStore`, `AppConfig` from `assets/app.yml`).

## Status by requirement

| ID | Requirement | Status | Gap |
|----|-------------|--------|-----|
| LI-1 | Enter/edit LinkedIn API value and key | done | |
| LI-2 | SAVE persists both values across restarts | done | Validated; key encrypted |
| LI-3 | Saved values pre-filled on reopen | done | |
| LI-4 | Posting uses saved values | todo | No LinkedIn client yet (LP-1) |
| LI-NFR | Key masked, secure storage, never logged | partial | Masked and encrypted; logging FND-6 |
| LI-Q3 | Auth model: pasted token vs "Sign in with LinkedIn" OAuth | **blocked (Q-03)** | |
| AG-1 | Open Claude, Gemini, Groq settings from the list | done | |
| AG-2 | Enter/edit/save API value and key per agent | done | Validated; key encrypted |
| AG-3 | Persist and pre-fill | done | |
| AG-4 | Agents configured independently | done | Separate keys per service |
| AG-5 | Generators use the configured agent(s) | partial | Clients done (AGC-1); selection and generators next |
| AG-6 *(new, Q-04)* | Show/edit the model per agent | blocked (Q-04) | `model` comes only from `app.yml` (D-6) |

## Implementation

| ID | Task | Depends on | Status |
|----|------|-----------|--------|
| LI-2-I / AG-2-I | Settings ViewModel: validate on SAVE (API must be an `https://` URL; key non-empty), inline field errors, toast on success. Saving a blank API clears the override (falls back to `app.yml`) instead of storing `""`. | — | done — `ApiSettingsViewModel` |
| LI-2-I2 / AG-2-I2 | Save through the secure store (FND-4). | FND-4 | done |
| LI-4-I | `LinkedInClient` reads `ApiSettingsStore.load(LINKEDIN)` at call time (no caching), so a new key takes effect immediately. | LP-1-I | done |
| LI-Q3-I | **If Q-03 = OAuth:** replace the key field with "Sign in with LinkedIn" (Custom Tab + PKCE, redirect to app link), store access token + expiry, show "Signed in as …" and Sign out. Requires a LinkedIn developer app with `w_member_social` (and `openid profile` for Q-21). **If Q-03 = pasted token:** relabel fields as "Access token", show token-expiry hint. | Q-03, FND-4 | blocked |
| AG-5-I | See AGC-1…4 in [06](06-content-generation.md). | | |
| AG-6-I | Add a "Model" field (or dropdown filled from `GET /models`, which the status check already calls) to S4–S6. | Q-04, FND-3 | blocked |
| AG-NFR-I | Keep one generic agent-settings screen driven by `AiAgent` so a fourth agent is one enum entry + one client. | — | done |

## Validation

| ID | Check | Status |
|----|-------|--------|
| LI-V1 | Enter values, SAVE, force-stop, reopen: values pre-filled, key masked. | todo |
| LI-V2 | Blank key → SAVE shows an inline error and saves nothing. Blank API → SAVE reverts to the `app.yml` URL. | todo |
| LI-V3 | After SAVE, API Status shows the LinkedIn result for the new key without restarting. | todo |
| AG-V1 | Same as LI-V1/LI-V2 for each of Claude, Gemini, Groq; saving one doesn't change the others. | todo |
| AG-V2 | Fresh install with keys added to a local `app.yml`: screens show defaults; saved values override them. | todo |

## Tests

| ID | Kind | Test | Status |
|----|------|------|--------|
| SET-T1 | unit | `AppConfig` parses `app.yml` (all four services, missing service, missing fields, empty file). Move parsing to a pure function taking an `InputStream` so it runs on the JVM. | done (`AppConfigTest`) |
| SET-T2 | unit | `ApiSettingsStore`: saved value overrides default; cleared value falls back; services are independent. | done (`ApiSettingsStoreTest`) |
| SET-T3 | unit | Settings ViewModel validation: valid save, blank key error, non-https URL error, trims whitespace. | done (`ApiSettingsViewModelTest`) |
| SET-T4 | UI | Settings screen: fields pre-filled from fake store, SAVE with blank key shows error text. | done (`ApiSettingsFieldsTest`) |
| SET-T5 | UI | AI Agent list opens the right settings screen for each agent (chip text = "<Agent> AI Agent"). | done (`AiAgentListScreenTest`) |
