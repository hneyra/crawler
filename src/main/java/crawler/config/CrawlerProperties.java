package crawler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "crawler")
public class CrawlerProperties {

    private String rawDataDir;
    private int discoveryMaxPages = 50;
    private int extractionBatchSize = 10;
    private String rendererBaseUrl = "http://localhost:3000";
    private int retryMaxRetries = 3;
    private long retryIntervalMs = 600000;

    public String getRawDataDir() {
        return rawDataDir;
    }

    public void setRawDataDir(String rawDataDir) {
        this.rawDataDir = rawDataDir;
    }

    public int getDiscoveryMaxPages() {
        return discoveryMaxPages;
    }

    public void setDiscoveryMaxPages(int discoveryMaxPages) {
        this.discoveryMaxPages = discoveryMaxPages;
    }

    public int getExtractionBatchSize() {
        return extractionBatchSize;
    }

    public void setExtractionBatchSize(int extractionBatchSize) {
        this.extractionBatchSize = extractionBatchSize;
    }

    public String getRendererBaseUrl() {
        return rendererBaseUrl;
    }

    public void setRendererBaseUrl(String rendererBaseUrl) {
        this.rendererBaseUrl = rendererBaseUrl;
    }

    public int getRetryMaxRetries() {
        return retryMaxRetries;
    }

    public void setRetryMaxRetries(int retryMaxRetries) {
        this.retryMaxRetries = retryMaxRetries;
    }

    public long getRetryIntervalMs() {
        return retryIntervalMs;
    }

    public void setRetryIntervalMs(long retryIntervalMs) {
        this.retryIntervalMs = retryIntervalMs;
    }

}
