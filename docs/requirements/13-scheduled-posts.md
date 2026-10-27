# 13. Scheduled LinkedIn posts

Source: requirements 10, 11 and 12 from the product owner on 2026-10-26. The schedule options were left to us ("come up with the best scheduling configurations for an Android app").

## Purpose

The user sets up one or more **schedulers**. Each one, on its schedule, picks a technical topic, turns it into a prompt, makes a post (text, an image, a video, text with an image, or text with a video), checks that it fits LinkedIn's limit with the default hashtags, and publishes it, all without the app being open.

## Functional requirements

### The automatic run

- **SC-1** On each run the scheduler gives the next of its technical content types (SC-3) to **Prepare LinkedIn Post Prompt** ([05](05-prepare-post-prompt.md)) as the topic. It asks for one specific, practical angle on that type and passes the titles of its last ten posts so the angle isn't repeated.
- **SC-2** The prepared prompt goes to **Generate Technical Content** ([06](06-content-generation.md)) with the same agent, system prompt, length budget and AI-suggested hashtags as the screen ([12](12-post-length.md)). The prompt is prepared for the scheduler's post type as Prepare Prompt's Prompt Type does ([14](14-prompt-types-and-media-workflow.md)): the post prompt goes to Generate Technical Content, and the "no limit" content prompt goes to Generate Image (*Image*, *Text & Image*) or Generate Video (*Video*, *Text & Video*). *Image* and *Video* posts have no written post: the prompt's title is the caption, followed by the default hashtags.
- **SC-3a** The post is checked as it will be published: Markdown converted to LinkedIn text, default hashtags added when that toggle is on, and the result within `linkedin_post.max_characters` (3,000). A post that doesn't fit is not published.
- **SC-3b** A valid post is published to LinkedIn with its images or video ([07](07-post-to-linkedin.md)). If the images or video could not be made, nothing is published: a *Text & Image* scheduler never posts text alone, and an *Image* scheduler never posts just a caption.
- **SC-6** The topics take turns: each run uses the next type in the scheduler's list, wrapping round. A failed run still moves on, so one bad topic doesn't block the rest.
- **SC-7** Each run's result (posted, with the post's link, or why it failed) is shown on the scheduler and in a notification. Failures that need the user, such as no text agent or an expired LinkedIn token, say what to set up.

### Scheduler settings

- **SC-3** **Technical content types**: a searchable multi-select of 200 topics around Java, Spring and full-stack development (Java, Spring, Angular, Distributed Systems, Kafka and others), grouped (Java, JVM, Spring, Data and persistence, Messaging and streaming, Distributed systems and architecture, APIs, Frontend, Cloud and DevOps, Testing and quality, Security, AI for developers). Search matches every typed word against the topic or its group. At least one is required.
- **SC-4** **Schedule**, one of:
  - *Every few hours*: every 1, 2, 3, 4, 6, 8 or 12 hours between a start and end time (default every 4 hours, 09:00–18:00), optionally weekdays only.
  - *Daily*: at a time (default 09:00), optionally weekdays only.
  - *Weekly*: at a time on one or more chosen days (default Tuesday and Thursday).
  - *Monthly*: at a time on a day of the month, 1–28 or *Last day*.
  Times are local and stay the same across daylight saving changes.
- **SC-5** **Post type**: *Text*, *Image*, *Video*, *Text & Image* or *Text & Video*, the same choices as Prompt Type.
- **SC-9** An optional **name**; without one the list shows the first topics.

### Several schedulers

- **SC-8** The user can create any number of schedulers, each switched on or off on its own, and edit or delete them. Several can be on at once.
- **SC-10** **Run now** runs a scheduler straight away, for trying it out; its next planned run doesn't change.

## Non-functional requirements

- **SC-NFR1** Runs use Android WorkManager, so they survive the app being closed and the phone restarting, and only start with a network connection. Android decides the exact moment: when the phone is idle (Doze) or in battery saver, a run can start several minutes, occasionally longer, after its time. The settings screen says so. Exact alarms are not used, as they need an extra permission that Play restricts to alarm and calendar apps.
- **SC-NFR2** If the phone was off or offline at a run's time, the run happens once when it can, and the next one is planned from then. Missed runs are not caught up one by one, so the feed never gets a burst of posts.
- **SC-NFR3** A run that fails on a network error or rate limit is retried up to twice with backoff before it is recorded as failed.
- **SC-NFR4** API keys and the LinkedIn token are read from the same encrypted settings as the screens; nothing new is stored as a secret. LinkedIn tokens last about 60 days, after which runs fail with "sign in again" until the user does.
