package crawler.model;

import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExtractedItemRepository extends JpaRepository<ExtractedItem, Long> {

    Optional<ExtractedItem> findFirstByDiscoveredUrlIdOrderByExtractedAtDesc(Long discoveredUrlId);

    long countBySiteId(Long siteId);

    @Query("SELECT i FROM ExtractedItem i JOIN FETCH i.discoveredUrl JOIN FETCH i.site ORDER BY i.extractedAt DESC")
    List<ExtractedItem> findAllWithAssociations(Pageable pageable);

    @Query("SELECT i FROM ExtractedItem i JOIN FETCH i.discoveredUrl JOIN FETCH i.site WHERE i.site.id = :siteId ORDER BY i.extractedAt DESC")
    List<ExtractedItem> findBySiteIdWithAssociations(@Param("siteId") Long siteId, Pageable pageable);

    @Query(value = "SELECT i FROM ExtractedItem i JOIN FETCH i.discoveredUrl d JOIN FETCH i.site s "
            + "WHERE (:siteId IS NULL OR s.id = :siteId) "
            + "AND (:search IS NULL OR LOWER(i.title) LIKE :search "
            + "    OR LOWER(d.url) LIKE :search "
            + "    OR LOWER(s.name) LIKE :search "
            + "    OR LOWER(CAST(i.properties AS String)) LIKE :search) "
            + "ORDER BY i.extractedAt DESC",
           countQuery = "SELECT COUNT(i) FROM ExtractedItem i JOIN i.discoveredUrl d JOIN i.site s "
            + "WHERE (:siteId IS NULL OR s.id = :siteId) "
            + "AND (:search IS NULL OR LOWER(i.title) LIKE :search "
            + "    OR LOWER(d.url) LIKE :search "
            + "    OR LOWER(s.name) LIKE :search "
            + "    OR LOWER(CAST(i.properties AS String)) LIKE :search)")
    Page<ExtractedItem> searchItems(@Param("siteId") Long siteId, @Param("search") String search, Pageable pageable);
}
