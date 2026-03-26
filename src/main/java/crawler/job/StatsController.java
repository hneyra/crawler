package crawler.job;

import crawler.model.DiscoveredUrlRepository;
import crawler.model.ExtractedItem;
import crawler.model.ExtractedItemRepository;
import crawler.model.Site;
import crawler.model.SiteRepository;
import crawler.model.UrlStatus;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
public class StatsController {

    private final SiteRepository siteRepository;
    private final DiscoveredUrlRepository discoveredUrlRepository;
    private final ExtractedItemRepository extractedItemRepository;

    public StatsController(SiteRepository siteRepository,
                           DiscoveredUrlRepository discoveredUrlRepository,
                           ExtractedItemRepository extractedItemRepository) {
        this.siteRepository = siteRepository;
        this.discoveredUrlRepository = discoveredUrlRepository;
        this.extractedItemRepository = extractedItemRepository;
    }

    @GetMapping("/sites")
    public List<Map<String, Object>> listSites() {
        return siteRepository.findAll().stream().map(site -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", site.getId());
            m.put("name", site.getName());
            m.put("baseUrl", site.getBaseUrl());
            m.put("enabled", site.isEnabled());
            return m;
        }).toList();
    }

    @GetMapping("/site/{id}")
    public ResponseEntity<Map<String, Object>> siteStats(@PathVariable Long id) {
        return siteRepository.findById(id)
                .map(site -> {
                    Map<String, Object> stats = new LinkedHashMap<>();
                    stats.put("siteId", site.getId());
                    stats.put("siteName", site.getName());

                    long total = discoveredUrlRepository.countByCategory_Site_Id(id);
                    long pending = discoveredUrlRepository.countByCategory_Site_IdAndStatus(id, UrlStatus.PENDING);
                    long inProgress = discoveredUrlRepository.countByCategory_Site_IdAndStatus(id, UrlStatus.IN_PROGRESS);
                    long completed = discoveredUrlRepository.countByCategory_Site_IdAndStatus(id, UrlStatus.COMPLETED);
                    long failed = discoveredUrlRepository.countByCategory_Site_IdAndStatus(id, UrlStatus.FAILED);
                    long extractedItems = extractedItemRepository.countBySiteId(id);

                    Instant lastCrawl = discoveredUrlRepository.findLastAttemptBySiteId(id)
                            .orElse(null);

                    stats.put("totalUrls", total);
                    stats.put("pending", pending);
                    stats.put("inProgress", inProgress);
                    stats.put("completed", completed);
                    stats.put("failed", failed);
                    stats.put("extractedItems", extractedItems);
                    stats.put("lastCrawl", lastCrawl);

                    return ResponseEntity.ok(stats);
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/items")
    public List<Map<String, Object>> recentItems(
            @RequestParam(defaultValue = "50") int limit,
            @RequestParam(required = false) Long siteId) {

        List<ExtractedItem> items;
        if (siteId != null) {
            items = extractedItemRepository.findBySiteIdOrderByExtractedAtDesc(
                    siteId, PageRequest.of(0, limit));
        } else {
            items = extractedItemRepository.findByOrderByExtractedAtDesc(
                    PageRequest.of(0, limit));
        }

        return items.stream().map(item -> {
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("id", item.getId());
            m.put("title", item.getTitle());
            m.put("url", item.getDiscoveredUrl().getUrl());
            m.put("siteName", item.getSite().getName());
            m.put("rawSnapshotPath", item.getRawSnapshotPath());
            m.put("extractedAt", item.getExtractedAt());
            m.put("properties", item.getProperties());
            return m;
        }).toList();
    }

}
