> Krishna's template file, added 2026-10-30 unchanged. The app uses an adapted copy (`assets/prompts/image_templates.md`, sizes changed to 2:3 / 1:1 / 3:2); see [the plan](../design/image-templates.md).

# Generic AI Image-Generation Prompt Templates for LinkedIn Technical Content

## Purpose

These templates are reusable across **any technical topic**. Provide the
template and either: 1. your finished technical content, or 2. your
prompt for generating technical content.

The AI should use your supplied material as the source and turn it into
a suitable LinkedIn visual. No topic, facts, labels, code, architecture,
or metrics are fixed in these templates.

> **Recommended workflow:** Generate and validate technical content
> first, then provide it with one image template. Review the image for
> technical accuracy before publishing.

------------------------------------------------------------------------

# 1. Universal Master Template --- Technical LinkedIn Image

Use this as the default for most technical content.

``` text
ROLE
Act as a professional technical visual designer who can translate complex technical material
into clear, accurate, engaging visuals for LinkedIn.

INPUTS
Technical content or technical-content prompt:
[PASTE MY TECHNICAL CONTENT OR CONTENT-GENERATION PROMPT HERE]

Target audience:
[OPTIONAL: students, developers, senior engineers, architects, technical leaders]

Brand/style preferences:
[OPTIONAL: colors, brand name, logo instructions, preferred style]

Image format:
[OPTIONAL: portrait 4:5, square 1:1, or landscape 16:9; default to portrait 4:5]

TASK
Analyze the supplied technical material and identify its most important message.
Choose the visual structure that best explains it: for example, a concept illustration, process flow,
comparison, architecture diagram, timeline, checklist, decision tree, or another suitable format.
Do not force the content into an unsuitable layout.

CONTENT RULES
- Use only information supported by the supplied content or well-established technical facts.
- Preserve important technical distinctions, limitations, and dependencies.
- Do not invent metrics, benchmarks, API names, code, components, guarantees, or claims.
- Do not silently change the meaning of the supplied content.
- Prioritize the central message; do not cram an entire article into one image.
- Use concise labels and short phrases rather than long paragraphs.
- If the supplied content is ambiguous or inconsistent, flag the issue or omit the uncertain detail.

DESIGN RULES
- Use a clear hierarchy: headline, main visual, supporting labels, and optional takeaway.
- Use a polished, modern design appropriate for a technical LinkedIn audience.
- Keep spacing, alignment, typography, icon style, and colors consistent.
- Use high contrast and ensure readability on a mobile screen.
- Keep essential elements within safe margins.
- Avoid clutter, decorative filler, fake logos, watermarks, random text, and tiny paragraphs.
- Follow supplied brand requirements; if none are supplied, choose a restrained professional palette.

TEXT AND RENDERING
Render only text needed to explain the concept. Preserve supplied technical terms exactly.
Do not create fake code or pseudo-labels to fill space. If accurate text rendering is unreliable,
leave clean spaces for the title and labels to be added later in a design tool.

OUTPUT
Generate one polished image suitable for a LinkedIn technical post. Prefer clarity and technical fidelity
over decoration. Do not add facts absent from the supplied content.
```

------------------------------------------------------------------------

# 2. Generic Template --- Technical Concept Explainer

**Use when:** The input explains one technical concept, principle,
feature, or mechanism.

``` text
Create a LinkedIn-ready technical concept visual using the content supplied below.

TECHNICAL CONTENT:
[PASTE TECHNICAL CONTENT OR TECHNICAL-CONTENT PROMPT]

Identify the central concept and the one takeaway the audience should remember.
Choose the clearest visual metaphor or explanatory structure. Show only the components and relationships
needed to explain it.

Include, where appropriate:
- a concise title based on the supplied content;
- the main concept or mechanism;
- a few supporting labels;
- an example or practical implication only if supported by the input;
- a short takeaway only if it adds value.

Do not force every element into the image. Adapt the layout to the content.
Keep terminology accurate and text concise. Do not invent examples, facts, code, or performance figures.
Use a clean, professional style with strong contrast and mobile readability.
Aspect ratio: [OPTIONAL; default 4:5].
Brand preferences: [OPTIONAL].
```

------------------------------------------------------------------------

# 3. Generic Template --- Architecture or System Diagram

