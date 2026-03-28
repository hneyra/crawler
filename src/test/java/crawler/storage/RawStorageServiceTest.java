package crawler.storage;

import static org.assertj.core.api.Assertions.assertThat;

import crawler.config.CrawlerProperties;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class RawStorageServiceTest {

    @TempDir
    Path tempDir;

    private RawStorageService service;

    @BeforeEach
    void setUp() {
        CrawlerProperties props = new CrawlerProperties();
        props.setRawDataDir(tempDir.toString());
        service = new RawStorageService(props, new io.micrometer.core.instrument.simple.SimpleMeterRegistry());
    }

    // -------------------------------------------------------------------------
    // saveHtml
    // -------------------------------------------------------------------------

    @Test
    void saveHtml_createsFileWithHtmlExtension() throws IOException {
        String path = service.saveHtml("<html>test</html>", 1L, "https://example.com/p/1");
        assertThat(path).endsWith(".html");
        assertThat(tempDir.resolve(path)).exists();
    }

    @Test
    void saveHtml_pathIncludesSiteIdAndCurrentDate() throws IOException {
        String today = LocalDate.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd"));
        String path = service.saveHtml("<html></html>", 42L, "https://example.com/p");
        assertThat(path).startsWith("42/" + today + "/");
    }

    @Test
    void saveHtml_persistsContentCorrectly() throws IOException {
        String content = "<html><body><h1>Hello</h1></body></html>";
        String path = service.saveHtml(content, 1L, "https://example.com/test");
        assertThat(Files.readString(tempDir.resolve(path))).isEqualTo(content);
    }

    @Test
    void saveHtml_sameUrlProducesSameFilePath() throws IOException {
        String url = "https://example.com/product/99";
        String path1 = service.saveHtml("<html>v1</html>", 1L, url);
        String path2 = service.saveHtml("<html>v2</html>", 1L, url);
        assertThat(path1).isEqualTo(path2);
    }

    @Test
    void saveHtml_differentUrlsProduceDifferentFilePaths() throws IOException {
        String path1 = service.saveHtml("<html></html>", 1L, "https://example.com/product/1");
        String path2 = service.saveHtml("<html></html>", 1L, "https://example.com/product/2");
        assertThat(path1).isNotEqualTo(path2);
    }

    @Test
    void saveHtml_usesForwardSlashesInReturnedPath() throws IOException {
        String path = service.saveHtml("<html></html>", 1L, "https://example.com/p");
        assertThat(path).doesNotContain("\\");
    }

    @Test
    void saveHtml_differentSiteIdsSaveToSeparatePaths() throws IOException {
        String path1 = service.saveHtml("<html></html>", 1L, "https://example.com/p");
        String path2 = service.saveHtml("<html></html>", 2L, "https://example.com/p");
        assertThat(path1).startsWith("1/");
        assertThat(path2).startsWith("2/");
        assertThat(path1).isNotEqualTo(path2);
    }

    // -------------------------------------------------------------------------
    // saveJson
    // -------------------------------------------------------------------------

    @Test
    void saveJson_createsFileWithJsonExtension() throws IOException {
        String path = service.saveJson("{\"key\":\"value\"}", 1L, "https://example.com/api/data");
        assertThat(path).endsWith(".json");
        assertThat(tempDir.resolve(path)).exists();
    }

    @Test
    void saveJson_persistsContentCorrectly() throws IOException {
        String content = "{\"name\":\"Product A\",\"price\":99.9}";
        String path = service.saveJson(content, 1L, "https://example.com/api/product/1");
        assertThat(Files.readString(tempDir.resolve(path))).isEqualTo(content);
    }

    @Test
    void saveJson_pathIncludesSiteId() throws IOException {
        String path = service.saveJson("{}", 7L, "https://example.com/api");
        assertThat(path).startsWith("7/");
    }

    // -------------------------------------------------------------------------
    // path structure
    // -------------------------------------------------------------------------

    @Test
    void savedFile_pathConsistsOfThreeSegments() throws IOException {
        String path = service.saveHtml("<html></html>", 1L, "https://example.com/p");
        // Expected: siteId/date/hash.html  → exactly 3 segments
        assertThat(path.split("/")).hasSize(3);
    }

    @Test
    void savedFile_fileNameIsHexString() throws IOException {
        String path = service.saveHtml("<html></html>", 1L, "https://example.com/p");
        String fileName = path.split("/")[2]; // e.g. "abc123...html"
        String nameWithoutExt = fileName.substring(0, fileName.lastIndexOf('.'));
        assertThat(nameWithoutExt).matches("[0-9a-f]{64}");
    }
}
