# Storage Module

## Purpose

Persists raw HTML and JSON snapshots to the local filesystem. Provides a simple content-addressed storage mechanism.

## Key Classes

| Class | Role |
|-------|------|
| `RawStorageService` | Saves HTML/JSON files with content-addressed naming |

## Storage Layout

```
{raw-data-dir}/
  {siteId}/
    {yyyy-MM-dd}/
      {sha256-of-url}.html
      {sha256-of-url}.json
```

- File name: SHA-256 of the URL (64 hex chars) + extension
- Date directory: `LocalDate.now()` formatted as `yyyy-MM-dd`
- Returned path: always uses forward slashes, relative to `raw-data-dir`

## Public API

```java
String saveHtml(String html, Long siteId, String url) throws IOException
String saveJson(String json, Long siteId, String url) throws IOException
```

Both return relative path string (e.g., `1/2024-06-15/abc123...def.html`).

## Important Details

- Writing the same URL twice on the same day **overwrites** the previous file (same path)
- Different sites create separate directory trees (siteId prefix)
- Uses `Files.createDirectories()` — safe for concurrent creation
- SHA-256 implementation is local to this class (duplicated from `ListDiscoveryService`)
- `raw-data-dir` comes from `CrawlerProperties.rawDataDir`

## Testing

- **Unit tests** (`RawStorageServiceTest`): uses JUnit `@TempDir`, tests extension, path structure, content persistence, idempotency, forward slashes

## Dependencies

- `crawler.config` — `CrawlerProperties` (for `rawDataDir`)
- No external dependencies beyond `java.nio`
