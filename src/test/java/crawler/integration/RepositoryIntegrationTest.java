package crawler.integration;

import static org.assertj.core.api.Assertions.assertThat;

import crawler.config.DataInitializer;
import crawler.model.Category;
import crawler.model.CategoryRepository;
import crawler.model.DiscoveredUrl;
import crawler.model.DiscoveredUrlRepository;
import crawler.model.ExtractedItem;
import crawler.model.ExtractedItemRepository;
import crawler.model.PaginationType;
import crawler.model.Site;
import crawler.model.SiteRepository;
import crawler.model.UrlStatus;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@SpringBootTest
@Testcontainers
@ActiveProfiles("test")
class RepositoryIntegrationTest {

    @Container
    @ServiceConnection
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    @MockitoBean
    @SuppressWarnings("unused")
    private DataInitializer dataInitializer;

    @Autowired
    private SiteRepository siteRepository;

    @Autowired
    private CategoryRepository categoryRepository;

    @Autowired
    private DiscoveredUrlRepository discoveredUrlRepository;

    @Autowired
    private ExtractedItemRepository extractedItemRepository;

    private Site site;
    private Category category;

    @BeforeEach
    void setUp() {
        extractedItemRepository.deleteAll();
        discoveredUrlRepository.deleteAll();
        categoryRepository.deleteAll();
        siteRepository.deleteAll();

        site = new Site();
        site.setName("Test Site");
        site.setBaseUrl("https://example.com");
        site.setEnabled(true);
        site.setPolitenessDelayMs(500);
        site.setCreatedAt(Instant.now());
        site = siteRepository.save(site);

        category = new Category();
        category.setSite(site);
        category.setName("Electronics");
        category.setUrl("https://example.com/electronics");
        category.setPaginationType(PaginationType.PAGE_PARAM);
        category.setItemLinkSelector("a.product-link");
        category.setEnabled(true);
        category = categoryRepository.save(category);
    }

    // -------------------------------------------------------------------------
    // SiteRepository
    // -------------------------------------------------------------------------

    @Test
    void findByName_returnsExistingSite() {
        Optional<Site> found = siteRepository.findByName("Test Site");
        assertThat(found).isPresent();
        assertThat(found.get().getBaseUrl()).isEqualTo("https://example.com");
    }

    @Test
    void findByName_returnsEmptyForUnknownName() {
        assertThat(siteRepository.findByName("Nonexistent")).isEmpty();
    }

    // -------------------------------------------------------------------------
    // CategoryRepository
    // -------------------------------------------------------------------------

    @Test
    void findBySiteIdAndEnabledTrue_returnsEnabledCategories() {
        // Add a disabled category
        Category disabled = new Category();
        disabled.setSite(site);
        disabled.setName("Disabled Cat");
        disabled.setUrl("https://example.com/disabled");
        disabled.setPaginationType(PaginationType.PAGE_PARAM);
        disabled.setItemLinkSelector("a");
        disabled.setEnabled(false);
        categoryRepository.save(disabled);

        List<Category> result = categoryRepository.findBySiteIdAndEnabledTrue(site.getId());

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Electronics");
    }

    // -------------------------------------------------------------------------
    // DiscoveredUrlRepository
    // -------------------------------------------------------------------------

    @Test
    void findByUrlHash_returnsUrlWhenHashMatches() {
        DiscoveredUrl url = saveUrl("https://example.com/product/1", "hash1", UrlStatus.PENDING);

        Optional<DiscoveredUrl> found = discoveredUrlRepository.findByUrlHash("hash1");

        assertThat(found).isPresent();
        assertThat(found.get().getUrl()).isEqualTo("https://example.com/product/1");
    }

    @Test
    void findByUrlHash_returnsEmptyForUnknownHash() {
        assertThat(discoveredUrlRepository.findByUrlHash("nonexistent-hash")).isEmpty();
    }

    @Test
    void findByCategory_Site_IdAndStatus_returnsMatchingUrls() {
        saveUrl("https://example.com/product/1", "hash-p1", UrlStatus.PENDING);
        saveUrl("https://example.com/product/2", "hash-p2", UrlStatus.PENDING);
        saveUrl("https://example.com/product/3", "hash-c1", UrlStatus.COMPLETED);

        List<DiscoveredUrl> pending = discoveredUrlRepository
                .findByCategory_Site_IdAndStatus(site.getId(), UrlStatus.PENDING,
                        PageRequest.of(0, 10));

        assertThat(pending).hasSize(2);
        assertThat(pending).extracting(DiscoveredUrl::getStatus).containsOnly(UrlStatus.PENDING);
    }

    @Test
    void findByCategory_Site_IdAndStatus_respectsPageSize() {
        saveUrl("https://example.com/p/1", "hash1", UrlStatus.PENDING);
        saveUrl("https://example.com/p/2", "hash2", UrlStatus.PENDING);
        saveUrl("https://example.com/p/3", "hash3", UrlStatus.PENDING);

        List<DiscoveredUrl> page = discoveredUrlRepository
                .findByCategory_Site_IdAndStatus(site.getId(), UrlStatus.PENDING,
                        PageRequest.of(0, 2));

        assertThat(page).hasSize(2);
    }

