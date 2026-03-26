# Extraction Module - Skills

## Common Tasks

### Add a new extraction strategy
1. Add value to `DetailStrategy` enum
2. Add routing in `DetailExtractionService.processBatch()` (the `useAjax` check)
3. Implement `processUrlWith{Strategy}()` method
4. Add fields to `ExtractionConfig` if needed
5. Add Flyway migration if config structure changes
6. Add unit test in `DetailExtractionServiceUnitTest`

### Add a new field extraction source
- Add a new `extractFrom{Source}()` private method
- Call it from `processUrlWithJsoup()` and/or `processUrlWithPlaywright()`
- All extraction methods write to a shared `Map<String, Object> properties`

### Modify how AJAX responses are handled
- Edit `processUrlWithPlaywright()` in `DetailExtractionService`
- AJAX responses come from `PlaywrightClient.RenderResponse.interceptedResponses()`
- Each response has `url`, `status`, `body` fields

## Key Interactions
```
ExtractionController
  -> ExtractionAsyncLauncher (@Async)
    -> DetailExtractionService.processBatch(siteId)
      -> DiscoveredUrlRepository (fetch PENDING batch, mark IN_PROGRESS)
      -> Jsoup.connect() OR PlaywrightClient.render()
      -> RawStorageService.saveHtml() / saveJson()
      -> extractFromHtml() / extractFromScripts() / extractJsonLd() / extractWithJsonPaths()
      -> ExtractedItemRepository.save()
      -> DiscoveredUrlRepository.save() (mark COMPLETED/FAILED)
```

## Tests to run after changes
```bash
./gradlew test --tests "crawler.extraction.*"
```
