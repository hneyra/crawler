package crawler.job;

import crawler.extraction.DetailExtractionService;
import crawler.model.DiscoveredUrlRepository;
import crawler.model.UrlStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.StepContribution;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.infrastructure.repeat.RepeatStatus;

public class ExtractionTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(ExtractionTasklet.class);

    private final DetailExtractionService detailExtractionService;
    private final DiscoveredUrlRepository discoveredUrlRepository;

    public ExtractionTasklet(DetailExtractionService detailExtractionService,
                             DiscoveredUrlRepository discoveredUrlRepository) {
        this.detailExtractionService = detailExtractionService;
        this.discoveredUrlRepository = discoveredUrlRepository;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        Long siteId = chunkContext.getStepContext()
                .getStepExecution()
                .getJobParameters()
                .getLong("siteId");

        long pending = discoveredUrlRepository.countByCategory_Site_IdAndStatus(
                siteId, UrlStatus.PENDING);

        if (pending == 0) {
            log.info("Extraction step: no PENDING URLs for site {}", siteId);
            return RepeatStatus.FINISHED;
        }

        log.info("Extraction step: {} PENDING URLs for site {}, processing batch...", pending, siteId);
        detailExtractionService.processBatch(siteId);

        long remaining = discoveredUrlRepository.countByCategory_Site_IdAndStatus(
                siteId, UrlStatus.PENDING);

        return remaining > 0 ? RepeatStatus.CONTINUABLE : RepeatStatus.FINISHED;
    }

}
