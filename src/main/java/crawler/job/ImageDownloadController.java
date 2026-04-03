package crawler.job;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import crawler.model.ExtractedItem;
import crawler.model.ExtractedItemRepository;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/items")
public class ImageDownloadController {

    private static final Logger log = LoggerFactory.getLogger(ImageDownloadController.class);
    private static final Pattern IMAGE_URL_PATTERN = Pattern.compile(
            "https?://.*\\.(jpe?g|png|webp|gif|bmp|svg|avif|tiff?)(\\?.*)?$", Pattern.CASE_INSENSITIVE);
    private static final Set<String> IMAGE_KEY_HINTS = Set.of(
            "image", "img", "photo", "picture", "thumbnail", "thumb", "imagen", "foto");

    private final ExtractedItemRepository extractedItemRepository;
    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;

    public ImageDownloadController(ExtractedItemRepository extractedItemRepository, ObjectMapper objectMapper) {
        this.extractedItemRepository = extractedItemRepository;
        this.objectMapper = objectMapper;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    @GetMapping("/{id}/images")
    public ResponseEntity<List<String>> listImages(@PathVariable Long id) {
        return extractedItemRepository.findById(id)
                .map(item -> ResponseEntity.ok(extractImageUrls(item)))
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/download-images")
    public ResponseEntity<byte[]> downloadImages(@PathVariable Long id) {
        var itemOpt = extractedItemRepository.findById(id);
        if (itemOpt.isEmpty()) {
            return ResponseEntity.notFound().build();
        }

        ExtractedItem item = itemOpt.get();
        List<String> imageUrls = extractImageUrls(item);
        if (imageUrls.isEmpty()) {
            return ResponseEntity.noContent().build();
        }

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            try (ZipOutputStream zos = new ZipOutputStream(baos)) {
                int index = 0;
                for (String url : imageUrls) {
                    try {
                        HttpRequest request = HttpRequest.newBuilder()
                                .uri(URI.create(url))
                                .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) Chrome/120.0.0.0")
                                .timeout(Duration.ofSeconds(15))
                                .GET()
                                .build();

                        HttpResponse<InputStream> response = httpClient.send(request,
                                HttpResponse.BodyHandlers.ofInputStream());

                        if (response.statusCode() == 200) {
                            String filename = buildFilename(url, index);
                            zos.putNextEntry(new ZipEntry(filename));
                            response.body().transferTo(zos);
                            zos.closeEntry();
                            index++;
                        } else {
                            log.warn("Failed to download image {}: HTTP {}", url, response.statusCode());
                        }
                    } catch (Exception e) {
                        log.warn("Error downloading image {}: {}", url, e.getMessage());
                    }
                }
            }

            if (baos.size() == 0) {
                return ResponseEntity.noContent().build();
            }

            String zipName = "images-item-" + id + ".zip";
            return ResponseEntity.ok()
                    .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + zipName + "\"")
                    .contentType(MediaType.parseMediaType("application/zip"))
                    .body(baos.toByteArray());

        } catch (Exception e) {
            log.error("Error creating zip for item {}", id, e);
            return ResponseEntity.internalServerError().build();
        }
    }

    private List<String> extractImageUrls(ExtractedItem item) {
        List<String> urls = new ArrayList<>();
        String props = item.getProperties();
        if (props == null || props.isBlank()) return urls;

        try {
            Map<String, Object> map = objectMapper.readValue(props, new TypeReference<>() {});
            collectImageUrls(map, "", urls);
        } catch (Exception e) {
            log.warn("Failed to parse properties for item {}: {}", item.getId(), e.getMessage());
        }
        return urls;
    }

    private void collectImageUrls(Object obj, String key, List<String> urls) {
        if (obj instanceof String s) {
            if (isImageUrl(s, key)) {
                urls.add(s);
            }
        } else if (obj instanceof Map<?, ?> map) {
            for (var entry : map.entrySet()) {
                collectImageUrls(entry.getValue(), String.valueOf(entry.getKey()), urls);
            }
        } else if (obj instanceof Collection<?> list) {
            for (Object elem : list) {
                collectImageUrls(elem, key, urls);
            }
        }
    }

    private boolean isImageUrl(String value, String key) {
        if (IMAGE_URL_PATTERN.matcher(value).matches()) {
            return true;
        }
        if (value.startsWith("http")) {
            String keyLower = key.toLowerCase();
            return IMAGE_KEY_HINTS.stream().anyMatch(keyLower::contains);
        }
        return false;
    }

    private String buildFilename(String url, int index) {
        String path = URI.create(url).getPath();
        int lastSlash = path.lastIndexOf('/');
        String name = lastSlash >= 0 ? path.substring(lastSlash + 1) : path;
        if (name.isBlank() || name.length() > 100) {
            name = "image-" + index;
        }
        return index + "-" + name;
    }
}
