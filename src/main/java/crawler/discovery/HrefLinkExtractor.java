package crawler.discovery;

import org.jsoup.nodes.Element;

public class HrefLinkExtractor implements LinkExtractor {

    @Override
    public String extractUrl(Element element, String baseUrl, String linkAttribute) {
        return element.absUrl("href");
    }
}
