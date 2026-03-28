import express from 'express';
import { renderPage, clickAndWaitForNavigation } from './renderer';

const app = express();
app.use(express.json({ limit: '50mb' }));

const PORT = parseInt(process.env.PORT || '3000', 10);
const DEFAULT_TIMEOUT = parseInt(process.env.DEFAULT_TIMEOUT || '30000', 10);

interface RenderRequest {
  url: string;
  waitForSelector?: string;
  interceptPatterns?: string[];
  scrollToBottom?: boolean;
  maxScrolls?: number;
  timeout?: number;
  headless?: boolean;
}

app.post('/render', async (req, res) => {
  const body: RenderRequest = req.body;

  if (!body.url) {
    res.status(400).json({ error: 'url is required' });
    return;
  }

  try {
    const result = await renderPage({
      url: body.url,
      waitForSelector: body.waitForSelector,
      interceptPatterns: body.interceptPatterns || [],
      scrollToBottom: body.scrollToBottom || false,
      maxScrolls: body.maxScrolls || 20,
      timeout: body.timeout || DEFAULT_TIMEOUT,
    });

    res.json(result);
  } catch (err: any) {
    console.error('Render error:', err.message);
    res.status(500).json({ error: err.message });
  }
});

interface ClickNavigateRequest {
  url: string;
  clickSelector: string;
  timeout?: number;
  includeHtml?: boolean;
}

app.post('/click-navigate', async (req, res) => {
  const body: ClickNavigateRequest = req.body;

  if (!body.url || !body.clickSelector) {
    res.status(400).json({ error: 'url and clickSelector are required' });
    return;
  }

  try {
    const result = await clickAndWaitForNavigation({
      url: body.url,
      clickSelector: body.clickSelector,
      timeout: body.timeout || DEFAULT_TIMEOUT,
      includeHtml: body.includeHtml || false,
    });

    res.json(result);
  } catch (err: any) {
    console.error('Click-navigate error:', err.message);
    res.status(500).json({ error: err.message });
  }
});

app.get('/health', (_req, res) => {
  res.json({ status: 'ok' });
});

app.listen(PORT, () => {
  console.log(`Renderer service listening on port ${PORT}`);
});
