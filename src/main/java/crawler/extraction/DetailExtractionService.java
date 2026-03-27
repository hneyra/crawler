package crawler.extraction;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.jayway.jsonpath.JsonPath;
import com.jayway.jsonpath.PathNotFoundException;
import crawler.config.CrawlerProperties;
import crawler.discovery.CloudflareDetector;
import crawler.discovery.PlaywrightClient;
import crawler.discovery.PlaywrightClient.InterceptedResponse;
import crawler.discovery.PlaywrightClient.RenderRequest;
import crawler.discovery.PlaywrightClient.RenderResponse;
import crawler.model.DetailStrategy;
import crawler.model.DiscoveredUrl;
import crawler.model.DiscoveredUrlRepository;
import crawler.model.ExtractedItem;
import crawler.model.ExtractedItemRepository;
import crawler.model.ExtractionConfig;
import crawler.model.Site;
import crawler.model.SiteRepository;
import crawler.model.UrlStatus;
import crawler.storage.RawStorageService;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

@Service
public class DetailExtractionService {

    private static final Logger log = LoggerFactory.getLogger(DetailExtractionService.class);

    private final DiscoveredUrlRepository discoveredUrlRepository;
    private final ExtractedItemRepository extractedItemRepository;
    private final SiteRepository siteRepository;
    private final RawStorageService rawStorageService;
    private final CrawlerProperties crawlerProperties;
    private final ObjectMapper objectMapper;
    private final PlaywrightClient playwrightClient;

    public DetailExtractionService(DiscoveredUrlRepository discoveredUrlRepository,
                                   ExtractedItemRepository extractedItemRepository,
                                   SiteRepository siteRepository,
                                   RawStorageService rawStorageService,
                                   CrawlerProperties crawlerProperties,
                                   ObjectMapper objectMapper,
                                   PlaywrightClient playwrightClient) {
        this.discoveredUrlRepository = discoveredUrlRepository;
        this.extractedItemRepository = extractedItemRepository;
        this.siteRepository = siteRepository;
        this.rawStorageService = rawStorageService;
        this.crawlerProperties = crawlerProperties;
        this.objectMapper = objectMapper;
        this.playwrightClient = playwrightClient;
    }

    public void processBatch(Long siteId) {
        Site site = siteRepository.findById(siteId).orElse(null);
        if (site == null) {
            log.warn("Site {} not found", siteId);
            return;
        }

        int batchSize = crawlerProperties.getExtractionBatchSize();
        List<DiscoveredUrl> batch = discoveredUrlRepository
                .findByCategory_Site_IdAndStatus(siteId, UrlStatus.PENDING,
                        PageRequest.of(0, batchSize));

        if (batch.isEmpty()) {
            log.info("No PENDING URLs for site '{}'", site.getName());
            return;
        }

        for (DiscoveredUrl url : batch) {
            url.setStatus(UrlStatus.IN_PROGRESS);
            url.setLastAttemptAt(Instant.now());
        }
        discoveredUrlRepository.saveAll(batch);

        MDC.put("siteId", String.valueOf(siteId));
        try {
            log.info("Processing batch of {} URLs for site '{}'", batch.size(), site.getName());

            ExtractionConfig config = site.getExtractionConfig();
            int politenessDelayMs = site.getPolitenessDelayMs();
            DetailStrategy strategy = config != null ? config.getDetailStrategy() : null;

            for (int i = 0; i < batch.size(); i++) {
                DiscoveredUrl discovered = batch.get(i);
                MDC.put("categoryId", String.valueOf(discovered.getCategory().getId()));
                MDC.put("url", discovered.getUrl());
                try {
                    if (strategy == DetailStrategy.AJAX) {
                        processUrlWithPlaywright(discovered, site, config);
                    } else {
                        processUrl(discovered, site, config);
                    }
                    discovered.setStatus(UrlStatus.COMPLETED);
                    log.info("Extracted successfully");
                } catch (Exception e) {
                    discovered.setStatus(UrlStatus.FAILED);
                    discovered.setRetryCount(discovered.getRetryCount() + 1);
                    log.error("Extraction failed: {}", e.getMessage());
                }
                discovered.setLastAttemptAt(Instant.now());
                discoveredUrlRepository.save(discovered);

                if (i < batch.size() - 1) {
                    sleep(politenessDelayMs);
                }
            }
        } finally {
            MDC.remove("siteId");
            MDC.remove("categoryId");
            MDC.remove("url");
        }
    }

