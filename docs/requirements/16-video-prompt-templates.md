> Krishna's video template file, added 2026-10-30 unchanged. The app uses an adapted copy (`assets/prompts/video_templates.md`); see [the plan](../design/video-templates.md).

# Generic AI Video-Generation Prompt Templates for LinkedIn Technical Content

## Purpose

This library contains reusable templates for turning **any technical
content or technical-content prompt** into a short, professional
LinkedIn video.

The templates are intentionally generic. They do not hard-code a
programming language, technology, architecture, example, metric, or
claim. You provide the technical content separately; the AI should adapt
the video structure to that content.

## How to use

1.  Choose the Universal Master Template or a specialized template
    below.
2.  Paste your technical content or technical-content prompt into the
    marked input field.
3.  Optionally specify the target audience, video duration, aspect
    ratio, brand style, voice-over, and subtitles.
4.  Submit the prompt to a video-generation model or a workflow that
    uses a language model to create a storyboard and a video model to
    render scenes.
5.  Review technical correctness, on-screen text, narration, timing, and
    licensing before publishing.

> **Important:** Video models may produce inaccurate labels, UI text,
> diagrams, or technical behavior. For technical explainers, generate a
> storyboard first, validate it, then render scenes. Add exact code,
> labels, captions, and diagrams using a video editor or deterministic
> renderer when accuracy matters.

------------------------------------------------------------------------

# 1. Universal Master Template --- Technical LinkedIn Video

Use this as the default template for most technical topics.

``` text
ROLE
Act as a technical educator, instructional video director, and professional LinkedIn video producer.
Translate technical material into a concise, visually clear, accurate video.

INPUTS
Technical content or technical-content prompt:
[PASTE MY TECHNICAL CONTENT OR CONTENT-GENERATION PROMPT HERE]

Target audience:
[OPTIONAL: students, developers, senior engineers, architects, technical leaders]

Target duration:
[OPTIONAL: e.g., 30, 45, or 60 seconds; default 45 seconds]

Video format:
[OPTIONAL: vertical 9:16, square 1:1, or landscape 16:9; default vertical 9:16]

Visual style / brand guide:
[OPTIONAL: colors, logo placement, typography style, animation style, brand name]

Narration:
[OPTIONAL: voice-over, on-screen text only, or both; default concise voice-over plus subtitles]

TASK
Analyze the supplied input and identify the central message and intended learning outcome.
Choose the most suitable video structure for that content. It may be an explanation, process walkthrough,
comparison, architecture animation, timeline, checklist, or another format. Do not force the content
into an unsuitable structure.

Before generating the final video, create a concise storyboard with:
- scene number and duration;
- purpose of the scene;
- visual action or composition;
- exact on-screen text;
- narration, if requested;
- transition to the next scene.

CONTENT ACCURACY
- Use only facts supported by the supplied content or verified technical knowledge.
- Preserve technical distinctions, assumptions, limitations, and caveats.
- Do not invent APIs, code, benchmarks, metrics, system components, guarantees, or outcomes.
- Do not silently change the meaning of the input.
- If the source is ambiguous or contradictory, flag the issue or omit the uncertain detail.
- Do not display the original content-generation instructions as if they were the content itself.

VIDEO DESIGN
- Start with a clear, relevant hook; do not use clickbait or unsupported claims.
- Focus on one main learning objective.
- Use a logical progression from context to explanation to takeaway.
- Use visual demonstrations, diagrams, icons, and motion only when they improve understanding.
- Keep scenes visually simple, with one main idea per scene.
- Maintain consistent typography, colors, spacing, icon style, and motion language.
- Use smooth, purposeful transitions and readable pacing.
- Keep essential text away from screen edges.
- Avoid clutter, fake logos, random text, distorted interfaces, and decorative motion that distracts.
- Ensure the video works without sound when subtitles are enabled.

TEXT AND AUDIO
- Preserve supplied technical terms exactly.
- Use concise on-screen text; place detailed explanations in narration or the accompanying post.
- Keep subtitles synchronized with narration and large enough for mobile viewing.
- Do not fabricate spoken quotations, product names, or code.
- If exact text rendering is unreliable, leave clean areas for captions to be added in post-production.
- Use only audio and visual assets that are original, licensed, or otherwise authorized.

OUTPUT
Produce a polished LinkedIn-ready video and, when supported, provide the storyboard and narration script
as separate outputs. Prefer technical fidelity, clarity, and accessibility over visual spectacle.
```

