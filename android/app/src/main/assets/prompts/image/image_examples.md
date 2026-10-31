# Few-shot examples for image generation

Each example below describes one of the sample LinkedIn post images in
`requirements/linkedin-posts-sample-images/`, written as the prompt that would produce it.
The app picks the examples whose `Keywords` best match the user's topic and puts them in
`image.md` as `{{examples}}`, so Gemini sees what a good image for a similar topic looks like.

Format: examples are separated by a line holding only `---`. Each one has a `Title`, a
`Keywords` line (comma separated, lower case), a `Template` (the id of the image template in
`image_templates.md` it is an example of), a `Topic` and the `Image prompt`.
Examples of the chosen template are shown first, then keyword matches.
When nothing matches, the first three are used, so keep three different layouts at the top.

---
Title: ArrayList internals cheat sheet
Sample: img1.jpg, img3.png
Keywords: java, arraylist, list, collections, internals, data structure, array, resizing, time complexity, interview
Template: learning
Topic: How ArrayList works internally in Java
Image prompt: A tall portrait (2:3) infographic cheat sheet titled "ArrayList Internals" in big bold white and yellow letters on a dark navy header band, with a small Java cup icon on the left, the subtitle "Behind the Scenes of Java's Resizable Array" in a blue pill, four feature chips (Dynamic, Fast Access, Resizable, Efficient) and a dashed yellow call-out box "A Must-Know for Interviews & Real-World Projects!". Below, a light background with 11 numbered rounded-corner cards in a three-column grid, each with a coloured header tab (blue, green, purple, orange, pink): What is ArrayList (check and cross bullets), Internal Data Structure (a row of indexed array cells E0..E4 then null, with Size = 5 and Capacity = 10 braces), How it works (icons for add/get/set/remove/size), add(E e) step by step (numbered circles), Array Growth (1.5x old capacity, array before and after), remove steps, a Time Complexity table, Key Points, Constructors and a small Java code block with syntax colouring, and When to use / Avoid when. A dark footer strip with "Interview Tip", "Keep learning, keep growing!" and "Follow for more Java & System Design content".

---
Title: Retry pattern in microservices with Spring Boot
Sample: img8.png
Keywords: retry, resilience, resilience4j, microservices, spring boot, spring, backoff, fault tolerance, transient failures, design pattern
Template: architecture
Topic: Retry design pattern in microservices with Spring Boot and Resilience4j
Image prompt: A wide landscape (3:2) technical poster on an off-white background. Title "RETRY DESIGN PATTERN IN MICROSERVICES" in dark navy capitals with "WITH SPRING BOOT" in green below, a green Spring power icon on the left and a one-line definition in a rounded box. Top right, a green "When to retry?" panel with four icons: Network Issues, Timeout Exceptions, Service Unavailable (5xx), Temporary Overload. Numbered section cards with coloured header tabs: 1 Architecture Overview (Client Service A → dashed Retry Interceptor box with Retry/Backoff/Max Attempts → red Target Microservice Service B → Database, red dashed Fail arrow, green Response arrow), 2 Retry Flow (a flowchart with a "Success?" diamond, "Is retry attempt < max attempts?" diamond, Wait (backoff) exponential delay, Fail & return exception), 3 Benefits (green check list), 4 Best Practices (star bullets), 5 Configuration & Code with three code panels (application.yml, a @Retry service method, Maven dependencies). A bottom strip of seven step tiles showing the request, failed attempts with red crosses, hourglass waits and a final green success. Footer with a lightbulb "Key Takeaway" and a legend for success, failure and retry arrows.

---
Title: SQL query optimization before and after
Sample: img20.jpg
Keywords: sql, query, database, optimization, performance, index, join, exists, select, tuning
Template: code
Topic: SQL query optimization techniques
Image prompt: A square (1:1) infographic on a pale blue grid background with faint circuit lines. Title "SQL QUERY OPTIMIZATION TECHNIQUES" in bold capitals with a blue-to-green gradient and the subtitle "Professional Guide to Faster Database Performance". A 2 x 3 grid of six cards, each with a blue header bar holding a numbered technique and a small database icon: 1 Use Indexes Effectively, 2 Avoid SELECT *, 3 Use EXISTS instead of IN, 4 Optimize JOINs, 5 Filter Early, 6 Avoid Functions on Indexed Columns. Inside every card a red-outlined "BEFORE" SQL code box, a green arrow, and a green-outlined "AFTER" SQL code box in monospace font, then a solid green banner "PERFORMANCE IMPACT: UP TO 90% FASTER" with a one-line reason in brackets.

