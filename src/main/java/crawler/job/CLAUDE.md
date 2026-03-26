# Job Module

## Purpose

Orchestrates crawl jobs using Spring Batch and provides REST APIs for job management, statistics, and retry scheduling.

## Key Classes

| Class | Role |
|-------|------|
| `CrawlJobConfig` | Spring Batch `@Configuration`: defines `crawlJob` with discovery + extraction steps |
| `DiscoveryTasklet` | Batch tasklet: runs discovery for all enabled categories of a site |
| `ExtractionTasklet` | Batch tasklet: processes pending URLs in batches, returns `CONTINUABLE` while work remains |
| `JobController` | REST: `POST /api/jobs/crawl/{siteId}`, `GET /api/jobs/status/{jobId}` |
| `StatsController` | REST: site listing, per-site stats, extracted items listing |
| `FailedUrlRetryScheduler` | `@Scheduled`: resets FAILED URLs to PENDING at `retry-interval-ms` intervals |

## Batch Job Structure

```
crawlJob
  ├─ discoveryStep (DiscoveryTasklet)
  │    → discovers URLs for all enabled categories
  └─ extractionStep (ExtractionTasklet)
       → extracts data from PENDING URLs in batches
       → repeats until no PENDING URLs remain
```

- Job parameter: `siteId` (Long) — determines which site to crawl
- Uses `RunIdIncrementer` for unique job instance per launch

## REST Endpoints

| Method | Path | Description |
|--------|------|-------------|
| POST | `/api/jobs/crawl/{siteId}` | Launch crawl job, returns job execution ID |
| GET | `/api/jobs/status/{jobId}` | Job status + step execution details |
| GET | `/api/stats/sites` | All sites with id, name, baseUrl, enabled |
| GET | `/api/stats/site/{id}` | URL counts by status, extracted count, last crawl time |
| GET | `/api/stats/items?siteId=&page=&size=` | Paginated extracted items |

## Important Details

- `ExtractionTasklet` returns `RepeatStatus.CONTINUABLE` if pending URLs > 0, `FINISHED` otherwise
- `FailedUrlRetryScheduler` uses `@Scheduled(fixedDelayString)` with `crawler.retry-interval-ms`
- `resetFailedUrls` JPQL query resets status to PENDING where `retryCount < maxRetries`
- `AsyncConfig` provides `TaskExecutorJobLauncher` with `SimpleAsyncTaskExecutor` for non-blocking job launch
- `StatsController` returns maps/lists directly (no dedicated DTOs)

## Dependencies

- `crawler.discovery` — `ListDiscoveryService`
- `crawler.extraction` — `DetailExtractionService`
- `crawler.model` — all repositories for stats queries
- Spring Batch core, Spring `@Scheduled`
