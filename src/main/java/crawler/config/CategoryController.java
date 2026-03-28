package crawler.config;

import crawler.model.Category;
import crawler.model.CategoryRepository;
import crawler.model.LinkExtractionType;
import crawler.model.PaginationType;
import crawler.model.SiteRepository;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api")
public class CategoryController {

    private final CategoryRepository categoryRepository;
    private final SiteRepository siteRepository;

    public CategoryController(CategoryRepository categoryRepository,
                              SiteRepository siteRepository) {
        this.categoryRepository = categoryRepository;
        this.siteRepository = siteRepository;
    }

    @GetMapping("/sites/{siteId}/categories")
    public ResponseEntity<List<Map<String, Object>>> listBySite(@PathVariable Long siteId) {
        if (!siteRepository.existsById(siteId)) {
            return ResponseEntity.notFound().build();
        }
        var categories = categoryRepository.findBySiteId(siteId);
        return ResponseEntity.ok(categories.stream().map(this::toMap).toList());
    }

    @GetMapping("/categories/{id}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable Long id) {
        return categoryRepository.findById(id)
                .map(cat -> ResponseEntity.ok(toMap(cat)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/sites/{siteId}/categories")
    public ResponseEntity<Map<String, Object>> create(@PathVariable Long siteId,
                                                       @RequestBody Map<String, Object> body) {
        return siteRepository.findById(siteId)
                .map(site -> {
                    var cat = new Category();
                    cat.setSite(site);
                    applyFields(cat, body);
                    var saved = categoryRepository.save(cat);
                    return ResponseEntity.ok(toMap(saved));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PutMapping("/categories/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id,
                                                       @RequestBody Map<String, Object> body) {
        return categoryRepository.findById(id)
                .map(cat -> {
                    applyFields(cat, body);
                    var saved = categoryRepository.save(cat);
                    return ResponseEntity.ok(toMap(saved));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/categories/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!categoryRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        categoryRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private void applyFields(Category cat, Map<String, Object> body) {
        if (body.containsKey("name")) cat.setName((String) body.get("name"));
        if (body.containsKey("url")) cat.setUrl((String) body.get("url"));
        if (body.containsKey("paginationType"))
            cat.setPaginationType(PaginationType.valueOf((String) body.get("paginationType")));
        if (body.containsKey("itemLinkSelector"))
            cat.setItemLinkSelector((String) body.get("itemLinkSelector"));
        if (body.containsKey("linkExtractionType"))
            cat.setLinkExtractionType(LinkExtractionType.valueOf((String) body.get("linkExtractionType")));
        if (body.containsKey("linkAttribute"))
            cat.setLinkAttribute((String) body.get("linkAttribute"));
        if (body.containsKey("nextPageSelector"))
            cat.setNextPageSelector((String) body.get("nextPageSelector"));
        if (body.containsKey("enabled"))
            cat.setEnabled((Boolean) body.get("enabled"));
    }

    private Map<String, Object> toMap(Category cat) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", cat.getId());
        m.put("siteId", cat.getSite().getId());
        m.put("name", cat.getName());
        m.put("url", cat.getUrl());
        m.put("paginationType", cat.getPaginationType());
        m.put("itemLinkSelector", cat.getItemLinkSelector());
        m.put("linkExtractionType", cat.getLinkExtractionType());
        m.put("linkAttribute", cat.getLinkAttribute());
        m.put("nextPageSelector", cat.getNextPageSelector());
        m.put("enabled", cat.isEnabled());
        return m;
    }
}
