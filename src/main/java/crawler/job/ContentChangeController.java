package crawler.job;

import crawler.model.ContentChange;
import crawler.model.ContentChangeRepository;
import java.util.List;
import java.util.Map;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/changes")
public class ContentChangeController {

    private final ContentChangeRepository contentChangeRepository;

    public ContentChangeController(ContentChangeRepository contentChangeRepository) {
        this.contentChangeRepository = contentChangeRepository;
    }

    @GetMapping("/site/{siteId}")
    public Map<String, Object> changesBySite(
            @PathVariable Long siteId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Page<ContentChange> result = contentChangeRepository.findBySiteId(
                siteId, PageRequest.of(page, size, Sort.by("detectedAt").descending()));
        return Map.of(
                "content", result.getContent().stream().map(this::toDto).toList(),
                "totalElements", result.getTotalElements(),
                "totalPages", result.getTotalPages(),
                "page", page,
                "size", size
        );
    }

    @GetMapping("/url/{discoveredUrlId}")
    public List<Map<String, Object>> changesByUrl(@PathVariable Long discoveredUrlId) {
        return contentChangeRepository.findByDiscoveredUrlId(discoveredUrlId)
                .stream().map(this::toDto).toList();
    }

    private Map<String, Object> toDto(ContentChange c) {
        return Map.of(
                "id", c.getId(),
                "url", c.getDiscoveredUrl().getUrl(),
                "siteId", c.getSite().getId(),
                "contentHash", c.getContentHash(),
                "previousHash", c.getPreviousHash() != null ? c.getPreviousHash() : "",
                "detectedAt", c.getDetectedAt()
        );
    }
}
