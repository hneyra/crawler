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

## Infraestructura (Pulumi)

El directorio `infra/` contiene la definicion de infraestructura con [Pulumi](https://www.pulumi.com/) (TypeScript).
Actualmente gestiona el rol y la base de datos PostgreSQL de la aplicacion.

### Prerrequisitos

- [Pulumi CLI](https://www.pulumi.com/docs/install/)
- Node.js 18+
- `PULUMI_ACCESS_TOKEN` configurado (`pulumi login`)

### Generar passwords (obligatorio antes del primer `pulumi up`)

El script `setup-passwords.ts` genera passwords aleatorios y los almacena como
secretos cifrados en `Pulumi.<stack>.yaml`. Es **idempotente**: si los passwords
ya existen no los sobreescribe.

```bash
cd infra
npm install

# Stack 'dev' por defecto
npm run setup-passwords

# Stack especifico
PULUMI_STACK=prod npm run setup-passwords
# o bien
npx ts-node setup-passwords.ts prod
```

Luego de ejecutarlo, **hace commit del archivo generado** antes de correr `pulumi up`:

```bash
git add infra/Pulumi.*.yaml
git commit -m "chore: update Pulumi password config"
```

> Los valores en `Pulumi.<stack>.yaml` estan cifrados con la clave del stack de
> Pulumi — es seguro commitearlos al repositorio.

### Leer passwords desde la linea de comandos

```bash
# Ver password del admin de PostgreSQL
pulumi config get postgres:adminPassword --stack dev

# Ver password del usuario de la aplicacion
pulumi config get postgres:appPassword --stack dev

# Listar toda la config del stack (secrets aparecen como [secret])
pulumi config --stack dev

# Mostrar todos los valores incluyendo secrets en texto plano
pulumi config --stack dev --show-secrets
```

### Desplegar infraestructura

```bash
cd infra
pulumi up --stack dev
```

### Automatizacion (GitHub Actions)

El workflow `.github/workflows/pulumi-infra.yml` se dispara automaticamente
cuando se detecta un cambio en `infra/postgres.ts` en `main`:

1. **`setup-passwords`** — Ejecuta `setup-passwords.ts`, hace commit de los
   `Pulumi.*.yaml` generados de vuelta al branch.
2. **`pulumi-up`** — Depende de `setup-passwords`, corre `pulumi up` con la
   config actualizada.

Secrets requeridos en el repositorio:

| Secret | Descripcion |
|--------|-------------|
| `PULUMI_ACCESS_TOKEN` | Token de acceso a Pulumi Cloud |
| `PGHOST` | Host del servidor PostgreSQL destino |
| `PGUSER` | Usuario admin de PostgreSQL |
| `PGPASSWORD` | Password admin de PostgreSQL |
| `GH_PAT` | (Opcional) PAT para que el commit del workflow dispare otros workflows |

## Estructura del Proyecto

```
crawler/
├── infra/                   # Pulumi infrastructure (TypeScript)
│   ├── postgres.ts          # PostgreSQL role + database resources
│   ├── setup-passwords.ts   # Genera passwords y los guarda en Pulumi config
│   ├── Pulumi.yaml          # Pulumi project definition
│   ├── Pulumi.dev.yaml      # Dev stack config (secrets cifrados)
│   └── package.json
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
