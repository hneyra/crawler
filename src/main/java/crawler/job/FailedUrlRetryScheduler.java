package crawler.job;

import crawler.config.CrawlerProperties;
import crawler.model.DiscoveredUrlRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class FailedUrlRetryScheduler {

    private static final Logger log = LoggerFactory.getLogger(FailedUrlRetryScheduler.class);

    private final DiscoveredUrlRepository discoveredUrlRepository;
    private final CrawlerProperties crawlerProperties;

    public FailedUrlRetryScheduler(DiscoveredUrlRepository discoveredUrlRepository,
                                   CrawlerProperties crawlerProperties) {
        this.discoveredUrlRepository = discoveredUrlRepository;
        this.crawlerProperties = crawlerProperties;
    }

    @Scheduled(fixedDelayString = "#{${crawler.retry-interval-minutes} * 60 * 1000}")
    @Transactional
    public void retryFailedUrls() {
        int maxRetries = crawlerProperties.getRetryMaxRetries();
        int reset = discoveredUrlRepository.resetFailedUrls(maxRetries);
        if (reset > 0) {
            log.info("Retry scheduler: reset {} FAILED URLs to PENDING (maxRetries={})",
                    reset, maxRetries);
        }
    }

}