**Use when:** The supplied content describes components, services,
modules, systems, data flows, or dependencies.

``` text
Transform the supplied technical content into a clear, technically faithful architecture or system diagram.

TECHNICAL CONTENT:
[PASTE TECHNICAL CONTENT OR TECHNICAL-CONTENT PROMPT]

Extract the components, actors, data stores, boundaries, and relationships that are provided or
necessary to represent the described design. Let the source determine which elements belong in the
diagram; do not insert a standard architecture merely because it is familiar.

- Give each component a concise, accurate label.
- Use arrows only where direction or relationship is supported by the input.
- Distinguish different flow types only when the input provides enough information.
- Preserve trust boundaries, data ownership, failure paths, or asynchronous relationships if relevant.
- Group related components and avoid unnecessary crossing lines.
- Do not invent technologies, dependencies, endpoints, data stores, capacity figures, or guarantees.
- If a relationship is unclear, omit it or flag the ambiguity rather than guessing.

Use a polished LinkedIn style. Choose portrait 4:5 for a compact diagram or landscape 16:9 when the
system needs more width. Prioritize architectural correctness over decoration.
```

------------------------------------------------------------------------

# 4. Generic Template --- Comparison or Decision Guide

**Use when:** The supplied content compares technologies, techniques,
approaches, patterns, versions, or trade-offs.

``` text
Create a balanced LinkedIn comparison infographic from the supplied technical content.

TECHNICAL CONTENT:
[PASTE TECHNICAL CONTENT OR TECHNICAL-CONTENT PROMPT]

Identify the items actually compared in the source. Choose a layout that makes similarities, differences,
trade-offs, and decision criteria easy to scan.

- Use equivalent criteria where a fair comparison is possible.
- Preserve qualifications and context from the source.
- Explain trade-offs without declaring a universal winner unless evidence supports it.
- Use concise phrases, clear visual grouping, and consistent labels.
- Include a “when to choose” takeaway only when it follows from the source.
- Do not invent scores, benchmarks, costs, rankings, or features.
- If the source lacks enough evidence for a comparison, make that limitation clear.

Create a polished, mobile-readable LinkedIn image. Use a simple grid or other suitable structure;
do not overload the visual with paragraphs. Aspect ratio: [OPTIONAL].
Brand/style preferences: [OPTIONAL].
```

------------------------------------------------------------------------

# 5. Generic Template --- Process, Workflow, or Lifecycle

**Use when:** The content describes steps, request flow, execution
sequence, a lifecycle, or a procedure.

``` text
Turn the supplied technical content into a clear workflow or lifecycle infographic.

TECHNICAL CONTENT:
[PASTE TECHNICAL CONTENT OR TECHNICAL-CONTENT PROMPT]

Determine the actual steps and order from the source. Represent the sequence using numbered stages,
arrows, branches, or swimlanes as appropriate.

- Preserve the sequence and dependencies described in the content.
- Show decision branches, error paths, or alternate outcomes only if supported by the source.
- Keep each step label short and meaningful.
- Distinguish mandatory steps from optional ones when the source does so.
- Do not add steps just to make the diagram look complete.
- Do not invent commands, APIs, events, or system behavior.
- If the sequence is unclear or contradictory, flag it rather than silently correcting it.

Use clear directional cues, consistent shapes, generous spacing, and high-contrast labels.
The image should be understandable at mobile size. Choose the aspect ratio that best fits the flow.
```

------------------------------------------------------------------------

# 6. Generic Template --- Code or Technical Example

**Use when:** The content includes code, an algorithm, a query, a
configuration, or a programming example.

``` text
Create a LinkedIn educational visual based on the supplied programming or technical example.

TECHNICAL CONTENT:
[PASTE THE CONTENT, CODE, OR TECHNICAL-CONTENT PROMPT]

Identify what the example teaches. Build the visual around the key idea, execution flow, or important
lines rather than trying to fit a long program into the image.

- Do not rewrite or fabricate code while presenting it as the supplied code.
- Preserve identifiers, operators, punctuation, and technical terms if code is displayed.
- If code is too long or exact rendering is unreliable, show a conceptual illustration and leave a clean
  code panel for later typesetting.
- Do not invent output, complexity analysis, or runtime behavior.
- If the example appears incorrect or incomplete, flag that issue rather than endorsing it.
- Separate explanation from literal code so the viewer can distinguish them.

Use readable code typography, strong contrast, minimal annotations, and a clean educational layout.
Prefer a visual explanation plus a short code excerpt over a wall of text.
Aspect ratio: [OPTIONAL]. Brand/style: [OPTIONAL].
```

