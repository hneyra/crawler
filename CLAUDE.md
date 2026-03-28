# CLAUDE.md - Crawler Project

## Project Overview

Web crawler with two runtime components:

- **Backend** (Java 25, Spring Boot 4.0.5) — discovery, extraction, batch jobs, REST API
- **Renderer** (Node.js, Playwright) — headless Chromium service for JS-heavy pages

Database: PostgreSQL 16 with Flyway migrations.

## Quick Reference

### Build & Run

```bash
# Build backend
./gradlew build

# Run tests (requires Docker for Testcontainers)
./gradlew test

# Start infrastructure (Postgres + Renderer)
docker-compose up -d

# Run application
./gradlew bootRun
```

### Key Paths

| Path | Description |
|------|-------------|
| `src/main/java/crawler/` | Backend source code |
| `src/main/resources/db/migration/` | Flyway SQL migrations (V1-V3) |
| `src/main/resources/application.yaml` | Main configuration |
| `src/test/java/crawler/` | Test code (unit + integration) |
| `src/test/resources/application-test.yaml` | Test profile config |
| `renderer/` | Node.js Playwright renderer service |
| `sites-config.yml` | Runtime site definitions (auto-created if missing) |

### Module Map

| Package | Responsibility |
|---------|---------------|
| `crawler.discovery` | URL discovery from category pages (Jsoup + Playwright) |
| `crawler.extraction` | Data extraction from product pages (HTML, SCRIPT_JSON, AJAX) |
| `crawler.storage` | Raw HTML/JSON snapshot persistence to filesystem |
| `crawler.job` | Spring Batch job config, tasklets, scheduler, REST controllers |
| `crawler.config` | App properties, async config, YAML site loader |
| `crawler.model` | JPA entities, enums, Spring Data repositories |
| `crawler.support` | Shared utilities: `HashUtils`, `UrlUtils`, `PageFetcher` |

### Architecture Patterns

- **Two-phase pipeline**: Discovery (find URLs) -> Extraction (get data)
- **Strategy pattern**: `PaginationType` selects Jsoup vs Playwright for discovery; `DetailStrategy` selects HTML/SCRIPT_JSON/AJAX for extraction
- **Spring Batch**: Orchestrates crawl jobs with `DiscoveryTasklet` -> `ExtractionTasklet`
- **Async execution**: `@Async` launchers + configurable thread pools for non-blocking REST triggers
- **URL deduplication**: SHA-256 hash of normalized URL stored in `discovered_url.url_hash` (unique index)
- **Shared utilities**: `PageFetcher` centralizes Jsoup+Cloudflare+Playwright fallback; `HashUtils` and `UrlUtils` eliminate duplication

### Database Schema

4 tables: `site`, `category`, `discovered_url`, `extracted_item`
URL lifecycle: `PENDING -> IN_PROGRESS -> COMPLETED | FAILED`
Failed URLs retry via `FailedUrlRetryScheduler` up to `retry-max-retries` times.

### REST API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/discovery/category/{id}` | Discover URLs for one category |
| POST | `/api/discovery/site/{id}` | Discover URLs for all enabled categories of a site |
| POST | `/api/extraction/site/{id}` | Extract data from pending URLs for a site |
| POST | `/api/jobs/crawl/{siteId}` | Launch full crawl job (discovery + extraction) |
| GET | `/api/jobs/status/{jobId}` | Check batch job status |
| GET | `/api/stats/sites` | List all sites |
| GET | `/api/stats/site/{id}` | Site statistics (URL counts, extracted items) |
| GET | `/api/stats/items` | Recent extracted items |

### Testing

- **Unit tests**: Mockito-based, no Spring context. Package `crawler.discovery`, `crawler.storage`, `crawler.extraction`
- **Integration tests**: Testcontainers PostgreSQL + WireMock. Package `crawler.integration`
- Test profile: `@ActiveProfiles("test")` loads `application-test.yaml`
- Integration tests mock `DataInitializer` to prevent side-effect file creation

### Common Conventions

- Entities use `@GeneratedValue(strategy = IDENTITY)` (PostgreSQL BIGSERIAL)
- JSONB columns: `Site.extractionConfig`, `ExtractedItem.properties`
- Logging: SLF4J with MDC keys `siteId`, `categoryId`, `url`
- Politeness delay between HTTP requests per `site.politeness_delay_ms`
- Raw snapshots saved to `{raw-data-dir}/{siteId}/{date}/{sha256}.html`

### Gotchas

- `sites-config.yml` is auto-created at app startup by `DataInitializer` if missing
- `application.yaml` has `raw-data-dir` hardcoded to `E:/crawler-data/` — override per environment
- Renderer service must be running at `crawler.renderer-base-url` for Playwright-based flows
- Flyway migrations use PostgreSQL-specific types (JSONB, BIGSERIAL, TIMESTAMPTZ) — no H2 compatibility
- `@DataJpaTest` integration tests require `@AutoConfigureTestDatabase(replace = NONE)` + Testcontainers