---
Title: Angular release highlights
Sample: img2.jpg
Keywords: angular, frontend, release, new features, what's new, version, web, typescript, signals
Template: universal
Topic: What's new in Angular 21 for developers
Image prompt: A square (1:1) infographic on a white background with red dot-grid and red curve accents in the corners. Large header "ANGULAR 21" with the red Angular shield logo, the tagline "FASTER. SIMPLER. SMARTER." and a red pill "What's New for Developers?". A 3 x 3 grid of white rounded cards with soft shadows, each with a large flat coloured icon on the left and a numbered coloured heading on the right plus a two-line description: 1 Zoneless Change Detection (speedometer), 2 Signals as Core Reactivity (atom), 3 Signal Forms (form with check), 4 Vitest as Default Test Runner (flask), 5 HttpClient Auto-Provided (globe), 6 Angular ARIA (accessibility icon), 7 Standalone Components (puzzle piece), 8 Performance & Build Improvements (rising bar chart), 9 Better Developer Experience (code brackets). A dark navy rounded footer bar with a rocket icon: "Angular 21 is a big step towards a faster, more modern and developer-friendly future!" with one phrase highlighted in red.

---
Title: RAG chatbot with LangGraph
Sample: img4.png
Keywords: ai, generative ai, genai, rag, retrieval, llm, chatbot, langgraph, langchain, vector database, embeddings, agents
Template: architecture
Topic: Building accurate chatbots with Generative AI, RAG and LangGraph
Image prompt: A wide landscape (3:2) infographic with a deep navy title banner "Generative AI + RAG (Retrieval-Augmentation-Generation)" and the subtitle "Building Accurate & Informative Chatbots with LangGraph". Below, light blue rounded panels with navy header bars: What is RAG? (a Venn diagram of Retrieval and Generation overlapping in RAG), RAG Architecture (Knowledge Base with PDFs, Word files, databases, wikis → Document Loader & Splitter → Embedding Model → pink Vector Database cylinder; User Question → Retriever → Prompt Builder → LLM → Accurate Response), RAG vs LLM Only (two columns with robot icons, red "Limited Accuracy" vs green "Reliable & Relevant"), RAG Workflow (six numbered pastel steps), LangGraph-based Chatbot (User → LangGraph Workflow branching to Retriever, Chat Memory, Tools/APIs → LLM → Chatbot Response), Example Chat Conversation (chat bubbles with a cited source), a dark Sample Python Code panel, Key Benefits of RAG (green checks) and Key Takeaways (lightbulb and checks).

---
Title: Angular performance optimization patterns
Sample: img5.png
Keywords: angular, performance, optimization, change detection, onpush, immutable, frontend, design patterns, rendering
Template: checklist
Topic: Angular performance comes from design patterns, not micro-optimizations
Image prompt: A tall portrait (2:3) infographic with a purple-to-blue gradient header showing a launching rocket, the title "Angular Performance Optimization", the Angular shield logo with a "v16+" badge and the tagline "Performance is driven more by Design Patterns than by Micro-Optimizations". Two side-by-side cards "The Reality" (target icon, pink) and "The Goal" (trophy, green). A purple pill heading "3 Key Performance Patterns in Angular v16+" over three cards with icon circles and mini diagrams: OnPush Change Detection (app → components tree), Immutable Data Structures (Old State → New State), Detached Change Detection (component with a detach hand), each with green check bullets and a "Result:" chip. Then "Common Assumptions vs Reality" as a two-column table and "Key Benefits" as a list of pastel rows with icons. A four-step horizontal "Performance Improvement Approach" (Understand, Find the Bottleneck, Apply the Right Pattern, Measure) joined by arrows, and a green "Bottom Line" footer with a trophy.

---
Title: Angular data binding types with code
Sample: img6.png
Keywords: angular, data binding, interpolation, property binding, event binding, two-way binding, ngmodel, template, component, frontend
Template: code
Topic: The four types of data binding in Angular
Image prompt: A tall portrait (2:3) infographic with a dark navy header: the red Angular shield, the title "ANGULAR DATA BINDING" (red and white), the subtitle "Connecting your data to the UI, keeping everything in sync!", a "Did you know?" donut chart showing 70% and a small illustration of a monitor with code. A definition strip showing COMPONENT ⇄ TEMPLATE. A heading "TYPES OF DATA BINDING IN ANGULAR" over four full-width colour-coded rows (blue, green, purple, orange): 1 Interpolation, 2 Property Binding, 3 Event Binding, 4 Two-Way Binding. Each row has a short description with an icon, a "Component (TypeScript)" code card, a dashed arrow labelled with the data flow, a "Template (HTML)" code card with the binding syntax highlighted, a small rendered UI preview below, and a "What happens?" box with an icon. Footer with Key Takeaways (check list), a laptop with the Angular logo surrounded by binding symbols, Why It Matters (icon list) and a hashtag strip.