------------------------------------------------------------------------

# 7. Generic Template --- Best Practices, Checklist, or Anti-Patterns

**Use when:** The content contains recommendations, pitfalls, design
principles, review criteria, or a checklist.

``` text
Convert the supplied technical content into a professional checklist or best-practices infographic.

TECHNICAL CONTENT:
[PASTE TECHNICAL CONTENT OR TECHNICAL-CONTENT PROMPT]

Extract recommendations and cautions supported by the source. Group related items and make each easy
to scan.

- Preserve the intended meaning and important conditions.
- Distinguish required practices from recommendations or context-dependent choices.
- Show an anti-pattern and its alternative only if the source describes both.
- Do not invent security requirements, performance claims, or universal rules.
- Avoid excessive text; use short labels and optional concise explanations.
- Do not imply that a checklist guarantees correctness, security, or reliability.

Use a consistent visual system, clear grouping, high contrast, and enough whitespace for mobile viewing.
Choose icons only when they reinforce meaning. Aspect ratio: [OPTIONAL].
```

------------------------------------------------------------------------

# 8. Generic Template --- Technical Data, Metrics, or Performance Results

**Use when:** The supplied content contains measurements, benchmarks,
trends, counts, or quantitative comparisons.

``` text
Create a data-driven technical infographic using only the quantitative information supplied below.

TECHNICAL CONTENT AND DATA:
[PASTE SOURCE CONTENT, DATA, AND CONTEXT]

Identify the main supported finding and select a chart or visual representation that fits the data.
Preserve units, time ranges, definitions, baselines, and comparison groups.

- Use only supplied or explicitly verified values.
- Do not invent missing values, sample sizes, units, baselines, or percentage changes.
- Do not imply causation from correlation.
- Do not distort axes, scale, proportions, or comparisons.
- Clearly label estimates or illustrative values if the source identifies them as such.
- If the input is insufficient for a meaningful chart, use a qualitative summary instead.

Use readable chart labels, restrained colors, clear legends when needed, and a concise takeaway supported
by the data. For exact chart rendering, prefer a charting or design tool over image-model-generated numbers.
Aspect ratio: [OPTIONAL].
```

------------------------------------------------------------------------

# 9. Generic Template --- Technical Career or Interview Learning

**Use when:** The supplied content is an interview topic, learning
summary, technical Q&A, or engineering lesson.

``` text
Create a professional LinkedIn learning infographic based on the supplied technical content.

TECHNICAL CONTENT:
[PASTE TECHNICAL CONTENT OR TECHNICAL-CONTENT PROMPT]

Identify the most valuable lesson for the stated audience. Organize it into a suitable visual hierarchy,
such as concept → explanation → example → takeaway, but adapt the structure to the actual input.

- Keep the technical explanation faithful to the source.
- Include an example only if supplied or confidently verified.
- Avoid turning nuanced engineering decisions into simplistic absolute rules.
- Use concise wording suitable for a visual, not a full article.
- Do not add interview questions, answers, certifications, or career claims that were not requested.
- Do not invent facts or imply that one infographic covers the entire subject.

Use an engaging but professional style suitable for software engineers and technical leaders.
Make the central lesson visible at thumbnail size. Aspect ratio: [OPTIONAL].
```

------------------------------------------------------------------------

# 10. Generic Template --- Visual Style Consistency Across a Series

**Use when:** You want multiple images to look like one coherent
LinkedIn series while technical content changes.