------------------------------------------------------------------------

# 2. Generic Template --- Technical Concept Explainer

**Use when:** The content explains a concept, principle, feature, or
mechanism.

``` text
Create a short educational video for LinkedIn from the supplied technical content.

TECHNICAL CONTENT:
[PASTE TECHNICAL CONTENT OR TECHNICAL-CONTENT PROMPT]

Identify the central concept and the one takeaway viewers should remember. Choose a visual analogy,
animation, or diagram only if it accurately represents the concept.

Suggested structure, adaptable to the input:
1. Hook: introduce the question, problem, or concept.
2. Explanation: show how the concept works.
3. Clarification: highlight a key distinction or limitation if relevant.
4. Takeaway: summarize the central message.

Do not force all four stages if the content is better explained another way.
Use one main idea per scene, concise labels, and clear visual progression.
Do not invent examples or technical behavior.
Create a scene-by-scene storyboard before rendering. Use [DURATION] seconds, [ASPECT RATIO],
and [STYLE GUIDE]. If values are omitted, choose appropriate professional defaults.
```

------------------------------------------------------------------------

# 3. Generic Template --- Architecture or System Walkthrough

**Use when:** The content describes services, components, data flow,
system boundaries, or dependencies.

``` text
Turn the supplied technical content into an animated architecture walkthrough.

TECHNICAL CONTENT:
[PASTE TECHNICAL CONTENT OR TECHNICAL-CONTENT PROMPT]

Extract the components and relationships actually described in the source. Build a storyboard that
introduces the system at a high level, then reveals the relevant components and flows in a readable order.

Rules:
- Do not add familiar components merely because they are common in similar systems.
- Label each component accurately.
- Use arrows only for relationships supported by the source.
- Distinguish request flow, data flow, event flow, and control flow when the input provides that detail.
- Show failure paths, trust boundaries, or asynchronous steps only where relevant and supported.
- Keep each scene focused; avoid displaying the entire complex architecture at once.
- Do not invent technology choices, APIs, scale figures, latency figures, or guarantees.

First output a storyboard listing scene duration, visual state, animation/action, exact labels, and narration.
After the storyboard is validated, render the video. Use [DURATION], [ASPECT RATIO], and [STYLE GUIDE].
For exact diagrams and labels, use a deterministic renderer or video editor during final composition.
```

------------------------------------------------------------------------

# 4. Generic Template --- Process, Workflow, or Lifecycle Animation

**Use when:** The content describes steps, an execution sequence,
request lifecycle, or procedure.

``` text
Create an animated workflow video from the supplied technical content.

TECHNICAL CONTENT:
[PASTE TECHNICAL CONTENT OR TECHNICAL-CONTENT PROMPT]

Identify the actual steps, order, dependencies, and decision branches from the input.
Animate the process progressively so viewers can follow one step at a time.

Rules:
- Preserve the source sequence.
- Show alternate paths, failures, retries, or decision branches only if supported by the content.
- Keep step labels short and accurate.
- Distinguish required steps from optional ones.
- Do not add steps just to make the animation appear complete.
- Do not invent commands, APIs, events, or behavior.
- Maintain visual continuity so objects do not change identity or meaning between scenes.

Create a storyboard with scene duration, action, labels, narration, and transition. Use clear motion,
purposeful transitions, and a readable pace. Render at [DURATION] and [ASPECT RATIO], following
[STYLE GUIDE]. Keep the video understandable on a mobile screen.
```

------------------------------------------------------------------------

# 5. Generic Template --- Comparison and Trade-Offs

**Use when:** The content compares approaches, technologies, patterns,
versions, or design choices.

