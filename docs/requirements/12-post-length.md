# 12. LinkedIn post length

Source: request from the product owner on 2026-10-25. Users were getting posts rejected for going over LinkedIn's character limit once the default hashtags were added.

## Purpose

LinkedIn allows at most **3,000 characters** per post, counting spaces, emoji and line breaks. The default hashtags ([10](10-default-hashtags.md)) are part of that count. The app should write posts that already fit, check them after generation, and have the AI agent shorten any that don't, so the user rarely has to cut a post by hand.

## Functional requirements

### Counting

- **PL-1** Length is counted the way LinkedIn counts it: every character, space and line break, with each emoji or other symbol counted once (Unicode code points, not UTF-16 units).
- **PL-2** The limit is `linkedin_post.max_characters` in `app.yml` (default 3000), so it can change without a code change if LinkedIn changes it.

### Generating to the budget

- **PL-3** Before generating, the app works out the **budget** for the post body: the limit minus the default hashtags and the blank line before them, when the hashtag toggle is on. All saved tags are reserved, because the body isn't known yet.
- **PL-4** The budget goes into the text agent's system prompt (Claude, Gemini or Groq), with a target about 10% under it, because models tend to overshoot. This applies to Technical Content, Content & Image and Content & Video.

### Checking and shortening

- **PL-5** After generation, the post is measured as it will be published: the Markdown converted to LinkedIn plain text, plus the hashtags that will actually be added.
- **PL-6** If it is over the limit, the app sends it back to the same agent with how far over it is and the budget, asking for a shorter version in the same format. It tries up to `linkedin_post.shorten_attempts` times (default 2) and keeps the shortest version it gets. A failed shorten call keeps the post it already has.

### Showing the length

- **PL-7** Under each generated post the app shows the count against the limit, e.g. "2,710 / 3000 characters with hashtags". If the post is still over, the note turns red and says by how much, so the user can ask the agent to shorten it or edit it when posting.
- **PL-8** The Post to LinkedIn confirmation shows a live count as the user edits, and **Post** stays disabled while the text is over the limit (as before, LP-5 / HT-14).

## Configuration

```yaml
linkedin_post:
  max_characters: 3000
  shorten_attempts: 2
  ai_hashtags: 3      # most hashtags the agent adds when AI-suggested hashtags are on (1 to 10, HT-21)
```

Missing or invalid values fall back to the defaults above. `shorten_attempts: 0` turns off the automatic shortening but keeps the budget in the prompt and the warning.
