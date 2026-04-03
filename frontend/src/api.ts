import type { Site, Category, SiteStats, ExtractedItem, PagedResult, JobResponse, JobStatus } from './types'

async function request<T>(url: string, options?: RequestInit): Promise<T> {
  const res = await fetch(url, {
    ...options,
    headers: { 'Content-Type': 'application/json', ...options?.headers },
  })
  if (res.status === 204) return undefined as T
  const contentType = res.headers.get('content-type') || ''
  if (!res.ok) {
    const text = await res.text()
    throw new Error(text || `HTTP ${res.status}`)
  }
  if (contentType.includes('application/json')) return res.json()
  return await res.text() as T
}

// Sites
export const getSites = () => request<Site[]>('/api/sites')
export const getSite = (id: number) => request<Site>(`/api/sites/${id}`)
export const createSite = (data: Partial<Site>) => request<Site>('/api/sites', { method: 'POST', body: JSON.stringify(data) })
export const updateSite = (id: number, data: Partial<Site>) => request<Site>(`/api/sites/${id}`, { method: 'PUT', body: JSON.stringify(data) })
export const deleteSite = (id: number) => request<void>(`/api/sites/${id}`, { method: 'DELETE' })

// Categories
export const getCategories = (siteId: number) => request<Category[]>(`/api/sites/${siteId}/categories`)
export const getCategory = (id: number) => request<Category>(`/api/categories/${id}`)
export const createCategory = (siteId: number, data: Partial<Category>) => request<Category>(`/api/sites/${siteId}/categories`, { method: 'POST', body: JSON.stringify(data) })
export const updateCategory = (id: number, data: Partial<Category>) => request<Category>(`/api/categories/${id}`, { method: 'PUT', body: JSON.stringify(data) })
export const deleteCategory = (id: number) => request<void>(`/api/categories/${id}`, { method: 'DELETE' })

// Stats
export const getSiteStats = (id: number) => request<SiteStats>(`/api/stats/site/${id}`)
export const getItems = (params: { page?: number; size?: number; siteId?: number; search?: string } = {}) => {
  const p = new URLSearchParams()
  p.set('page', String(params.page ?? 0))
  p.set('size', String(params.size ?? 25))
  if (params.siteId) p.set('siteId', String(params.siteId))
  if (params.search) p.set('search', params.search)
  return request<PagedResult<ExtractedItem>>(`/api/stats/items?${p}`)
}

// Items
export const getItemImages = (id: number) => request<string[]>(`/api/items/${id}/images`)
export const downloadItemImages = (id: number) => {
  window.open(`/api/items/${id}/download-images`, '_blank')
}

// Actions
export const launchCrawl = (siteId: number) => request<JobResponse>(`/api/jobs/crawl/${siteId}`, { method: 'POST' })
export const getJobStatus = (jobId: number) => request<JobStatus>(`/api/jobs/status/${jobId}`)
export const startDiscoverySite = (siteId: number) => request<string>(`/api/discovery/site/${siteId}`, { method: 'POST' })
export const startDiscoveryCategory = (categoryId: number) => request<string>(`/api/discovery/category/${categoryId}`, { method: 'POST' })
export const startExtraction = (siteId: number) => request<string>(`/api/extraction/site/${siteId}`, { method: 'POST' })