``` text
Create a balanced technical comparison video based on the supplied content.

TECHNICAL CONTENT:
[PASTE TECHNICAL CONTENT OR TECHNICAL-CONTENT PROMPT]

Identify the items and criteria that are actually compared in the source. Present the comparison in
parallel scenes or a side-by-side visual when suitable.

Rules:
- Compare equivalent criteria where possible.
- Preserve context and caveats.
- Explain trade-offs without declaring a universal winner unless the source supports it.
- Do not invent scores, benchmark numbers, prices, rankings, or features.
- If evidence is insufficient, make the limitation clear.
- Keep labels and narration concise.

Storyboard the introduction, comparison, trade-offs, and conclusion as appropriate to the input.
Use consistent visual treatment for each option so the comparison is fair and easy to follow.
Use [DURATION], [ASPECT RATIO], and [STYLE GUIDE].
```

------------------------------------------------------------------------

# 6. Generic Template --- Code, Algorithm, or Debugging Walkthrough

**Use when:** The content includes code, an algorithm, a query, a
configuration, or debugging steps.

``` text
Create a technical tutorial video based on the supplied code or technical example.

TECHNICAL CONTENT / CODE:
[PASTE CODE, EXAMPLE, OR TECHNICAL-CONTENT PROMPT]

Identify the learning objective. Plan scenes that show the relevant code or pseudocode, its execution,
the important state changes, and the resulting behavior only when supported by the input.

Accuracy rules:
- Do not fabricate code and present it as the source code.
- Preserve identifiers, operators, punctuation, and terminology in any displayed code.
- Do not invent program output, runtime behavior, or complexity claims.
- If the example appears incorrect or incomplete, flag it before presenting it as correct.
- Avoid trying to render long code blocks inside generated footage.
- If exact code rendering is unreliable, leave a stable code-panel area and add the code in post-production.
- Distinguish actual code from explanatory pseudocode.

Use close-ups and highlights sparingly to direct attention. Keep each scene focused on one execution step
or concept. Provide a storyboard, narration, and exact text separately. Use [DURATION], [ASPECT RATIO],
and [STYLE GUIDE].
```

------------------------------------------------------------------------

# 7. Generic Template --- Best Practices, Checklist, or Anti-Patterns

**Use when:** The content contains recommendations, pitfalls,
principles, or review criteria.

``` text
Turn the supplied technical recommendations into a concise educational video.

TECHNICAL CONTENT:
[PASTE TECHNICAL CONTENT OR TECHNICAL-CONTENT PROMPT]

Group related points and choose a visual structure that helps viewers understand and remember them.
Use an example or contrast between an anti-pattern and a better approach only when the source supports it.

Rules:
- Preserve conditions and qualifications.
- Distinguish mandatory requirements from recommendations and context-dependent practices.
- Do not imply that following a checklist guarantees security, performance, or reliability.
- Do not invent risks, requirements, or outcomes.
- Use concise on-screen text, with optional narration to explain context.

Give each scene one main point. Use a consistent visual language for categories or priorities, but do not
rely on color alone to communicate meaning. Include a brief conclusion that reflects the source.
Use [DURATION], [ASPECT RATIO], and [STYLE GUIDE].
```

------------------------------------------------------------------------

# 8. Generic Template --- Data, Metrics, or Performance Story

**Use when:** The content contains supplied measurements, benchmarks,
trends, counts, or performance results.

``` text
Create a data-led technical video from the supplied content and data.

TECHNICAL CONTENT AND DATA:
[PASTE SOURCE CONTENT, DATA, UNITS, AND CONTEXT]

Identify the main finding supported by the data. Choose a suitable chart, timeline, animated comparison,
or other visualization. Build the animation to help viewers interpret the values accurately.

Accuracy rules:
- Use only supplied or explicitly verified values.
- Preserve units, definitions, time ranges, baselines, and comparison groups.
- Do not invent missing values, sample sizes, percentage changes, or benchmark results.
- Do not distort axes or use motion to exaggerate differences.
- Do not imply causation from correlation.
- Clearly label estimates or illustrative data if the source identifies them as such.
- If the input is insufficient for a valid quantitative visualization, use a qualitative explanation instead.

Provide the chart specification and exact labels separately from the visual-generation instructions when
possible. For precise data visualization, create the chart with a deterministic charting tool and animate
it in post-production. Use [DURATION], [ASPECT RATIO], and [STYLE GUIDE].
```

