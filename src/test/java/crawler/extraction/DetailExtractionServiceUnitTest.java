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
import crawler.support.PageFetcher;
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

    @Mock
    private PageFetcher pageFetcher;

    private DetailExtractionService service;

    @BeforeEach
    void setUp() {
        service = new DetailExtractionService(
                discoveredUrlRepository, extractedItemRepository, siteRepository,
                rawStorageService, crawlerProperties, new ObjectMapper(), playwrightClient,
                pageFetcher, new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
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
    // processBatch – HTML strategy extension: attributes, lists, regex, label/value blocks
    // -------------------------------------------------------------------------

    @Test
    void processBatch_extractsAttributeSelectorFromHtml() throws Exception {
        ExtractionConfig config = new ExtractionConfig();
        config.setDetailStrategy(DetailStrategy.HTML);
        config.setFieldSelectors(Map.of("coverImage", "meta[property=og:image]::attr(content)"));

        Site site = buildSite(1L, config);
        DiscoveredUrl url = buildDiscoveredUrl(site, "https://example.com/product/1");

        when(siteRepository.findById(1L)).thenReturn(Optional.of(site));
        when(crawlerProperties.getExtractionBatchSize()).thenReturn(10);
        when(discoveredUrlRepository.findByCategory_Site_IdAndStatus(any(), any(), any()))
                .thenReturn(List.of(url));
        when(pageFetcher.fetch(any())).thenReturn(org.jsoup.Jsoup.parse(
                "<html><head><meta property=\"og:image\" content=\"https://img.example.com/cover.jpg\">"
                        + "</head><body></body></html>", url.getUrl()));
        when(rawStorageService.saveHtml(any(), any(), any())).thenReturn("1/2024-01-01/abc.html");

        service.processBatch(1L);

        ArgumentCaptor<crawler.model.ExtractedItem> itemCaptor =
                ArgumentCaptor.forClass(crawler.model.ExtractedItem.class);
        verify(extractedItemRepository).save(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getProperties())
                .contains("https://img.example.com/cover.jpg");
    }

    @Test
    void processBatch_extractsListFieldSelectors() throws Exception {
        ExtractionConfig config = new ExtractionConfig();
        config.setDetailStrategy(DetailStrategy.HTML);
        config.setListFieldSelectors(Map.of("images", ".sp-slide img::attr(data-src)"));

        Site site = buildSite(1L, config);
        DiscoveredUrl url = buildDiscoveredUrl(site, "https://example.com/product/1");

        when(siteRepository.findById(1L)).thenReturn(Optional.of(site));
        when(crawlerProperties.getExtractionBatchSize()).thenReturn(10);
        when(discoveredUrlRepository.findByCategory_Site_IdAndStatus(any(), any(), any()))
                .thenReturn(List.of(url));
        when(pageFetcher.fetch(any())).thenReturn(org.jsoup.Jsoup.parse(
                "<html><body>"
                        + "<div class='sp-slide'><img data-src='https://img.example.com/1.jpg'></div>"
                        + "<div class='sp-slide'><img data-src='https://img.example.com/2.jpg'></div>"
                        + "</body></html>", url.getUrl()));
        when(rawStorageService.saveHtml(any(), any(), any())).thenReturn("1/2024-01-01/abc.html");

        service.processBatch(1L);

        ArgumentCaptor<crawler.model.ExtractedItem> itemCaptor =
                ArgumentCaptor.forClass(crawler.model.ExtractedItem.class);
        verify(extractedItemRepository).save(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getProperties())
                .contains("https://img.example.com/1.jpg")
                .contains("https://img.example.com/2.jpg");
    }

    @Test
    void processBatch_extractsRegexFields() throws Exception {
        ExtractionConfig config = new ExtractionConfig();
        config.setDetailStrategy(DetailStrategy.HTML);
        config.setRegexFields(Map.of(
                "latitude", "setView\\(\\[(-?[0-9.]+),\\s*-?[0-9.]+\\]",
                "longitude", "setView\\(\\[-?[0-9.]+,\\s*(-?[0-9.]+)\\]"));

        Site site = buildSite(1L, config);
        DiscoveredUrl url = buildDiscoveredUrl(site, "https://example.com/product/1");

        when(siteRepository.findById(1L)).thenReturn(Optional.of(site));
        when(crawlerProperties.getExtractionBatchSize()).thenReturn(10);
        when(discoveredUrlRepository.findByCategory_Site_IdAndStatus(any(), any(), any()))
                .thenReturn(List.of(url));
        when(pageFetcher.fetch(any())).thenReturn(org.jsoup.Jsoup.parse(
                "<html><body><script>var map = L.map('m').setView([-12.19984, -77.01840], 14);"
                        + "</script></body></html>", url.getUrl()));
        when(rawStorageService.saveHtml(any(), any(), any())).thenReturn("1/2024-01-01/abc.html");

        service.processBatch(1L);

        ArgumentCaptor<crawler.model.ExtractedItem> itemCaptor =
                ArgumentCaptor.forClass(crawler.model.ExtractedItem.class);
        verify(extractedItemRepository).save(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getProperties())
                .contains("-12.19984")
                .contains("-77.01840");
    }

    @Test
    void processBatch_extractsLabelValueBlocksWithNormalizedKeys() throws Exception {
        crawler.model.LabelValueBlockConfig block = new crawler.model.LabelValueBlockConfig();
        block.setContainerSelector("#specs");
        block.setItemSelector(".col-6");
        block.setLabelSelector("label");
        block.setValueSelector("p");
        block.setTargetKey("specs");

        ExtractionConfig config = new ExtractionConfig();
        config.setDetailStrategy(DetailStrategy.HTML);
        config.setLabelValueBlocks(List.of(block));

        Site site = buildSite(1L, config);
        DiscoveredUrl url = buildDiscoveredUrl(site, "https://example.com/product/1");

        when(siteRepository.findById(1L)).thenReturn(Optional.of(site));
        when(crawlerProperties.getExtractionBatchSize()).thenReturn(10);
        when(discoveredUrlRepository.findByCategory_Site_IdAndStatus(any(), any(), any()))
                .thenReturn(List.of(url));
        when(pageFetcher.fetch(any())).thenReturn(org.jsoup.Jsoup.parse(
                "<html><body><div id='specs'>"
                        + "<div class='col-6'><label>Área Terreno</label><p>625.00 m2</p></div>"
                        + "<div class='col-6'><label>N° de Pisos</label><p>0</p></div>"
                        + "</div></body></html>", url.getUrl()));
        when(rawStorageService.saveHtml(any(), any(), any())).thenReturn("1/2024-01-01/abc.html");

        service.processBatch(1L);

        ArgumentCaptor<crawler.model.ExtractedItem> itemCaptor =
                ArgumentCaptor.forClass(crawler.model.ExtractedItem.class);
        verify(extractedItemRepository).save(itemCaptor.capture());
        assertThat(itemCaptor.getValue().getProperties())
                .contains("\"area_terreno\":\"625.00 m2\"")
                .contains("\"n_de_pisos\":\"0\"");
    }

    // -------------------------------------------------------------------------
    // processBatch – full REMAX detail page fixture (mirrors sites-config.yml)
    // -------------------------------------------------------------------------

    @Test
    void processBatch_extractsRemaxPropertyFixture() throws Exception {
        ExtractionConfig config = new ExtractionConfig();
        config.setDetailStrategy(DetailStrategy.HTML);
        config.setFieldSelectors(Map.of(
                "title", ".titulo_01 h1",
                "propertyType", ".status_float .badge-blue",
                "location", ".titulo_07 h5",
                "coverImage", "meta[property=og:image]::attr(content)"));
        config.setListFieldSelectors(Map.of("images", ".sp-slide img::attr(data-src)"));
        config.setRegexFields(Map.of(
                "externalId", "ID:\\s*(\\d+)",
                "priceSoles", "S/\\.\\s*([\\d,\\.']+)",
                "priceUsd", "USD\\s*([\\d,\\.']+)",
                "agentPhone", "href=\"tel:(\\d+)\"",
                "latitude", "setView\\(\\[(-?[0-9.]+),\\s*-?[0-9.]+\\]",
                "longitude", "setView\\(\\[-?[0-9.]+,\\s*(-?[0-9.]+)\\]",
                "publishedDate", "Publicada\\s*:\\s*([^<]+)<br"));
        crawler.model.LabelValueBlockConfig block = new crawler.model.LabelValueBlockConfig();
        block.setContainerSelector("#collapse_2, #collapse_3");
        block.setItemSelector(".col-6");
        block.setLabelSelector("label.__bodyt");
        block.setValueSelector("p.__bodyc");
        block.setTargetKey("specs");
        config.setLabelValueBlocks(List.of(block));

        Site site = buildSite(1L, config);
        DiscoveredUrl url = buildDiscoveredUrl(site,
                "https://www.remax.pe/web/search/property/propiedad-terreno-en-venta-1187626/");

        when(siteRepository.findById(1L)).thenReturn(Optional.of(site));
        when(crawlerProperties.getExtractionBatchSize()).thenReturn(10);
        when(discoveredUrlRepository.findByCategory_Site_IdAndStatus(any(), any(), any()))
                .thenReturn(List.of(url));
        when(pageFetcher.fetch(any())).thenReturn(org.jsoup.Jsoup.parse(REMAX_FIXTURE_HTML, url.getUrl()));
        when(rawStorageService.saveHtml(any(), any(), any())).thenReturn("1/2024-01-01/abc.html");

        service.processBatch(1L);

        ArgumentCaptor<crawler.model.ExtractedItem> itemCaptor =
                ArgumentCaptor.forClass(crawler.model.ExtractedItem.class);
        verify(extractedItemRepository).save(itemCaptor.capture());
        String properties = itemCaptor.getValue().getProperties();

        assertThat(properties)
                .contains("Venta De Terreno En Zona De Cedros De Villa")
                .contains("TERRENO URBANO EN VENTA")
                .contains("Lima, Lima, Chorrillos")
                .contains("\"externalId\":\"1187626\"")
                .contains("\"priceSoles\":\"1'690,000.00\"")
                .contains("\"priceUsd\":\"500,000.00\"")
                .contains("\"agentPhone\":\"965098513\"")
                .contains("-12.199848642988874")
                .contains("-77.01840437589111")
                .contains("26 de Junio de 2026")
                .contains("cover_1187626.jpg")
                .contains("8e6be013-9ec3-4d72-b860-e735ef254.jpg")
                .contains("\"area_terreno\":\"625.00 m2\"")
                .contains("\"medidas\":\"7.42 X 36.08\"")
                .contains("\"servicio_de_gas\":\"Cálidda\"");
        assertThat(itemCaptor.getValue().getTitle())
                .isEqualTo("Venta De Terreno En Zona De Cedros De Villa");
    }

    private static final String REMAX_FIXTURE_HTML = """
            <html><head>
            <meta property="og:image" content="https://sfo3.digitaloceanspaces.com/media-remax-peru/media/public/photos/propertys/1187626/cover_1187626.jpg">
            </head><body>
            <div class="__slider">
              <div class="sp-slides">
                <div class="sp-slide"><img data-src="https://sfo3.digitaloceanspaces.com/media-remax-peru/media/public/photos/propertys/1187626/cover_1187626.jpg"/></div>
                <div class="sp-slide"><img data-src="https://sfo3.digitaloceanspaces.com/media-remax-peru/media/public/photos/propertys/1187626/8e6be013-9ec3-4d72-b860-e735ef254.jpg?X-Amz-Signature=abc"/></div>
              </div>
            </div>
            <div class="status_float">
              <span class="badge badge-pill base-badge badge-blue">TERRENO URBANO EN VENTA</span>
              <span class="badge badge-pill base-badge badge-danger">ID: 1187626</span>
            </div>
            <div class="titulo_07"><h5><i class="fas fa-map-marker-alt"></i>&nbsp;&nbsp;Lima, Lima, Chorrillos</h5></div>
            <div class="titulo_01"><h1>Venta De Terreno En Zona De Cedros De Villa</h1></div>
            <div class="titulo_04"><ul><li>S/. 1'690,000.00</li><li>-</li><li>USD 500,000.00</li></ul></div>
            <div id="collapse_2">
              <div class="col-6 col-md-4"><label class="__bodyt">Área Terreno</label><p class="__bodyc">625.00 m2</p></div>
              <div class="col-6 col-md-4"><label class="__bodyt">Área Construida</label><p class="__bodyc">0.00 m²</p></div>
              <div class="col-6 col-md-4"><label class="__bodyt">Medidas</label><p class="__bodyc">7.42 X 36.08</p></div>
            </div>
            <div id="collapse_3">
              <div class="col-6 col-md-3"><label class="__bodyt">Antigüedad</label><p class="__bodyc">0 Años</p></div>
              <div class="col-6 col-md-3"><label class="__bodyt">Servicio de Gas</label><p class="__bodyc">Cálidda</p></div>
            </div>
            <div class="__agenteintpro">
              <h2>Rodolfo Tito Flores</h2>
              <h4>REMAX ABITARE</h4>
              <a href="tel:965098513" class="contact-images" title="Llamar"></a>
            </div>
            <script>
              var content = '<p>Publicada : 26 de Junio de 2026<br><br>';
              var map = L.map('map_property').setView([-12.199848642988874, -77.01840437589111], 14);
            </script>
            </body></html>
            """;

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
        category.setPaginationType(PaginationType.PAGE_PARAM);
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
