package crawler.model;

import java.util.List;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ContentChangeRepository extends JpaRepository<ContentChange, Long> {

    @Query("SELECT c FROM ContentChange c JOIN FETCH c.discoveredUrl JOIN FETCH c.site "
            + "WHERE c.discoveredUrl.id = :urlId ORDER BY c.detectedAt DESC")
    List<ContentChange> findByDiscoveredUrlId(@Param("urlId") Long urlId);

    @Query(value = "SELECT c FROM ContentChange c JOIN FETCH c.discoveredUrl JOIN FETCH c.site "
            + "WHERE c.site.id = :siteId ORDER BY c.detectedAt DESC",
           countQuery = "SELECT COUNT(c) FROM ContentChange c WHERE c.site.id = :siteId")
    Page<ContentChange> findBySiteId(@Param("siteId") Long siteId, Pageable pageable);
}
