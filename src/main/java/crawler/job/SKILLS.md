# Job Module - Skills

## Common Tasks

### Add a new batch step
1. Create a new `Tasklet` implementation
2. Register step in `CrawlJobConfig` (StepBuilder)
3. Chain it in the job: `.start(step1).next(step2).next(newStep)`

### Add a new REST endpoint for stats
- Add method to `StatsController`
- Use existing repositories for queries
- Return `Map<>` or `List<>` directly

### Modify retry behavior
- `FailedUrlRetryScheduler.retryFailedUrls()` calls `discoveredUrlRepository.resetFailedUrls(maxRetries)`
- `maxRetries` comes from `CrawlerProperties.retryMaxRetries`
- Interval from `CrawlerProperties.retryIntervalMs`

### Modify async thread pool
- Edit `AsyncConfig.discoveryExecutor()` — controls discovery parallelism
- Edit `AsyncConfig.asyncJobLauncher()` — controls batch job launch

## Key Interactions
```
JobController.launchCrawl(siteId)
  -> AsyncJobLauncher.run(crawlJob, params)
    -> DiscoveryTasklet (runs ListDiscoveryService for all categories)
    -> ExtractionTasklet (runs DetailExtractionService in batches, CONTINUABLE loop)

FailedUrlRetryScheduler (periodic)
  -> DiscoveredUrlRepository.resetFailedUrls()
```

## Tests to run after changes
```bash
./gradlew test --tests "crawler.job.*"
```
