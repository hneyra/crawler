# Config Module

## Purpose

Application configuration: properties binding, async executors, and runtime site data initialization from YAML.

## Key Classes

| Class | Role |
|-------|------|
| `CrawlerProperties` | `@ConfigurationProperties(prefix = "crawler")` — all tunable parameters |
| `AsyncConfig` | Thread pool for `@Async` tasks + async `JobLauncher` for Spring Batch |
| `DataInitializer` | `CommandLineRunner`: loads `sites-config.yml` and seeds DB on startup |
| `SitesConfig` | POJO: wrapper for list of `SiteDefinition` |
| `SiteDefinition` | POJO: site YAML config (name, baseUrl, categories, extractionConfig) |
| `CategoryDefinition` | POJO: category YAML config (name, url, paginationType, selectors) |

## Configuration Properties (`crawler.*`)

| Property | Default | Description |
|----------|---------|-------------|
| `raw-data-dir` | — | Filesystem path for HTML/JSON snapshots |
| `discovery-max-pages` | 50 | Max pages to crawl per category |
| `extraction-batch-size` | 10 | URLs processed per extraction batch |
| `renderer-base-url` | `http://localhost:3000` | Playwright renderer service URL |
| `retry-max-retries` | 3 | Max retry attempts for failed URLs |
| `retry-interval-ms` | 600000 (10 min) | Interval between retry scheduler runs |

## DataInitializer Behavior

1. On startup, checks for `sites-config.yml` in working directory
2. If missing: creates example config file with sample site definition
3. Reads YAML config using Jackson `YAMLFactory`
4. For each site: skips if `siteRepository.findByName()` finds existing, otherwise creates site + categories
5. Runs once per application startup

## AsyncConfig Thread Pools

- **discoveryExecutor**: core=2, max=4, queue=50 — used by `DiscoveryAsyncLauncher`
- **jobLauncher**: `SimpleAsyncTaskExecutor` — for non-blocking batch job launch

## Important Details

- `sites-config.yml` is created in CWD (side effect at startup) — mock `DataInitializer` in tests
- `SiteDefinition` includes `ExtractionConfig` directly (shared model class)
- `CategoryDefinition.paginationType` is the `PaginationType` enum
- Example config file has Spanish site names (MiEcommerce, Celulares, Laptops)
