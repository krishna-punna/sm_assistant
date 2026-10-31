# Video prompt templates

Krishna's LinkedIn technical video templates (docs/requirements/16-video-prompt-templates.md),
adapted for the app (docs/design/video-templates.md):

- Videos use the standard video sizes only: vertical 9:16, square 1:1 or landscape 16:9
  (Krishna, 2026-10-30).
- The app draws every scene from the storyboard and makes the MP4 on the phone. There is no video
  model and no sound, so narration is on-screen text only and every label, code line and chart is
  drawn exactly by the app.
- The bracketed inputs are fixed slots the app fills: `{{content}}` (the technical content),
  `{{audience}}`, `{{duration}}` (the length picked on the screen), `{{format}}` (the video size),
  `{{style}}` (style guide or brand preferences) and `{{brand}}` (brand name). An optional slot left
  empty reads "not specified"; one a template doesn't have is added at the end when it is set.
- Sections 11 to 13 (the adapter, scene prompts for video models and the output specification)
  aren't here: the app writes the content first, and `video_deck.md` holds the storyboard shape.

Format: templates are separated by a line holding only `---`. Each has an `Id`, a `Name`, a
`Use when` line and, after a `Template:` line, the template itself. `choose_video_template.md`
picks one of them for the content.

---
Id: universal
Name: Universal Master
Use when: Unsure which video structure fits; the default for most technical content.
Template:
ROLE
Act as a technical educator, instructional video director, and professional LinkedIn video producer.
Translate technical material into a concise, visually clear, accurate video.

INPUTS
Technical content or technical-content prompt:
{{content}}

Target audience:
{{audience}}

Target duration:
{{duration}}

Video format:
{{format}}

Visual style / brand guide:
{{style}}

Brand name:
{{brand}}

Narration:
On-screen text only; the video plays without sound.

TASK
Analyze the supplied input and identify the central message and intended learning outcome.
Choose the most suitable video structure for that content. It may be an explanation, process walkthrough,
comparison, architecture animation, timeline, checklist, or another format. Do not force the content
into an unsuitable structure.

Plan a concise storyboard with:
- scene number and duration;
- purpose of the scene;
- visual composition;
- exact on-screen text.

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
- Use diagrams, icons, and step-by-step reveals only when they improve understanding.
- Keep scenes visually simple, with one main idea per scene.
- Maintain consistent typography, colors, spacing, icon style, and motion language.
- Use readable pacing.
- Keep essential text away from screen edges.
- Avoid clutter, fake logos, random text, distorted interfaces, and decorative filler that distracts.
- Make sure the video works without sound.

TEXT
- Preserve supplied technical terms exactly.
- Use concise on-screen text; place detailed explanations in the accompanying post.
- Keep text large enough for mobile viewing.
- Do not fabricate quotations, product names, or code.

OUTPUT
The app draws each scene from your storyboard, with exact labels, code and charts. Prefer technical
fidelity, clarity, and accessibility over visual spectacle.

---
Id: concept
Name: Concept Explainer
Use when: The content explains a concept, principle, feature, or mechanism.
Template:
Create a short educational video for LinkedIn from the supplied technical content.

TECHNICAL CONTENT:
{{content}}

Identify the central concept and the one takeaway viewers should remember. Choose a visual analogy,
illustration, or diagram only if it accurately represents the concept.

Suggested structure, adaptable to the input:
1. Hook: introduce the question, problem, or concept.
2. Explanation: show how the concept works.
3. Clarification: highlight a key distinction or limitation if relevant.
4. Takeaway: summarize the central message.

Do not force all four stages if the content is better explained another way.
Use one main idea per scene, concise labels, and clear visual progression.
Do not invent examples or technical behavior.
Plan a scene-by-scene storyboard. Use {{duration}}, {{format}}, and this style guide: {{style}}.
If values are not specified, choose appropriate professional defaults.

---
Id: architecture
Name: Architecture Walkthrough
Use when: The content describes services, components, data flow, system boundaries, or dependencies.
Template:
Turn the supplied technical content into an architecture walkthrough video.

TECHNICAL CONTENT:
{{content}}

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

Plan a storyboard listing scene duration, visual state, what is revealed, and exact labels.
The app draws the diagrams and labels exactly. Use {{duration}}, {{format}}, and this style guide: {{style}}.

---
Id: workflow
Name: Workflow or Lifecycle
Use when: The content describes steps, an execution sequence, request lifecycle, or procedure.
Template:
Create a workflow video from the supplied technical content.

TECHNICAL CONTENT:
{{content}}

Identify the actual steps, order, dependencies, and decision branches from the input.
Reveal the process progressively so viewers can follow one step at a time.

Rules:
- Preserve the source sequence.
- Show alternate paths, failures, retries, or decision branches only if supported by the content.
- Keep step labels short and accurate.
- Distinguish required steps from optional ones.
- Do not add steps just to make the video appear complete.
- Do not invent commands, APIs, events, or behavior.
- Maintain visual continuity so objects do not change identity or meaning between scenes.

Plan a storyboard with scene duration, what is revealed, and exact labels, at a readable pace.
Use {{duration}} and {{format}}, following this style guide: {{style}}. Keep the video understandable
on a mobile screen.

---
Id: comparison
Name: Comparison and Trade-Offs
Use when: The content compares approaches, technologies, patterns, versions, or design choices.
Template:
Create a balanced technical comparison video based on the supplied content.

