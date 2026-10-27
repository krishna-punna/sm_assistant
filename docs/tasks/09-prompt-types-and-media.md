# 09. Prompt types and the image/video content workflow

Requirements: [14](../requirements/14-prompt-types-and-media-workflow.md).

## Implementation

| ID | Task | Status |
|----|------|--------|
| PT-I1 | `PromptType` enum, `AgentPreferences.promptType`, `PromptTypeDropdown` on Prepare Prompt (PT-1). | done |
| PT-I2 | `prepare_content_prompt.md` (no-limit content prompt, `{{medium}}` image or video) and `PreparePromptsUseCase` giving a `PromptSet` of post and/or content prompt, both in parallel (PT-2 to PT-4). Saved with `PromptSetSaver`; old results restore as Text. | done |
| PT-I3 | Labelled prompt cards for non-Text types (PT-3, PT-4). | done |
| MW-I1 | Image and video pipelines write no-limit technical content first and plan the infographic or deck from it (MW-1 to MW-3). | todo |

## Tests

| ID | Kind | Test | Status |
|----|------|------|--------|
| PT-T1 | unit | Each type asks for the right prompts with the right medium; one failure fails the submit; no agent gives NoAgent (`PreparePromptTest`). | done |
| PT-T2 | unit | `PromptSetSaver` round trips and restores old results as Text (`ResultSaverTest`). | done |
| PT-T3 | UI | Prompt Type picker lists every type (`PreparePromptScreenTest`). | done |
