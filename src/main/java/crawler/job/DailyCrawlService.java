package crawler.job;

import crawler.model.DiscoveredUrlRepository;
import crawler.model.UrlStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DailyCrawlService {

    private final DiscoveredUrlRepository discoveredUrlRepository;

    public DailyCrawlService(DiscoveredUrlRepository discoveredUrlRepository) {
        this.discoveredUrlRepository = discoveredUrlRepository;
    }

    @Transactional
    public int resetCompletedUrlsForSite(Long siteId) {
        return discoveredUrlRepository.resetCompletedUrlsBySiteId(siteId);
    }

    public long countPendingForSite(Long siteId) {
        return discoveredUrlRepository.countByCategory_Site_IdAndStatus(siteId, UrlStatus.PENDING);
    }
}
