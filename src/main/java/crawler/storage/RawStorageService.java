package crawler.storage;

import crawler.config.CrawlerProperties;
import crawler.support.HashUtils;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Service;

@Service
public class RawStorageService {

    private static final DateTimeFormatter DATE_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");

    private final Path baseDir;
    private final Counter snapshotsHtmlCounter;
    private final Counter snapshotsJsonCounter;

    public RawStorageService(CrawlerProperties properties, MeterRegistry registry) {
        this.baseDir = Path.of(properties.getRawDataDir());
        this.snapshotsHtmlCounter = Counter.builder("crawler.storage.snapshots")
                .tag("type", "html")
                .description("HTML snapshots saved")
                .register(registry);
        this.snapshotsJsonCounter = Counter.builder("crawler.storage.snapshots")
                .tag("type", "json")
                .description("JSON snapshots saved")
                .register(registry);
    }

    public String saveHtml(String html, Long siteId, String url) throws IOException {
        String path = save(html, siteId, url, ".html");
        snapshotsHtmlCounter.increment();
        return path;
    }

    public String saveJson(String json, Long siteId, String url) throws IOException {
        String path = save(json, siteId, url, ".json");
        snapshotsJsonCounter.increment();
        return path;
    }

    private String save(String content, Long siteId, String url, String extension) throws IOException {
        String dateDir = LocalDate.now().format(DATE_FMT);
        String hash = HashUtils.sha256(url);
        String fileName = hash + extension;

        Path relativePath = Path.of(siteId.toString(), dateDir, fileName);
        Path fullPath = baseDir.resolve(relativePath);

        Files.createDirectories(fullPath.getParent());
        Files.writeString(fullPath, content, StandardCharsets.UTF_8);

        return relativePath.toString().replace('\\', '/');
    }
}
