# Image prompt templates

Krishna's LinkedIn technical image templates (docs/requirements/15-image-prompt-templates.md),
adapted for the app (docs/design/image-templates.md):

- Sizes follow the app's rule: portrait 2:3, square 1:1 or landscape 3:2, never the source's
  phone-screen sizes.
- The bracketed inputs are fixed slots the app fills: `{{content}}` (the technical content),
  `{{audience}}`, `{{style}}` (style guide or brand preferences), `{{brand}}` (brand name) and
  `{{format}}` (the image format). An optional slot left empty reads "not specified"; one a
  template doesn't have is added at the end when it is set.
- Section 11 (the adapter) isn't here: the app already writes the content first.

Format: templates are separated by a line holding only `---`. Each has an `Id`, a `Name`, a
`Use when` line and, after a `Template:` line, the template itself.

---
Id: universal
Name: Universal Master
Use when: Unsure which visual form fits; the default for most technical content.
Template:
ROLE
Act as a professional technical visual designer who can translate complex technical material
into clear, accurate, engaging visuals for LinkedIn.

INPUTS
Technical content or technical-content prompt:
{{content}}

Target audience:
{{audience}}

Brand/style preferences:
{{style}}

Brand name:
{{brand}}

Image format:
{{format}}

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

---
Id: concept
Name: Concept Explainer
Use when: The input explains one technical concept, principle, feature, or mechanism.
Template:
Create a LinkedIn-ready technical concept visual using the content supplied below.

TECHNICAL CONTENT:
{{content}}

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
Aspect ratio: {{format}}.
Brand preferences: {{style}}.

---
Id: architecture
Name: Architecture Diagram
Use when: The supplied content describes components, services, modules, systems, data flows, or dependencies.
Template:
Transform the supplied technical content into a clear, technically faithful architecture or system diagram.

TECHNICAL CONTENT:
{{content}}

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

Use a polished LinkedIn style. Image format: {{format}}. Prioritize architectural correctness over decoration.

---
Id: comparison
Name: Comparison
Use when: The supplied content compares technologies, techniques, approaches, patterns, versions, or trade-offs.
Template:
Create a balanced LinkedIn comparison infographic from the supplied technical content.

TECHNICAL CONTENT:
{{content}}

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
do not overload the visual with paragraphs. Aspect ratio: {{format}}.
Brand/style preferences: {{style}}.

---
Id: workflow
Name: Workflow
Use when: The content describes steps, request flow, execution sequence, a lifecycle, or a procedure.
Template:
Turn the supplied technical content into a clear workflow or lifecycle infographic.

TECHNICAL CONTENT:
{{content}}

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
The image should be understandable at mobile size. Aspect ratio: {{format}}.

---
Id: code
Name: Code Visual
Use when: The content includes code, an algorithm, a query, a configuration, or a programming example.
Template:
Create a LinkedIn educational visual based on the supplied programming or technical example.

TECHNICAL CONTENT:
{{content}}

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
Aspect ratio: {{format}}. Brand/style: {{style}}.

---
Id: checklist
Name: Checklist
Use when: The content contains recommendations, pitfalls, design principles, review criteria, or a checklist.
Template:
Convert the supplied technical content into a professional checklist or best-practices infographic.

TECHNICAL CONTENT:
{{content}}

Extract recommendations and cautions supported by the source. Group related items and make each easy
to scan.

- Preserve the intended meaning and important conditions.
- Distinguish required practices from recommendations or context-dependent choices.
- Show an anti-pattern and its alternative only if the source describes both.
- Do not invent security requirements, performance claims, or universal rules.
- Avoid excessive text; use short labels and optional concise explanations.
- Do not imply that a checklist guarantees correctness, security, or reliability.

Use a consistent visual system, clear grouping, high contrast, and enough whitespace for mobile viewing.
Choose icons only when they reinforce meaning. Aspect ratio: {{format}}.

---
Id: data
Name: Data and Metrics
Use when: The supplied content contains measurements, benchmarks, trends, counts, or quantitative comparisons.
Template:
Create a data-driven technical infographic using only the quantitative information supplied below.

TECHNICAL CONTENT AND DATA:
{{content}}

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
Aspect ratio: {{format}}.

---
Id: learning
Name: Learning Visual
Use when: The supplied content is an interview topic, learning summary, technical Q&A, or engineering lesson.
Template:
Create a professional LinkedIn learning infographic based on the supplied technical content.

TECHNICAL CONTENT:
{{content}}

Identify the most valuable lesson for the stated audience. Organize it into a suitable visual hierarchy,
such as concept → explanation → example → takeaway, but adapt the structure to the actual input.

- Keep the technical explanation faithful to the source.
- Include an example only if supplied or confidently verified.
- Avoid turning nuanced engineering decisions into simplistic absolute rules.
- Use concise wording suitable for a visual, not a full article.
- Do not add interview questions, answers, certifications, or career claims that were not requested.
- Do not invent facts or imply that one infographic covers the entire subject.

Use an engaging but professional style suitable for software engineers and technical leaders.
Make the central lesson visible at thumbnail size. Aspect ratio: {{format}}.

---
Id: series
Name: Series Style
Use when: You want multiple images to look like one coherent LinkedIn series while technical content changes.
Template:
Generate one image for a recurring LinkedIn technical-content series.

TECHNICAL CONTENT FOR THIS IMAGE:
{{content}}

SERIES STYLE GUIDE:
{{style}}

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
Image format: {{format}}.
