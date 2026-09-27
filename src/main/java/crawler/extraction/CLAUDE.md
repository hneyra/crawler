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
| `HTML` | `processUrl` | Fetches page via `PageFetcher`, extracts fields using CSS selectors |
| `SCRIPT_JSON` | `processUrl` | Parses `<script>` tags with regex patterns, extracts via JsonPath |
| `AJAX` | `processUrlWithPlaywright` | Renders via Playwright, intercepts network responses, extracts via JsonPath |

## Data Flow

1. `processBatch(siteId)` loads site config and fetches up to `extraction-batch-size` PENDING URLs
2. All URLs marked `IN_PROGRESS` via `saveAll()`
3. For each URL:
   - Route to `PageFetcher` or Playwright based on `detailStrategy`
   - Save raw HTML snapshot via `RawStorageService`
   - Extract fields into `Map<String, Object> properties`
   - Extract JSON-LD data if present
   - Save `ExtractedItem` with title, JSON properties, snapshot path
   - Mark URL `COMPLETED` or `FAILED` (increment `retryCount` on failure)
4. Politeness delay between URLs

## Extraction Sources (applied in order)

1. **HTML CSS selectors** — `ExtractionConfig.fieldSelectors` map: `fieldName -> cssSelector`. Selector value may end in `::attr(name)` (e.g. `meta[property=og:image]::attr(content)`) to extract an attribute instead of `.text()`
2. **List field selectors** — `ExtractionConfig.listFieldSelectors` map: `fieldName -> cssSelector` (optionally with `::attr(name)`), matches multiple elements and stores a `List<String>` (e.g. image galleries)
3. **Label/value blocks** — `ExtractionConfig.labelValueBlocks` list of `LabelValueBlockConfig` (containerSelector, itemSelector, labelSelector, valueSelector, targetKey). Scrapes repeated label+value pairs (spec tables) into a nested `Map<String,String>` under `targetKey`, with labels normalized to `snake_case` ASCII keys (`normalizeKey`: strip accents, lowercase, non-alphanumeric runs -> `_`)
4. **Script tag JSON** — `ExtractionConfig.scriptPatterns` regexes match script content, then `jsonPaths` extract values
5. **Regex fields** — `ExtractionConfig.regexFields` map: `fieldName -> regex` (first capturing group, or whole match if none), applied against the full raw HTML string — for values embedded in inline `<script>` blocks that aren't valid JSON (e.g. map coordinates, plain-text dates)
6. **JSON-LD** — `<script type="application/ld+json">` parsed as JSON tree
7. **AJAX responses** — Intercepted network responses queried via `jsonPaths`

## Important Details

- `ExtractionConfig` is stored as JSONB in `site.extraction_config`
- Title falls back to `doc.title()` if not extracted via selectors
- `sanitizeKey(url)` replaces non-alphanumeric chars with `_` for AJAX response property keys
- Intercepted AJAX responses are also saved as `.json` snapshots
- All private extraction methods (`extractFromHtml`, `extractListFields`, `extractLabelValueBlocks`, `extractFromScripts`, `extractRegexFields`, `extractJsonLd`, `extractWithJsonPaths`) operate on a shared `properties` map
- `extractFromHtml`/`extractListFields` skip blank values instead of storing empty strings
- `SelectorSpec` (private record) parses the `::attr(name)` suffix shared by `fieldSelectors` and `listFieldSelectors`
- Example config style: see the `REMAX Perú` site entry in `sites-config.yml` (real estate listing detail page: price/ID/coordinates via `regexFields`, specs table via `labelValueBlocks`, image gallery via `listFieldSelectors`)

## Testing

- **Unit tests** (`DetailExtractionServiceUnitTest`): early exits, status lifecycle, retry increment, Playwright delegation, field extraction from rendered HTML, attribute/list/regex/label-value extraction, and a full REMAX detail-page fixture mirroring `sites-config.yml`
- Uses `.invalid` TLD (RFC-reserved, guaranteed not to resolve) for failure tests

## Dependencies

- `crawler.model` — all entities and repositories
- `crawler.discovery` — `PlaywrightClient` for AJAX strategy
- `crawler.storage` — `RawStorageService` for snapshots
- `crawler.support` — `PageFetcher` for HTML/SCRIPT_JSON fetching with Cloudflare fallback
- `crawler.config` — `CrawlerProperties` (for `extractionBatchSize`)
- External: Jsoup, Jackson ObjectMapper, JsonPath
