package crawler.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "content_change")
public class ContentChange {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "discovered_url_id", nullable = false)
    private DiscoveredUrl discoveredUrl;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "site_id", nullable = false)
    private Site site;

    @Column(name = "content_hash", nullable = false, length = 64)
    private String contentHash;

    @Column(name = "previous_hash", length = 64)
    private String previousHash;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    private String properties;

    @Column(name = "raw_snapshot_path")
    private String rawSnapshotPath;

    @Column(name = "detected_at", nullable = false)
    private Instant detectedAt;

    public Long getId() {
        return id;
    }

    public DiscoveredUrl getDiscoveredUrl() {
        return discoveredUrl;
    }

    public void setDiscoveredUrl(DiscoveredUrl discoveredUrl) {
        this.discoveredUrl = discoveredUrl;
    }

    public Site getSite() {
        return site;
    }

    public void setSite(Site site) {
        this.site = site;
    }

    public String getContentHash() {
        return contentHash;
    }

    public void setContentHash(String contentHash) {
        this.contentHash = contentHash;
    }

    public String getPreviousHash() {
        return previousHash;
    }

    public void setPreviousHash(String previousHash) {
        this.previousHash = previousHash;
    }

    public String getProperties() {
        return properties;
    }

    public void setProperties(String properties) {
        this.properties = properties;
    }

    public String getRawSnapshotPath() {
        return rawSnapshotPath;
    }

    public void setRawSnapshotPath(String rawSnapshotPath) {
        this.rawSnapshotPath = rawSnapshotPath;
    }

    public Instant getDetectedAt() {
        return detectedAt;
    }

    public void setDetectedAt(Instant detectedAt) {
        this.detectedAt = detectedAt;
    }
}
