package crawler.discovery;

import crawler.config.CrawlerProperties;
import crawler.model.Category;
import crawler.model.DiscoveredUrl;
import crawler.model.DiscoveredUrlRepository;
import crawler.model.LinkExtractionType;
import crawler.model.PaginationType;
import crawler.model.UrlStatus;
import crawler.support.HashUtils;
import crawler.support.PageFetcher;
import crawler.support.UrlUtils;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import java.time.Instant;
import java.util.EnumMap;
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
    private final PageFetcher pageFetcher;
    private final Map<LinkExtractionType, LinkExtractor> linkExtractors;

    private final Timer discoveryTimer;
    private final Counter urlsDiscoveredCounter;
    private final Counter urlsDuplicateCounter;
    private final Counter discoveryErrorCounter;
    private final Counter pagesVisitedCounter;

    public ListDiscoveryService(DiscoveredUrlRepository discoveredUrlRepository,
                                CrawlerProperties crawlerProperties,
                                PlaywrightClient playwrightClient,
                                PageFetcher pageFetcher,
                                MeterRegistry registry) {
        this.discoveredUrlRepository = discoveredUrlRepository;
        this.crawlerProperties = crawlerProperties;
        this.playwrightClient = playwrightClient;
        this.pageFetcher = pageFetcher;
        this.linkExtractors = new EnumMap<>(LinkExtractionType.class);
        this.linkExtractors.put(LinkExtractionType.HREF, new HrefLinkExtractor());
        this.linkExtractors.put(LinkExtractionType.ONCLICK, new OnclickLinkExtractor());
        this.linkExtractors.put(LinkExtractionType.DATA_ATTRIBUTE, new DataAttributeLinkExtractor());

        this.discoveryTimer = Timer.builder("crawler.discovery.duration")
                .description("Time spent discovering URLs for a category")
                .register(registry);
        this.urlsDiscoveredCounter = Counter.builder("crawler.discovery.urls")
                .tag("result", "new")
                .description("Newly discovered URLs")
                .register(registry);
        this.urlsDuplicateCounter = Counter.builder("crawler.discovery.urls")
                .tag("result", "duplicate")
                .description("Already known URLs encountered during discovery")
                .register(registry);
        this.discoveryErrorCounter = Counter.builder("crawler.discovery.errors")
                .description("Discovery errors")
                .register(registry);
        this.pagesVisitedCounter = Counter.builder("crawler.discovery.pages")
                .description("Pages visited during discovery")
                .register(registry);
    }

    public void discoverCategory(Category category) {
        MDC.put("siteId", String.valueOf(category.getSite().getId()));
        MDC.put("categoryId", String.valueOf(category.getId()));
        try {
            discoveryTimer.record(() -> {
                if (category.getPaginationType() == PaginationType.SCROLL_AJAX) {
                    discoverWithPlaywright(category);
                } else {
                    discoverWithJsoup(category);
                }
            });
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
            pagesVisitedCounter.increment();
            MDC.put("url", currentUrl);

            Document doc;
            try {
                doc = pageFetcher.fetch(currentUrl, category.getItemLinkSelector());
            } catch (Exception e) {
                discoveryErrorCounter.increment();
                log.error("Failed to fetch page for category '{}': {}", categoryName, e.getMessage(), e);
                break;
            }

            int[] counts = extractLinks(doc, category);
            newUrls += counts[0];
            knownUrls += counts[1];

            currentUrl = findNextPageUrl(doc, category.getNextPageSelector());

            if (currentUrl != null && pagesVisited < maxPages) {
                PageFetcher.politenessDelay(politenessDelayMs);
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
            discoveryErrorCounter.increment();
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

            String normalized = UrlUtils.normalizeUrl(url);
            if (normalized == null) {
                continue;
            }

            String hash = HashUtils.sha256(normalized);

            if (discoveredUrlRepository.findByUrlHash(hash).isPresent()) {
                knownUrls++;
                urlsDuplicateCounter.increment();
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
            urlsDiscoveredCounter.increment();
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

}