    private void processUrlWithJsoup(DiscoveredUrl discovered, Site site,
                                      ExtractionConfig config) throws Exception {
        Document doc;
        try {
            doc = Jsoup.connect(discovered.getUrl())
                    .userAgent("CrawlerBot/1.0")
                    .timeout(15_000)
                    .get();
        } catch (org.jsoup.HttpStatusException e) {
            if (crawler.discovery.CloudflareDetector.isCloudflareStatusCode(e.getStatusCode())) {
                log.warn("Cloudflare block detected (HTTP {}), retrying with Playwright: {}",
                        e.getStatusCode(), discovered.getUrl());
                processUrlWithPlaywrightSimple(discovered, site, config);
                return;
            }
            throw e;
        }

        if (crawler.discovery.CloudflareDetector.isCloudflareBlock(doc)) {
            log.warn("Cloudflare challenge page detected, retrying with Playwright: {}",
                    discovered.getUrl());
            processUrlWithPlaywrightSimple(discovered, site, config);
            return;
        }

        String html = doc.outerHtml();
        String snapshotPath = rawStorageService.saveHtml(html, site.getId(), discovered.getUrl());

        Map<String, Object> properties = new LinkedHashMap<>();

        if (config != null) {
            DetailStrategy strategy = config.getDetailStrategy();

            if (strategy == DetailStrategy.HTML || strategy == null) {
                extractFromHtml(doc, config.getFieldSelectors(), properties);
            }

            if (strategy == DetailStrategy.SCRIPT_JSON || strategy == null) {
                extractFromScripts(doc, config, properties);
            }

            extractJsonLd(doc, config, properties);
        }

        saveExtractedItem(discovered, site, doc, properties, snapshotPath);
    }

    private void processUrlWithPlaywright(DiscoveredUrl discovered, Site site,
                                           ExtractionConfig config) throws Exception {
        List<String> interceptPatterns = config.getInterceptPatterns();

        RenderRequest request = RenderRequest.withIntercept(
                discovered.getUrl(), interceptPatterns, null, 30_000);

        RenderResponse response = playwrightClient.render(request);

        String html = response.html();
        String snapshotPath = rawStorageService.saveHtml(html, site.getId(), discovered.getUrl());

        // Also save intercepted JSON responses
        for (InterceptedResponse intercepted : response.interceptedResponses()) {
            rawStorageService.saveJson(intercepted.body(), site.getId(),
                    discovered.getUrl() + "#intercepted-" + intercepted.url());
        }

        Map<String, Object> properties = new LinkedHashMap<>();

        // Extract from rendered HTML
        Document doc = Jsoup.parse(html, discovered.getUrl());
        if (config.getFieldSelectors() != null && !config.getFieldSelectors().isEmpty()) {
            extractFromHtml(doc, config.getFieldSelectors(), properties);
        }

        // Extract from intercepted JSON responses via JsonPath
        for (InterceptedResponse intercepted : response.interceptedResponses()) {
            if (config.getJsonPaths() != null) {
                extractWithJsonPaths(intercepted.body(), config.getJsonPaths(), properties);
            }

            // Store raw intercepted data under a keyed entry
            try {
                JsonNode node = objectMapper.readTree(intercepted.body());
                properties.put("ajax_" + intercepted.status() + "_"
                        + sanitizeKey(intercepted.url()), node);
            } catch (JsonProcessingException e) {
                log.debug("Non-JSON intercepted response from {}", intercepted.url());
            }
        }

        extractJsonLd(doc, config, properties);

        saveExtractedItem(discovered, site, doc, properties, snapshotPath);
    }

