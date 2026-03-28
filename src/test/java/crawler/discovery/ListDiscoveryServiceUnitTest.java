package crawler.discovery;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

import crawler.config.CrawlerProperties;
import crawler.model.Category;
import crawler.model.DiscoveredUrl;
import crawler.model.DiscoveredUrlRepository;
import crawler.model.PaginationType;
import crawler.model.Site;
import crawler.model.UrlStatus;
import crawler.support.HashUtils;
import crawler.support.PageFetcher;
import crawler.support.UrlUtils;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class ListDiscoveryServiceUnitTest {

    @Mock
    private DiscoveredUrlRepository discoveredUrlRepository;

    @Mock
    private CrawlerProperties crawlerProperties;

    @Mock
    private PlaywrightClient playwrightClient;

    @Mock
    private PageFetcher pageFetcher;

    private ListDiscoveryService service;

    @BeforeEach
    void setUp() {
        service = new ListDiscoveryService(discoveredUrlRepository, crawlerProperties, playwrightClient, pageFetcher, new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
    }

    // -------------------------------------------------------------------------
    // normalizeUrl
    // -------------------------------------------------------------------------

    @Test
    void normalizeUrl_stripsFragment() {
        assertThat(UrlUtils.normalizeUrl("https://example.com/product/1#reviews"))
                .isEqualTo("https://example.com/product/1");
    }

    @Test
    void normalizeUrl_preservesQueryParameters() {
        assertThat(UrlUtils.normalizeUrl("https://example.com/product?id=42&ref=home"))
                .isEqualTo("https://example.com/product?id=42&ref=home");
    }

    @Test
    void normalizeUrl_stripsFragmentAndKeepsQuery() {
        assertThat(UrlUtils.normalizeUrl("https://example.com/product?id=1#section"))
                .isEqualTo("https://example.com/product?id=1");
    }

    @Test
    void normalizeUrl_tripsLeadingAndTrailingWhitespace() {
        assertThat(UrlUtils.normalizeUrl("  https://example.com/product  "))
                .isEqualTo("https://example.com/product");
    }

    @Test
    void normalizeUrl_returnsNullForInvalidUrl() {
        assertThat(UrlUtils.normalizeUrl("http://[invalid")).isNull();
    }

    @Test
    void normalizeUrl_handlesSimpleHttpsUrl() {
        assertThat(UrlUtils.normalizeUrl("https://example.com/"))
                .isEqualTo("https://example.com/");
    }

    // -------------------------------------------------------------------------
    // sha256
    // -------------------------------------------------------------------------

    @Test
    void sha256_producesSixtyFourCharacterHexString() {
        String hash = HashUtils.sha256("https://example.com/product/123");
        assertThat(hash).hasSize(64).matches("[0-9a-f]{64}");
    }

    @Test
    void sha256_isDeterministic() {
        String url = "https://example.com/product/123";
        assertThat(HashUtils.sha256(url)).isEqualTo(HashUtils.sha256(url));
    }

    @Test
    void sha256_differentInputsProduceDifferentHashes() {
        assertThat(HashUtils.sha256("url-a"))
                .isNotEqualTo(HashUtils.sha256("url-b"));
    }

    @Test
    void sha256_emptyStringProducesKnownHash() {
        // SHA-256 of empty string is well-known
        assertThat(HashUtils.sha256(""))
                .isEqualTo("e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855");
    }

    // -------------------------------------------------------------------------
    // discoverCategory – routing (SCROLL_AJAX → Playwright, other → Jsoup)
    // -------------------------------------------------------------------------

    @Test
    void discoverCategory_usesPlaywrightWhenScrollAjax() {
        Category category = buildCategory(PaginationType.SCROLL_AJAX, "https://example.com/cat", "a.item");
        when(crawlerProperties.getDiscoveryMaxPages()).thenReturn(3);
        when(playwrightClient.render(any()))
                .thenReturn(new PlaywrightClient.RenderResponse("<html><body></body></html>", List.of()));

        service.discoverCategory(category);

        verify(playwrightClient).render(any());
        verifyNoMoreInteractions(playwrightClient);
    }

    @Test
    void discoverCategory_savesNewUrlsDiscoveredViaPlaywright() {
        Category category = buildCategory(PaginationType.SCROLL_AJAX, "https://example.com/cat", "a.item");
        when(crawlerProperties.getDiscoveryMaxPages()).thenReturn(3);

        String html = "<html><body>"
                + "<a class='item' href='https://example.com/product/1'>P1</a>"
                + "<a class='item' href='https://example.com/product/2'>P2</a>"
                + "</body></html>";
        when(playwrightClient.render(any()))
                .thenReturn(new PlaywrightClient.RenderResponse(html, List.of()));
        when(discoveredUrlRepository.findByUrlHash(anyString())).thenReturn(Optional.empty());

        service.discoverCategory(category);

        ArgumentCaptor<DiscoveredUrl> captor = ArgumentCaptor.forClass(DiscoveredUrl.class);
        verify(discoveredUrlRepository, times(2)).save(captor.capture());

        List<DiscoveredUrl> saved = captor.getAllValues();
        assertThat(saved).extracting(DiscoveredUrl::getStatus).containsOnly(UrlStatus.PENDING);
        assertThat(saved).extracting(DiscoveredUrl::getRetryCount).containsOnly(0);
        assertThat(saved).extracting(DiscoveredUrl::getUrl)
                .containsExactlyInAnyOrder(
                        "https://example.com/product/1",
                        "https://example.com/product/2");
    }

    @Test
    void discoverCategory_skipsDuplicateUrlsFromPlaywright() {
        Category category = buildCategory(PaginationType.SCROLL_AJAX, "https://example.com/cat", "a.item");
        when(crawlerProperties.getDiscoveryMaxPages()).thenReturn(3);

        String html = "<html><body>"
                + "<a class='item' href='https://example.com/product/1'>P1</a>"
                + "</body></html>";
        when(playwrightClient.render(any()))
                .thenReturn(new PlaywrightClient.RenderResponse(html, List.of()));
        when(discoveredUrlRepository.findByUrlHash(anyString()))
                .thenReturn(Optional.of(new DiscoveredUrl()));

        service.discoverCategory(category);

        verify(discoveredUrlRepository, never()).save(any(DiscoveredUrl.class));
    }

    @Test
    void discoverCategory_stripsFragmentFromDiscoveredUrls() {
        Category category = buildCategory(PaginationType.SCROLL_AJAX, "https://example.com/cat", "a.item");
        when(crawlerProperties.getDiscoveryMaxPages()).thenReturn(3);

        String html = "<html><body>"
                + "<a class='item' href='https://example.com/product/1#details'>P1</a>"
                + "</body></html>";
        when(playwrightClient.render(any()))
                .thenReturn(new PlaywrightClient.RenderResponse(html, List.of()));
        when(discoveredUrlRepository.findByUrlHash(anyString())).thenReturn(Optional.empty());

        service.discoverCategory(category);

        ArgumentCaptor<DiscoveredUrl> captor = ArgumentCaptor.forClass(DiscoveredUrl.class);
        verify(discoveredUrlRepository).save(captor.capture());
        assertThat(captor.getValue().getUrl()).isEqualTo("https://example.com/product/1");
    }

    @Test
    void discoverCategory_savedUrlHasNonNullHashAndTimestamp() {
        Category category = buildCategory(PaginationType.SCROLL_AJAX, "https://example.com/cat", "a.item");
        when(crawlerProperties.getDiscoveryMaxPages()).thenReturn(3);

        String html = "<html><body>"
                + "<a class='item' href='https://example.com/product/99'>P99</a>"
                + "</body></html>";
        when(playwrightClient.render(any()))
                .thenReturn(new PlaywrightClient.RenderResponse(html, List.of()));
        when(discoveredUrlRepository.findByUrlHash(anyString())).thenReturn(Optional.empty());

        service.discoverCategory(category);

        ArgumentCaptor<DiscoveredUrl> captor = ArgumentCaptor.forClass(DiscoveredUrl.class);
        verify(discoveredUrlRepository).save(captor.capture());
        DiscoveredUrl saved = captor.getValue();
        assertThat(saved.getUrlHash()).isNotNull().hasSize(64);
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private static Category buildCategory(PaginationType type, String url, String selector) {
        Site site = new Site();
        site.setId(1L);
        site.setPolitenessDelayMs(0);

        Category category = new Category();
        category.setId(10L);
        category.setSite(site);
        category.setName("Test Category");
        category.setUrl(url);
        category.setPaginationType(type);
        category.setItemLinkSelector(selector);
        category.setEnabled(true);
        return category;
    }
}