---
Title: Microservices failures and resilience
Sample: img7.png
Keywords: microservices, failures, resilience, distributed systems, circuit breaker, service mesh, istio, observability, monitoring, prometheus, grafana, reliability
Template: concept
Topic: Microservice failures are a necessary step to resilient systems
Image prompt: A tall portrait (2:3) infographic with a dark navy header: 3D cubes with one red cracked cube marked with an X, the title "MICROSERVICES FAILURES" in white and yellow, the subtitle "Not just inevitable, but a necessary step to resilient systems", and a quote box with a Fail → Learn → Improve cycle diagram. White cards below: The Reality and Don't Just Prevent, Prepare & Adapt (network of green cubes with one red cube and a warning sign); "Why do microservices fail?" with five icon columns (Network Partitions, Hardware Failures, Traffic Spikes, Code/Deploy Changes, Dependency Failures); "Focus on 3 key areas" with numbered green, blue and orange columns (Service Design, Communication with a circuit-breaker diagram, Monitoring & Observability with Prometheus and Grafana icons); "How it works together" as four coloured step boxes joined by bold arrows (Failure Occurs, Detect & Respond, Isolate & Mitigate, Recover & Learn); a "Mindset Shift" dark panel with a target, Key Takeaways with icons, and a hashtag footer.

---
Title: Java CompletableFuture explained
Sample: img9.png
Keywords: java, completablefuture, async, asynchronous, concurrency, non-blocking, future, threads, callbacks, multithreading
Template: concept
Topic: Java CompletableFuture: asynchronous, non-blocking and composable
Image prompt: A square (1:1) infographic with a dark blue header: Java cup icon, the title "Java CompletableFuture" with the second word in cyan, four keywords separated by dots (Asynchronous, Non-blocking, Composable, Powerful) and a handwritten-style note "Write asynchronous code, in a synchronous way!". Seven numbered light cards with blue number circles: 1 What is CompletableFuture (check bullets, an "In short" lightbulb tip and a dark code block), 2 Why do we need it (a two-column table "Limitation of Future" vs "How CompletableFuture fixes it" with grey and green headers), 3 How it works internally (five stacked colourful step boxes: Creation, Register Callbacks, Completion, Execution Context, Exception Propagation, plus Key Internal Fields), 4 Methods (Creation, Chaining, Combining, Exception Handling as colour-labelled boxes), 5 Real-time use cases (four icons), 6 Complete Example (dark code panel with an Output line), 7 Pros and Cons (green and red columns) and a Quick Cheat Sheet. A dark blue Key Takeaways footer with five numbered tips and a cartoon Duke mascot.

---
Title: HashMap internals cheat sheet
Sample: img10.png
Keywords: java, hashmap, map, hashing, collections, internals, buckets, collision, red-black tree, data structure, interview
Template: learning
Topic: How HashMap works internally in Java
Image prompt: A tall portrait (2:3) cheat sheet in the same series style as "ArrayList Internals": dark navy header with the Java cup, the title "HashMap Internals" in white and yellow, a blue pill subtitle "Understand How HashMap Works Under the Hood", feature chips and a dashed yellow "Must-Know for Interviews" box. Ten numbered cards with coloured header tabs: What is HashMap (with a code snippet), Internal Structure (a Node<K,V>[] table of buckets 0..n-1 holding null, linked-list nodes K1/V1 and a red-black tree), How put(key, value) works (five numbered steps with icons), Collision Handling (linked list chain vs treeification with a red-black tree), How get(key) works, Resizing & Rehashing (old capacity 4 → new capacity 8), Key Points, a Complexity table (average vs worst case before and after Java 8), Hash Function & Index Calculation (code) and a Mini Example (dark code panel plus a bucket array diagram). Dark footer: Interview Tip, Remember, Save & Share.

