# Few-shot examples for video slide decks

Each example below is a good short explainer video for LinkedIn, written as the slides it plays
in order. They are videos, not posters: one idea per slide, few words, the story built up slide by
slide. The app picks the examples whose `Keywords` best match the topic and puts them in
`video_deck.md` as `{{examples}}`.

Format: examples are separated by a line holding only `---`. Each one has a `Title`, a
`Keywords` line (comma separated, lower case), a `Template` (the id of the video template in
`video_templates.md` it is an example of), a `Topic` and the `Slides`.
Examples of the chosen template are shown first, then keyword matches.
When nothing matches, the first three are used, so keep three different structures at the top.

---
Title: How an HTTP request reaches a Spring Boot controller
Keywords: spring boot, spring, http, request, dispatcher servlet, filter, controller, mvc, lifecycle, workflow
Template: workflow
Topic: The lifecycle of an HTTP request in Spring Boot
Slides: 1 Hook: "What happens between curl and your @GetMapping?" with a big question icon. 2 The path at a glance: a flow of five boxes, Client → Tomcat → Filter chain → DispatcherServlet → Controller, revealed one box at a time. 3 Filters: security and logging filters as a stack, one line each on what they check. 4 DispatcherServlet: HandlerMapping finds the method, HandlerAdapter calls it, two short steps. 5 Controller and response: the return value turned into JSON by Jackson, a three-line code panel. 6 Takeaway: "Every request passes the filters before your code runs", with a check icon.

---
Title: Kafka architecture in 30 seconds
Keywords: kafka, event streaming, broker, topic, partition, consumer group, producer, architecture, messaging, distributed
Template: architecture
Topic: How Apache Kafka is put together
Slides: 1 Title: "Kafka in 30 seconds" with a queue icon. 2 Producers write to a topic: producer boxes → topic box, one arrow each. 3 A topic is split into partitions: three partition lanes with numbered offsets, revealed lane by lane. 4 Brokers hold the partitions, replicated: three broker boxes, a leader star on one replica each. 5 Consumer groups: two consumers each reading their own partitions, "one partition, one consumer in a group". 6 Takeaway: "Partitions give order and scale", with a layers icon.

---
Title: REST vs gRPC
Keywords: rest, grpc, api, http, protobuf, json, microservices, comparison, performance, streaming
Template: comparison
Topic: When to use REST and when to use gRPC
Slides: 1 Hook: "REST or gRPC for your next service?" with two icons side by side. 2 Payload: JSON text vs Protobuf binary, a two-column table row. 3 Transport: HTTP/1.1 request-response vs HTTP/2 streams, two short flows. 4 Contracts: OpenAPI optional vs .proto required, one line each. 5 Where each fits: public and browser APIs for REST, internal low-latency calls for gRPC. 6 Takeaway: "Pick by who calls you, not by speed alone", no winner declared.

---
Title: CompletableFuture chaining
Keywords: java, completablefuture, async, concurrency, thenapply, thencompose, code, futures, non-blocking
Template: code
Topic: Chaining async calls with CompletableFuture
Slides: 1 Title: "Chain async calls without blocking" with a bolt icon. 2 The blocking way: a four-line code panel with .get() highlighted as the problem. 3 thenApply: a three-line code panel transforming the result, one line on when to use it. 4 thenCompose: a code panel chaining a second async call, "flat-maps the future". 5 exceptionally: a code panel with a fallback value, a shield icon. 6 Takeaway: "Compose, don't block", with a check icon.

---
Title: What idempotency means for APIs
Keywords: idempotency, api, rest, retry, payments, idempotency key, http methods, concept, distributed systems
Template: concept
Topic: Idempotent APIs
Slides: 1 Hook: "The client retried. Did you charge twice?" with a warning icon. 2 Definition: "Same request, same effect, however many times", in one big line. 3 HTTP methods: GET, PUT, DELETE idempotent; POST not, four chips. 4 Idempotency key: the client sends a key, the server stores the first result, a three-box flow. 5 Takeaway: "Make retries safe before you add them", with a retry icon.

---
Title: HashMap interview questions
Keywords: java, hashmap, collections, hashing, buckets, collision, treeify, interview, learning, data structure
Template: learning
Topic: Five HashMap questions interviewers ask
Slides: 1 Title: "5 HashMap questions you will be asked" with a book icon. 2 Q1 How does put work: hash → bucket index → node, a three-step flow. 3 Q2 Collisions: a bucket with a linked list, then a tree after 8 entries. 4 Q3 Why resize at 0.75: one line and a before/after bucket row. 5 Q4 Why override equals and hashCode together: a two-line code panel. 6 Q5 Is it thread-safe: no, use ConcurrentHashMap, a lock icon. 7 Takeaway: "Know put, collisions and resize, and you know HashMap".
