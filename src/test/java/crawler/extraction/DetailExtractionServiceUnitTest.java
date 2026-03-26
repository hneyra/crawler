package crawler.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import crawler.config.CrawlerProperties;
import crawler.discovery.PlaywrightClient;
import crawler.model.Category;
import crawler.model.DetailStrategy;
import crawler.model.DiscoveredUrl;
import crawler.model.DiscoveredUrlRepository;
import crawler.model.ExtractionConfig;
import crawler.model.ExtractedItemRepository;
import crawler.model.PaginationType;
import crawler.model.Site;
import crawler.model.SiteRepository;
import crawler.model.UrlStatus;
import crawler.storage.RawStorageService;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class DetailExtractionServiceUnitTest {

    @Mock
    private DiscoveredUrlRepository discoveredUrlRepository;

    @Mock
    private ExtractedItemRepository extractedItemRepository;

    @Mock
    private SiteRepository siteRepository;

    @Mock
    private RawStorageService rawStorageService;

    @Mock
    private CrawlerProperties crawlerProperties;

    @Mock
    private PlaywrightClient playwrightClient;

    private DetailExtractionService service;

    @BeforeEach
    void setUp() {
        service = new DetailExtractionService(
                discoveredUrlRepository, extractedItemRepository, siteRepository,
                rawStorageService, crawlerProperties, new ObjectMapper(), playwrightClient);
    }

    // -------------------------------------------------------------------------
    // processBatch – early exits
    // -------------------------------------------------------------------------

    @Test
    void processBatch_returnsEarlyWhenSiteNotFound() {
        when(siteRepository.findById(99L)).thenReturn(Optional.empty());

        service.processBatch(99L);

        verify(discoveredUrlRepository, never())
                .findByCategory_Site_IdAndStatus(any(), any(), any(Pageable.class));
    }

    @Test
    void processBatch_returnsEarlyWhenNoPendingUrls() {
        Site site = buildSite(1L, null);
        when(siteRepository.findById(1L)).thenReturn(Optional.of(site));
        when(crawlerProperties.getExtractionBatchSize()).thenReturn(10);
        when(discoveredUrlRepository.findByCategory_Site_IdAndStatus(
                eq(1L), eq(UrlStatus.PENDING), any(Pageable.class)))
                .thenReturn(List.of());

        service.processBatch(1L);

        verify(extractedItemRepository, never()).save(any());
    }

    // -------------------------------------------------------------------------
    // processBatch – URL status lifecycle
    // -------------------------------------------------------------------------

    @Test
    void processBatch_setsUrlInProgressBeforeProcessing() {
        Site site = buildSite(1L, null);
        DiscoveredUrl url = buildDiscoveredUrl(site, "https://this-host-does-not.exist/p");

        when(siteRepository.findById(1L)).thenReturn(Optional.of(site));
        when(crawlerProperties.getExtractionBatchSize()).thenReturn(10);
        when(discoveredUrlRepository.findByCategory_Site_IdAndStatus(any(), any(), any()))
                .thenReturn(List.of(url));

        service.processBatch(1L);

        // saveAll is called first (marks IN_PROGRESS), then save per URL (marks FAILED/COMPLETED)
        verify(discoveredUrlRepository).saveAll(any());
    }

    @Test
    void processBatch_marksUrlAsFailedWhenJsoupCannotConnect() {
        Site site = buildSite(1L, null);
        // .invalid TLD is RFC-reserved and guaranteed to not resolve
        DiscoveredUrl url = buildDiscoveredUrl(site, "https://host.that.does.not.exist.invalid/product/1");

        when(siteRepository.findById(1L)).thenReturn(Optional.of(site));
        when(crawlerProperties.getExtractionBatchSize()).thenReturn(10);
        when(discoveredUrlRepository.findByCategory_Site_IdAndStatus(any(), any(), any()))
                .thenReturn(List.of(url));

        service.processBatch(1L);

        ArgumentCaptor<DiscoveredUrl> captor = ArgumentCaptor.forClass(DiscoveredUrl.class);
        verify(discoveredUrlRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(UrlStatus.FAILED);
    }

    @Test
    void processBatch_incrementsRetryCountOnFailure() {
        Site site = buildSite(1L, null);
        DiscoveredUrl url = buildDiscoveredUrl(site, "https://host.that.does.not.exist.invalid/product/1");
        url.setRetryCount(2);

        when(siteRepository.findById(1L)).thenReturn(Optional.of(site));
        when(crawlerProperties.getExtractionBatchSize()).thenReturn(10);
        when(discoveredUrlRepository.findByCategory_Site_IdAndStatus(any(), any(), any()))
                .thenReturn(List.of(url));

        service.processBatch(1L);

        ArgumentCaptor<DiscoveredUrl> captor = ArgumentCaptor.forClass(DiscoveredUrl.class);
        verify(discoveredUrlRepository).save(captor.capture());
        assertThat(captor.getValue().getRetryCount()).isEqualTo(3);
    }

    @Test
    void processBatch_setsLastAttemptAtOnFailure() {
        Instant before = Instant.now();
        Site site = buildSite(1L, null);
        DiscoveredUrl url = buildDiscoveredUrl(site, "https://host.that.does.not.exist.invalid/product/1");

        when(siteRepository.findById(1L)).thenReturn(Optional.of(site));
        when(crawlerProperties.getExtractionBatchSize()).thenReturn(10);
        when(discoveredUrlRepository.findByCategory_Site_IdAndStatus(any(), any(), any()))
                .thenReturn(List.of(url));

        service.processBatch(1L);

        ArgumentCaptor<DiscoveredUrl> captor = ArgumentCaptor.forClass(DiscoveredUrl.class);
        verify(discoveredUrlRepository).save(captor.capture());
        assertThat(captor.getValue().getLastAttemptAt()).isAfterOrEqualTo(before);
    }

    // -------------------------------------------------------------------------
    // processBatch – AJAX strategy delegates to Playwright
    // -------------------------------------------------------------------------

    @Test
    void processBatch_delegatesToPlaywrightWhenAjaxStrategy() throws Exception {
        ExtractionConfig config = new ExtractionConfig();
        config.setDetailStrategy(DetailStrategy.AJAX);
        config.setInterceptPatterns(List.of("*/api/*"));

        Site site = buildSite(1L, config);
        DiscoveredUrl url = buildDiscoveredUrl(site, "https://example.com/product/1");

        when(siteRepository.findById(1L)).thenReturn(Optional.of(site));
        when(crawlerProperties.getExtractionBatchSize()).thenReturn(10);
        when(discoveredUrlRepository.findByCategory_Site_IdAndStatus(any(), any(), any()))
                .thenReturn(List.of(url));
        when(playwrightClient.render(any()))
                .thenReturn(new PlaywrightClient.RenderResponse(
                        "<html><head><title>Test</title></head><body></body></html>",
                        List.of()));
        when(rawStorageService.saveHtml(any(), any(), any())).thenReturn("1/2024-01-01/abc.html");

        service.processBatch(1L);

        verify(playwrightClient).render(any());

        ArgumentCaptor<DiscoveredUrl> captor = ArgumentCaptor.forClass(DiscoveredUrl.class);
        verify(discoveredUrlRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(UrlStatus.COMPLETED);
    }

    @Test
    void processBatch_marksUrlFailedWhenPlaywrightThrows() {
        ExtractionConfig config = new ExtractionConfig();
        config.setDetailStrategy(DetailStrategy.AJAX);
        config.setInterceptPatterns(List.of("*/api/*"));

        Site site = buildSite(1L, config);
        DiscoveredUrl url = buildDiscoveredUrl(site, "https://example.com/product/1");

        when(siteRepository.findById(1L)).thenReturn(Optional.of(site));
        when(crawlerProperties.getExtractionBatchSize()).thenReturn(10);
        when(discoveredUrlRepository.findByCategory_Site_IdAndStatus(any(), any(), any()))
                .thenReturn(List.of(url));
        when(playwrightClient.render(any()))
                .thenThrow(new RuntimeException("Renderer unavailable"));

        service.processBatch(1L);

        ArgumentCaptor<DiscoveredUrl> captor = ArgumentCaptor.forClass(DiscoveredUrl.class);
        verify(discoveredUrlRepository).save(captor.capture());
        assertThat(captor.getValue().getStatus()).isEqualTo(UrlStatus.FAILED);
    }

    @Test
    void processBatch_extractsFieldSelectorsFromHtml() throws Exception {
        ExtractionConfig config = new ExtractionConfig();
        config.setDetailStrategy(DetailStrategy.HTML);
        config.setFieldSelectors(Map.of("title", "h1", "price", "span.price"));

        Site site = buildSite(1L, config);
        DiscoveredUrl url = buildDiscoveredUrl(site, "https://example.com/product/1");

        when(siteRepository.findById(1L)).thenReturn(Optional.of(site));
        when(crawlerProperties.getExtractionBatchSize()).thenReturn(10);
        when(discoveredUrlRepository.findByCategory_Site_IdAndStatus(any(), any(), any()))
                .thenReturn(List.of(url));
        when(playwrightClient.render(any()))
                .thenReturn(new PlaywrightClient.RenderResponse(
                        "<html><head><title>Product</title></head><body>"
                                + "<h1>My Product</h1><span class='price'>$9.99</span>"
                                + "</body></html>",
                        List.of()));

        // Use Playwright strategy so we can inject full HTML without real HTTP calls
        config.setDetailStrategy(DetailStrategy.AJAX);
        config.setInterceptPatterns(List.of());
        when(rawStorageService.saveHtml(any(), any(), any())).thenReturn("1/2024-01-01/abc.html");

        service.processBatch(1L);

        ArgumentCaptor<crawler.model.ExtractedItem> itemCaptor =
                ArgumentCaptor.forClass(crawler.model.ExtractedItem.class);
        verify(extractedItemRepository).save(itemCaptor.capture());
        String properties = itemCaptor.getValue().getProperties();
        assertThat(properties).contains("My Product").contains("$9.99");
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private static Site buildSite(Long id, ExtractionConfig config) {
        Site site = new Site();
        site.setId(id);
        site.setName("Test Site");
        site.setBaseUrl("https://example.com");
        site.setEnabled(true);
        site.setPolitenessDelayMs(0);
        site.setExtractionConfig(config);
        site.setCreatedAt(Instant.now());
        return site;
    }

    private static DiscoveredUrl buildDiscoveredUrl(Site site, String url) {
        Category category = new Category();
        category.setId(1L);
        category.setSite(site);
        category.setName("Test Cat");
        category.setUrl("https://example.com/cat");
        category.setPaginationType(PaginationType.STATIC);
        category.setItemLinkSelector("a");
        category.setEnabled(true);

        DiscoveredUrl du = new DiscoveredUrl();
        du.setId(1L);
        du.setCategory(category);
        du.setUrl(url);
        du.setUrlHash("a".repeat(64));
        du.setStatus(UrlStatus.PENDING);
        du.setRetryCount(0);
        du.setCreatedAt(Instant.now());
        return du;
    }
}
