# CHANGES

## Refactoring: Eliminate Code Duplication & Introduce `crawler.support` Package

### Summary

Extracted duplicated code across the `discovery`, `extraction`, and `storage` modules into a new shared `crawler.support` package. This eliminates copy-pasted logic, centralizes cross-cutting utilities, and improves maintainability following Single Responsibility Principle.

### New Package: `crawler.support`

| Class | Responsibility | Replaces |
|-------|---------------|----------|
| `HashUtils` | SHA-256 hashing utility | Duplicate `sha256()` in `ListDiscoveryService` and `RawStorageService` |
| `UrlUtils` | URL normalization and resolution | `normalizeUrl()` from `ListDiscoveryService`, duplicate `resolveUrl()` from `OnclickLinkExtractor` and `DataAttributeLinkExtractor` |
| `PageFetcher` | Jsoup page fetching with Cloudflare detection and Playwright fallback | Duplicate `fetchDocument()` + `renderDocument()` in `ListDiscoveryService` and `DetailExtractionService` |

### Changes by File

#### New Files
- `src/main/java/crawler/support/HashUtils.java` — static `sha256(String)` method
- `src/main/java/crawler/support/UrlUtils.java` — static `normalizeUrl(String)` and `resolveUrl(String, String)` methods
- `src/main/java/crawler/support/PageFetcher.java` — Spring `@Component` with `fetch(url)`, `fetch(url, waitForSelector)`, and static `politenessDelay(millis)`
- `src/main/java/crawler/support/package-info.java`

#### Modified Files

**`ListDiscoveryService`**
- Removed: `fetchDocument()`, `renderDocument()`, `normalizeUrl()`, `sha256()`, `sleep()`
- Now uses: `PageFetcher.fetch()`, `UrlUtils.normalizeUrl()`, `HashUtils.sha256()`, `PageFetcher.politenessDelay()`
- Constructor updated to accept `PageFetcher` dependency

**`DetailExtractionService`**
- Removed: `fetchDocument()`, `renderDocument()`, `sleep()`
- Now uses: `PageFetcher.fetch()`, `PageFetcher.politenessDelay()`
- Constructor updated to accept `PageFetcher` dependency

**`RawStorageService`**
- Removed: private `sha256()` method
- Now uses: `HashUtils.sha256()`

**`OnclickLinkExtractor`**
- Removed: private `resolveUrl()` method
- Now uses: `UrlUtils.resolveUrl()`

**`DataAttributeLinkExtractor`**
- Removed: private `resolveUrl()` method
- Now uses: `UrlUtils.resolveUrl()`

**`PageFetcher`**
- Centralizes User-Agent string as a constant (`USER_AGENT`)
- Centralizes Jsoup timeout (15s) and Playwright timeout (30s) as constants
- Handles both with-selector and without-selector Playwright fallback via method overloading

#### Test Updates
- `ListDiscoveryServiceUnitTest` — references `UrlUtils.normalizeUrl()` and `HashUtils.sha256()` instead of `ListDiscoveryService` static methods; mocks `PageFetcher`
- `DetailExtractionServiceUnitTest` — mocks `PageFetcher` in constructor
- `ListDiscoveryIntegrationTest` — uses `HashUtils.sha256()` for hash verification

### Duplication Eliminated

| Duplicated Code | Occurrences Before | After |
|-----------------|-------------------|-------|
| `sha256()` method | 2 (discovery + storage) | 1 (`HashUtils`) |
| `fetchDocument()` + Cloudflare fallback | 2 (discovery + extraction) | 1 (`PageFetcher`) |
| `renderDocument()` | 2 (discovery + extraction) | 1 (`PageFetcher`) |
| `resolveUrl()` | 2 (onclick + data-attribute extractors) | 1 (`UrlUtils`) |
| `sleep()` / politeness delay | 2 (discovery + extraction) | 1 (`PageFetcher.politenessDelay()`) |
| User-Agent string literal | 2 (discovery + extraction) | 1 constant (`PageFetcher.USER_AGENT`) |
| `normalizeUrl()` | 1 (discovery, but misplaced) | 1 (`UrlUtils`, proper location) |

### No Breaking Changes

- All public API endpoints unchanged
- Database schema unchanged
- Configuration properties unchanged
- No library version changes
- No Java or Spring Boot version changes
