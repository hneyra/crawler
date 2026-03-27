package crawler.discovery;

import java.util.List;
import org.jsoup.nodes.Document;

/**
 * Detects Cloudflare challenge and block pages by inspecting the page title,
 * body content, and HTTP status codes.
 */
public final class CloudflareDetector {

    private static final List<String> CF_TITLE_SIGNALS = List.of(
            "just a moment",
            "attention required",
            "sorry, you have been blocked"
    );

    private static final List<String> CF_BODY_SIGNALS = List.of(
            "cf-browser-verification",
            "cf_chl_prog",
            "jschl-answer",
            "__cf_chl_jschl_tk__",
            "cf-spinner",
            "cf-im-under-attack"
    );

    private CloudflareDetector() {}

    /**
     * Returns {@code true} if the document appears to be a Cloudflare challenge
     * or block page.
     */
    public static boolean isCloudflareBlock(Document doc) {
        String title = doc.title().toLowerCase();
        for (String signal : CF_TITLE_SIGNALS) {
            if (title.contains(signal)) {
                return true;
            }
        }
        String bodyHtml = doc.body() != null ? doc.body().html() : "";
        for (String signal : CF_BODY_SIGNALS) {
            if (bodyHtml.contains(signal)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Returns {@code true} for HTTP status codes commonly used by Cloudflare
     * when blocking or rate-limiting requests.
     */
    public static boolean isCloudflareStatusCode(int statusCode) {
        return statusCode == 403 || statusCode == 429 || statusCode == 503;
    }
}
