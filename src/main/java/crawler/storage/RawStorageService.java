package crawler.storage;

import crawler.config.CrawlerProperties;
import crawler.support.HashUtils;
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

    public RawStorageService(CrawlerProperties properties) {
        this.baseDir = Path.of(properties.getRawDataDir());
    }

    public String saveHtml(String html, Long siteId, String url) throws IOException {
        return save(html, siteId, url, ".html");
    }

    public String saveJson(String json, Long siteId, String url) throws IOException {
        return save(json, siteId, url, ".json");
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