---
Title: Java Collections interview cheat sheet
Sample: img11.png
Keywords: java, collections, list, set, queue, map, hierarchy, cheat sheet, interview, data structures, comparison
Template: learning
Topic: Java Collections interview cheat sheet
Image prompt: A tall portrait (2:3) cheat sheet with a dark navy header: Java cup, the title "JAVA COLLECTIONS" in white and yellow, a purple pill "INTERVIEW CHEAT SHEET", the subtitle "Essential Concepts, Key Differences & Tricky Questions" and a dashed yellow "Master Collections, Crack Interviews!" box. Section 1 Collections Framework Hierarchy as a colour-coded tree: Iterable → Collection → List (purple), Set (orange, with SortedSet → TreeSet), Queue (teal) and a separate Map (yellow) with their implementations as small boxes; side cards Key Takeaway and Why Map doesn't extend Collection. Section 2 a comparison table List vs Set vs Queue vs Map with icon row labels (Definition, Order, Duplicates, Null Elements, Access, Use Cases, Examples). Section 3 Important Differences as three green-vs-red "VS" pairs (ArrayList vs LinkedList, HashMap vs ConcurrentHashMap, HashSet vs TreeSet). Section 4 Internal Working quick view of HashMap and ArrayList, Section 5 Common Tricky Questions as Q/A with icons, Section 6 a time complexity strip with big O(1) and O(log n). Dark footer with a Golden Rule.

---
Title: ConcurrentHashMap internals (square)
Sample: img12.jpg
Keywords: java, concurrenthashmap, concurrency, thread-safe, multithreading, cas, locking, hashmap, internals, collections
Template: concept
Topic: ConcurrentHashMap internals in Java 8+
Image prompt: A square (1:1) infographic with a dark navy header bar: the title "ConcurrentHashMap Internals (Java 8+)" with the middle word in yellow, a target icon and the tagline "High performance. Thread-safe. Built for Concurrency." and the Java logo on the right. A grid of white panels with dark navy pill headings: Why ConcurrentHashMap? (HashMap and Hashtable with red crosses, ConcurrentHashMap with a green check), Internal Structure (a Node<K,V>[] table with lavender Node boxes chained below and pink TreeNode red-black trees), Key Concepts (Bucket-level Locking with a padlock, CAS, Volatile Variables with an eye), How put() works (four green numbered steps with small flow diagrams: hash → spread → index, locate bucket, insert using CAS, lock only this bucket), Handling Collisions (linked list → red-black tree when nodes > 8), Resize Operation (old table → new 2x table with transfer arrows), When to use? (green checks) and a yellow Pro Tip. Dark footer with "Key Takeaway" and "Master the Internals."

---
Title: ConcurrentHashMap internals explained (portrait)
Sample: img13.png
Keywords: java, concurrenthashmap, concurrency, thread-safe, lock striping, cas, hashtable, hashmap, internals, time complexity, interview
Template: learning
Topic: ConcurrentHashMap internals explained
Image prompt: A tall portrait (2:3) cheat sheet in the dark-header Java series style: Java cup, the title "ConcurrentHashMap" with "Map" in yellow, a purple pill "INTERNALS EXPLAINED", chips High Performance, Thread-Safe, Scalable and a dashed yellow "A Must-Know for Interviews" box. Nine numbered cards: What is ConcurrentHashMap (icon bullets), Internal Data Structure (Node<K,V>[] table with nodes and a red-black tree, legend for linked list and tree), How it works internally (five vertically connected features: Lock Striping, CAS + Synchronization, Volatile + Final, Tree Bins, Counter Cells), put(key, value) flow (seven steps with round icons), get(key) flow (four steps, lock-free reads), Collision Handling, Resizing (old capacity 4 → new capacity 8, ForwardingNode), a comparison table ConcurrentHashMap vs HashMap vs Hashtable, and a Time Complexity table. Dark Key Takeaways footer with five icon tiles and a blue "Save & Share" strip with hashtags.

---
Title: CopyOnWriteArrayList internals
Sample: img14.png
Keywords: java, copyonwritearraylist, concurrency, thread-safe, list, collections, iterator, fail-safe, internals, interview
Template: concept
Topic: How CopyOnWriteArrayList works internally in Java
Image prompt: A tall portrait (2:3) cheat sheet in the dark-header Java series style: Java cup, the title "How CopyOnWriteArrayList Works Internally in Java" with the class name in yellow, a pill subtitle "Thread-Safe Reads, Safe Writes with Copy!" and a friendly flat cartoon developer at a laptop with a plant and a code speech bubble. Eleven numbered cards: What is it (green checks), Internal Data Structure (a volatile Object[] array line of code and an array reference pointing at E0..En-1, plus a Key Point box), How does it work (read vs write operations with eye and pencil icons), add(E e) step by step (six numbered steps) next to a Visual Flow diagram (old array A B C → copy → new array A B C D → set new reference), Example add(40), Iterator behaviour (snapshot vs after modification), Time Complexity table, Key Features, When to use / Avoid when, Internal Implementation (code block with a "What's happening?" numbered side note) and Summary with the cartoon developer again. Dark footer: Key Takeaway, Interview Tip, Save This Post.

