# Model Module - Skills

## Common Tasks

### Add a new entity
1. Create JPA `@Entity` class in `crawler.model`
2. Create Spring Data `JpaRepository` interface
3. Add Flyway migration: `V{N}__{description}.sql`
4. Add integration tests in `RepositoryIntegrationTest`

### Add a new column to existing entity
1. Add field + getter/setter to entity class
2. Add Flyway migration: `ALTER TABLE ... ADD COLUMN ...`
3. Update integration tests

### Add a new repository query
1. Add method to repository interface (Spring Data naming convention or `@Query`)
2. Use `@Modifying` for UPDATE/DELETE queries
3. Add test in `RepositoryIntegrationTest`

### Modify ExtractionConfig structure
- `ExtractionConfig` is a POJO stored as JSONB in `site.extraction_config`
- Add fields + getters/setters
- Existing data in DB will have `null` for new fields (use defaults in Java)

## Important Conventions

- Generation strategy: `IDENTITY` (PostgreSQL BIGSERIAL)
- JSONB mapping: `@JdbcTypeCode(SqlTypes.JSON)` + `@Column(columnDefinition = "jsonb")`
- Fetch type: `LAZY` on all `@ManyToOne`
- Timestamps: `Instant` mapped to `TIMESTAMPTZ`
- Unique constraints via Flyway indexes, not JPA annotations alone

## Tests to run after changes
```bash
./gradlew test --tests "crawler.integration.RepositoryIntegrationTest"
```
