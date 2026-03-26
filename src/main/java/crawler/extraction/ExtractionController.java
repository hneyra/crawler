package crawler.extraction;

import crawler.model.Site;
import crawler.model.SiteRepository;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/extraction")
public class ExtractionController {

    private final ExtractionAsyncLauncher asyncLauncher;
    private final SiteRepository siteRepository;

    public ExtractionController(ExtractionAsyncLauncher asyncLauncher,
                                SiteRepository siteRepository) {
        this.asyncLauncher = asyncLauncher;
        this.siteRepository = siteRepository;
    }

    @PostMapping("/site/{id}")
    public ResponseEntity<String> extractSite(@PathVariable Long id) {
        Site site = siteRepository.findById(id).orElse(null);
        if (site == null) {
            return ResponseEntity.notFound().build();
        }

        asyncLauncher.launchExtraction(id);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body("Extraction started for site: " + site.getName());
    }

}