    private void processUrlWithPlaywrightSimple(DiscoveredUrl discovered, Site site,
                                                 ExtractionConfig config) throws Exception {
        RenderRequest request = RenderRequest.simple(discovered.getUrl(), 30_000);
        RenderResponse response = playwrightClient.render(request);

        String html = response.html();
        String snapshotPath = rawStorageService.saveHtml(html, site.getId(), discovered.getUrl());
        Document doc = Jsoup.parse(html, discovered.getUrl());

        Map<String, Object> properties = new LinkedHashMap<>();

        if (config != null) {
            DetailStrategy strategy = config.getDetailStrategy();
            if (strategy == DetailStrategy.HTML || strategy == null) {
                extractFromHtml(doc, config.getFieldSelectors(), properties);
            }
            if (strategy == DetailStrategy.SCRIPT_JSON || strategy == null) {
                extractFromScripts(doc, config, properties);
            }
            extractJsonLd(doc, config, properties);
        }

        saveExtractedItem(discovered, site, doc, properties, snapshotPath);
    }

    private void saveExtractedItem(DiscoveredUrl discovered, Site site,
                                    Document doc, Map<String, Object> properties,
                                    String snapshotPath) throws JsonProcessingException {
        String title = properties.containsKey("title")
                ? properties.get("title").toString()
                : doc.title();

        ExtractedItem item = new ExtractedItem();
        item.setDiscoveredUrl(discovered);
        item.setSite(site);
        item.setTitle(title);
        item.setProperties(objectMapper.writeValueAsString(properties));
        item.setRawSnapshotPath(snapshotPath);
        item.setExtractedAt(Instant.now());
        extractedItemRepository.save(item);
    }

    private void extractFromHtml(Document doc, Map<String, String> fieldSelectors,
                                 Map<String, Object> properties) {
        if (fieldSelectors == null || fieldSelectors.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : fieldSelectors.entrySet()) {
            Element el = doc.selectFirst(entry.getValue());
            if (el != null) {
                properties.put(entry.getKey(), el.text());
            }
        }
    }

    private void extractFromScripts(Document doc, ExtractionConfig config,
                                    Map<String, Object> properties) {
        List<String> patterns = config.getScriptPatterns();
        if (patterns == null || patterns.isEmpty()) {
            return;
        }

        Elements scripts = doc.select("script");
        for (Element script : scripts) {
            String data = script.data();
            if (data.isBlank()) {
                continue;
            }
            for (String patternStr : patterns) {
                Pattern pattern = Pattern.compile(patternStr);
                Matcher matcher = pattern.matcher(data);
                if (matcher.find()) {
                    String json = matcher.groupCount() > 0 ? matcher.group(1) : data;
                    extractWithJsonPaths(json, config.getJsonPaths(), properties);
                }
            }
        }
    }

    private void extractJsonLd(Document doc, ExtractionConfig config,
                               Map<String, Object> properties) {
        Elements ldScripts = doc.select("script[type=application/ld+json]");
        for (Element script : ldScripts) {
            String json = script.data().trim();
            if (json.isEmpty()) {
                continue;
            }
            try {
                JsonNode node = objectMapper.readTree(json);
                properties.put("jsonLd", node);
            } catch (JsonProcessingException e) {
                log.debug("Invalid JSON-LD: {}", e.getMessage());
            }

            if (config != null && config.getJsonPaths() != null) {
                extractWithJsonPaths(json, config.getJsonPaths(), properties);
            }
        }
    }

    private void extractWithJsonPaths(String json, Map<String, String> jsonPaths,
                                      Map<String, Object> properties) {
        if (jsonPaths == null || jsonPaths.isEmpty()) {
            return;
        }
        for (Map.Entry<String, String> entry : jsonPaths.entrySet()) {
            try {
                Object value = JsonPath.read(json, entry.getValue());
                properties.put(entry.getKey(), value);
            } catch (PathNotFoundException e) {
                // path not found in this JSON block
            } catch (Exception e) {
                log.debug("JsonPath error for '{}': {}", entry.getKey(), e.getMessage());
            }
        }
    }

    private static String sanitizeKey(String url) {
        return url.replaceAll("[^a-zA-Z0-9]", "_");

    }

    private void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
