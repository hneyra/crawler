package crawler.integration;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static org.assertj.core.api.Assertions.assertThat;

import com.github.tomakehurst.wiremock.junit5.WireMockExtension;
import crawler.config.DataInitializer;
import crawler.discovery.ListDiscoveryService;
import crawler.model.Category;
import crawler.model.CategoryRepository;
import crawler.model.DiscoveredUrlRepository;
import crawler.model.PaginationType;
import crawler.model.Site;
import crawler.model.SiteRepository;
import crawler.model.UrlStatus;
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class ListDiscoveryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @RegisterExtension
    static WireMockExtension wireMock = WireMockExtension.newInstance()
            .options(com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig()
                    .dynamicPort())
            .build();

    @MockBean
    @SuppressWarnings("unused")
    private DataInitializer dataInitializer;

    @Autowired
    private ListDiscoveryService listDiscoveryService;

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private DiscoveredUrlRepository discoveredUrlRepository;

    private Site site;

    @BeforeEach
    void setUp() {
        discoveredUrlRepository.deleteAll();
        categoryRepository.deleteAll();
        siteRepository.deleteAll();

        site = new Site();
        site.setName("WireMock Site");
        site.setBaseUrl(wireMock.baseUrl());
        site.setEnabled(true);
        site.setPolitenessDelayMs(0);
        site.setCreatedAt(Instant.now());
        site = siteRepository.save(site);
    }

    // -------------------------------------------------------------------------
    // STATIC pagination (Jsoup)
    // -------------------------------------------------------------------------

    @Test
    void discoverCategory_savesProductUrlsFromSinglePage() {
        stubFor(get(urlEqualTo("/category/phones"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.TEXT_HTML_VALUE)
                        .withBody(buildHtmlPage(
                                List.of("/product/1", "/product/2", "/product/3"),
                                null))));

        Category category = saveCategory(
                wireMock.baseUrl() + "/category/phones",
                PaginationType.STATIC,
                "a.item");

        listDiscoveryService.discoverCategory(category);

        List<crawler.model.DiscoveredUrl> saved = discoveredUrlRepository.findAll();
        assertThat(saved).hasSize(3);
        assertThat(saved).extracting(crawler.model.DiscoveredUrl::getStatus)
                .containsOnly(UrlStatus.PENDING);
        assertThat(saved).extracting(crawler.model.DiscoveredUrl::getUrl)
                .containsExactlyInAnyOrder(
                        wireMock.baseUrl() + "/product/1",
                        wireMock.baseUrl() + "/product/2",
                        wireMock.baseUrl() + "/product/3");
    }

    @Test
    void discoverCategory_followsNextPageLink() {
        stubFor(get(urlEqualTo("/category/phones"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.TEXT_HTML_VALUE)
                        .withBody(buildHtmlPage(
                                List.of("/product/1"),
                                "/category/phones?page=2"))));

        stubFor(get(urlEqualTo("/category/phones?page=2"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.TEXT_HTML_VALUE)
                        .withBody(buildHtmlPage(
                                List.of("/product/2"),
                                null))));

        Category category = saveCategory(
                wireMock.baseUrl() + "/category/phones",
                PaginationType.STATIC,
                "a.item");

        listDiscoveryService.discoverCategory(category);

        assertThat(discoveredUrlRepository.findAll()).hasSize(2);
    }

    @Test
    void discoverCategory_deduplicatesUrls() {
        // Same URL appears twice across two pages
        stubFor(get(urlEqualTo("/category/phones"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.TEXT_HTML_VALUE)
                        .withBody(buildHtmlPage(
                                List.of("/product/1"),
                                "/category/phones?page=2"))));

        stubFor(get(urlEqualTo("/category/phones?page=2"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.TEXT_HTML_VALUE)
                        .withBody(buildHtmlPage(
                                List.of("/product/1"),   // duplicate
                                null))));

        Category category = saveCategory(
                wireMock.baseUrl() + "/category/phones",
                PaginationType.STATIC,
                "a.item");

        listDiscoveryService.discoverCategory(category);

        assertThat(discoveredUrlRepository.findAll()).hasSize(1);
    }

    @Test
    void discoverCategory_handlesPageFetchError_gracefully() {
        stubFor(get(urlEqualTo("/category/errors"))
                .willReturn(aResponse().withStatus(500)));

        Category category = saveCategory(
                wireMock.baseUrl() + "/category/errors",
                PaginationType.STATIC,
                "a.item");

        // Should not throw
        listDiscoveryService.discoverCategory(category);

        assertThat(discoveredUrlRepository.findAll()).isEmpty();
    }

    @Test
    void discoverCategory_urlHashIsSha256OfNormalizedUrl() {
        String productPath = "/product/42";
        stubFor(get(urlEqualTo("/category/test"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.TEXT_HTML_VALUE)
                        .withBody(buildHtmlPage(List.of(productPath), null))));

        Category category = saveCategory(
                wireMock.baseUrl() + "/category/test",
                PaginationType.STATIC,
                "a.item");

        listDiscoveryService.discoverCategory(category);

        String expectedUrl = wireMock.baseUrl() + productPath;
        String expectedHash = ListDiscoveryService.sha256(expectedUrl);

        assertThat(discoveredUrlRepository.findByUrlHash(expectedHash)).isPresent();
    }

    @Test
    void discoverCategory_respectsCustomNextPageSelector() {
        stubFor(get(urlEqualTo("/shop"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.TEXT_HTML_VALUE)
                        .withBody("""
                                <html><body>
                                <a class='item' href='/product/1'>P1</a>
                                <a class='custom-next' href='/shop?p=2'>Siguiente</a>
                                </body></html>
                                """)));

        stubFor(get(urlEqualTo("/shop?p=2"))
                .willReturn(aResponse()
                        .withStatus(200)
                        .withHeader("Content-Type", MediaType.TEXT_HTML_VALUE)
                        .withBody(buildHtmlPage(List.of("/product/2"), null))));

        Category category = saveCategory(
                wireMock.baseUrl() + "/shop",
                PaginationType.STATIC,
                "a.item");
        category.setNextPageSelector("a.custom-next");
        categoryRepository.save(category);

        listDiscoveryService.discoverCategory(category);

        assertThat(discoveredUrlRepository.findAll()).hasSize(2);
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private Category saveCategory(String url, PaginationType type, String selector) {
        Category cat = new Category();
        cat.setSite(site);
        cat.setName("Test Category");
        cat.setUrl(url);
        cat.setPaginationType(type);
        cat.setItemLinkSelector(selector);
        cat.setEnabled(true);
        return categoryRepository.save(cat);
    }

    private static String buildHtmlPage(List<String> productPaths, String nextPath) {
        StringBuilder sb = new StringBuilder("<html><body>\n");
        for (String path : productPaths) {
            sb.append("<a class='item' href='").append(path).append("'>Product</a>\n");
        }
        if (nextPath != null) {
            sb.append("<a rel='next' href='").append(nextPath).append("'>Next</a>\n");
        }
        sb.append("</body></html>");
        return sb.toString();
    }
}
