# Testing

## Estructura de Tests

```
src/test/java/crawler/
├── CrawlerApplicationTests.java          # Context load test (existente)
├── discovery/
│   └── ListDiscoveryServiceUnitTest.java # Unit tests - discovery
├── extraction/
│   └── DetailExtractionServiceUnitTest.java # Unit tests - extraction
├── storage/
│   └── RawStorageServiceTest.java        # Unit tests - storage
└── integration/
    ├── RepositoryIntegrationTest.java    # JPA repos con Testcontainers
    └── ListDiscoveryIntegrationTest.java # Discovery E2E con WireMock

src/test/resources/
└── application-test.yaml                 # Config para perfil "test"
```

## Ejecutar Tests

```bash
# Todos los tests (requiere Docker)
./gradlew test

# Solo unit tests (sin Docker)
./gradlew test --tests "crawler.discovery.ListDiscoveryServiceUnitTest" \
               --tests "crawler.extraction.DetailExtractionServiceUnitTest" \
               --tests "crawler.storage.RawStorageServiceTest"

# Solo integration tests (requiere Docker)
./gradlew test --tests "crawler.integration.*"

# Tests de un modulo especifico
./gradlew test --tests "crawler.discovery.*"
```

## Dependencias de Testing

| Dependencia | Uso |
|-------------|-----|
| `spring-boot-starter-test` | JUnit 5, Mockito, AssertJ, Spring Test |
| `spring-batch-test` | Batch job testing utilities |
| `spring-boot-testcontainers` | `@ServiceConnection` auto-config |
| `testcontainers:postgresql` | Container PostgreSQL 16 para integration |
| `testcontainers:junit-jupiter` | `@Container` + `@Testcontainers` lifecycle |
| `wiremock` | Servidor HTTP mock para simular paginas web |

## Patrones de Testing

### Unit Tests (sin Spring Context)

```mermaid
flowchart LR
    TEST[Test Class] -->|@ExtendWith MockitoExtension| MOCK[Mockito Mocks]
    MOCK --> SUT[Service Under Test]
    SUT --> ASSERT[AssertJ Assertions]
```

**Caracteristicas:**
- `@ExtendWith(MockitoExtension.class)`
- Mocks con `@Mock` para todas las dependencias
- Construccion manual del servicio en `@BeforeEach`
- Sin base de datos, sin red, sin filesystem (excepto `@TempDir`)
- Rapidos: ejecutan en milisegundos

**Ejemplo:**
```java
@ExtendWith(MockitoExtension.class)
class MyServiceTest {
    @Mock Repository repo;
    MyService service;

    @BeforeEach
    void setUp() {
        service = new MyService(repo);
    }

    @Test
    void myTest() {
        when(repo.find(any())).thenReturn(Optional.empty());
        service.doWork();
        verify(repo).save(any());
    }
}
```

### Integration Tests - Repository Layer

```mermaid
flowchart LR
    TEST[Test Class] -->|@DataJpaTest| SPRING[Spring JPA Slice]
    SPRING -->|@ServiceConnection| TC[Testcontainers PostgreSQL]
    TC --> FLYWAY[Flyway Migrations]
    FLYWAY --> REPOS[Real Repositories]
    REPOS --> DB[(PostgreSQL Container)]
```

**Caracteristicas:**
- `@DataJpaTest` — solo carga JPA slice (repositories, Flyway)
- `@AutoConfigureTestDatabase(replace = NONE)` — usa PostgreSQL real
- `@Testcontainers` + `@Container` + `@ServiceConnection`
- `@ActiveProfiles("test")` — carga `application-test.yaml`
- Flyway ejecuta las migraciones reales sobre el container
- Cada test limpia datos en `@BeforeEach`

### Integration Tests - Service + HTTP

```mermaid
flowchart LR
    TEST[Test Class] -->|@SpringBootTest| SPRING[Full Spring Context]
    SPRING -->|@ServiceConnection| TC[Testcontainers PostgreSQL]
    SPRING --> SVC[Real Services]
    SVC -->|HTTP| WM[WireMock Server]
    WM --> HTML[HTML Pages]
```

**Caracteristicas:**
- `@SpringBootTest` — contexto completo de Spring
- WireMock con `@RegisterExtension` y puerto dinamico
- `@MockBean DataInitializer` — previene side effects de startup
- Stubs WireMock sirven paginas HTML para testing de discovery
- Verifica persistencia real en PostgreSQL

## Cobertura por Modulo

### Discovery

| Test | Que Verifica |
|------|-------------|
| `normalizeUrl_*` (6 tests) | Normalizacion: fragmentos, query params, whitespace, URLs invalidas |
| `sha256_*` (4 tests) | Hash deterministico, formato hex 64 chars, valor conocido |
| `discoverCategory_usesPlaywright*` | Routing SCROLL_AJAX → Playwright |
| `discoverCategory_savesNewUrls*` | URLs guardadas con status PENDING |
| `discoverCategory_skipsDuplicates*` | Deduplicacion por urlHash |
| `discoverCategory_stripsFragment*` | Fragmentos removidos de URLs |
| Integration: `savesProductUrls*` | E2E: WireMock → Discovery → BD |
| Integration: `followsNextPageLink` | Paginacion multi-pagina |
| Integration: `deduplicatesUrls` | Dedup cross-pagina |
| Integration: `handlesPageFetchError` | Manejo de errores HTTP |
| Integration: `respectsCustomNextPageSelector` | Selector personalizado |
| Integration: `urlHashIsSha256*` | Verificacion de hash en BD |

### Extraction

| Test | Que Verifica |
|------|-------------|
| `processBatch_returnsEarly*` (2 tests) | Early exit: site no existe, batch vacio |
| `processBatch_setsUrlInProgress*` | Marca IN_PROGRESS antes de procesar |
| `processBatch_marksUrlAsFailed*` | FAILED cuando Jsoup no puede conectar |
| `processBatch_incrementsRetryCount*` | retryCount + 1 en cada fallo |
| `processBatch_setsLastAttemptAt*` | Timestamp actualizado |
| `processBatch_delegatesToPlaywright*` | AJAX strategy → PlaywrightClient |
| `processBatch_marksUrlFailed*Playwright*` | FAILED cuando Playwright lanza excepcion |
| `processBatch_extractsFieldSelectors*` | CSS selectors extraen valores al properties JSON |

### Storage

| Test | Que Verifica |
|------|-------------|
| `saveHtml_*` (6 tests) | Extension .html, path correcto, contenido, idempotencia, separacion por site |
| `saveJson_*` (3 tests) | Extension .json, contenido, path con siteId |
| `savedFile_*` (2 tests) | 3 segmentos en path, nombre de archivo es hex SHA-256 |

### Repositories (Integration)

| Test | Que Verifica |
|------|-------------|
| `findByName_*` (2 tests) | Busqueda de site por nombre |
| `findBySiteIdAndEnabledTrue_*` | Filtro de categorias habilitadas |
| `findByUrlHash_*` (2 tests) | Busqueda por hash |
| `findByCategory_Site_IdAndStatus_*` (2 tests) | Filtro por siteId + status + paginacion |
| `countByCategory_*` (2 tests) | Conteos por status y totales |
| `resetFailedUrls_*` | Reset respeta limite de reintentos |
| `findLastAttemptBySiteId_*` | Ultimo intento de procesamiento |
| `countBySiteId_*` | Conteo de items extraidos |
| `findByOrderByExtractedAtDesc_*` | Orden descendente |
| `findBySiteIdOrderByExtractedAtDesc_*` | Filtro + orden |
