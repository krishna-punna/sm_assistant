# 10. Default hashtags

Source: wireframe supplied by the product owner on 2026-10-25 ([wireframe](wireframes/default-hashtags.png)), not part of the original slide deck.

## Purpose

The user keeps a list of default hashtags (e.g. `#JavaDeveloper #AIEngineer`) that the app adds to every LinkedIn post when they tap **Post in LinkedIn**. A setting turns this on or off.

## Entry point

Home → **Default Hash Tags** *(new Home button; S14 in the [screen inventory](02-screens-and-navigation.md))*.

## Layout

| Element | Type | Notes |
|---------|------|-------|
| "SM Assistant" | App bar | |
| "Default Hash Tags#" | Section chip (yellow) | |
| Hashtag text box | Multi-line text box | Free text, e.g. `#JavaDeveloper #AIEngineer`. Shows the saved tags when the screen opens. |
| Save | Button (blue) | Saves the tags. |
| Clear | Button (blue) | Empties the text box. |
| Add default hashtags to LinkedIn posts | On/off toggle | *Not drawn in the wireframe.* Placed on this screen so the tags and their switch live together (see Q-17). |
| Back to Home | Footer button (black) | |

## Functional requirements

### Managing the hashtag list

- **HT-1** Home has a **Default Hash Tags** button that opens this screen.
- **HT-2** The user can type hashtags separated by spaces, commas or new lines.
- **HT-3** **Save** stores the list on the device. It persists across app restarts and is pre-filled when the screen reopens.
- **HT-4** When the list is saved, it is normalised:
  - a `#` is added to any tag typed without one
  - characters LinkedIn does not allow in hashtags (spaces, punctuation other than `_`) are rejected with an inline message
  - duplicates are removed, case-insensitively
  - the user's order is kept
- **HT-5** **Clear** empties the text box. The saved list only changes when the user then taps **Save** (an empty save removes all default tags). See Q-18.
- **HT-6** Show a confirmation after a successful save.

### Enable / disable

- **HT-7** An **Add default hashtags to LinkedIn posts** toggle turns the feature on or off. The setting persists across restarts.
- **HT-8** The toggle starts **off** on first install and switches **on** automatically the first time the user saves a non-empty list. After that, only the user changes it.
- **HT-9** Turning the toggle off keeps the saved list, so the user can turn it back on without retyping.

### Applying hashtags when posting

- **HT-10** When the toggle is on and the list is not empty, **Post in LinkedIn** appends the default hashtags to the post text. They go at the end, after one blank line, separated by single spaces.
- **HT-11** Tags already in the generated text are not added again (case-insensitive match).
- **HT-12** This applies to every result that can be posted:
  - Technical Content
  - Image
  - Video
  - Content & Image
  - Content & Video

  For image-only or video-only posts, the hashtags become the post text.
- **HT-13** Hashtags are added at posting time. They are not saved into the generated content, so changing the list or the toggle affects the next post without regenerating anything.
- **HT-14** Hashtags count towards LinkedIn's ~3,000-character post limit. If the post text plus hashtags would go over, the hashtags are kept and the user is told the body must be shortened (ties in with [LP-5](07-post-to-linkedin.md) / Q-12).
- **HT-15** When the toggle is on, the pre-publish confirmation or preview (if there is one, see Q-12) shows the hashtags that will be added.
- **HT-16** Hashtags are never added to Prepare LinkedIn Post Prompt output, which is a prompt, not a post.

## Example

Saved list: `#JavaDeveloper #AIEngineer`, toggle on.

```
🔧 YAML vs .properties in Spring Boot — Quick Comparison
...
📌 In short: YAML is cleaner and more flexible ...

#JavaDeveloper #AIEngineer
```

## Non-functional requirements *(inferred)*

- Stored locally alongside the other settings. Not a secret, so normal app storage is fine.
- LinkedIn advises about 3–5 hashtags per post. Show a gentle warning above 5, but do not block.
