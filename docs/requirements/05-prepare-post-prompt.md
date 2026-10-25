# 5. Prepare LinkedIn Post Prompt

Source: slide 5 ([wireframe](wireframes/slide-05.jpg)).

## Purpose

Turns a short topic written by the user into a detailed, expert-quality prompt that can then be fed into one of the generation features.

## Entry point

Home → SM Assistant → **Prepare LinkedIn Post Prompt**.

## Flow (as shown, left to right)

| State | What the user sees |
|-------|--------------------|
| 1. Input | Empty chat area. The user types a topic in the bottom input bar — example: **"Design a system for Short URL"** — and taps **Submit**. |
| 2. Result | The user's topic appears as a message at the top. Below it, the assistant's reply is a card titled **"URL Shortener System Design Prompt"** with **copy** and **expand** icons, containing the generated prompt. The input bar is cleared and ready for another topic. |

### Example output (from the wireframe)

> Act as a Senior Principal Software Engineer with 15+ years of experience in distributed systems and system design. Design a scalable, highly available URL Shortener system similar to Bitly using Java 17, Spring Boot, Redis, Kafka, and a suitable database.
>
> Cover functional and non-functional requirements, capacity estimation, high-level architecture, REST API design, Base62 short-code generation, database schema, caching strategy, and asynchronous click analytics. Explain horizontal scaling, database partitioning, fault tolerance, security, and disaster recovery. Include architecture and sequence diagrams, Java code examples, API specifications, and design trade-offs. Justify technology choices and explain failure scenarios. Optimize for billions of URLs and low-latency redirects.

## Functional requirements

- **PP-1** The user can type a free-text topic and submit it.
- **PP-2** The app sends the topic to the configured AI agent with instructions to produce a detailed prompt suitable for generating a LinkedIn post.
- **PP-3** The generated prompt is displayed as a titled card (title derived from the topic).
- **PP-4** The card has a **copy** action that copies the full prompt text to the clipboard.
- **PP-5** The card has an **expand** action that shows the prompt full-screen *(inferred from the icon)*.
- **PP-6** The conversation stays on screen; the user can submit another topic.
- **PP-7** **Back to Home** returns to Home.

## Observed prompt pattern *(inferred)*

The generated prompt follows a consistent recipe that the system prompt for this feature should reproduce:

1. Assign an expert persona with seniority and experience.
2. Restate the task with a concrete tech stack.
3. List the sections to cover (requirements, estimation, architecture, API, schema, caching, scaling, security, DR).
4. Ask for diagrams, code examples and trade-offs.
5. State scale / performance targets.

## Hand-off to generators

In slides 6–11 the same prompt text is pasted into each generator's input. A **"Use this prompt in…"** shortcut from the prompt card to a generator would remove the manual copy/paste (Q-06).
