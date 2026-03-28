package crawler.config;

import crawler.model.DiscoveredUrlRepository;
import crawler.model.ExtractedItemRepository;
import crawler.model.SiteRepository;
import crawler.model.UrlStatus;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class CrawlerMetrics {

    public CrawlerMetrics(MeterRegistry registry,
                          SiteRepository siteRepository,
                          DiscoveredUrlRepository discoveredUrlRepository,
                          ExtractedItemRepository extractedItemRepository) {

        Gauge.builder("crawler.sites.total", siteRepository, r -> r.count())
                .description("Total number of configured sites")
                .register(registry);

        for (UrlStatus status : UrlStatus.values()) {
            Gauge.builder("crawler.urls", discoveredUrlRepository,
                            r -> r.countByStatus(status))
                    .tag("status", status.name().toLowerCase())
                    .description("Number of discovered URLs by status")
                    .register(registry);
        }

        Gauge.builder("crawler.items.total", extractedItemRepository, r -> r.count())
                .description("Total number of extracted items")
                .register(registry);
    }
}
