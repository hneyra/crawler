import { Browser, BrowserContext, Page, chromium } from 'patchright';
import { execSync, spawn, ChildProcess } from 'child_process';
import path from 'path';
import fs from 'fs';

const PROFILES_DIR = process.env.PROFILES_DIR || path.join(process.cwd(), 'browser-profiles');
const CDP_PORT = parseInt(process.env.CDP_PORT || '9222', 10);

let connectedBrowser: Browser | null = null;
let chromeProcess: ChildProcess | null = null;

function findChrome(): string {
  const candidates = [
    'google-chrome-stable',
    'google-chrome',
    'chromium-browser',
    'chromium',
  ];
  for (const cmd of candidates) {
    try {
      return execSync(`which ${cmd}`, { encoding: 'utf-8' }).trim();
    } catch { /* next */ }
  }
  throw new Error('No Chrome/Chromium found. Install with: sudo apt install google-chrome-stable');
}

function launchChromeProcess(headless: boolean): void {
  const chromePath = findChrome();
  const profileDir = path.join(PROFILES_DIR, headless ? 'headless' : 'headed');
  fs.mkdirSync(profileDir, { recursive: true });

  const args = [
    `--remote-debugging-port=${CDP_PORT}`,
    `--user-data-dir=${profileDir}`,
    '--no-first-run',
    '--no-default-browser-check',
    '--disable-blink-features=AutomationControlled',
    '--window-size=1920,1080',
    '--lang=es-PE',
  ];

  if (headless) {
    args.push('--headless=new');
  }

  console.log(`Launching Chrome: ${chromePath}`);
  chromeProcess = spawn(chromePath, args, {
    stdio: 'ignore',
    detached: false,
  });

  chromeProcess.on('exit', (code) => {
    console.log(`Chrome process exited with code ${code}`);
    chromeProcess = null;
    connectedBrowser = null;
  });
}

async function waitForCDP(timeoutMs: number = 10000): Promise<void> {
  const deadline = Date.now() + timeoutMs;
  while (Date.now() < deadline) {
    try {
      const resp = await fetch(`http://127.0.0.1:${CDP_PORT}/json/version`);
      if (resp.ok) return;
    } catch { /* not ready yet */ }
    await new Promise((r) => setTimeout(r, 300));
  }
  throw new Error(`Chrome CDP not available on port ${CDP_PORT} after ${timeoutMs}ms`);
}

async function getBrowser(headless: boolean): Promise<Browser> {
  if (connectedBrowser?.isConnected()) {
    return connectedBrowser;
  }

  // Check if Chrome is already running on CDP port
  let cdpReady = false;
  try {
    const resp = await fetch(`http://127.0.0.1:${CDP_PORT}/json/version`);
    cdpReady = resp.ok;
  } catch { /* not running */ }

  if (!cdpReady) {
    launchChromeProcess(headless);
    await waitForCDP();
  }

  console.log(`Connecting to Chrome via CDP on port ${CDP_PORT}`);
  connectedBrowser = await chromium.connectOverCDP(`http://127.0.0.1:${CDP_PORT}`);
  return connectedBrowser;
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

async function waitForCloudflare(page: Page, timeout: number): Promise<void> {
  const deadline = Date.now() + timeout;

  while (Date.now() < deadline) {
    const title = await page.title();
    const url = page.url();

    if (
      !title.includes('Just a moment') &&
      !title.includes('Attention Required') &&
      !title.includes('Checking your browser') &&
      !url.includes('/cdn-cgi/challenge-platform')
    ) {
      return;
    }

    await page.waitForTimeout(1000);
  }
}

export async function renderPage(options: RenderOptions): Promise<RenderResult> {
  const browser = await getBrowser(options.headless);
  const context = await browser.newContext({
    viewport: { width: 1920, height: 1080 },
  });
  const page = await context.newPage();

  const interceptedResponses: InterceptedResponse[] = [];

  if (options.interceptPatterns.length > 0) {
    const blockPatterns = options.interceptPatterns.map((p) =>
      new RegExp(p.replace(/\\\*/g, '.*'))
    );
    await page.route('**/*', (route) => {
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

    await waitForCloudflare(page, Math.min(15000, options.timeout));

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
    await page.close();
    await context.close();
  }
}

async function autoScroll(page: Page, maxScrolls: number, timeout: number): Promise<void> {
  const deadline = Date.now() + timeout;

  for (let i = 0; i < maxScrolls; i++) {
    if (Date.now() > deadline) break;

    const previousHeight = await page.evaluate(() => document.body.scrollHeight);
    await page.evaluate(() => window.scrollTo(0, document.body.scrollHeight));

    try {
      await page.waitForFunction(
        (prevH: number) => document.body.scrollHeight > prevH,
        previousHeight,
        { timeout: 3000 }
      );
    } catch {
      break;
    }

    await page.waitForTimeout(500);
  }
}

function cleanup() {
  if (connectedBrowser?.isConnected()) {
    connectedBrowser.close().catch(() => {});
  }
  if (chromeProcess) {
    chromeProcess.kill('SIGTERM');
  }
}

process.on('SIGINT', () => { cleanup(); process.exit(0); });
process.on('SIGTERM', () => { cleanup(); process.exit(0); });
