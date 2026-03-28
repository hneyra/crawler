export interface Site {
  id: number
  name: string
  baseUrl: string
  enabled: boolean
  politenessDelayMs: number
  extractionConfig: ExtractionConfig
  createdAt: string
}

export interface ExtractionConfig {
  detailStrategy: 'HTML' | 'SCRIPT_JSON' | 'AJAX'
  fieldSelectors: Record<string, string>
  scriptPatterns: string[]
  jsonPaths: Record<string, string>
  interceptPatterns: string[]
}

export interface Category {
  id: number
  siteId: number
  name: string
  url: string
  paginationType: 'PAGE_PARAM' | 'CURSOR' | 'SCROLL_AJAX'
  itemLinkSelector: string
  linkExtractionType: 'HREF' | 'ONCLICK' | 'DATA_ATTRIBUTE'
  linkAttribute: string | null
  nextPageSelector: string | null
  enabled: boolean
}

export interface SiteStats {
  siteId: number
  siteName: string
  totalUrls: number
  pending: number
  inProgress: number
  completed: number
  failed: number
  extractedItems: number
  lastCrawl: string | null
}

export interface ExtractedItem {
  id: number
  title: string
  url: string
  siteName: string
  rawSnapshotPath: string
  extractedAt: string
  properties: string
}

export interface JobResponse {
  jobExecutionId: number
  status: string
  siteId: number
}

export interface JobStatus {
  jobExecutionId: number
  jobName: string
  status: string
  startTime: string
  endTime: string | null
  exitCode: string
  steps: Record<string, { status: string; startTime: string; endTime: string | null; exitCode: string }>
}
