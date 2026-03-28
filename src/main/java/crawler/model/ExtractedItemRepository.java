package crawler.model;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ExtractedItemRepository extends JpaRepository<ExtractedItem, Long> {

    long countBySiteId(Long siteId);

    @Query("SELECT i FROM ExtractedItem i JOIN FETCH i.discoveredUrl JOIN FETCH i.site ORDER BY i.extractedAt DESC")
    List<ExtractedItem> findAllWithAssociations(Pageable pageable);

    @Query("SELECT i FROM ExtractedItem i JOIN FETCH i.discoveredUrl JOIN FETCH i.site WHERE i.site.id = :siteId ORDER BY i.extractedAt DESC")
    List<ExtractedItem> findBySiteIdWithAssociations(@Param("siteId") Long siteId, Pageable pageable);
}