TECHNICAL CONTENT:
{{content}}

Identify the items and criteria that are actually compared in the source. Present the comparison in
parallel scenes or a side-by-side visual when suitable.

Rules:
- Compare equivalent criteria where possible.
- Preserve context and caveats.
- Explain trade-offs without declaring a universal winner unless the source supports it.
- Do not invent scores, benchmark numbers, prices, rankings, or features.
- If evidence is insufficient, make the limitation clear.
- Keep labels concise.

Storyboard the introduction, comparison, trade-offs, and conclusion as appropriate to the input.
Use consistent visual treatment for each option so the comparison is fair and easy to follow.
Use {{duration}}, {{format}}, and this style guide: {{style}}.

---
Id: code
Name: Code or Algorithm Walkthrough
Use when: The content includes code, an algorithm, a query, a configuration, or debugging steps.
Template:
Create a technical tutorial video based on the supplied code or technical example.

TECHNICAL CONTENT / CODE:
{{content}}

Identify the learning objective. Plan scenes that show the relevant code or pseudocode, its execution,
the important state changes, and the resulting behavior only when supported by the input.

Accuracy rules:
- Do not fabricate code and present it as the source code.
- Preserve identifiers, operators, punctuation, and terminology in any displayed code.
- Do not invent program output, runtime behavior, or complexity claims.
- If the example appears incorrect or incomplete, flag it before presenting it as correct.
- Keep code short: show only the lines a scene explains.
- The app draws the code exactly from your storyboard; never put code into an illustration.
- Distinguish actual code from explanatory pseudocode.

Use highlights sparingly to direct attention. Keep each scene focused on one execution step or concept.
Give the exact text of each scene. Use {{duration}}, {{format}}, and this style guide: {{style}}.

---
Id: checklist
Name: Best Practices or Checklist
Use when: The content contains recommendations, pitfalls, anti-patterns, principles, or review criteria.
Template:
Turn the supplied technical recommendations into a concise educational video.

TECHNICAL CONTENT:
{{content}}

Group related points and choose a visual structure that helps viewers understand and remember them.
Use an example or contrast between an anti-pattern and a better approach only when the source supports it.

Rules:
- Preserve conditions and qualifications.
- Distinguish mandatory requirements from recommendations and context-dependent practices.
- Do not imply that following a checklist guarantees security, performance, or reliability.
- Do not invent risks, requirements, or outcomes.
- Use concise on-screen text.

Give each scene one main point. Use a consistent visual language for categories or priorities, but do not
rely on color alone to communicate meaning. Include a brief conclusion that reflects the source.
Use {{duration}}, {{format}}, and this style guide: {{style}}.

---
Id: data
Name: Data or Performance Story
Use when: The content contains supplied measurements, benchmarks, trends, counts, or performance results.
Template:
Create a data-led technical video from the supplied content and data.

TECHNICAL CONTENT AND DATA:
{{content}}

Identify the main finding supported by the data. Choose a suitable chart, table, side-by-side comparison,
or other visualization that helps viewers interpret the values accurately.

Accuracy rules:
- Use only supplied or explicitly verified values.
- Preserve units, definitions, time ranges, baselines, and comparison groups.
- Do not invent missing values, sample sizes, percentage changes, or benchmark results.
- Do not distort scales to exaggerate differences.
- Do not imply causation from correlation.
- Clearly label estimates or illustrative data if the source identifies them as such.
- If the input is insufficient for a valid quantitative visualization, use a qualitative explanation instead.

Give the exact values and labels in the storyboard; the app draws the charts exactly.
Use {{duration}}, {{format}}, and this style guide: {{style}}.

---
Id: learning
Name: Learning or Interview Lesson
Use when: The content is a learning summary, interview answer, technical question, or engineering lesson.
Template:
Create a short educational LinkedIn video based on the supplied technical learning content.

TECHNICAL CONTENT:
{{content}}

Identify the most useful lesson for the intended audience ({{audience}}). Choose a structure that fits
the source, such as a question and explanation, concept and example, common misconception and
clarification, or problem and solution.

Rules:
- Keep the explanation faithful to the input.
- Use examples only if supplied or verified.
- Avoid reducing nuanced engineering decisions to absolute rules.
- Do not add questions, answers, certifications, or claims not requested by the source.
- Focus on one coherent lesson per video.
- Keep on-screen text short; use the post caption for additional context.

Create a hook that accurately represents the content, a clear visual explanation, and a concise takeaway.
Use {{duration}}, {{format}}, and this style guide: {{style}}.

---
Id: series
Name: Consistent Video Series
Use when: Many videos should share one brand and visual identity while the technical topic changes.
Template:
Create one video in a recurring LinkedIn technical education series.

TECHNICAL CONTENT FOR THIS VIDEO:
{{content}}

SERIES STYLE GUIDE:
{{style}}

Apply the supplied style guide consistently, but adapt the scene structure to the current content.
The topic, examples, labels, and components must come from the current input, not from earlier videos.

Maintain consistency in:
- palette and typography;
- title and subtitle treatment;
- margins and text placement;
- icon, line, and diagram style;
- transition and reveal style;
- brand name placement, if provided.

Allow the central visual and pacing to change when the content requires it. Do not reuse irrelevant objects,
facts, labels, or examples from earlier videos. Do not fabricate details to fill a scene.

If no style guide is provided, choose a simple reusable style and keep it for this video.
Use {{duration}} and {{format}}.
