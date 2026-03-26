package crawler.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import crawler.model.Category;
import crawler.model.CategoryRepository;
import crawler.model.Site;
import crawler.model.SiteRepository;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

@Component
public class DataInitializer implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);
    private static final String CONFIG_FILE = "sites-config.yml";

    private final SiteRepository siteRepository;
    private final CategoryRepository categoryRepository;

    public DataInitializer(SiteRepository siteRepository, CategoryRepository categoryRepository) {
        this.siteRepository = siteRepository;
        this.categoryRepository = categoryRepository;
    }

    @Override
    public void run(String... args) throws Exception {
        Path configPath = Path.of(CONFIG_FILE);

        if (!Files.exists(configPath)) {
            createExampleConfig(configPath);
            log.info("Created example config file: {}", configPath.toAbsolutePath());
        }

        SitesConfig config = loadConfig(configPath);

        for (SiteDefinition siteDef : config.getSites()) {
            if (siteRepository.findByName(siteDef.getName()).isPresent()) {
                log.info("Site '{}' already exists, skipping", siteDef.getName());
                continue;
            }

            Site site = new Site();
            site.setName(siteDef.getName());
            site.setBaseUrl(siteDef.getBaseUrl());
            site.setEnabled(siteDef.isEnabled());
            site.setPolitenessDelayMs(siteDef.getPolitenessDelayMs());
            site.setExtractionConfig(siteDef.getExtractionConfig());
            site.setCreatedAt(Instant.now());
            site = siteRepository.save(site);
            log.info("Created site '{}'", site.getName());

            for (CategoryDefinition catDef : siteDef.getCategories()) {
                Category category = new Category();
                category.setSite(site);
                category.setName(catDef.getName());
                category.setUrl(catDef.getUrl());
                category.setPaginationType(catDef.getPaginationType());
                category.setItemLinkSelector(catDef.getItemLinkSelector());
                category.setNextPageSelector(catDef.getNextPageSelector());
                category.setEnabled(catDef.isEnabled());
                categoryRepository.save(category);
                log.info("  Created category '{}'", category.getName());
            }
        }
    }

    private SitesConfig loadConfig(Path path) throws IOException {
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        try (InputStream in = Files.newInputStream(path)) {
            return mapper.readValue(in, SitesConfig.class);
        }
    }

    private void createExampleConfig(Path path) throws IOException {
        String example = """
                sites:
                  - name: MiEcommerce
                    baseUrl: https://ejemplo.com
                    enabled: true
                    politenessDelayMs: 1000
                    extractionConfig:
                      detailStrategy: HTML
                      fieldSelectors:
                        title: "h1.product-title"
                        price: "span.price"
                        description: "div.product-description"
                      scriptPatterns:
                        - "__NEXT_DATA__\\s*=\\s*(\\{.*?\\});?"
                        - "window\\.__INITIAL_STATE__\\s*=\\s*(\\{.*?\\});?"
                      jsonPaths:
                        productName: "$.props.pageProps.product.name"
                        productPrice: "$.props.pageProps.product.price"
                    categories:
                      - name: Celulares
                        url: https://ejemplo.com/celulares
                        paginationType: PAGE_PARAM
                        itemLinkSelector: a.product-link
                        enabled: true
                      - name: Laptops
                        url: https://ejemplo.com/laptops
                        paginationType: PAGE_PARAM
                        itemLinkSelector: a.product-link
                        enabled: true
                """;
        Files.writeString(path, example);
    }

}
