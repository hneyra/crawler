# Extraction Module

## Purpose

Extracts structured data from discovered product URLs. Processes URLs in batches per site with configurable extraction strategies.

## Key Classes

| Class | Role |
|-------|------|
| `DetailExtractionService` | Core extraction logic: batch processing, strategy routing, field extraction |
| `ExtractionAsyncLauncher` | `@Async` wrapper for non-blocking extraction triggers |
| `ExtractionController` | REST endpoint: `POST /api/extraction/site/{id}` |

## Extraction Strategies (`DetailStrategy` enum)

| Strategy | Method | How It Works |
|----------|--------|-------------|
| `HTML` | `processUrlWithJsoup` | Fetches page via Jsoup, extracts fields using CSS selectors |
| `SCRIPT_JSON` | `processUrlWithJsoup` | Parses `<script>` tags with regex patterns, extracts via JsonPath |
| `AJAX` | `processUrlWithPlaywright` | Renders via Playwright, intercepts network responses, extracts via JsonPath |

## Data Flow

1. `processBatch(siteId)` loads site config and fetches up to `extraction-batch-size` PENDING URLs
2. All URLs marked `IN_PROGRESS` via `saveAll()`
3. For each URL:
   - Route to Jsoup or Playwright based on `detailStrategy`
   - Save raw HTML snapshot via `RawStorageService`
   - Extract fields into `Map<String, Object> properties`
   - Extract JSON-LD data if present
   - Save `ExtractedItem` with title, JSON properties, snapshot path
   - Mark URL `COMPLETED` or `FAILED` (increment `retryCount` on failure)
4. Politeness delay between URLs

## Extraction Sources (applied in order)

1. **HTML CSS selectors** — `ExtractionConfig.fieldSelectors` map: `fieldName -> cssSelector`
2. **Script tag JSON** — `ExtractionConfig.scriptPatterns` regexes match script content, then `jsonPaths` extract values
3. **JSON-LD** — `<script type="application/ld+json">` parsed as JSON tree
4. **AJAX responses** — Intercepted network responses queried via `jsonPaths`

## Important Details

- `ExtractionConfig` is stored as JSONB in `site.extraction_config`
- Title falls back to `doc.title()` if not extracted via selectors
- `sanitizeKey(url)` replaces non-alphanumeric chars with `_` for AJAX response property keys
- Intercepted AJAX responses are also saved as `.json` snapshots
- All private extraction methods (`extractFromHtml`, `extractFromScripts`, `extractJsonLd`, `extractWithJsonPaths`) operate on a shared `properties` map

## Testing

- **Unit tests** (`DetailExtractionServiceUnitTest`): early exits, status lifecycle, retry increment, Playwright delegation, field extraction from rendered HTML
- Uses `.invalid` TLD (RFC-reserved, guaranteed not to resolve) for failure tests

## Dependencies

- `crawler.model` — all entities and repositories
- `crawler.discovery` — `PlaywrightClient` for AJAX strategy
- `crawler.storage` — `RawStorageService` for snapshots
- `crawler.config` — `CrawlerProperties` (for `extractionBatchSize`)
- External: Jsoup, Jackson ObjectMapper, JsonPath
