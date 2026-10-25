# 8. UI conventions

Derived from all wireframe slides.

## Colour roles

| Colour | Used for |
|--------|----------|
| Blue (`#4472C4` in the deck) | App bar; navigation/list buttons (Home, AI Agent list, hub); text fields; **Post in LinkedIn** |
| Yellow / amber (`#FFC000`) | Section chip naming the current screen or feature |
| Green (`#70AD47`) | Primary commit actions: **SAVE**, **Submit** |
| Black | **Back to Home** footer |
| Light blue/lavender | Chat input bar |
| Dark grey | Prompt card background (copy + expand icons in its header) |

*(These are default Office theme colours in the wireframe; final visual design may differ.)*

## Components

| Component | Description |
|-----------|-------------|
| App bar | Full-width, centred "SM Assistant". Present on every screen. |
| Section chip | Left-aligned label under the app bar identifying the screen. |
| Menu button | Full-width blue button with centred label (Home, AI Agent list, hub). |
| Labelled text field | Label above a full-width field (settings screens). |
| Primary button | Full-width green **SAVE** above the footer (settings). |
| Chat input bar | Text input with a green **Submit** button to its right, pinned above the footer. |
| Prompt card | Dark card with title, copy icon, expand icon, and the prompt text. |
| Result view | Scrollable rich text and/or media gallery, followed by **Post in LinkedIn**. |
| Footer | Full-width black **Back to Home**. |

## Layout rules

- Portrait phone layout, single column.
- Header and footer are fixed; the body scrolls.
- Settings screens: chip → fields → (space) → SAVE → Back to Home.
- Feature screens: chip → chat/result → input bar → Back to Home.
