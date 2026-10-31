package com.smassistant.app.generate

/** How many few-shot examples go into one image prompt. */
const val IMAGE_EXAMPLES_PER_PROMPT = 3

/**
 * One few-shot example from `assets/prompts/image/image_examples.md` (or `video/video_examples.md`): a topic, the image prompt that suits
 * it and the id of the image template it is an example of.
 */
data class ImageExample(
    val title: String,
    val keywords: List<String>,
    val topic: String,
    val imagePrompt: String,
    val template: String? = null,
)

/**
 * Reads `image_examples.md`: blocks separated by a `---` line, each with `Title:`, `Keywords:`,
 * `Template:`, `Topic:` and `Image prompt:` lines (`Slides:` in `video_examples.md`). The intro before the first `---` and incomplete
 * blocks are skipped; `Template:` may be left out.
 */
fun parseImageExamples(text: String): List<ImageExample> =
    text.split(Regex("""(?m)^---\s*$""")).drop(1).mapNotNull { block ->
        val fields = block.lines().mapNotNull { line ->
            val colon = line.indexOf(':')
            if (colon <= 0) null else line.substring(0, colon).trim().lowercase() to line.substring(colon + 1).trim()
        }.toMap()
        val title = fields["title"].orEmpty()
        val topic = fields["topic"].orEmpty()
        val imagePrompt = (fields["image prompt"] ?: fields["slides"]).orEmpty()
        if (title.isEmpty() || topic.isEmpty() || imagePrompt.isEmpty()) return@mapNotNull null
        val keywords = fields["keywords"].orEmpty().split(',').map { it.trim().lowercase() }.filter { it.isNotEmpty() }
        ImageExample(title, keywords, topic, imagePrompt, fields["template"]?.lowercase()?.takeIf { it.isNotEmpty() })
    }

/**
 * The [limit] examples to show for [prompt]: first the examples of the chosen [template], then the
 * others whose keywords match, each group best keyword match first and in file order on ties. With
 * neither it falls back to the first [limit], which the asset keeps as three different layouts.
 */
fun pickImageExamples(
    examples: List<ImageExample>,
    prompt: String,
    limit: Int = IMAGE_EXAMPLES_PER_PROMPT,
    template: String? = null,
): List<ImageExample> {
    val text = " " + prompt.lowercase().replace(Regex("[^a-z0-9+#]+"), " ") + " "
    val scored = examples.map { example ->
        example to example.keywords.count { keyword ->
            val words = keyword.replace(Regex("[^a-z0-9+#]+"), " ").trim()
            words.isNotEmpty() && text.contains(" $words ")
        }
    }
    val (ofTemplate, others) = scored.partition { template != null && it.first.template == template }
    val picked = ofTemplate.sortedByDescending { it.second } + others.filter { it.second > 0 }.sortedByDescending { it.second }
    if (picked.isEmpty()) return examples.take(limit)
    return picked.take(limit).map { it.first }
}

/** The examples as they read in the prompt: numbered, each with its topic and image description. */
fun formatImageExamples(examples: List<ImageExample>): String =
    examples.mapIndexed { index, example ->
        "Example ${index + 1}: ${example.title}\nTopic: ${example.topic}\nImage: ${example.imagePrompt}"
    }.joinToString("\n\n")
