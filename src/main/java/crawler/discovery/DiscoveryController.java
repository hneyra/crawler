package crawler.discovery;

import crawler.model.Category;
import crawler.model.CategoryRepository;
import crawler.model.Site;
import crawler.model.SiteRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.scheduling.annotation.Async;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/discovery")
public class DiscoveryController {

    private static final Logger log = LoggerFactory.getLogger(DiscoveryController.class);

    private final DiscoveryAsyncLauncher asyncLauncher;
    private final CategoryRepository categoryRepository;
    private final SiteRepository siteRepository;

    public DiscoveryController(DiscoveryAsyncLauncher asyncLauncher,
                               CategoryRepository categoryRepository,
                               SiteRepository siteRepository) {
        this.asyncLauncher = asyncLauncher;
        this.categoryRepository = categoryRepository;
        this.siteRepository = siteRepository;
    }

    @PostMapping("/category/{id}")
    public ResponseEntity<String> discoverCategory(@PathVariable Long id) {
        Category category = categoryRepository.findByIdWithSite(id).orElse(null);
        if (category == null) {
            return ResponseEntity.notFound().build();
        }

        asyncLauncher.launchCategoryDiscovery(category);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body("Discovery started for category: " + category.getName());
    }

    @PostMapping("/site/{id}")
    public ResponseEntity<String> discoverSite(@PathVariable Long id) {
        Site site = siteRepository.findById(id).orElse(null);
        if (site == null) {
            return ResponseEntity.notFound().build();
        }

        List<Category> categories = categoryRepository.findBySiteIdAndEnabledTrueWithSite(id);
        if (categories.isEmpty()) {
            return ResponseEntity.ok("No enabled categories found for site: " + site.getName());
        }

        for (Category category : categories) {
            asyncLauncher.launchCategoryDiscovery(category);
        }

        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body("Discovery started for " + categories.size()
                        + " categories of site: " + site.getName());
    }

}
