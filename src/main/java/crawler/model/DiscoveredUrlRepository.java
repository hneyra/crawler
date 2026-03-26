package crawler.model;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface DiscoveredUrlRepository extends JpaRepository<DiscoveredUrl, Long> {

    Optional<DiscoveredUrl> findByUrlHash(String urlHash);

    List<DiscoveredUrl> findByStatus(UrlStatus status);

    List<DiscoveredUrl> findByCategory_Site_IdAndStatus(Long siteId, UrlStatus status, Pageable pageable);

    long countByCategory_Site_IdAndStatus(Long siteId, UrlStatus status);
}
