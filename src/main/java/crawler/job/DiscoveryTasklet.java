package crawler.job;

import crawler.discovery.ListDiscoveryService;
import crawler.model.Category;
import crawler.model.CategoryRepository;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.core.StepContribution;
import org.springframework.batch.core.scope.context.ChunkContext;
import org.springframework.batch.core.step.tasklet.Tasklet;
import org.springframework.batch.repeat.RepeatStatus;

public class DiscoveryTasklet implements Tasklet {

    private static final Logger log = LoggerFactory.getLogger(DiscoveryTasklet.class);

    private final ListDiscoveryService listDiscoveryService;
    private final CategoryRepository categoryRepository;

    public DiscoveryTasklet(ListDiscoveryService listDiscoveryService,
                            CategoryRepository categoryRepository) {
        this.listDiscoveryService = listDiscoveryService;
        this.categoryRepository = categoryRepository;
    }

    @Override
    public RepeatStatus execute(StepContribution contribution, ChunkContext chunkContext) {
        Long siteId = chunkContext.getStepContext()
                .getStepExecution()
                .getJobParameters()
                .getLong("siteId");

        List<Category> categories = categoryRepository.findBySiteIdAndEnabledTrue(siteId);
        log.info("Discovery step: {} enabled categories for site {}", categories.size(), siteId);

        for (Category category : categories) {
            listDiscoveryService.discoverCategory(category);
        }

        return RepeatStatus.FINISHED;
    }

}
