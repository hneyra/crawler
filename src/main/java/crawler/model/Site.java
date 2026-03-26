package crawler.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "site")
public class Site {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String name;

    @Column(name = "base_url", nullable = false)
    private String baseUrl;

    @Column(nullable = false)
    private boolean enabled;

    @Column(name = "politeness_delay_ms", nullable = false)
    private int politenessDelayMs;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "extraction_config", columnDefinition = "jsonb")
    private ExtractionConfig extractionConfig;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

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

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

}
