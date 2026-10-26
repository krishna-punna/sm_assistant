package com.smassistant.app.scheduler

/** A group of topics in the technical content type picker. */
data class TopicGroup(val name: String, val topics: List<String>)

/**
 * The technical content types a scheduler can post about (SC-3): 200 topics around Java, Spring
 * and full-stack development, grouped for the picker and searchable by name or group.
 */
object TechTopics {
    val groups: List<TopicGroup> = listOf(
        TopicGroup(
            "Java",
            listOf(
                "Java", "Java 21", "Java 17", "Java Records", "Java Sealed Classes", "Java Pattern Matching",
                "Java Streams API", "Java Collections", "Java Generics", "Java Lambdas", "Java Optional",
                "Java Exceptions", "Java Memory Model", "Java Concurrency", "Java Virtual Threads",
                "CompletableFuture", "Java Executors", "Java NIO", "Java Reflection",
                "Java Modules (JPMS)", "Design Patterns in Java", "SOLID Principles", "Clean Code in Java",
               
            ),
        ),
        TopicGroup(
            "JVM",
            listOf(
                "JVM Internals", "Garbage Collection", "G1 GC", "ZGC", "JIT Compilation", "Class Loading",
                "JVM Performance Tuning", "Heap Dumps and Memory Leaks", "Java Flight Recorder", "GraalVM",
                "GraalVM Native Image", "Project Loom", "Kotlin",
            ),
        ),
        TopicGroup(
            "Spring",
            listOf(
                "Spring", "Spring Boot", "Spring Boot 3", "Spring Framework 6", "Spring Core and IoC",
                "Dependency Injection", "Spring AOP", "Spring MVC", "Spring WebFlux", "Project Reactor",
                "Spring Data JPA", "Spring Data JDBC", "Spring Data MongoDB", "Spring Data Redis",
                "Spring Security", "Spring Security OAuth2", "Spring Authorization Server", "Spring Cloud",
                "Spring Cloud Gateway", "Spring Cloud Config", "Spring Cloud Stream", "Spring Cloud OpenFeign",
                "Spring Kafka", "Spring Batch", "Spring Integration", "Spring Boot Actuator",
                "Spring Boot Testing", "Spring Native", "Spring AI", "Spring Modulith",
            ),
        ),
        TopicGroup(
            "Data and persistence",
            listOf(
                "Hibernate", "JPA", "JDBC", "SQL", "PostgreSQL", "MySQL", "Oracle Database", "MongoDB",
                "Redis", "Cassandra", "Elasticsearch", "DynamoDB", "Database Indexing",
                "Database Transactions", "Database Sharding", "Database Replication", "Flyway", "Liquibase",
                "Connection Pooling (HikariCP)", "Caching Strategies",
            ),
        ),
        TopicGroup(
            "Messaging and streaming",
            listOf(
                "Kafka", "Kafka Streams", "Kafka Connect", "Schema Registry", "RabbitMQ", "ActiveMQ",
                "Apache Pulsar", "Amazon SQS and SNS", "Event Sourcing", "CQRS", "Change Data Capture (Debezium)",
                "Apache Flink",
            ),
        ),
        TopicGroup(
            "Distributed systems and architecture",
            listOf(
                "Distributed Systems", "Microservices", "System Design", "Monolith to Microservices",
                "Domain-Driven Design", "Hexagonal Architecture", "Clean Architecture", "Event-Driven Architecture",
                "API Gateway", "Service Discovery", "Load Balancing", "Circuit Breaker (Resilience4j)",
                "Rate Limiting", "Saga Pattern", "Outbox Pattern", "Distributed Transactions",
                "CAP Theorem", "Consistent Hashing", "Idempotency", "Distributed Caching", "Distributed Locks",
                "Consensus (Raft, Paxos)", "Scalability", "High Availability", "Observability",
            ),
        ),
        TopicGroup(
            "APIs",
            listOf(
                "REST API Design", "GraphQL", "gRPC", "WebSockets", "Server-Sent Events", "OpenAPI and Swagger",
                "API Versioning", "API Security", "HTTP/2 and HTTP/3", "JSON and Jackson",
            ),
        ),
        TopicGroup(
            "Frontend",
            listOf(
                "Angular", "Angular Signals", "Angular Standalone Components", "RxJS", "NgRx", "React",
                "React Hooks", "Next.js", "Redux", "TypeScript", "JavaScript", "ES2024 Features",
                "Node.js", "Tailwind CSS", "Web Performance", "Micro Frontends",
                "Progressive Web Apps", "Web Accessibility", "Frontend Testing",
                "State Management", "Server-Side Rendering",
            ),
        ),
        TopicGroup(
            "Cloud and DevOps",
            listOf(
                "Docker", "Kubernetes", "Helm", "AWS", "Azure", "Google Cloud", "Serverless", "AWS Lambda",
                "Terraform", "CI/CD", "GitHub Actions", "GitOps (Argo CD)", "Git",
                "Maven", "Gradle", "Service Mesh (Istio)", "Prometheus", "Grafana",
                "OpenTelemetry", "Site Reliability Engineering",
            ),
        ),
        TopicGroup(
            "Testing and quality",
            listOf(
                "JUnit 5", "Mockito", "Testcontainers", "Integration Testing", "Contract Testing (Pact)",
                "Test-Driven Development", "Performance Testing (JMeter, Gatling)", "Selenium", "Cypress",
                "Code Review",
            ),
        ),
        TopicGroup(
            "Security",
            listOf(
                "OAuth 2.0", "OpenID Connect", "JWT", "OWASP Top 10", "Keycloak", "Secrets Management (Vault)",
                "TLS and HTTPS", "Zero Trust Architecture",
            ),
        ),
        TopicGroup(
            "AI for developers",
            listOf(
                "Generative AI for Java Developers", "LangChain4j", "RAG (Retrieval-Augmented Generation)",
                "Vector Databases", "Prompt Engineering", "AI Coding Assistants", "Machine Learning Basics",
                "LLM Integration in Spring Boot",
            ),
        ),
    )

    /** Every topic, in picker order. */
    val all: List<String> = groups.flatMap { it.topics }

    /**
     * The groups with only the topics matching [query] (case-insensitive, any word, by topic or group
     * name); a blank query keeps everything, and groups with no match are left out.
     */
    fun search(query: String): List<TopicGroup> {
        val words = query.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return groups
        return groups.mapNotNull { group ->
            val groupMatches = words.all { it in group.name.lowercase() }
            val topics = if (groupMatches) group.topics else group.topics.filter { topic -> words.all { it in topic.lowercase() } }
            topics.takeIf { it.isNotEmpty() }?.let { TopicGroup(group.name, it) }
        }
    }
}
