package crawler.discovery;

import com.fasterxml.jackson.databind.JsonNode;
import crawler.config.CrawlerProperties;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

@Component
public class PlaywrightClient {

    private final WebClient webClient;

    public PlaywrightClient(CrawlerProperties properties, WebClient.Builder webClientBuilder) {
        this.webClient = webClientBuilder
                .baseUrl(properties.getRendererBaseUrl())
                .build();
    }

    public RenderResponse render(RenderRequest request) {
        return webClient.post()
                .uri("/render")
                .bodyValue(request)
                .retrieve()
                .bodyToMono(RenderResponse.class)
                .block(Duration.ofSeconds(60));
    }

    public record RenderRequest(
            String url,
            String waitForSelector,
            List<String> interceptPatterns,
            boolean scrollToBottom,
            int maxScrolls,
            int timeout
    ) {
        public static RenderRequest scrolling(String url, int maxScrolls, int timeout) {
            return new RenderRequest(url, null, List.of(), true, maxScrolls, timeout);
        }

        public static RenderRequest withIntercept(String url, List<String> interceptPatterns,
                                                   String waitForSelector, int timeout) {
            return new RenderRequest(url, waitForSelector, interceptPatterns, false, 0, timeout);
        }

        public static RenderRequest scrollingWithIntercept(String url, List<String> interceptPatterns,
                                                            int maxScrolls, int timeout) {
            return new RenderRequest(url, null, interceptPatterns, true, maxScrolls, timeout);
        }

        public static RenderRequest simple(String url, int timeout) {
            return new RenderRequest(url, null, List.of(), false, 0, timeout);
        }

        public static RenderRequest simple(String url, String waitForSelector, int timeout) {
            return new RenderRequest(url, waitForSelector, List.of(), false, 0, timeout);
        }
    }

    public record InterceptedResponse(String url, int status, String body) {}

    public record RenderResponse(String html, List<InterceptedResponse> interceptedResponses) {}

}
