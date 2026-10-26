# 12. LinkedIn post length

Source: request from the product owner on 2026-10-25. Users were getting posts rejected for going over LinkedIn's character limit once the default hashtags were added. Updated 2026-10-26: generated posts were still sometimes over 3,000 characters, so the app now never shows a generated post that can't be posted, and the user can trim a post themselves.

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
- **PL-6** If it is over the limit, the app sends it back to the same agent with how far over it is and the budget, asking for a shorter version in the same format. It tries up to `linkedin_post.shorten_attempts` times (default 3), aiming lower each time (80%, then 70%, then 60% of the budget), and keeps the shortest version it gets.
- **PL-9** If the shortest version is still over the limit, or a shorten call fails, the app cuts it to fit before showing it, so the screen only ever shows a post that can be published. The cut keeps the opening hook, the "📌 In short:" summary and the hashtags, and removes the least important parts first: the last lines of the last sections (a section heading left on its own goes too, and tables and code blocks go whole), then, if needed, the end of the text at a word break, ending with "…". A long paragraph is cut part-way at a word break rather than dropped when that's enough.

### Showing the length

- **PL-7** Under each generated post the app shows the count against the limit, e.g. "2,710 / 3000 characters with hashtags". If a post is over (for example after the hashtags were changed), the note turns red, says by how much and points to Trim post.
- **PL-8** The Post to LinkedIn confirmation shows a live count as the user edits, and **Post** stays disabled while the text is over the limit (as before, LP-5 / HT-14).

### Trim post

- **PL-10** The Post to LinkedIn confirmation has a **Trim post** option under the count with two actions. **✂ Trim with AI** sends the text, without the hashtags, to the selected text agent and asks it to condense the post while keeping every key point, number and the conclusion. When the post is over the limit it aims for the limit minus the hashtags; when it already fits it aims for about a fifth shorter, so the user can tighten a long post. The text can't be edited or posted while the agent works.
- **PL-11** The agent's reply is converted to plain text, cut to fit as in PL-9 if it is still over, and the hashtags are put back. If no text agent is set up or the call fails, the text is left as it was and the dialog says why.
- **PL-12** **Cut to fit** works without an AI agent and is enabled only while the text is over the limit. It cuts the text as in PL-9, keeping the hook, the summary and the hashtags.

## Configuration

```yaml
linkedin_post:
  max_characters: 3000
  shorten_attempts: 3
```

Missing or invalid values fall back to the defaults above. `shorten_attempts: 0` turns off the AI shortening; the budget stays in the prompt and a post that's over is still cut to fit (PL-9).