---
Title: Java Memory Model
Sample: img15.png
Keywords: java, jmm, memory model, happens-before, visibility, volatile, synchronization, threads, immutable, concurrency
Template: concept
Topic: Understanding the Java Memory Model
Image prompt: A tall portrait (2:3) infographic on a white background. Header: the Java logo on the left, the title "UNDERSTANDING THE JAVA MEMORY MODEL" in navy and blue capitals, the subtitle "The foundation for building scalable, efficient & reliable Java applications", and a stat card on the right with a red-to-green gauge and a big red "30%" with a one-line warning. A light blue definition strip with a head-and-lightbulb icon. A heading "KEY ASPECTS OF JAVA MEMORY MODEL" over a 2 x 2 grid of pastel cards with large numbered circles: 1 Happens-Before Relationship (green; Thread 1 → Thread 2 arrow with Main Memory between), 2 Synchronization (orange; padlock, database and volatile keyword icons and a code line), 3 Visibility (teal; a red X between two threads, "Not immediately visible"), 4 Immutable Objects (purple; a shield with a check and a code card). Each card ends with a tinted one-line tip. Footer card "The Bottom Line" with a target, four icon chips and a trophy with "Master the JMM, build better systems!".

---
Title: Kafka design patterns for Java backend engineers
Sample: img16.jpg
Keywords: kafka, event streaming, messaging, outbox, saga, dlq, retry, log compaction, consumer group, java, backend, distributed systems
Template: checklist
Topic: 5 Kafka design patterns every Java backend engineer should know
Image prompt: A wide landscape (3:2) infographic on a light grey background. Header: Java cup on the left, the title "5 Kafka Design Patterns Every Java Backend Engineer Should Know" with "Java" in orange, the Apache Kafka logo on the right, and a dark pill "Reliable. Scalable. Observable. Production-Ready.". Five tall columns with numbered circles: 1 Outbox Pattern (DB → outbox table → publisher), 2 Retry Topics + DLQ (main topic → retry 5 min → retry 30 min → dead letter queue with a consumer and dashed arrows), 3 Log Compaction = State (an events table compacted into a state table), 4 Controlled Concurrency (topic partitions → consumer group workers → ordered by key), 5 Saga Pattern (Order → Payment → Inventory services with compensating actions). Each column ends with two short lines of insight. Bottom left, a wide "Example: End-to-End Flow" diagram from Client through Order Service, Outbox, a Kafka cluster with coloured topics, a consumer group, Payment and Inventory services, with a legend for main, retry/DLQ and compaction flows. Bottom right, dark-header boxes: Bonus: Cost Matters, Takeaway, Your Turn (a question to the reader).

---
Title: Java Optional methods compared
Sample: img17.png
Keywords: java, optional, null handling, orelse, orelseget, orelsethrow, get, lazy evaluation, comparison, best practices
Template: comparison
Topic: Java Optional get() vs orElse() vs orElseGet() vs orElseThrow()
Image prompt: A tall portrait (2:3) comparison chart. A dark navy title bar "Java Optional Methods - get() vs orElse() vs orElseGet() vs orElseThrow()" with "Handle nulls the smart way!". A header row of coloured column labels: Method (purple), Definition (teal), Usage / Behavior (green), Example (navy). Four full-width rows, one per method, each in its own accent colour (purple, blue, green, orange): the method name large with a flat icon and a one-line role (e.g. "Returns value or a default (eager evaluation)"), a short definition, a mini "Behavior Flow" tree (Optional → Value Present → Return Value / Value Absent → throw or default or supplier) with a note, and a monospace code example with comments plus a tinted "Use Case" box. Bottom: Key Takeaways (four icon bullets) and a "When to Use What?" panel mapping each method chip to an arrow and a sentence, and a yellow "Remember" tip strip.