``` text
Generate one image for a recurring LinkedIn technical-content series.

TECHNICAL CONTENT FOR THIS IMAGE:
[PASTE THE CURRENT TECHNICAL CONTENT OR TECHNICAL-CONTENT PROMPT]

SERIES STYLE GUIDE:
[PASTE THE STYLE GUIDE OR DESCRIBE THE DESIRED VISUAL IDENTITY]

Apply the supplied style guide consistently, but adapt the composition to the current content.
The subject matter, labels, examples, and diagram structure must come from the current technical input,
not from a fixed sample or previous topic.

Preserve consistency in:
- color palette;
- typography style and hierarchy;
- spacing and margins;
- icon and line style;
- title placement, if appropriate;
- branding placement, if supplied.

Allow flexibility in the central visual so each image suits its own content. Do not reuse irrelevant
components from previous images. Do not add facts, labels, examples, or metrics from earlier topics.
Use the style guide only for visual consistency.

If no series style guide is supplied, create a clean professional style and briefly describe it so it
can be reused for future images. Keep the result mobile-readable and suitable for LinkedIn.
```

------------------------------------------------------------------------

# 11. Optional Adapter --- When You Have a Content Prompt, Not Finished Content

Use this if you want the model to generate the technical content first
and then create an image.

``` text
You will receive a prompt describing technical content to create.

TECHNICAL-CONTENT PROMPT:
[PASTE MY ORIGINAL TECHNICAL-CONTENT PROMPT]

Perform these stages in order:

1. Interpret the requested topic, audience, scope, and output requirements.
2. Generate the requested technical content, checking factual and technical consistency to the extent possible.
3. Extract the central message and the few supporting points that best fit one image.
4. Choose a visual format that suits the generated content.
5. Generate one LinkedIn-ready image using the extracted content.

Do not treat the technical-content prompt itself as the text to print on the image. Use it as instructions
for generating technical content. Do not cram the entire output into the image. Preserve key facts,
terminology, and caveats. Do not invent technical claims or measurements.

If the content is too broad for one image, select one coherent subtopic and make the scope clear.
If essential details are ambiguous, ask for clarification or omit the uncertain claim.
Keep labels concise and ensure the final visual is readable on mobile.
```

------------------------------------------------------------------------

# 12. Choosing the Right Template

  Input content type                      Recommended template
  --------------------------------------- ----------------------------
  One concept or principle                2 --- Concept Explainer
  Components, services, dependencies      3 --- Architecture Diagram
  Two or more approaches to compare       4 --- Comparison
  Steps, lifecycle, request flow          5 --- Workflow
  Code, algorithm, query, configuration   6 --- Code Visual
  Recommendations or pitfalls             7 --- Checklist
  Measured results or quantitative data   8 --- Data and Metrics
  Interview or learning material          9 --- Learning Visual
  Consistent branding across a series     10 --- Series Style
  Unsure which visual form fits           1 --- Universal Master

------------------------------------------------------------------------

# 13. Recommended Reusable Workflow

## If your technical content is already written

1.  Prepare the technical content.
2.  Choose one of Templates 1--10.
3.  Paste the full content into the template's input section.
4.  Add optional audience, aspect ratio, and style preferences.
5.  Generate the image.
6.  Verify technical labels, relationships, code, and claims.
7.  Correct text using a deterministic design tool when necessary.
8.  Write the LinkedIn caption separately and publish after review.

## If you have only a technical-content prompt

1.  Use the adapter in Section 11.
2.  Paste your original content-generation prompt.
3.  Let the model generate content, select a visual structure, and
    create the image.
4.  Review the generated content and image before publishing.

## If you are automating image generation

Keep these as separate fields in your application: - `contentPrompt`:
instructions that produce the technical content. - `technicalContent`:
the generated or approved content. - `imageTemplate`: one reusable
template from this file. - `audience`: optional target audience. -
`styleGuide`: reusable visual preferences. - `aspectRatio`: desired
image shape. - `brandName`: optional brand label. - `approvalStatus`:
pending, approved, or rejected.

This separation allows the topic and technical content to change without
rewriting the image template.

------------------------------------------------------------------------

# Final Recommendations

-   Start with the **Universal Master Template** for general use.
-   Choose a specialized template when the content calls for a diagram,
    workflow, comparison, code visual, checklist, or chart.
-   Supply technical content separately; do not hard-code a topic into
    the generic template.
-   Keep text in the image concise. Put detailed explanations in the
    LinkedIn caption or accompanying post.
-   For exact labels, code, charts, and architecture diagrams, use AI
    for the visual draft and a deterministic renderer for final text and
    geometry.
-   Verify technical accuracy before publishing. A visually polished
    image can still contain errors.