------------------------------------------------------------------------

# 9. Generic Template --- Technical Learning or Interview Lesson

**Use when:** The content is a learning summary, interview answer,
technical question, or engineering lesson.

``` text
Create a short educational LinkedIn video based on the supplied technical learning content.

TECHNICAL CONTENT:
[PASTE TECHNICAL CONTENT OR TECHNICAL-CONTENT PROMPT]

Identify the most useful lesson for the intended audience. Choose a structure that fits the source,
such as a question and explanation, concept and example, common misconception and clarification,
or problem and solution.

Rules:
- Keep the explanation faithful to the input.
- Use examples only if supplied or verified.
- Avoid reducing nuanced engineering decisions to absolute rules.
- Do not add questions, answers, certifications, or claims not requested by the source.
- Focus on one coherent lesson per video.
- Keep on-screen text short; use narration or the post caption for additional context.

Create a hook that accurately represents the content, a clear visual explanation, and a concise takeaway.
Provide the storyboard, narration script, and on-screen text separately. Use [DURATION], [ASPECT RATIO],
and [STYLE GUIDE].
```

------------------------------------------------------------------------

# 10. Generic Template --- Consistent Video Series

**Use when:** You want many videos to share a consistent brand and
visual identity while the technical topic changes.

``` text
Create one video in a recurring LinkedIn technical education series.

TECHNICAL CONTENT FOR THIS VIDEO:
[PASTE CURRENT TECHNICAL CONTENT OR TECHNICAL-CONTENT PROMPT]

SERIES STYLE GUIDE:
[PASTE VISUAL STYLE GUIDE, BRAND COLORS, TYPOGRAPHY, LOGO RULES, AND MOTION STYLE]

Apply the supplied style guide consistently, but adapt the scene structure to the current content.
The topic, examples, labels, components, and narration must come from the current input—not from earlier videos.

Maintain consistency in:
- palette and typography;
- title and subtitle treatment;
- margins and text placement;
- icon, line, and diagram style;
- transition and motion language;
- logo placement, if provided;
- narration and subtitle style, if specified.

Allow the central visual and pacing to change when the content requires it. Do not reuse irrelevant objects,
facts, labels, or examples from earlier videos. Do not fabricate details to fill a scene.

If no style guide is provided, propose a simple reusable style guide and use it for this video.
Return a storyboard and a brief style-guide description so the next video can match it.
Use [DURATION], [ASPECT RATIO], and the supplied audio preferences.
```

------------------------------------------------------------------------

# 11. Content-to-Video Adapter --- When You Have Only a Content Prompt

Use this when you want the AI to generate the technical content and then
turn it into a video.

``` text
You will receive a prompt describing technical content to create.

ORIGINAL TECHNICAL-CONTENT PROMPT:
[PASTE MY ORIGINAL TECHNICAL-CONTENT PROMPT HERE]

Complete these stages in order:

1. Interpret the topic, scope, target audience, and output requirements.
2. Generate the requested technical content and check it for consistency and accuracy to the extent possible.
3. Extract one coherent learning objective suitable for a short video.
4. Create a scene-by-scene storyboard with timing, visual action, exact on-screen text, narration,
   and transitions.
5. Check that the storyboard faithfully represents the technical content.
6. Generate the video from the approved storyboard, or provide scene prompts suitable for a video model
   if direct video generation is not supported.

Do not print the original prompt on screen. It is an instruction for generating content, not the content
itself. Do not try to fit the entire technical response into the video. Select a coherent subset and make
the scope clear. Do not invent facts, examples, code, metrics, or guarantees.

If important technical details are ambiguous, flag them rather than guessing. Keep the result professional,
accessible, mobile-readable, and suitable for LinkedIn. Use [DURATION], [ASPECT RATIO], and [STYLE GUIDE].
```

------------------------------------------------------------------------

# 12. Generic Template --- Scene-by-Scene Prompts for Video Models

Use this if the video model generates only short clips or struggles with
long videos. Generate scenes individually and assemble them afterward.

