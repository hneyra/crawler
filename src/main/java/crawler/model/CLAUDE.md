# Model Module

## Purpose

JPA entities, enums, and Spring Data repositories. Defines the persistence layer and database schema.

## Entities

| Entity | Table | Key Fields |
|--------|-------|------------|
| `Site` | `site` | id, name, baseUrl, enabled, politenessDelayMs, extractionConfig (JSONB), createdAt |
| `Category` | `category` | id, site (FK), name, url, paginationType, itemLinkSelector, nextPageSelector, enabled |
| `DiscoveredUrl` | `discovered_url` | id, category (FK), url, urlHash (unique), status, retryCount, lastAttemptAt, createdAt |
| `ExtractedItem` | `extracted_item` | id, discoveredUrl (FK), site (FK), title, properties (JSONB), rawSnapshotPath, extractedAt |

## Enums

| Enum | Values | Used By |
|------|--------|---------|
| `PaginationType` | `PAGE_PARAM`, `CURSOR`, `SCROLL_AJAX` | Category — selects discovery strategy |
| `DetailStrategy` | `HTML`, `SCRIPT_JSON`, `AJAX` | ExtractionConfig — selects extraction strategy |
| `UrlStatus` | `PENDING`, `IN_PROGRESS`, `COMPLETED`, `FAILED` | DiscoveredUrl — tracks processing lifecycle |

## Repositories

| Repository | Key Methods |
|------------|-------------|
| `SiteRepository` | `findByName(String)` |
| `CategoryRepository` | `findBySiteIdAndEnabledTrue(Long)` |
| `DiscoveredUrlRepository` | `findByUrlHash`, `findByCategory_Site_IdAndStatus` (pageable), `countBy*`, `resetFailedUrls` (JPQL), `findLastAttemptBySiteId` (JPQL) |
| `ExtractedItemRepository` | `countBySiteId`, `findByOrderByExtractedAtDesc`, `findBySiteIdOrderByExtractedAtDesc` |

## Value Object

| Class | Description |
|-------|-------------|
| `ExtractionConfig` | Not an entity — embedded as JSONB in `Site`. Fields: detailStrategy, fieldSelectors (Map), scriptPatterns (List), jsonPaths (Map), interceptPatterns (List) |

## Important Details

- `Site.extractionConfig` uses `@JdbcTypeCode(SqlTypes.JSON)` for Hibernate JSONB mapping
- `ExtractedItem.properties` also uses `@JdbcTypeCode(SqlTypes.JSON)` but stores a JSON string (not typed)
- `DiscoveredUrl.urlHash` has a unique index — enforces URL deduplication
- All entities use `IDENTITY` generation strategy (PostgreSQL BIGSERIAL)
- `LAZY` fetch on all `@ManyToOne` relationships
- `DiscoveredUrl.resetFailedUrls` is a modifying JPQL query (`@Modifying`)

## Database Migrations

| Migration | Description |
|-----------|-------------|
| `V1__init_schema.sql` | Creates all 4 tables with indexes |
| `V2__add_next_page_selector.sql` | Adds `next_page_selector` to category |
| `V3__add_extraction_config.sql` | Adds `extraction_config` JSONB to site |

## Testing

- **Integration tests** (`RepositoryIntegrationTest`): all repository methods tested with `@DataJpaTest` + Testcontainers PostgreSQL