---
Title: RestTemplate vs RestClient vs FeignClient
Sample: img18.jpg
Keywords: spring boot, spring, resttemplate, restclient, feignclient, http client, rest, microservices, service discovery, eureka, comparison
Template: comparison
Topic: RestTemplate vs RestClient vs FeignClient: which one to use in Spring Boot
Image prompt: A near-square infographic on white. Title "RestTemplate vs RestClient vs FeignClient" with each name in its own colour (red, navy, green) and the subtitle "Which one should you use in Spring Boot?" with a rocket emoji. Three side-by-side pastel cards (red, blue, green) with a numbered title and a big flat icon (old telephone, monitor with code, magic wand): bullet points, a code snippet (RestClient fluent call, @FeignClient interface) or a red "Now DEPRECATED" warning, and a tinted bottom recommendation with an arrow ("Use only for maintaining legacy systems", "Best for simple service-to-service calls", "Ideal for microservices"). Below, a wide light-blue panel "How FeignClient Works with Service Discovery": Your Service → FeignClient → Service Registry (Eureka / Consul) → load balancer → stacked server instances, with six numbered green step circles on solid and dashed arrows.

---
Title: Transaction management in Spring Boot (dark theme)
Sample: img19.jpg
Keywords: spring boot, spring, transaction, transactional, acid, database, propagation, isolation, rollback, commit, backend
Template: workflow
Topic: Transaction management in Spring Boot
Image prompt: A square (1:1) infographic in a dark navy theme with neon green accents and glowing borders. Header: a large green hexagon with the Spring leaf, the title "TRANSACTION MANAGEMENT IN SPRING BOOT" in white and bright green, a 3D database with a shield and check, and the tagline "Ensure Data Consistency, Prevent Partial Updates. Build Reliable Applications.". Dark cards with coloured header bars: What is a Transaction? (ACID principles with four icons), How it works (Client → Service @Transactional → Database with three numbered stages), Why it matters? (green checks), Example: @Transactional (dark code block with an annotation callout "If an exception occurs, Spring rolls back the transaction"), Key Properties table, Example: Custom Properties (code) with a yellow tip, Propagation Types (blue pills with one-line meanings), Success vs Failure (commit vs rollback columns into a "No Partial Updates!" database) and Behind the Scenes (numbered steps with gears). Footer: "TRANSACTIONS = RELIABILITY + CONSISTENCY + PEACE OF MIND" and hashtag pills.

---
Title: Java Stream API with example (friendly hand-drawn style)
Sample: img21.jpg
Keywords: java, stream api, streams, functional programming, lambda, filter, map, collect, java 8, beginner
Template: workflow
Topic: Java Stream API with example
Image prompt: A portrait (about 3:4) beginner-friendly infographic on a warm off-white paper texture with a playful hand-drawn feel. Big black title "Java Stream API" with "(With Example)" in a handwritten font. Section headings are on yellow or lavender highlighter blobs. Left: "What is Java Stream API?" text and a flat cartoon developer at a laptop with a mug and a plant. Right: "Key Features" with green check bullets (Functional, Declarative, Lazy, Can be Parallel, Concise) and a small document doodle. A dashed "How it Works?" pipeline of five pastel boxes joined by arrows: Collection → Stream → Intermediate Operations → Terminal Operation → Result. Below, "Example Code" as a line-numbered code block, and "Common Operations" as a two-column table (filter, map, distinct, sorted, count, forEach, collect). Bottom: "Quick Facts" with orange dot bullets and an "Interview Tip" box with the cartoon developer again.

---
Title: URL shortener system design
Sample: img22.png
Keywords: system design, url shortener, bitly, scalability, architecture, load balancer, cache, redis, database, base62, distributed systems, interview
Template: architecture
Topic: System design of a Bitly-style URL shortener
Image prompt: A tall portrait (2:3) system design infographic with a deep blue header: a chain link icon, the title "Bitly-Style URL Shortener" in white with "System Design" in cyan, the strap "Simple Idea | Complex Design | Scales to Billions" and a handwritten note "Turn long URLs into short links... with speed, scale and reliability!". Eight numbered light cards: Requirements (Functional with checks and a cross, Non-Functional), High-Level Architecture (Client → Load Balancer → URL Service, splitting into a red write path with Redis counter and PostgreSQL and a green read path with Redis cache, read replica and CDN/Edge), Core Entities & API (icons for Original URL, Short URL, User and coloured POST/GET endpoint boxes), Short-Code Generation (three options: URL prefix rejected in red, hash + Base62 risky in orange, counter + Base62 preferred in green, and counter batching across four servers), Fast Redirects (cache hit/miss flow with 302 badges and a latency table), Why 302 instead of 301 (red vs green), Scaling to 1B URLs (storage estimate, replication, counter coordination) and Staff-Level Discussion Points (two icon columns). A dark navy "What Interviewers Look For" band with Mid-Level, Senior and Staff+ columns, and a Key Takeaway footer with hashtags.

