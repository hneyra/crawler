package crawler.discovery;

import crawler.config.CrawlerProperties;
import crawler.model.Category;
import crawler.model.DiscoveredUrl;
import crawler.model.DiscoveredUrlRepository;
import crawler.model.UrlStatus;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

@Service
public class ListDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(ListDiscoveryService.class);

    private final DiscoveredUrlRepository discoveredUrlRepository;
    private final CrawlerProperties crawlerProperties;

    public ListDiscoveryService(DiscoveredUrlRepository discoveredUrlRepository,
                                CrawlerProperties crawlerProperties) {
        this.discoveredUrlRepository = discoveredUrlRepository;
        this.crawlerProperties = crawlerProperties;
    }

    public void discoverCategory(Category category) {
        String categoryName = category.getName();
        int maxPages = crawlerProperties.getDiscoveryMaxPages();
        int politenessDelayMs = category.getSite().getPolitenessDelayMs();

        String currentUrl = category.getUrl();
        int pagesVisited = 0;
        int newUrls = 0;
        int knownUrls = 0;

        log.info("Starting discovery for category '{}', url={}", categoryName, currentUrl);

        while (currentUrl != null && pagesVisited < maxPages) {
            pagesVisited++;

            Document doc;
            try {
                doc = Jsoup.connect(currentUrl)
                        .userAgent("CrawlerBot/1.0")
                        .timeout(15_000)
                        .get();
            } catch (Exception e) {
                log.error("Failed to fetch page {} for category '{}': {}",
                        currentUrl, categoryName, e.getMessage());
                break;
            }

            Elements links = doc.select(category.getItemLinkSelector());

            for (Element link : links) {
                String href = link.absUrl("href");
                if (href.isBlank()) {
                    continue;
                }

                String normalized = normalizeUrl(href);
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

            currentUrl = findNextPageUrl(doc, category.getNextPageSelector());

            if (currentUrl != null && pagesVisited < maxPages) {
                sleep(politenessDelayMs);
            }
        }

        log.info("Discovery complete for category '{}': pages={}, newUrls={}, knownUrls={}",
                categoryName, pagesVisited, newUrls, knownUrls);
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

        // Default: look for rel=next
        Element relNext = doc.selectFirst("a[rel=next]");
        if (relNext != null) {
            String href = relNext.absUrl("href");
            if (!href.isBlank()) {
                return href;
            }
        }

        // Fallback: look for common "next" text patterns
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
            // Rebuild without fragment
            URI normalized = new URI(
                    uri.getScheme(),
                    uri.getAuthority(),
                    uri.getPath(),
                    uri.getQuery(),
                    null // no fragment
            );
            return normalized.toString();
        } catch (Exception e) {
            return null;
        }
    }

    static String sha256(String input) {
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
