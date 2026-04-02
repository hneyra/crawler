package crawler;

import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import crawler.config.CategoryDefinition;
import crawler.config.SiteDefinition;
import crawler.config.SitesConfig;
import crawler.model.Category;
import crawler.model.DetailStrategy;
import crawler.model.DiscoveredUrl;
import crawler.model.ExtractedItem;
import crawler.model.ExtractionConfig;
import crawler.model.LinkExtractionType;
import crawler.model.PaginationType;
import crawler.model.Site;
import crawler.model.UrlStatus;
import org.springframework.aot.hint.MemberCategory;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.RuntimeHintsRegistrar;

public class CrawlerRuntimeHints implements RuntimeHintsRegistrar {

    @Override
    public void registerHints(RuntimeHints hints, ClassLoader classLoader) {
        // YAML-deserialized config POJOs (DataInitializer uses raw ObjectMapper + YAMLFactory)
        hints.reflection().registerType(SitesConfig.class,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_METHODS,
                MemberCategory.DECLARED_FIELDS);
        hints.reflection().registerType(SiteDefinition.class,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_METHODS,
                MemberCategory.DECLARED_FIELDS);
        hints.reflection().registerType(CategoryDefinition.class,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_METHODS,
                MemberCategory.DECLARED_FIELDS);
        hints.reflection().registerType(ExtractionConfig.class,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_METHODS,
                MemberCategory.DECLARED_FIELDS);

        // JPA entities (Hibernate proxy generation and field access)
        hints.reflection().registerType(Site.class,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_METHODS,
                MemberCategory.DECLARED_FIELDS);
        hints.reflection().registerType(Category.class,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_METHODS,
                MemberCategory.DECLARED_FIELDS);
        hints.reflection().registerType(DiscoveredUrl.class,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_METHODS,
                MemberCategory.DECLARED_FIELDS);
        hints.reflection().registerType(ExtractedItem.class,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_METHODS,
                MemberCategory.DECLARED_FIELDS);

        // Enums used via Enum.valueOf() in controllers and Jackson deserialization
        hints.reflection().registerType(DetailStrategy.class,
                MemberCategory.INVOKE_PUBLIC_METHODS,
                MemberCategory.DECLARED_FIELDS);
        hints.reflection().registerType(PaginationType.class,
                MemberCategory.INVOKE_PUBLIC_METHODS,
                MemberCategory.DECLARED_FIELDS);
        hints.reflection().registerType(LinkExtractionType.class,
                MemberCategory.INVOKE_PUBLIC_METHODS,
                MemberCategory.DECLARED_FIELDS);
        hints.reflection().registerType(UrlStatus.class,
                MemberCategory.INVOKE_PUBLIC_METHODS,
                MemberCategory.DECLARED_FIELDS);

        // Jackson YAML support
        hints.reflection().registerType(YAMLFactory.class,
                MemberCategory.INVOKE_DECLARED_CONSTRUCTORS,
                MemberCategory.INVOKE_PUBLIC_METHODS);

        // Flyway SQL migration scripts
        hints.resources().registerPattern("db/migration/*.sql");
    }
}
