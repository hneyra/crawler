package crawler.model;

import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SiteRepository extends JpaRepository<Site, Long> {

    Optional<Site> findByName(String name);

    List<Site> findByEnabledTrue();
}
