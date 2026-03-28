package crawler.discovery;

import crawler.support.UrlUtils;
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
            return UrlUtils.resolveUrl(raw, baseUrl);
        }

        return "";
    }
}
