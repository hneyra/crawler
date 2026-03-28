package crawler.discovery;

import crawler.support.UrlUtils;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.jsoup.nodes.Element;

public class OnclickLinkExtractor implements LinkExtractor {

    private static final Pattern URL_IN_ONCLICK = Pattern.compile(
            "(?:location\\.href|window\\.location|window\\.open|location\\.assign|location\\.replace)"
                    + "\\s*[=(]\\s*['\"]([^'\"]+)['\"]"
    );

    private static final Pattern SIMPLE_URL = Pattern.compile(
            "['\"]((https?://|/)[^'\"]+)['\"]"
    );

    @Override
    public String extractUrl(Element element, String baseUrl, String linkAttribute) {
        String onclick = element.attr("onclick");
        if (onclick.isBlank()) {
            return "";
        }

        Matcher matcher = URL_IN_ONCLICK.matcher(onclick);
        if (matcher.find()) {
            return UrlUtils.resolveUrl(matcher.group(1), baseUrl);
        }

        matcher = SIMPLE_URL.matcher(onclick);
        if (matcher.find()) {
            return UrlUtils.resolveUrl(matcher.group(1), baseUrl);
        }

        return "";
    }
}
