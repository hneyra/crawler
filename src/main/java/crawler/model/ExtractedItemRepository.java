package crawler.model;

import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExtractedItemRepository extends JpaRepository<ExtractedItem, Long> {

    long countBySiteId(Long siteId);

    List<ExtractedItem> findByOrderByExtractedAtDesc(Pageable pageable);

    List<ExtractedItem> findBySiteIdOrderByExtractedAtDesc(Long siteId, Pageable pageable);
}
