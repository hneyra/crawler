package crawler.discovery;

import crawler.model.Category;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class DiscoveryAsyncLauncher {

    private static final Logger log = LoggerFactory.getLogger(DiscoveryAsyncLauncher.class);

    private final ListDiscoveryService listDiscoveryService;

    public DiscoveryAsyncLauncher(ListDiscoveryService listDiscoveryService) {
        this.listDiscoveryService = listDiscoveryService;
    }

    @Async("discoveryExecutor")
    public void launchCategoryDiscovery(Category category) {
        try {
            listDiscoveryService.discoverCategory(category);
        } catch (Exception e) {
            log.error("Discovery failed for category '{}': {}", category.getName(), e.getMessage(), e);
        }
    }

}
