package crawler.extraction;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
public class ExtractionAsyncLauncher {

    private static final Logger log = LoggerFactory.getLogger(ExtractionAsyncLauncher.class);

    private final DetailExtractionService detailExtractionService;

    public ExtractionAsyncLauncher(DetailExtractionService detailExtractionService) {
        this.detailExtractionService = detailExtractionService;
    }

    @Async("discoveryExecutor")
    public void launchExtraction(Long siteId) {
        try {
            detailExtractionService.processBatch(siteId);
        } catch (Exception e) {
            log.error("Extraction failed for site {}: {}", siteId, e.getMessage(), e);
        }
    }

}
