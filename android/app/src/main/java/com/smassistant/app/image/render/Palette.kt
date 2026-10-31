package com.smassistant.app.image.render

/** The colour sets an infographic can use; the model picks one by [key]. */
enum class Palette(
    val key: String,
    val header: String,
    val headerText: String,
    val highlight: String,
    val background: String,
    val card: String,
    val cardBorder: String,
    val text: String,
    val mutedText: String,
    /** Card header colours, used in turn. */
    val accents: List<String>,
) {
    NAVY(
        "navy", header = "#0F1E3D", headerText = "#FFFFFF", highlight = "#FACC15", background = "#F3F6FB",
        card = "#FFFFFF", cardBorder = "#DCE3EE", text = "#1E293B", mutedText = "#475569",
        accents = listOf("#2563EB", "#16A34A", "#7C3AED", "#EA580C", "#DB2777", "#0D9488"),
    ),
    TEAL(
        "teal", header = "#0B3B3C", headerText = "#FFFFFF", highlight = "#5EEAD4", background = "#F0F7F7",
        card = "#FFFFFF", cardBorder = "#D3E5E5", text = "#1F2937", mutedText = "#4B5563",
        accents = listOf("#0D9488", "#0284C7", "#65A30D", "#D97706", "#9333EA", "#E11D48"),
    ),
    PURPLE(
        "purple", header = "#2E1065", headerText = "#FFFFFF", highlight = "#F0ABFC", background = "#F7F5FD",
        card = "#FFFFFF", cardBorder = "#E4DDF7", text = "#1E1B4B", mutedText = "#4C4870",
        accents = listOf("#7C3AED", "#2563EB", "#DB2777", "#0D9488", "#EA580C", "#4F46E5"),
    ),
    SUNSET(
        "sunset", header = "#431407", headerText = "#FFFFFF", highlight = "#FDBA74", background = "#FFF8F1",
        card = "#FFFFFF", cardBorder = "#F5DFCB", text = "#292524", mutedText = "#57534E",
        accents = listOf("#EA580C", "#DC2626", "#CA8A04", "#9333EA", "#0891B2", "#16A34A"),
    ),
    FOREST(
        "forest", header = "#14301F", headerText = "#FFFFFF", highlight = "#BEF264", background = "#F4F8F2",
        card = "#FFFFFF", cardBorder = "#D9E6D3", text = "#1C2A1F", mutedText = "#4A5A4D",
        accents = listOf("#15803D", "#0E7490", "#A16207", "#7C3AED", "#BE123C", "#4D7C0F"),
    ),
    DARK(
        "dark", header = "#020617", headerText = "#F8FAFC", highlight = "#38BDF8", background = "#0B1220",
        card = "#111B2E", cardBorder = "#22324D", text = "#E2E8F0", mutedText = "#94A3B8",
        accents = listOf("#3B82F6", "#22C55E", "#A855F7", "#F97316", "#EC4899", "#14B8A6"),
    ),
    ;

    fun accent(index: Int) = accents[index % accents.size]

    companion object {
        fun of(key: String?): Palette = entries.firstOrNull { it.key == key?.trim()?.lowercase() } ?: NAVY
    }
}

/** The palette in words, for illustration prompts: image models follow colour names better than hex codes. */
val Palette.colorWords: String
    get() = when (this) {
        Palette.NAVY -> "a navy blue, bright blue, green, purple and orange colour palette"
        Palette.TEAL -> "a teal, sky blue, lime green and amber colour palette"
        Palette.PURPLE -> "a violet, indigo, pink and teal colour palette"
        Palette.SUNSET -> "a warm orange, red, golden yellow and purple colour palette"
        Palette.FOREST -> "a forest green, sage, ochre and teal colour palette"
        Palette.DARK -> "a bright blue, cyan, green and purple palette that stands out on a dark navy background"
    }