``` text
Create a production-ready scene plan for a technical LinkedIn video based on the supplied content.

TECHNICAL CONTENT:
[PASTE TECHNICAL CONTENT OR TECHNICAL-CONTENT PROMPT]

Total target duration: [DURATION]
Visual style: [STYLE GUIDE]
Aspect ratio: [ASPECT RATIO]

Divide the video into a small number of coherent scenes. For each scene, provide:
1. Scene number and target duration.
2. Its purpose in the explanation.
3. A standalone video-generation prompt describing only the visual action for that scene.
4. Exact on-screen labels and captions, separately from the visual prompt.
5. Narration text, if requested.
6. Starting and ending visual states.
7. Transition into the next scene.
8. Continuity notes so characters, icons, colors, diagrams, and objects remain consistent.

Continuity and accuracy rules:
- Each scene must represent the same underlying technical concept and style.
- Do not change component labels or system relationships between scenes.
- Avoid unnecessary camera movement or visual effects.
- Keep technical labels out of the generative footage if the model cannot render them reliably;
  reserve clean areas for captions and diagrams to be added later.
- Do not introduce details absent from the supplied content.
- Make the scenes join naturally when edited together.

Return the scene plan first. Do not claim that a finished video has been rendered unless a video tool actually
produced it.
```

------------------------------------------------------------------------

# 13. Optional Output Specification for Automated Applications

For an automated workflow, ask the language model to return a structured
storyboard before calling a video-generation model.

``` text
Return a storyboard with these fields for each scene:
- scene_number
- duration_seconds
- scene_purpose
- visual_description
- motion_or_action
- exact_on_screen_text
- narration
- transition
- continuity_notes
- technical_claims_to_verify

Also return:
- video_title
- intended_audience
- central_takeaway
- total_duration_seconds
- aspect_ratio
- style_guide
- required_post_production_text
- technical_validation_checklist
```

This is an output specification for your application; it is not a
universal schema supported directly by every video model. Your
application may need to convert it to the selected provider's API
format.

------------------------------------------------------------------------

# 14. Recommended LinkedIn Video Workflow

1.  **Select a topic** using your content calendar or scheduler.
2.  **Generate the technical content** using your technical-content
    prompt.
3.  **Review the content** for correctness, clarity, and scope.
4.  **Choose a video template** that matches the content type.
5.  **Generate a storyboard** with scenes, timing, narration, and
    labels.
6.  **Validate the storyboard** before spending resources on video
    generation.
7.  **Generate clips or the full video** using the selected video model.
8.  **Add exact text and diagrams** in a video editor or deterministic
    rendering pipeline when needed.
9.  **Add narration, subtitles, and audio** with appropriate permissions
    and synchronization.
10. **Review the complete video** for technical accuracy, continuity,
    readability, and sound.
11. **Prepare the LinkedIn caption** separately from the video.
12. **Publish only after approval**; keep human review for technical
    claims and security-related content.

## Suggested fields to store in your application

Keep the content and rendering instructions separate:

-   `contentPrompt`: prompt used to generate the technical content.
-   `technicalContent`: generated or approved technical content.
-   `videoTemplate`: selected generic template.
-   `storyboard`: approved scene plan.
-   `audience`: optional target audience.
-   `durationSeconds`: target video duration.
-   `aspectRatio`: vertical, square, or landscape.
-   `styleGuide`: reusable visual and motion identity.
-   `narrationMode`: voice-over, on-screen text, or both.
-   `subtitleMode`: whether subtitles are required.
-   `brandName`: optional brand label.
-   `approvalStatus`: pending, approved, or rejected.

------------------------------------------------------------------------

# Final Recommendations

-   Start with **Template 1: Universal Master Template** for general
    use.
-   Use Templates 2--10 when the content needs a specific storytelling
    format.
-   Use Template 11 when you provide only a content-generation prompt.
-   Use Template 12 when the video model works best with short,
    separately generated clips.
-   Generate and validate the storyboard before rendering.
-   Keep technical content, storyboard, video prompts, exact labels, and
    final video as separate assets.
-   For precise diagrams, code, metrics, and subtitles, use a
    deterministic post-production step.
-   Check technical claims and accessibility before publishing.
