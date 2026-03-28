package crawler.support;

import crawler.discovery.CloudflareDetector;
import crawler.discovery.PlaywrightClient;
import crawler.discovery.PlaywrightClient.RenderRequest;
import crawler.discovery.PlaywrightClient.RenderResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

@Component
public class PageFetcher {

    private static final Logger log = LoggerFactory.getLogger(PageFetcher.class);

    static final String USER_AGENT =
            "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
                    + "(KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36";

    private static final int DEFAULT_TIMEOUT = 15_000;
    private static final int PLAYWRIGHT_TIMEOUT = 30_000;

    private final PlaywrightClient playwrightClient;
    private final Timer fetchTimer;
    private final Counter cloudflareFallbackCounter;
    private final Counter fetchErrorCounter;

    public PageFetcher(PlaywrightClient playwrightClient, MeterRegistry registry) {
        this.playwrightClient = playwrightClient;
        this.fetchTimer = Timer.builder("crawler.fetch.duration")
                .description("Time spent fetching a page")
                .register(registry);
        this.cloudflareFallbackCounter = Counter.builder("crawler.fetch.cloudflare.fallbacks")
                .description("Number of Cloudflare fallbacks to Playwright")
                .register(registry);
        this.fetchErrorCounter = Counter.builder("crawler.fetch.errors")
                .description("Page fetch errors")
                .register(registry);
    }

    public Document fetch(String url) throws Exception {
        return fetch(url, null);
    }

    public Document fetch(String url, String waitForSelector) throws Exception {
        return fetchTimer.recordCallable(() -> doFetch(url, waitForSelector));
    }

    private Document doFetch(String url, String waitForSelector) throws Exception {
        Document doc;
        try {
            doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(DEFAULT_TIMEOUT)
                    .get();
        } catch (org.jsoup.HttpStatusException e) {
            if (CloudflareDetector.isCloudflareStatusCode(e.getStatusCode())) {
                cloudflareFallbackCounter.increment();
                log.warn("Cloudflare block (HTTP {}), falling back to Playwright: {}",
                        e.getStatusCode(), url);
                return renderDocument(url, waitForSelector);
            }
            fetchErrorCounter.increment();
            throw e;
        }

        if (CloudflareDetector.isCloudflareBlock(doc)) {
            cloudflareFallbackCounter.increment();
            log.warn("Cloudflare challenge detected, falling back to Playwright: {}", url);
            return renderDocument(url, waitForSelector);
        }

        return doc;
    }

    private Document renderDocument(String url, String waitForSelector) {
        RenderRequest request = waitForSelector != null
                ? RenderRequest.simple(url, waitForSelector, PLAYWRIGHT_TIMEOUT)
                : RenderRequest.simple(url, PLAYWRIGHT_TIMEOUT);
        RenderResponse response = playwrightClient.render(request);
        return Jsoup.parse(response.html(), url);
    }

    public static void politenessDelay(int millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}
