# Discovery Module

## Purpose

Discovers product/item URLs from category listing pages. Two strategies based on `PaginationType`:

- **STATIC / PAGE_PARAM / CURSOR** -> `discoverWithJsoup()` — fetches HTML via Jsoup, follows pagination links
- **SCROLL_AJAX** -> `discoverWithPlaywright()` — renders page via headless Chromium, scrolls to load all items

## Key Classes

| Class | Role |
|-------|------|
| `ListDiscoveryService` | Core discovery logic: fetches pages, extracts links, deduplicates, persists |
| `PlaywrightClient` | WebClient wrapper calling the Node.js renderer service at `/render` |
| `DiscoveryAsyncLauncher` | `@Async` wrapper to run discovery without blocking the caller |
| `DiscoveryController` | REST endpoints: `POST /api/discovery/category/{id}`, `POST /api/discovery/site/{id}` |

## Data Flow

1. Receive category -> check `PaginationType`
2. Fetch page HTML (Jsoup direct or Playwright render)
3. Select links via `category.itemLinkSelector` CSS selector
4. For each link: normalize URL (strip fragment), SHA-256 hash, check dedup in DB, persist as `PENDING`
5. If pagination: find next page URL and repeat (up to `discovery-max-pages`)

## Important Details

- URL normalization via `UrlUtils.normalizeUrl()` and hashing via `HashUtils.sha256()` (from `crawler.support`)
- Page fetching with Cloudflare fallback delegated to `PageFetcher` (from `crawler.support`)
- `findNextPageUrl` checks in order: custom `nextPageSelector`, `a[rel=next]`, heuristic text matching ("Next", "Siguiente", "›", "»")
- `PlaywrightClient` uses records: `RenderRequest`, `RenderResponse`, `InterceptedResponse`
- `PlaywrightClient.RenderRequest` has factory methods: `scrolling()`, `withIntercept()`, `scrollingWithIntercept()`
- MDC keys set during discovery: `siteId`, `categoryId`, `url`

## Testing

- **Unit tests** (`ListDiscoveryServiceUnitTest`): tests `UrlUtils.normalizeUrl()` and `HashUtils.sha256()`, Playwright routing, URL save/dedup logic using Mockito
- **Integration tests** (`ListDiscoveryIntegrationTest`): full flow with WireMock serving HTML pages + Testcontainers PostgreSQL

## Dependencies

- `crawler.model` — `Category`, `DiscoveredUrl`, `DiscoveredUrlRepository`, `PaginationType`, `UrlStatus`
- `crawler.config` — `CrawlerProperties` (for `discoveryMaxPages`)
- `crawler.support` — `PageFetcher`, `HashUtils`, `UrlUtils`
- External: Jsoup, Spring WebFlux (WebClient)
