import { chromium, Browser, Page, Response } from 'playwright';

let browser: Browser | null = null;

async function getBrowser(): Promise<Browser> {
  if (!browser || !browser.isConnected()) {
    browser = await chromium.launch({
      headless: true,
      args: ['--no-sandbox', '--disable-setuid-sandbox'],
    });
  }
  return browser;
}

export interface RenderOptions {
  url: string;
  waitForSelector?: string;
  interceptPatterns: string[];
  scrollToBottom: boolean;
  maxScrolls: number;
  timeout: number;
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
  const browser = await getBrowser();
  const context = await browser.newContext({
    userAgent: 'CrawlerBot/1.0',
  });
  const page = await context.newPage();

  const interceptedResponses: InterceptedResponse[] = [];
  const patterns = options.interceptPatterns.map((p) => new RegExp(p));

  // Intercept fetch/XHR responses
  if (patterns.length > 0) {
    page.on('response', async (response: Response) => {
      const url = response.url();
      const resourceType = response.request().resourceType();

      if (resourceType !== 'fetch' && resourceType !== 'xhr') {
        return;
      }

      const matches = patterns.some((re) => re.test(url));
      if (!matches) {
        return;
      }

      try {
        const body = await response.text();
        interceptedResponses.push({
          url,
          status: response.status(),
          body,
        });
      } catch {
        // response body may not be available
      }
    });
  }

  try {
    await page.goto(options.url, {
      waitUntil: 'networkidle',
      timeout: options.timeout,
    });

    if (options.waitForSelector) {
      await page.waitForSelector(options.waitForSelector, {
        timeout: options.timeout,
      });
    }

    if (options.scrollToBottom) {
      await autoScroll(page, options.maxScrolls, options.timeout);
    }

    const html = await page.content();

    return { html, interceptedResponses };
  } finally {
    await context.close();
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
  if (browser) await browser.close();
  process.exit(0);
});

process.on('SIGTERM', async () => {
  if (browser) await browser.close();
  process.exit(0);
});
