package crawler.model;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

public interface DiscoveredUrlRepository extends JpaRepository<DiscoveredUrl, Long> {

    Optional<DiscoveredUrl> findByUrlHash(String urlHash);

    List<DiscoveredUrl> findByStatus(UrlStatus status);

    List<DiscoveredUrl> findByCategory_Site_IdAndStatus(Long siteId, UrlStatus status, Pageable pageable);

    long countByStatus(UrlStatus status);

    long countByCategory_Site_IdAndStatus(Long siteId, UrlStatus status);

    long countByCategory_Site_Id(Long siteId);

    @Modifying
    @Query("UPDATE DiscoveredUrl d SET d.status = 'PENDING' " +
            "WHERE d.status = 'FAILED' AND d.retryCount < :maxRetries")
    int resetFailedUrls(int maxRetries);

    @Query("SELECT MAX(d.lastAttemptAt) FROM DiscoveredUrl d WHERE d.category.site.id = :siteId")
    Optional<java.time.Instant> findLastAttemptBySiteId(Long siteId);
}
