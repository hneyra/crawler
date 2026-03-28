package crawler.support;

import java.net.URI;

public final class UrlUtils {

    private UrlUtils() {}

    public static String normalizeUrl(String raw) {
        try {
            URI uri = URI.create(raw.trim());
            URI normalized = new URI(
                    uri.getScheme(),
                    uri.getAuthority(),
                    uri.getPath(),
                    uri.getQuery(),
                    null
            );
            return normalized.toString();
        } catch (Exception e) {
            return null;
        }
    }

    public static String resolveUrl(String url, String baseUrl) {
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
