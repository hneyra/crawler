package crawler.job;

import crawler.discovery.ListDiscoveryService;
import crawler.extraction.DetailExtractionService;
import crawler.model.CategoryRepository;
import crawler.model.DiscoveredUrlRepository;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class CrawlJobConfig {

    @Bean
    public Job crawlJob(JobRepository jobRepository, Step discoveryStep, Step extractionStep) {
        return new JobBuilder("crawlJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(discoveryStep)
                .next(extractionStep)
                .build();
    }

    @Bean
    public Step discoveryStep(JobRepository jobRepository,
                              PlatformTransactionManager transactionManager,
                              ListDiscoveryService listDiscoveryService,
                              CategoryRepository categoryRepository) {
        return new StepBuilder("discoveryStep", jobRepository)
                .tasklet(new DiscoveryTasklet(listDiscoveryService, categoryRepository),
                        transactionManager)
                .build();
    }

    @Bean
    public Step extractionStep(JobRepository jobRepository,
                               PlatformTransactionManager transactionManager,
                               DetailExtractionService detailExtractionService,
                               DiscoveredUrlRepository discoveredUrlRepository) {
        return new StepBuilder("extractionStep", jobRepository)
                .tasklet(new ExtractionTasklet(detailExtractionService, discoveredUrlRepository),
                        transactionManager)
                .build();
    }

}
