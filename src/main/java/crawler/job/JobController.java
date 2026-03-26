package crawler.job;

import java.util.LinkedHashMap;
import java.util.Map;
import org.springframework.batch.core.job.Job;
import org.springframework.batch.core.job.JobExecution;
import org.springframework.batch.core.job.parameters.JobParameters;
import org.springframework.batch.core.job.parameters.JobParametersBuilder;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.explore.JobExplorer;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/jobs")
public class JobController {

    private final JobLauncher jobLauncher;
    private final Job crawlJob;
    private final JobExplorer jobExplorer;

    public JobController(JobLauncher jobLauncher, Job crawlJob, JobExplorer jobExplorer) {
        this.jobLauncher = jobLauncher;
        this.crawlJob = crawlJob;
        this.jobExplorer = jobExplorer;
    }

    @PostMapping("/crawl/{siteId}")
    public ResponseEntity<Map<String, Object>> launchCrawl(@PathVariable Long siteId) {
        try {
            JobParameters params = new JobParametersBuilder()
                    .addLong("siteId", siteId)
                    .addLong("run.id", System.currentTimeMillis())
                    .toJobParameters();

            JobExecution execution = jobLauncher.run(crawlJob, params);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("jobExecutionId", execution.getId());
            response.put("status", execution.getStatus().toString());
            response.put("siteId", siteId);

            return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
        } catch (Exception e) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @GetMapping("/status/{jobExecutionId}")
    public ResponseEntity<Map<String, Object>> getStatus(@PathVariable Long jobExecutionId) {
        JobExecution execution = jobExplorer.getJobExecution(jobExecutionId);
        if (execution == null) {
            return ResponseEntity.notFound().build();
        }

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("jobExecutionId", execution.getId());
        response.put("jobName", execution.getJobInstance().getJobName());
        response.put("status", execution.getStatus().toString());
        response.put("startTime", execution.getStartTime());
        response.put("endTime", execution.getEndTime());
        response.put("exitCode", execution.getExitStatus().getExitCode());

        Map<String, Object> steps = new LinkedHashMap<>();
        execution.getStepExecutions().forEach(step -> {
            Map<String, Object> stepInfo = new LinkedHashMap<>();
            stepInfo.put("status", step.getStatus().toString());
            stepInfo.put("startTime", step.getStartTime());
            stepInfo.put("endTime", step.getEndTime());
            stepInfo.put("exitCode", step.getExitStatus().getExitCode());
            steps.put(step.getStepName(), stepInfo);
        });
        response.put("steps", steps);

        return ResponseEntity.ok(response);
    }

}
