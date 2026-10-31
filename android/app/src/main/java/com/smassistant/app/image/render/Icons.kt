package com.smassistant.app.image.render

/**
 * Built-in line icons the model can name in a definition. Each is SVG drawn in a 24 x 24 box
 * with a 2-unit stroke and no fill; [InfographicRenderer] scales and colours it.
 */
val ICONS: Map<String, String> = mapOf(
    "database" to """<ellipse cx="12" cy="5" rx="8" ry="3"/><path d="M4 5v14c0 1.7 3.6 3 8 3s8-1.3 8-3V5"/><path d="M4 12c0 1.7 3.6 3 8 3s8-1.3 8-3"/>""",
    "server" to """<rect x="3" y="3" width="18" height="7" rx="2"/><rect x="3" y="14" width="18" height="7" rx="2"/><path d="M7 6.5h.01M7 17.5h.01"/>""",
    "cloud" to """<path d="M7 18h10a4 4 0 0 0 .6-7.96A6 6 0 0 0 6.1 9.1 4.5 4.5 0 0 0 7 18z"/>""",
    "user" to """<circle cx="12" cy="8" r="4"/><path d="M4 21c0-4 3.6-7 8-7s8 3 8 7"/>""",
    "lock" to """<rect x="5" y="11" width="14" height="10" rx="2"/><path d="M8 11V7a4 4 0 0 1 8 0v4"/>""",
    "key" to """<circle cx="8" cy="15" r="4"/><path d="M11 12l9-9M17 6l3 3M15 8l2 2"/>""",
    "gear" to """<circle cx="12" cy="12" r="3"/><circle cx="12" cy="12" r="7"/><path d="M12 2v3M12 19v3M2 12h3M19 12h3M4.9 4.9L7 7M17 17l2.1 2.1M4.9 19.1L7 17M17 7l2.1-2.1"/>""",
    "check" to """<path d="M5 12l5 5L20 7"/>""",
    "cross" to """<path d="M6 6l12 12M18 6L6 18"/>""",
    "bolt" to """<path d="M13 2L4 14h7l-1 8 9-12h-7z"/>""",
    "clock" to """<circle cx="12" cy="12" r="9"/><path d="M12 7v5l3 3"/>""",
    "chart" to """<path d="M3 21h18M6 18v-7M11 18V5M16 18v-9M21 18v-4"/>""",
    "code" to """<path d="M8 7l-5 5 5 5M16 7l5 5-5 5M14 4l-4 16"/>""",
    "globe" to """<circle cx="12" cy="12" r="9"/><path d="M3 12h18M12 3c2.5 2.7 3.8 5.7 3.8 9s-1.3 6.3-3.8 9c-2.5-2.7-3.8-5.7-3.8-9S9.5 5.7 12 3z"/>""",
    "queue" to """<rect x="2" y="8" width="5" height="8" rx="1"/><rect x="9.5" y="8" width="5" height="8" rx="1"/><rect x="17" y="8" width="5" height="8" rx="1"/>""",
    "layers" to """<path d="M12 2l9 5-9 5-9-5z"/><path d="M3 12l9 5 9-5"/><path d="M3 17l9 5 9-5"/>""",
    "plug" to """<path d="M9 2v5M15 2v5M6 7h12v4a6 6 0 0 1-12 0zM12 17v5"/>""",
    "shield" to """<path d="M12 2l8 3v6c0 5-3.4 9.4-8 11-4.6-1.6-8-6-8-11V5z"/>""",
    "rocket" to """<path d="M14 4c3-2 6-2 6-2s0 3-2 6l-6 6-4-4z"/><path d="M9 10H5l-2 3h5M14 15v4l-3 2v-5M6 16c-1.5 1.5-2 5-2 5s3.5-.5 5-2"/>""",
    "warning" to """<path d="M12 3l10 18H2z"/><path d="M12 10v5M12 18v.5"/>""",
    "idea" to """<path d="M9 18h6M10 21h4M12 3a6 6 0 0 0-3.5 10.9c.6.5 1 1.2 1 2.1h5c0-.9.4-1.6 1-2.1A6 6 0 0 0 12 3z"/>""",
    "book" to """<path d="M4 4h6a2 2 0 0 1 2 2v14a2 2 0 0 0-2-2H4z"/><path d="M20 4h-6a2 2 0 0 0-2 2v14a2 2 0 0 1 2-2h6z"/>""",
    "search" to """<circle cx="11" cy="11" r="7"/><path d="M16 16l5 5"/>""",
    "mobile" to """<rect x="7" y="2" width="10" height="20" rx="2"/><path d="M11 18h2"/>""",
    "network" to """<circle cx="12" cy="5" r="2.5"/><circle cx="5" cy="19" r="2.5"/><circle cx="19" cy="19" r="2.5"/><path d="M11 7.2L6.3 16.8M13 7.2l4.7 9.6M7.5 19h9"/>""",
    "arrow" to """<path d="M4 12h16M14 6l6 6-6 6"/>""",
    "box" to """<path d="M3 7l9-4 9 4v10l-9 4-9-4z"/><path d="M3 7l9 4 9-4M12 11v10"/>""",
    "target" to """<circle cx="12" cy="12" r="9"/><circle cx="12" cy="12" r="5"/><circle cx="12" cy="12" r="1.5"/>""",
    "star" to """<path d="M12 3l2.8 5.7 6.2.9-4.5 4.4 1 6.2-5.5-2.9-5.5 2.9 1-6.2L3 9.6l6.2-.9z"/>""",
    "chat" to """<path d="M4 5h16v11H9l-5 4z"/>""",
    "coffee" to """<path d="M5 10h11v5a5 5 0 0 1-5 5h-1a5 5 0 0 1-5-5z"/><path d="M16 11h2a2 2 0 0 1 0 4h-2M9 3c-1 1.5 1 2.5 0 4M12 3c-1 1.5 1 2.5 0 4"/>""",
    "terminal" to """<rect x="2" y="4" width="20" height="16" rx="2"/><path d="M6 9l3 3-3 3M12 15h6"/>""",
    "retry" to """<path d="M20 11a8 8 0 1 0-2.3 5.7"/><path d="M20 4v7h-7"/>""",
    "cpu" to """<rect x="6" y="6" width="12" height="12" rx="1"/><rect x="9" y="9" width="6" height="6"/><path d="M9 2v4M15 2v4M9 18v4M15 18v4M2 9h4M2 15h4M18 9h4M18 15h4"/>""",
    "list" to """<path d="M9 6h12M9 12h12M9 18h12M4 6h.01M4 12h.01M4 18h.01"/>""",
)
