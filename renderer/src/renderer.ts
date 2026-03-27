import { Browser, Page } from 'playwright';
import { chromium } from 'playwright-extra';
import StealthPlugin from 'puppeteer-extra-plugin-stealth';

chromium.use(StealthPlugin());

let headlessBrowser: Browser | null = null;

async function getBrowser(headless: boolean): Promise<{ browser: Browser; owned: boolean }> {
  if (!headless) {
    const browser = await chromium.launch({
      headless: false,
      args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-blink-features=AutomationControlled'],
    });
    return { browser, owned: true };
  }

  if (!headlessBrowser || !headlessBrowser.isConnected()) {
    headlessBrowser = await chromium.launch({
      headless: true,
      args: ['--no-sandbox', '--disable-setuid-sandbox', '--disable-blink-features=AutomationControlled'],
    });
  }
  return { browser: headlessBrowser, owned: false };
}

export interface RenderOptions {
  url: string;
  waitForSelector?: string;
  interceptPatterns: string[];
  scrollToBottom: boolean;
  maxScrolls: number;
  timeout: number;
  headless: boolean;
}

export interface InterceptedResponse {
  url: string;
  status: number;
  body: string;
}

export interface RenderResult {
  html: string;
  interceptedResponses: InterceptedResponse[];
}

export async function renderPage(options: RenderOptions): Promise<RenderResult> {
  const { browser, owned } = await getBrowser(options.headless);
  const context = await browser.newContext({
    userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    viewport: { width: 1920, height: 1080 },
    extraHTTPHeaders: {
      'Accept-Language': 'es-PE,es;q=0.9,en;q=0.8',
      'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8',
    },
  });

  const page = await context.newPage();

  const interceptedResponses: InterceptedResponse[] = [];

  // Block static resources to speed up loading
  if (options.interceptPatterns.length > 0) {
    const blockPatterns = options.interceptPatterns.map((p) =>
      new RegExp(p.replace(/\\\*/g, '.*'))
    );
    await context.route('**/*', (route) => {
      const url = route.request().url();
      if (blockPatterns.some((re) => re.test(url))) {
        route.abort();
      } else {
        route.continue();
      }
    });
  }

  const startTime = Date.now();

  try {
    await page.goto(options.url, {
      waitUntil: 'domcontentloaded',
      timeout: options.timeout,
    });

    if (options.waitForSelector) {
      const elapsed = Date.now() - startTime;
      const remaining = options.timeout - elapsed;
      await page.waitForSelector(options.waitForSelector, {
        timeout: Math.max(remaining, 5000),
      });
    }

    if (options.scrollToBottom) {
      await autoScroll(page, options.maxScrolls, options.timeout);
    }

    const html = await page.content();

    return { html, interceptedResponses };
  } finally {
    await context.close();
    if (owned) await browser.close();
  }
}

async function autoScroll(page: Page, maxScrolls: number, timeout: number): Promise<void> {
  const deadline = Date.now() + timeout;

  for (let i = 0; i < maxScrolls; i++) {
    if (Date.now() > deadline) break;

    const previousHeight = await page.evaluate(() => document.body.scrollHeight);

    await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight));

    // Wait for potential new content
    try {
      await page.waitForFunction(
        (prevH: number) => document.body.scrollHeight > prevH,
        previousHeight,
        { timeout: 3000 }
      );
    } catch {
      // No new content loaded, we've reached the bottom
      break;
    }

    // Small delay for network requests to settle
    await page.waitForTimeout(500);
  }
}

// Cleanup on process exit
process.on('SIGINT', async () => {
  if (headlessBrowser) await headlessBrowser.close();
  process.exit(0);
});

process.on('SIGTERM', async () => {
  if (headlessBrowser) await headlessBrowser.close();
  process.exit(0);
});
