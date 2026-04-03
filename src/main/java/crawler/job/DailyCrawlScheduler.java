package crawler.job;

import crawler.extraction.DetailExtractionService;
import crawler.model.Site;
import crawler.model.SiteRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class DailyCrawlScheduler {

    private static final Logger log = LoggerFactory.getLogger(DailyCrawlScheduler.class);

    private final SiteRepository siteRepository;
    private final DailyCrawlService dailyCrawlService;
    private final DetailExtractionService detailExtractionService;

    public DailyCrawlScheduler(SiteRepository siteRepository,
                               DailyCrawlService dailyCrawlService,
                               DetailExtractionService detailExtractionService) {
        this.siteRepository = siteRepository;
        this.dailyCrawlService = dailyCrawlService;
        this.detailExtractionService = detailExtractionService;
    }

    @Scheduled(cron = "0 0 7 * * *")
    public void dailyRecrawl() {
        log.info("Daily recrawl started");
        List<Site> sites = siteRepository.findByEnabledTrue();
        for (Site site : sites) {
            try {
                recrawlSite(site);
            } catch (Exception e) {
                log.error("Daily recrawl failed for site '{}': {}", site.getName(), e.getMessage(), e);
            }
        }
        log.info("Daily recrawl finished");
    }

    private void recrawlSite(Site site) {
        int reset = dailyCrawlService.resetCompletedUrlsForSite(site.getId());
        log.info("Daily recrawl: reset {} COMPLETED URLs to PENDING for site '{}'", reset, site.getName());

        if (reset == 0) {
            return;
        }

        long pending;
        do {
            detailExtractionService.processBatch(site.getId());
            pending = dailyCrawlService.countPendingForSite(site.getId());
        } while (pending > 0);

        log.info("Daily recrawl completed for site '{}'", site.getName());
    }
}