---
Title: ConcurrentHashMap internals (Java 8+, dense square)
Sample: ConcurrentHashMapInternals.jpg
Keywords: java, concurrenthashmap, concurrency, thread safety, cas, buckets, red-black tree, collections, internals, multithreading
Template: concept
Topic: How ConcurrentHashMap works internally in Java 8+
Image prompt: A dense square (1:1) cheat sheet on a white background with a dark navy header band: the title "ConcurrentHashMap Internals (Java 8+)" in white with "Internals" in yellow, a Java cup logo on the right and the strap "High performance. Thread-safe. Built for Concurrency." Eight rounded cards in a three-row grid, each with a navy pill heading: Why ConcurrentHashMap? (HashMap and Hashtable with red crosses, ConcurrentHashMap with a green check and one line each), Internal Structure (a Node<K,V>[] table of buckets 0 to n-1 with linked Node boxes and one bucket grown into a pink TreeNode red-black tree), Key Concepts (three icon rows: bucket-level locking, CAS compare-and-swap, volatile variables), How put() works step by step (four numbered green circles: hash the key with key.hashCode() → spread() → index = (n-1) & hash, locate the bucket, insert by CAS when empty, lock only that bucket on a collision and convert to a red-black tree above 8 nodes), Handling Collisions (linked list vs red-black tree, "O(n) → O(log n)"), Resize Operation (old table to a 2x new table with transfer arrows), When to use? (four green-check bullets) and a yellow Pro Tip box. A navy footer with a rocket "Key Takeaway" and "Master the Internals. Write Scalable, High-Performance Code."

---
Title: Java Optional get vs orElse vs orElseGet vs orElseThrow (table poster)
Sample: Options.png
Keywords: java, optional, orelse, orelseget, orelsethrow, get, null handling, lazy evaluation, eager evaluation, best practices
Template: comparison
Topic: Java Optional methods compared: get(), orElse(), orElseGet() and orElseThrow()
Image prompt: A tall portrait (2:3) comparison table poster with a dark navy title band "Java Optional Methods – get() vs orElse() vs orElseGet() vs orElseThrow()" and the strap "Handle nulls the smart way!". Four coloured column headers: Method, Definition, Usage / Behavior, Example. Four full-width rows, each tinted in its method's colour (purple get(), blue orElse(), green orElseGet(), orange orElseThrow()): the method name in large coloured monospace with an icon and a one-line summary (directly gets the value; default with eager evaluation; computed value with lazy evaluation; throws an exception), a short definition, a small behaviour flowchart (Optional → Value Present → Return Value / Value Absent → throw, default, run supplier or throw from supplier) with a one-line warning or tip, and a monospace code example with comments plus a tinted "Use Case" box. Below, two navy-headed panels side by side: Key Takeaways (four icon bullets) and When to Use What? (four coloured method pills with arrows to one-line answers). A pale yellow footer strip with a lightbulb: "Remember: Prefer orElseGet() and orElseThrow() in production code".

---
Title: Angular 21 what's new for developers (square feature grid)
Sample: angular21-adv.jpg
Keywords: angular, angular 21, release, new features, signals, zoneless, vitest, standalone components, frontend, typescript
Template: universal
Topic: What's new in Angular 21 for developers
Image prompt: A square (1:1) release highlights poster on a white background with red dot patterns in the corners and red curved shapes at the bottom. A large red Angular shield logo beside "ANGULAR 21" in bold navy and red letters, the strap "FASTER. SIMPLER. SMARTER." in spaced capitals, and a red pill "What's New for Developers?". A 3 x 3 grid of nine white rounded cards with soft shadows, each with a large flat coloured icon on the left, a numbered coloured capital heading and two lines of text: 1 Zoneless Change Detection (speedometer), 2 Signals as Core Reactivity (atom), 3 Signal Forms (Experimental) (form with a check), 4 Vitest as Default Test Runner (flask), 5 HttpClient Auto-Provided (globe), 6 Angular ARIA (Accessibility) (accessibility figure), 7 Standalone Components (puzzle piece), 8 Performance & Build Improvements (rising bar chart), 9 Better Developer Experience (code brackets). A dark navy footer banner with a red rocket: "Angular 21 is a big step towards a faster, more modern and developer-friendly future!" with "developer-friendly" in red.

