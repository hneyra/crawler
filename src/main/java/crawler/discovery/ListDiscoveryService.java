package crawler.discovery;

import crawler.config.CrawlerProperties;
import crawler.model.Category;
import crawler.model.DiscoveredUrl;
import crawler.model.DiscoveredUrlRepository;
import crawler.model.LinkExtractionType;
import crawler.model.PaginationType;
import crawler.model.UrlStatus;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.EnumMap;
import java.util.HexFormat;
import java.util.Map;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;

@Service
public class ListDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(ListDiscoveryService.class);

    private final DiscoveredUrlRepository discoveredUrlRepository;
    private final CrawlerProperties crawlerProperties;
    private final PlaywrightClient playwrightClient;
    private final Map<LinkExtractionType, LinkExtractor> linkExtractors;

    public ListDiscoveryService(DiscoveredUrlRepository discoveredUrlRepository,
                                CrawlerProperties crawlerProperties,
                                PlaywrightClient playwrightClient) {
        this.discoveredUrlRepository = discoveredUrlRepository;
        this.crawlerProperties = crawlerProperties;
        this.playwrightClient = playwrightClient;
        this.linkExtractors = new EnumMap<>(LinkExtractionType.class);
        this.linkExtractors.put(LinkExtractionType.HREF, new HrefLinkExtractor());
        this.linkExtractors.put(LinkExtractionType.ONCLICK, new OnclickLinkExtractor());
        this.linkExtractors.put(LinkExtractionType.DATA_ATTRIBUTE, new DataAttributeLinkExtractor());
    }

    public void discoverCategory(Category category) {
        MDC.put("siteId", String.valueOf(category.getSite().getId()));
        MDC.put("categoryId", String.valueOf(category.getId()));
        try {
            if (category.getPaginationType() == PaginationType.SCROLL_AJAX) {
                discoverWithPlaywright(category);
            } else {
                discoverWithJsoup(category);
            }
        } finally {
            MDC.remove("siteId");
            MDC.remove("categoryId");
        }
    }

    private void discoverWithJsoup(Category category) {
        String categoryName = category.getName();
        int maxPages = crawlerProperties.getDiscoveryMaxPages();
        int politenessDelayMs = category.getSite().getPolitenessDelayMs();

        String currentUrl = category.getUrl();
        int pagesVisited = 0;
        int newUrls = 0;
        int knownUrls = 0;

        log.info("Starting Jsoup discovery for category '{}', url={}", categoryName, currentUrl);

        while (currentUrl != null && pagesVisited < maxPages) {
            pagesVisited++;
            MDC.put("url", currentUrl);

            Document doc;
            try {
                doc = fetchDocument(currentUrl, category.getItemLinkSelector());
            } catch (Exception e) {
                log.error("Failed to fetch page for category '{}': {}", categoryName, e.getMessage(), e);
                break;
            }

            int[] counts = extractLinks(doc, category);
            newUrls += counts[0];
            knownUrls += counts[1];

            currentUrl = findNextPageUrl(doc, category.getNextPageSelector());

            if (currentUrl != null && pagesVisited < maxPages) {
                sleep(politenessDelayMs);
            }
        }

        MDC.remove("url");
        log.info("Discovery complete for category '{}': pages={}, newUrls={}, knownUrls={}",
                categoryName, pagesVisited, newUrls, knownUrls);
    }

    private void discoverWithPlaywright(Category category) {
        String categoryName = category.getName();
        int maxScrolls = crawlerProperties.getDiscoveryMaxPages();

        MDC.put("url", category.getUrl());
        log.info("Starting Playwright scroll discovery for category '{}', url={}",
                categoryName, category.getUrl());

        PlaywrightClient.RenderRequest request = PlaywrightClient.RenderRequest.scrolling(
                category.getUrl(), maxScrolls, 30_000);

        PlaywrightClient.RenderResponse response;
        try {
            response = playwrightClient.render(request);
        } catch (Exception e) {
            log.error("Playwright render failed for category '{}': {}",
                    categoryName, e.getMessage());
            return;
        } finally {
            MDC.remove("url");
        }

        Document doc = Jsoup.parse(response.html(), category.getUrl());
        int[] counts = extractLinks(doc, category);

        log.info("Playwright discovery complete for category '{}': newUrls={}, knownUrls={}",
                categoryName, counts[0], counts[1]);
    }

    private Document fetchDocument(String url, String itemLinkSelector) throws Exception {
        Document doc;
        try {
            doc = Jsoup.connect(url)
                    .userAgent("Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36")
                    .timeout(15_000)
                    .get();
        } catch (org.jsoup.HttpStatusException e) {
            if (CloudflareDetector.isCloudflareStatusCode(e.getStatusCode())) {
                log.warn("Cloudflare block detected (HTTP {}), retrying with Playwright: {}",
                        e.getStatusCode(), url);
                return renderDocumentWithPlaywright(url, itemLinkSelector);
            }
            throw e;
        }

        if (CloudflareDetector.isCloudflareBlock(doc)) {
            log.warn("Cloudflare challenge page detected, retrying with Playwright: {}", url);
            return renderDocumentWithPlaywright(url, itemLinkSelector);
        }

        return doc;
    }

    private Document renderDocumentWithPlaywright(String url, String waitForSelector) throws Exception {
        PlaywrightClient.RenderRequest request = PlaywrightClient.RenderRequest.simple(url, waitForSelector, 30_000);
        PlaywrightClient.RenderResponse response = playwrightClient.render(request);
        return Jsoup.parse(response.html(), url);
    }

    private int[] extractLinks(Document doc, Category category) {
        int newUrls = 0;
        int knownUrls = 0;

        LinkExtractor extractor = linkExtractors.get(category.getLinkExtractionType());
        String baseUrl = doc.baseUri();
        String linkAttribute = category.getLinkAttribute();
        Elements elements = doc.select(category.getItemLinkSelector());

        for (Element element : elements) {
            String url = extractor.extractUrl(element, baseUrl, linkAttribute);
            if (url.isBlank()) {
                continue;
            }

            String normalized = normalizeUrl(url);
            if (normalized == null) {
                continue;
            }

            String hash = sha256(normalized);

            if (discoveredUrlRepository.findByUrlHash(hash).isPresent()) {
                knownUrls++;
                continue;
            }

            DiscoveredUrl discovered = new DiscoveredUrl();
            discovered.setCategory(category);
            discovered.setUrl(normalized);
            discovered.setUrlHash(hash);
            discovered.setStatus(UrlStatus.PENDING);
            discovered.setRetryCount(0);
            discovered.setCreatedAt(Instant.now());
            discoveredUrlRepository.save(discovered);
            newUrls++;
        }

        return new int[]{newUrls, knownUrls};
    }

    private String findNextPageUrl(Document doc, String customSelector) {
        if (customSelector != null && !customSelector.isBlank()) {
            Element el = doc.selectFirst(customSelector);
            if (el != null) {
                String href = el.absUrl("href");
                return href.isBlank() ? null : href;
            }
            return null;
        }

        Element relNext = doc.selectFirst("a[rel=next]");
        if (relNext != null) {
            String href = relNext.absUrl("href");
            if (!href.isBlank()) {
                return href;
            }
        }

        for (Element a : doc.select("a[href]")) {
            String text = a.text().trim();
            if (text.equalsIgnoreCase("Siguiente")
                    || text.equalsIgnoreCase("Next")
                    || text.equals("›")
                    || text.equals("»")) {
                String href = a.absUrl("href");
                if (!href.isBlank()) {
                    return href;
                }
            }
        }

        return null;
    }

    static String normalizeUrl(String raw) {
        try {
            URI uri = URI.create(raw.trim());
            URI normalized = new URI(
                    uri.getScheme(),
                    uri.getAuthority(),
                    uri.getPath(),
                    uri.getQuery(),
                    null
            );
            return normalized.toString();
        } catch (Exception e) {
            return null;
        }
    }

    public static String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new RuntimeException("SHA-256 not available", e);
        }
    }

    private void sleep(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

}
