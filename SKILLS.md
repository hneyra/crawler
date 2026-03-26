# SKILLS.md - Agent Optimization Guide

## Task Routing

Use this guide to select the right agent/approach for common tasks on this project.

### Quick Search (use Glob/Grep directly)

| Task | Tool | Example |
|------|------|---------|
| Find a class by name | `Glob` | `**/*Service.java` |
| Find where an entity is used | `Grep` | pattern `DiscoveredUrl` type `java` |
| Find a REST endpoint | `Grep` | pattern `@PostMapping\|@GetMapping` type `java` |
| Find SQL migrations | `Glob` | `**/db/migration/*.sql` |
| Find test files | `Glob` | `**/test/**/*Test.java` |

### Exploration Agent (use for broad understanding)

- Understanding how discovery or extraction works end-to-end
- Tracing data flow across modules (e.g., "how does a URL go from discovery to extraction?")
- Reviewing all REST endpoints and their interactions
- Understanding the Playwright renderer integration

### General-Purpose Agent (use for multi-step implementation)

- Adding a new extraction strategy
- Adding a new REST endpoint with service + repository changes
- Modifying the batch job pipeline
- Adding new Flyway migrations + entity changes
- Writing new integration tests (requires reading multiple modules)

## Module Expertise Matrix

When working on a task, these are the files you most likely need:

### Adding a new site/category configuration
- `src/main/java/crawler/config/SiteDefinition.java`
- `src/main/java/crawler/config/CategoryDefinition.java`
- `src/main/java/crawler/config/DataInitializer.java`
- `sites-config.yml` (runtime)

### Modifying discovery logic
- `src/main/java/crawler/discovery/ListDiscoveryService.java` (core logic)
- `src/main/java/crawler/discovery/PlaywrightClient.java` (renderer integration)
- `src/main/java/crawler/model/PaginationType.java` (if adding new pagination)
- `src/test/java/crawler/discovery/ListDiscoveryServiceUnitTest.java`
- `src/test/java/crawler/integration/ListDiscoveryIntegrationTest.java`

### Modifying extraction logic
- `src/main/java/crawler/extraction/DetailExtractionService.java` (core logic)
- `src/main/java/crawler/model/ExtractionConfig.java` (config structure)
- `src/main/java/crawler/model/DetailStrategy.java` (if adding new strategy)
- `src/test/java/crawler/extraction/DetailExtractionServiceUnitTest.java`

### Modifying the database schema
- `src/main/resources/db/migration/` (add new V{N}__ migration)
- Entity in `src/main/java/crawler/model/` (update JPA mapping)
- Repository interface if new queries needed
- `src/test/java/crawler/integration/RepositoryIntegrationTest.java` (test new queries)

### Modifying batch job behavior
- `src/main/java/crawler/job/CrawlJobConfig.java` (job/step definitions)
- `src/main/java/crawler/job/DiscoveryTasklet.java` or `ExtractionTasklet.java`
- `src/main/java/crawler/job/FailedUrlRetryScheduler.java` (retry logic)

### Modifying the renderer service
- `renderer/src/renderer.ts` (Playwright logic)
- `renderer/src/index.ts` (Express routes)
- `renderer/package.json` (dependencies)

### Adding REST endpoints
- Controller in appropriate package (`discovery`, `extraction`, or `job`)
- Reuse existing services and repositories
- Follow existing patterns: return `Map<>` or simple DTOs

## Testing Patterns

### Unit tests (fast, no infrastructure)
```java
@ExtendWith(MockitoExtension.class)
class MyServiceTest {
    @Mock DependencyRepository repo;
    @InjectMocks MyService service;
}
```

### Integration tests (Testcontainers PostgreSQL)
```java
@DataJpaTest
@AutoConfigureTestDatabase(replace = NONE)
@Testcontainers
@ActiveProfiles("test")
class MyRepositoryTest {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
}
```

### Integration tests with HTTP mocking (WireMock)
```java
@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class MyIntegrationTest {
    @Container @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
        .options(wireMockConfig().dynamicPort()).build();

    @MockBean DataInitializer dataInitializer; // suppress startup side effects
}
```

## Code Style

- No Lombok — manual getters/setters on entities
- Records for immutable DTOs (see `PlaywrightClient` inner records)
- Package-private access for utility static methods (testable from same package)
- SLF4J + MDC for structured logging
- Direct `Map<>` returns from controllers (no wrapper DTOs)
- `List.of()` and `Map.of()` for immutable default values in config classes
