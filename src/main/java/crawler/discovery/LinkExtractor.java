package crawler.discovery;

import org.jsoup.nodes.Element;

public interface LinkExtractor {
    String extractUrl(Element element, String baseUrl, String linkAttribute);
}
