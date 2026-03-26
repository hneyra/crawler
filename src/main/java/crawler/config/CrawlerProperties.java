package crawler.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "crawler")
public class CrawlerProperties {

    private String rawDataDir;

    public String getRawDataDir() {
        return rawDataDir;
    }

    public void setRawDataDir(String rawDataDir) {
        this.rawDataDir = rawDataDir;
    }

}