    @Test
    void countByCategory_Site_IdAndStatus_countsCorrectly() {
        saveUrl("https://example.com/p/1", "hash1", UrlStatus.PENDING);
        saveUrl("https://example.com/p/2", "hash2", UrlStatus.FAILED);
        saveUrl("https://example.com/p/3", "hash3", UrlStatus.COMPLETED);

        assertThat(discoveredUrlRepository
                .countByCategory_Site_IdAndStatus(site.getId(), UrlStatus.PENDING)).isEqualTo(1);
        assertThat(discoveredUrlRepository
                .countByCategory_Site_IdAndStatus(site.getId(), UrlStatus.FAILED)).isEqualTo(1);
        assertThat(discoveredUrlRepository
                .countByCategory_Site_IdAndStatus(site.getId(), UrlStatus.COMPLETED)).isEqualTo(1);
    }

    @Test
    void countByCategory_Site_Id_countsAllUrls() {
        saveUrl("https://example.com/p/1", "hash1", UrlStatus.PENDING);
        saveUrl("https://example.com/p/2", "hash2", UrlStatus.COMPLETED);

        assertThat(discoveredUrlRepository.countByCategory_Site_Id(site.getId())).isEqualTo(2);
    }

    @Test
    void resetFailedUrls_resetUrlsUnderRetryLimit() {
        DiscoveredUrl url1 = saveUrl("https://example.com/p/1", "hash1", UrlStatus.FAILED);
        url1.setRetryCount(1);
        discoveredUrlRepository.save(url1);

        DiscoveredUrl url2 = saveUrl("https://example.com/p/2", "hash2", UrlStatus.FAILED);
        url2.setRetryCount(3);
        discoveredUrlRepository.save(url2);

        int reset = discoveredUrlRepository.resetFailedUrls(3);

        assertThat(reset).isEqualTo(1);
        assertThat(discoveredUrlRepository.findByUrlHash("hash1").get().getStatus())
                .isEqualTo(UrlStatus.PENDING);
        assertThat(discoveredUrlRepository.findByUrlHash("hash2").get().getStatus())
                .isEqualTo(UrlStatus.FAILED);
    }

    @Test
    void findLastAttemptBySiteId_returnsLatestAttempt() {
        Instant earlier = Instant.parse("2024-01-01T10:00:00Z");
        Instant later = Instant.parse("2024-01-01T12:00:00Z");

        DiscoveredUrl url1 = saveUrl("https://example.com/p/1", "hash1", UrlStatus.COMPLETED);
        url1.setLastAttemptAt(earlier);
        discoveredUrlRepository.save(url1);

        DiscoveredUrl url2 = saveUrl("https://example.com/p/2", "hash2", UrlStatus.COMPLETED);
        url2.setLastAttemptAt(later);
        discoveredUrlRepository.save(url2);

        Optional<Instant> last = discoveredUrlRepository.findLastAttemptBySiteId(site.getId());

        assertThat(last).isPresent();
        assertThat(last.get()).isEqualTo(later);
    }

    // -------------------------------------------------------------------------
    // ExtractedItemRepository
    // -------------------------------------------------------------------------

    @Test
    void countBySiteId_countsExtractedItems() {
        DiscoveredUrl url = saveUrl("https://example.com/p/1", "hash-ei1", UrlStatus.COMPLETED);
        saveExtractedItem(url, "Product A");
        saveExtractedItem(url, "Product B");

        assertThat(extractedItemRepository.countBySiteId(site.getId())).isEqualTo(2);
    }

    @Test
    void findByOrderByExtractedAtDesc_returnsItemsInDescendingOrder() {
        DiscoveredUrl url = saveUrl("https://example.com/p/1", "hash-order", UrlStatus.COMPLETED);
        ExtractedItem item1 = saveExtractedItem(url, "First");
        ExtractedItem item2 = saveExtractedItem(url, "Second");

        List<ExtractedItem> items = extractedItemRepository
                .findByOrderByExtractedAtDesc(PageRequest.of(0, 10));

        assertThat(items).hasSizeGreaterThanOrEqualTo(2);
        // Most recently extracted is first
        assertThat(items.get(0).getExtractedAt())
                .isAfterOrEqualTo(items.get(items.size() - 1).getExtractedAt());
    }

    @Test
    void findBySiteIdOrderByExtractedAtDesc_filtersBySite() {
        DiscoveredUrl url = saveUrl("https://example.com/p/1", "hash-site", UrlStatus.COMPLETED);
        saveExtractedItem(url, "Product For Site");

        List<ExtractedItem> items = extractedItemRepository
                .findBySiteIdOrderByExtractedAtDesc(site.getId(), PageRequest.of(0, 10));

        assertThat(items).isNotEmpty();
        assertThat(items).extracting(i -> i.getSite().getId()).containsOnly(site.getId());
    }

    // -------------------------------------------------------------------------
    // helpers
    // -------------------------------------------------------------------------

    private DiscoveredUrl saveUrl(String url, String hash, UrlStatus status) {
        DiscoveredUrl du = new DiscoveredUrl();
        du.setCategory(category);
        du.setUrl(url);
        du.setUrlHash(hash);
        du.setStatus(status);
        du.setRetryCount(0);
        du.setCreatedAt(Instant.now());
        return discoveredUrlRepository.save(du);
    }

    private ExtractedItem saveExtractedItem(DiscoveredUrl url, String title) {
        ExtractedItem item = new ExtractedItem();
        item.setDiscoveredUrl(url);
        item.setSite(site);
        item.setTitle(title);
        item.setProperties("{}");
        item.setExtractedAt(Instant.now());
        return extractedItemRepository.save(item);
    }
}
