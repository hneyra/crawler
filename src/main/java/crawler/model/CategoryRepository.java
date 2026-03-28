package crawler.model;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface CategoryRepository extends JpaRepository<Category, Long> {

    List<Category> findBySiteId(Long siteId);

    List<Category> findBySiteIdAndEnabledTrue(Long siteId);

    @Query("SELECT c FROM Category c JOIN FETCH c.site WHERE c.id = :id")
    Optional<Category> findByIdWithSite(@Param("id") Long id);

    @Query("SELECT c FROM Category c JOIN FETCH c.site WHERE c.site.id = :siteId AND c.enabled = true")
    List<Category> findBySiteIdAndEnabledTrueWithSite(@Param("siteId") Long siteId);
}
