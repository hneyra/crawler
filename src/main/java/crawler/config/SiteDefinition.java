package crawler.config;

import crawler.model.ExtractionConfig;
import java.util.List;

public class SiteDefinition {

    private String name;
    private String baseUrl;
    private boolean enabled = true;
    private int politenessDelayMs = 1000;
    private ExtractionConfig extractionConfig;
    private List<CategoryDefinition> categories = List.of();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getBaseUrl() {
        return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public int getPolitenessDelayMs() {
        return politenessDelayMs;
    }

    public void setPolitenessDelayMs(int politenessDelayMs) {
        this.politenessDelayMs = politenessDelayMs;
    }

    public ExtractionConfig getExtractionConfig() {
        return extractionConfig;
    }

    public void setExtractionConfig(ExtractionConfig extractionConfig) {
        this.extractionConfig = extractionConfig;
    }

    public List<CategoryDefinition> getCategories() {
        return categories;
    }

    public void setCategories(List<CategoryDefinition> categories) {
        this.categories = categories;
    }

}
