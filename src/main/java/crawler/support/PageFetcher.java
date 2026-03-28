package crawler.support;

import crawler.discovery.CloudflareDetector;
import crawler.discovery.PlaywrightClient;
import crawler.discovery.PlaywrightClient.RenderRequest;
import crawler.discovery.PlaywrightClient.RenderResponse;
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

    public PageFetcher(PlaywrightClient playwrightClient) {
        this.playwrightClient = playwrightClient;
    }

    public Document fetch(String url) throws Exception {
        return fetch(url, null);
    }

    public Document fetch(String url, String waitForSelector) throws Exception {
        Document doc;
        try {
            doc = Jsoup.connect(url)
                    .userAgent(USER_AGENT)
                    .timeout(DEFAULT_TIMEOUT)
                    .get();
        } catch (org.jsoup.HttpStatusException e) {
            if (CloudflareDetector.isCloudflareStatusCode(e.getStatusCode())) {
                log.warn("Cloudflare block (HTTP {}), falling back to Playwright: {}",
                        e.getStatusCode(), url);
                return renderDocument(url, waitForSelector);
            }
            throw e;
        }

        if (CloudflareDetector.isCloudflareBlock(doc)) {
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
