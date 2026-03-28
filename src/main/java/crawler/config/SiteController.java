package crawler.config;

import crawler.model.ExtractionConfig;
import crawler.model.Site;
import crawler.model.SiteRepository;
import java.time.Instant;
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
@RequestMapping("/api/sites")
public class SiteController {

    private final SiteRepository siteRepository;

    public SiteController(SiteRepository siteRepository) {
        this.siteRepository = siteRepository;
    }

    @GetMapping
    public List<Map<String, Object>> list() {
        return siteRepository.findAll().stream().map(this::toMap).toList();
    }

    @GetMapping("/{id}")
    public ResponseEntity<Map<String, Object>> get(@PathVariable Long id) {
        return siteRepository.findById(id)
                .map(site -> ResponseEntity.ok(toMap(site)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping
    public ResponseEntity<Map<String, Object>> create(@RequestBody Map<String, Object> body) {
        var site = new Site();
        site.setName((String) body.get("name"));
        site.setBaseUrl((String) body.get("baseUrl"));
        site.setEnabled(body.containsKey("enabled") ? (Boolean) body.get("enabled") : true);
        site.setPolitenessDelayMs(body.containsKey("politenessDelayMs")
                ? ((Number) body.get("politenessDelayMs")).intValue() : 1000);
        site.setCreatedAt(Instant.now());

        if (body.containsKey("extractionConfig")) {
            site.setExtractionConfig(parseExtractionConfig(body.get("extractionConfig")));
        } else {
            site.setExtractionConfig(new ExtractionConfig());
        }

        var saved = siteRepository.save(site);
        return ResponseEntity.ok(toMap(saved));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Map<String, Object>> update(@PathVariable Long id,
                                                       @RequestBody Map<String, Object> body) {
        return siteRepository.findById(id)
                .map(site -> {
                    if (body.containsKey("name")) site.setName((String) body.get("name"));
                    if (body.containsKey("baseUrl")) site.setBaseUrl((String) body.get("baseUrl"));
                    if (body.containsKey("enabled")) site.setEnabled((Boolean) body.get("enabled"));
                    if (body.containsKey("politenessDelayMs"))
                        site.setPolitenessDelayMs(((Number) body.get("politenessDelayMs")).intValue());
                    if (body.containsKey("extractionConfig"))
                        site.setExtractionConfig(parseExtractionConfig(body.get("extractionConfig")));

                    var saved = siteRepository.save(site);
                    return ResponseEntity.ok(toMap(saved));
                })
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        if (!siteRepository.existsById(id)) {
            return ResponseEntity.notFound().build();
        }
        siteRepository.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    private Map<String, Object> toMap(Site site) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("id", site.getId());
        m.put("name", site.getName());
        m.put("baseUrl", site.getBaseUrl());
        m.put("enabled", site.isEnabled());
        m.put("politenessDelayMs", site.getPolitenessDelayMs());
        m.put("extractionConfig", site.getExtractionConfig());
        m.put("createdAt", site.getCreatedAt());
        return m;
    }

    @SuppressWarnings("unchecked")
    private ExtractionConfig parseExtractionConfig(Object raw) {
        if (raw == null) return new ExtractionConfig();
        var map = (Map<String, Object>) raw;
        var config = new ExtractionConfig();

        if (map.containsKey("detailStrategy")) {
            config.setDetailStrategy(
                    crawler.model.DetailStrategy.valueOf((String) map.get("detailStrategy")));
        }
        if (map.containsKey("fieldSelectors")) {
            config.setFieldSelectors((Map<String, String>) map.get("fieldSelectors"));
        }
        if (map.containsKey("scriptPatterns")) {
            config.setScriptPatterns((List<String>) map.get("scriptPatterns"));
        }
        if (map.containsKey("jsonPaths")) {
            config.setJsonPaths((Map<String, String>) map.get("jsonPaths"));
        }
        if (map.containsKey("interceptPatterns")) {
            config.setInterceptPatterns((List<String>) map.get("interceptPatterns"));
        }
        return config;
    }
}