---
Title: Java Collections interview cheat sheet (six numbered panels)
Sample: collecions.png
Keywords: java, collections, interview, list, set, queue, map, hashmap, arraylist, treeset, time complexity, cheat sheet
Template: learning
Topic: Java Collections interview cheat sheet: hierarchy, differences and tricky questions
Image prompt: A very dense tall portrait (2:3) interview cheat sheet. A black header with a Java logo, "JAVA COLLECTIONS" in huge white and yellow condensed capitals, "INTERVIEW CHEAT SHEET" in a purple ribbon between yellow chevrons, the italic strap "Essential Concepts, Key Differences & Tricky Questions" and a dashed yellow box "Master Collections, Crack Interviews!". Six numbered sections with navy number tabs and blue outlined panels: 1 Collections Framework Hierarchy (a tree: Iterable → Collection → List, Set, Queue and a separate Map, each with its classes in tinted boxes), with a Key Takeaway box and a "Why Map doesn't extend Collection?" box beside it; 2 List vs Set vs Queue vs Map (a table with icon row labels: Definition, Order, Duplicates, Null Elements, Access, Example Use Cases, Examples); 3 Important Differences (three green vs pink "VS" pairs: ArrayList vs LinkedList, HashMap vs ConcurrentHashMap, HashSet vs TreeSet); 4 Internal Working (HashMap key → hashCode() → index → bucket diagram, ArrayList indexed cells E0..En, short bullets); 5 Common Tricky Questions (five Q and A rows with icons); 6 Time Complexity (a row of operations with large O(1) and O(log n) values). A black footer with three parts: a medal "Golden Rule", a lightbulb "Understand. Practice. Implement." and "Save this post & share with your fellow developers!".

---
Title: Transaction management in Spring Boot (dark neon architecture)
Sample: spring-tx-arch.jpg
Keywords: spring boot, spring, transaction, transactional, acid, propagation, isolation, rollback, commit, database, backend, java
Template: architecture
Topic: Transaction management in Spring Boot with @Transactional
Image prompt: A square (1:1) dark navy poster with glowing neon card borders. Header: a green hexagon Spring leaf logo, "TRANSACTION MANAGEMENT" in large white and lime capitals, "IN SPRING BOOT" in yellow, a glowing database with a green shield on the right, and the strap "Ensure Data Consistency, Prevent Partial Updates. Build Reliable Applications." Nine cards in a 3 x 3 grid: What is a Transaction? (definition plus four ACID icons: Atomicity, Consistency, Isolation, Durability), How it Works (Client → Service @Transactional → Database with numbered steps begin, execute, commit or rollback), Why it Matters? (four green checks), Example: @Transactional (an OrderService code panel with an orange note "If an exception occurs, Spring rolls back the transaction"), Key Properties (a table: propagation, isolation, readOnly, timeout, rollbackFor, noRollbackFor), Example: Custom Properties (code with Propagation.REQUIRED and Isolation.READ_COMMITTED and a yellow tip "Use @Transactional at the service layer"), Propagation Types (seven blue pills REQUIRED to NESTED with one line each), Success vs Failure (green COMMIT column vs red ROLLBACK column and a "No Partial Updates!" database), and Behind the Scenes (five numbered steps: AOP proxy, DataSource connection, auto-commit off, run, commit or roll back). A footer: "TRANSACTIONS = RELIABILITY + CONSISTENCY + PEACE OF MIND" and four hashtag pills.

---
Title: Java Stream API with example (hand-drawn friendly portrait)
Sample: streamAPi.jpg
Keywords: java, stream api, streams, java 8, functional programming, filter, map, collect, lambda, interview
Template: workflow
Topic: Java Stream API explained with an example
Image prompt: A tall portrait (2:3) friendly cheat sheet on an off-white paper texture with hand-drawn dashed outlines. The title "Java Stream API" in big black letters with "(With Example)" in a handwritten font. Section headings sit on yellow or lilac highlighter strokes: What is Java Stream API? (two short paragraphs with "what" and "how" in bold, and a cartoon developer at a laptop with a plant and a "</>" speech bubble), Key Features (five green-check rows: Functional, Declarative, Lazy, Can be Parallel, Concise, with a small document illustration), How it Works? (five pastel boxes joined by arrows: Collection → Stream → Intermediate Operations (lazy) → Terminal Operation → Result), Example Code (a numbered 19-line Java listing in monospace that filters names starting with "J", maps to upper case, removes duplicates, sorts and collects), Common Operations (a two-column table: filter(), map(), distinct(), sorted(), count(), forEach(), collect()), Quick Facts (four bullets) and Interview Tip (one paragraph with a second cartoon developer).
