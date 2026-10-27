# 09. Prompt types and the image/video content workflow

Requirements: [14](../requirements/14-prompt-types-and-media-workflow.md).

## Implementation

| ID | Task | Status |
|----|------|--------|
| PT-I1 | `PromptType` enum, `AgentPreferences.promptType`, `PromptTypeDropdown` on Prepare Prompt (PT-1). | done |
| PT-I2 | `prepare_content_prompt.md` (no-limit content prompt, `{{medium}}` image or video) and `PreparePromptsUseCase` giving a `PromptSet` of post and/or content prompt, both in parallel (PT-2 to PT-4). Saved with `PromptSetSaver`; old results restore as Text. | done |
| PT-I3 | Labelled prompt cards for non-Text types (PT-3, PT-4). | done |
| MW-I2 | Separate models (deck slides 6 to 9, "Text Agent Model: ABC", "Image/Video AI Agent Model: XYZ"): the image and video screens show "Text agent" and "Text model" pickers for the content, next to the existing image or video agent and model pickers. | done |
| MW-I1 | `media_content.md` and `MediaContentWriter`: the image and video pipelines first have the text agent write no-limit technical content, and `InfographicWriter` and `SlideDeckWriter` plan the infographic or deck from it (MW-1, MW-2). Content & Image and Content & Video get it through the same pipelines while the post is unchanged (MW-3). The fallbacks without a text agent and Hugging Face AI clips still use the prompt directly. | done |

## Tests

| ID | Kind | Test | Status |
|----|------|------|--------|
| PT-T1 | unit | Each type asks for the right prompts with the right medium; one failure fails the submit; no agent gives NoAgent (`PreparePromptTest`). | done |
| PT-T2 | unit | `PromptSetSaver` round trips and restores old results as Text (`ResultSaverTest`). | done |
| PT-T3 | UI | Prompt Type picker lists every type (`PreparePromptScreenTest`). | done |
| MW-T1 | unit | Content is written first and passed to every infographic and the deck; a failed content step fails the submit (`GenerateImagesTest`, `GenerateVideoTest`, `InfographicWriterTest`). | done |

## Validation

| ID | Check | Status |
|----|-------|--------|
| PT-V1 | "Java 21 features" with each Prompt Type gives the labelled prompts of the deck's examples 1 to 5. | todo |
| MW-V1 | Generate Image and Generate Video on a real device: the infographics and slides carry the depth of the no-limit content (compare with the deck's sample images). | todo |
