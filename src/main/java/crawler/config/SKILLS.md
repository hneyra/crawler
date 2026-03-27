# Config Module - Skills

## Common Tasks

### Add a new configuration property
1. Add field + getter/setter to `CrawlerProperties`
2. Add default value in `application.yaml` under `crawler:` prefix
3. Override in `application-test.yaml` for tests if needed

### Modify site YAML config structure
1. Update `SiteDefinition` / `CategoryDefinition` POJOs
2. Update `DataInitializer.createExampleConfig()` if changing example
3. Runtime config: update `sites-config.yml`

### Modify startup data loading
- Edit `DataInitializer.run()` — runs as `CommandLineRunner`
- Uses `siteRepository.findByName()` to skip existing sites
- Mock with `@MockBean DataInitializer` in integration tests

## Key Interactions
```
Application Startup
  -> DataInitializer.run()
    -> Check/create sites-config.yml
    -> Load YAML via Jackson YAMLFactory
    -> SiteRepository.save() / CategoryRepository.save()

All Services
  -> @Autowired CrawlerProperties (for tunable parameters)
```
