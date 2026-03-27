# Flujo de Datos

## Pipeline Completo de Crawling

```mermaid
sequenceDiagram
    participant U as Usuario / Scheduler
    participant API as REST API
    participant JOB as Spring Batch Job
    participant DISC as Discovery Service
    participant EXTR as Extraction Service
    participant PW as Playwright Client
    participant REND as Renderer Service
    participant DB as PostgreSQL
    participant FS as Filesystem

    U->>API: POST /api/jobs/crawl/{siteId}
    API->>JOB: Launch crawlJob(siteId)

    Note over JOB: Fase 1: Discovery
    JOB->>DISC: discoverCategory(category)

    alt PaginationType = STATIC/PAGE_PARAM/CURSOR
        DISC->>DISC: Jsoup.connect(url).get()
        loop Cada pagina (hasta maxPages)
            DISC->>DB: findByUrlHash(hash)
            alt URL nueva
                DISC->>DB: save(DiscoveredUrl, PENDING)
            end
            DISC->>DISC: findNextPageUrl(doc)
        end
    else PaginationType = SCROLL_AJAX
        DISC->>PW: render(scrolling request)
        PW->>REND: POST /render
        REND-->>PW: {html, interceptedResponses}
        PW-->>DISC: RenderResponse
        DISC->>DB: save(DiscoveredUrl, PENDING) por cada link
    end

    Note over JOB: Fase 2: Extraction
    loop Mientras haya URLs PENDING
        JOB->>EXTR: processBatch(siteId)
        EXTR->>DB: findByCategory_Site_IdAndStatus(PENDING)
        EXTR->>DB: saveAll(batch, IN_PROGRESS)

        loop Cada URL del batch
            alt DetailStrategy = HTML / SCRIPT_JSON
                EXTR->>EXTR: Jsoup.connect(url).get()
            else DetailStrategy = AJAX
                EXTR->>PW: render(withIntercept)
                PW->>REND: POST /render
                REND-->>PW: {html, interceptedResponses}
                PW-->>EXTR: RenderResponse
            end

            EXTR->>FS: saveHtml(html)
            EXTR->>EXTR: extract fields
            EXTR->>DB: save(ExtractedItem)
            EXTR->>DB: save(DiscoveredUrl, COMPLETED)
        end
    end
```

## Ciclo de Vida de una URL

```mermaid
stateDiagram-v2
    [*] --> PENDING: URL descubierta
    PENDING --> IN_PROGRESS: Batch selecciona URL
    IN_PROGRESS --> COMPLETED: Extraccion exitosa
    IN_PROGRESS --> FAILED: Error en extraccion
    FAILED --> PENDING: Retry Scheduler<br/>(si retryCount < maxRetries)
    FAILED --> [*]: Max reintentos alcanzado
    COMPLETED --> [*]
```

## Flujo de Discovery con Paginacion (Jsoup)

```mermaid
flowchart TD
    A[Inicio: URL de categoria] --> B[Fetch pagina con Jsoup]
    B --> C{Exito?}
    C -->|No| Z[Fin discovery]
    C -->|Si| D[Seleccionar links con CSS selector]
    D --> E[Para cada link]
    E --> F[Normalizar URL: strip fragment]
    F --> G[Calcular SHA-256]
    G --> H{Existe en BD?}
    H -->|Si| I[Skip - URL conocida]
    H -->|No| J[Guardar como PENDING]
    I --> K{Mas links?}
    J --> K
    K -->|Si| E
    K -->|No| L[Buscar link de siguiente pagina]
    L --> M{Encontrado?}
    M -->|No| Z
    M -->|Si| N{paginas < maxPages?}
    N -->|No| Z
    N -->|Si| O[Esperar politenessDelay]
    O --> B
```

## Flujo de Extraccion por Estrategia

```mermaid
flowchart TD
    A[URL a extraer] --> B{DetailStrategy?}

    B -->|HTML| C[Jsoup.connect]
    C --> D[CSS Selectors]
    D --> G[JSON-LD]

    B -->|SCRIPT_JSON| C
    C --> E[Regex en script tags]
    E --> F[JsonPath sobre JSON]
    F --> G

    B -->|AJAX| H[Playwright render + intercept]
    H --> I[CSS Selectors sobre HTML]
    I --> J[JsonPath sobre AJAX responses]
    J --> G

    G --> K[Guardar HTML snapshot]
    K --> L[Crear ExtractedItem]
    L --> M[properties = JSON con campos]
```

## Flujo del Renderer (Playwright)

```mermaid
sequenceDiagram
    participant C as Backend (PlaywrightClient)
    participant S as Express Server
    participant P as Playwright
    participant B as Chromium Browser

    C->>S: POST /render {url, scrollToBottom, interceptPatterns, ...}
    S->>P: newPage()

    opt interceptPatterns definidos
        P->>B: route.continue() + capturar responses
    end

    P->>B: page.goto(url)

    opt waitForSelector definido
        P->>B: page.waitForSelector(selector)
    end

    opt scrollToBottom = true
        loop Hasta maxScrolls o sin cambios
            P->>B: window.scrollTo(0, document.body.scrollHeight)
            P->>B: waitForTimeout(1500)
            Note over P,B: Compara height anterior vs actual
        end
    end

    P->>B: page.content()
    B-->>P: HTML renderizado
    P-->>S: {html, interceptedResponses}
    S-->>C: JSON response
```
