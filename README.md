# Crawler

Web crawler configurable por sitio con soporte para paginas estaticas y dinamicas (AJAX/SPA). Descubre URLs de productos desde paginas de listado y extrae datos estructurados de cada producto.

## Arquitectura

El sistema consta de dos componentes en tiempo de ejecucion:

| Componente | Tecnologia | Puerto | Descripcion |
|------------|-----------|--------|-------------|
| **Backend** | Java 25, Spring Boot 4.0.5 | 8080 | Discovery, extraction, batch jobs, REST API |
| **Renderer** | Node.js, Playwright | 3000 | Renderizado headless con Chromium para paginas JS |

Base de datos: **PostgreSQL 16** con migraciones Flyway.

```
┌─────────────┐     ┌─────────────┐     ┌──────────────┐
│   Backend    │────>│  Renderer   │────>│  Chromium     │
│ Spring Boot  │     │  Express +  │     │  (headless)   │
│              │     │  Playwright │     │               │
└──────┬───────┘     └─────────────┘     └──────────────┘
       │
       v
┌──────────────┐
│  PostgreSQL   │
│     16        │
└──────────────┘
```

## Inicio Rapido

### Prerrequisitos

- Java 25+
- Docker y Docker Compose
- Node.js 18+ (solo si ejecutas el renderer localmente sin Docker)

### Levantar infraestructura

```bash
docker-compose up -d
```

Esto inicia PostgreSQL y el servicio Renderer.

### Ejecutar el backend

```bash
./gradlew bootRun
```

Al iniciar, el backend:
1. Ejecuta migraciones Flyway sobre PostgreSQL
2. Lee `sites-config.yml` (lo crea con ejemplo si no existe)
3. Registra los sitios y categorias configurados en la BD

### Configurar sitios

Edita `sites-config.yml` en la raiz del proyecto:

```yaml
sites:
  - name: MiTienda
    baseUrl: https://mitienda.com
    enabled: true
    politenessDelayMs: 1000
    extractionConfig:
      detailStrategy: HTML
      fieldSelectors:
        title: "h1.product-title"
        price: "span.price"
        description: "div.description"
    categories:
      - name: Celulares
        url: https://mitienda.com/celulares
        paginationType: PAGE_PARAM
        itemLinkSelector: a.product-link
        enabled: true
```

## Pipeline de Crawling

El crawler opera en dos fases:

### Fase 1: Discovery (Descubrimiento)

Encuentra URLs de productos desde paginas de categoria.

| Estrategia | Cuando se usa | Como funciona |
|------------|---------------|---------------|
| **Jsoup** | `PAGE_PARAM`, `CURSOR`, `STATIC` | Descarga HTML directamente, sigue links de paginacion |
| **Playwright** | `SCROLL_AJAX` | Renderiza con Chromium, hace scroll para cargar items por AJAX |

### Fase 2: Extraction (Extraccion)

Extrae datos estructurados de cada URL descubierta.

| Estrategia | Fuente de datos |
|------------|----------------|
| **HTML** | Selectores CSS sobre el DOM |
| **SCRIPT_JSON** | Regex sobre `<script>` tags + JsonPath |
| **AJAX** | Intercepcion de respuestas de red via Playwright + JsonPath |

## API REST

### Discovery

```bash
# Descubrir URLs de una categoria
curl -X POST http://localhost:8080/api/discovery/category/1

# Descubrir URLs de todas las categorias de un sitio
curl -X POST http://localhost:8080/api/discovery/site/1
```

### Extraction

```bash
# Extraer datos de URLs pendientes de un sitio
curl -X POST http://localhost:8080/api/extraction/site/1
```

### Jobs (Batch completo)

```bash
# Lanzar crawl completo (discovery + extraction)
curl -X POST http://localhost:8080/api/jobs/crawl/1

# Ver estado del job
curl http://localhost:8080/api/jobs/status/{jobId}
```

### Estadisticas

```bash
# Listar sitios
curl http://localhost:8080/api/stats/sites

# Stats de un sitio (conteos por status, items extraidos)
curl http://localhost:8080/api/stats/site/1

# Items extraidos recientes
curl "http://localhost:8080/api/stats/items?siteId=1&page=0&size=20"
```

## Estructura del Proyecto

```
crawler/
├── src/main/java/crawler/
│   ├── config/          # Propiedades, async config, carga de YAML
│   ├── discovery/       # Descubrimiento de URLs (Jsoup + Playwright)
│   ├── extraction/      # Extraccion de datos (HTML, JSON, AJAX)
│   ├── job/             # Spring Batch jobs, REST controllers, scheduler
│   ├── model/           # Entidades JPA, enums, repositorios
│   └── storage/         # Almacenamiento de snapshots HTML/JSON
├── src/main/resources/
│   ├── application.yaml
│   └── db/migration/    # Flyway SQL (V1-V3)
├── src/test/java/crawler/
│   ├── discovery/       # Unit tests - ListDiscoveryService
│   ├── extraction/      # Unit tests - DetailExtractionService
│   ├── storage/         # Unit tests - RawStorageService
│   └── integration/     # Integration tests (Testcontainers + WireMock)
├── renderer/
│   └── src/             # Express + Playwright (TypeScript)
├── build.gradle.kts
├── docker-compose.yml
├── CLAUDE.md            # Guia para Claude Code
└── SKILLS.md            # Guia de habilidades por modulo
```

## Testing

```bash
# Ejecutar todos los tests (requiere Docker para Testcontainers)
./gradlew test

# Solo tests unitarios
./gradlew test --tests "crawler.discovery.*" --tests "crawler.extraction.*" --tests "crawler.storage.*"

# Solo tests de integracion
./gradlew test --tests "crawler.integration.*"
```

### Stack de testing

- **JUnit 5** + **Mockito** para unit tests
- **Testcontainers** (PostgreSQL 16) para integration tests con base de datos real
- **WireMock** para simular servidores HTTP en integration tests
- Perfil `test` con `application-test.yaml` (desactiva batch jobs, configura temp dirs)

## Configuracion

Propiedades principales en `application.yaml`:

| Propiedad | Default | Descripcion |
|-----------|---------|-------------|
| `crawler.raw-data-dir` | - | Directorio para snapshots HTML/JSON |
| `crawler.discovery-max-pages` | 50 | Maximo de paginas por categoria |
| `crawler.extraction-batch-size` | 10 | URLs por batch de extraccion |
| `crawler.renderer-base-url` | `http://localhost:3000` | URL del servicio renderer |
| `crawler.retry-max-retries` | 3 | Reintentos maximos para URLs fallidas |
| `crawler.retry-interval-ms` | 600000 | Intervalo del scheduler de reintentos (ms) |

## Licencia

Proyecto privado.
