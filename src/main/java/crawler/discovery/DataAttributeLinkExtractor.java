package crawler.discovery;

import java.net.URI;
import org.jsoup.nodes.Element;

public class DataAttributeLinkExtractor implements LinkExtractor {

    @Override
    public String extractUrl(Element element, String baseUrl, String linkAttribute) {
        String url = element.absUrl(linkAttribute);
        if (!url.isBlank()) {
            return url;
        }

        String raw = element.attr(linkAttribute);
        if (!raw.isBlank()) {
            return resolveUrl(raw, baseUrl);
        }

        return "";
    }

    private String resolveUrl(String url, String baseUrl) {
        if (url.startsWith("http://") || url.startsWith("https://")) {
            return url;
        }
        try {
            return URI.create(baseUrl).resolve(url).toString();
        } catch (Exception e) {
            return "";
        }
    }
}
