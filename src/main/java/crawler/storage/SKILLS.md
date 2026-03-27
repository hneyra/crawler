# Storage Module - Skills

## Common Tasks

### Change storage layout
- Edit `RawStorageService.save()` private method
- Path structure: `{siteId}/{date}/{hash}{extension}`
- Update unit tests in `RawStorageServiceTest`

### Add new file type support
- Add public method similar to `saveHtml()`/`saveJson()` with appropriate extension
- Call `save()` with the new extension

## Key Interactions
```
DetailExtractionService
  -> RawStorageService.saveHtml(html, siteId, url)
  -> RawStorageService.saveJson(json, siteId, url)
  -> Returns relative path string stored in ExtractedItem.rawSnapshotPath
```

## Tests to run after changes
```bash
./gradlew test --tests "crawler.storage.*"
```
