# Discovery Module - Skills

## Common Tasks

### Add a new pagination type
1. Add value to `PaginationType` enum
2. Add routing logic in `ListDiscoveryService.discoverCategory()`
3. Implement fetch method (Jsoup or Playwright-based)
4. Add unit test in `ListDiscoveryServiceUnitTest`
5. Add integration test in `ListDiscoveryIntegrationTest`

### Modify URL normalization
- Edit `ListDiscoveryService.normalizeUrl()` (static, package-private)
- Update unit tests in `ListDiscoveryServiceUnitTest`

### Modify next-page detection
- Edit `ListDiscoveryService.findNextPageUrl()` (private)
- Test indirectly via `ListDiscoveryIntegrationTest` using WireMock

### Modify Playwright rendering options
- Edit `PlaywrightClient.RenderRequest` record and its factory methods
- If renderer API changes: also update `renderer/src/index.ts`

## Key Interactions
```
DiscoveryController
  -> DiscoveryAsyncLauncher (@Async)
    -> ListDiscoveryService.discoverCategory()
      -> Jsoup.connect() OR PlaywrightClient.render()
      -> DiscoveredUrlRepository.save()
```

## Tests to run after changes
```bash
./gradlew test --tests "crawler.discovery.*" --tests "crawler.integration.ListDiscoveryIntegrationTest"
```
