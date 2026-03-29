import { useEffect, useState, useCallback } from 'react'
import { getSites, getItems } from '../api'
import { ToastContext } from '../App'
import type { Site, ExtractedItem, PagedResult } from '../types'

function parseProps(raw: string | null): Record<string, unknown> | null {
  if (!raw) return null
  try { return JSON.parse(raw) } catch { return null }
}

function PropValue({ value }: { value: unknown }) {
  if (value === null || value === undefined) return <span style={{ color: '#9ca3af' }}>null</span>
  if (typeof value === 'string') return <span style={{ color: '#059669' }}>"{value}"</span>
  if (typeof value === 'number' || typeof value === 'boolean')
    return <span style={{ color: '#2563eb' }}>{String(value)}</span>
  if (typeof value === 'object') {
    const json = JSON.stringify(value)
    if (json.length <= 120) return <span style={{ color: '#6b7280' }}>{json}</span>
    return <details style={{ display: 'inline' }}><summary style={{ cursor: 'pointer', color: '#6b7280' }}>{Array.isArray(value) ? `Array(${(value as unknown[]).length})` : 'Object'}</summary><pre style={{ margin: '4px 0 0', fontSize: 11, whiteSpace: 'pre-wrap', wordBreak: 'break-all' }}>{JSON.stringify(value, null, 2)}</pre></details>
  }
  return <span>{String(value)}</span>
}

function PropsPanel({ raw }: { raw: string | null }) {
  const obj = parseProps(raw)
  if (!obj) return <span style={{ color: '#9ca3af' }}>-</span>

  const entries = Object.entries(obj)
  if (entries.length === 0) return <span style={{ color: '#9ca3af' }}>{ }</span>

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: 3 }}>
      {entries.map(([key, val]) => (
        <div key={key} style={{ display: 'flex', gap: 6, lineHeight: 1.4 }}>
          <span style={{ fontWeight: 600, color: '#374151', flexShrink: 0 }}>{key}:</span>
          <PropValue value={val} />
        </div>
      ))}
    </div>
  )
}

export default function ItemsPage() {
  const [sites, setSites] = useState<Site[]>([])
  const [result, setResult] = useState<PagedResult<ExtractedItem> | null>(null)
  const [siteFilter, setSiteFilter] = useState<number | ''>('')
  const [search, setSearch] = useState('')
  const [searchInput, setSearchInput] = useState('')
  const [page, setPage] = useState(0)
  const [pageSize] = useState(25)
  const [expandedId, setExpandedId] = useState<number | null>(null)
  const [loading, setLoading] = useState(true)

  const load = useCallback(async () => {
    setLoading(true)
    try {
      const [allSites, pagedItems] = await Promise.all([
        getSites(),
        getItems({
          page,
          size: pageSize,
          siteId: siteFilter || undefined,
          search: search || undefined,
        })
      ])
      setSites(allSites)
      setResult(pagedItems)
    } catch (e: any) {
      ToastContext.show('Error: ' + e.message, true)
    }
    setLoading(false)
  }, [page, pageSize, siteFilter, search])

  useEffect(() => { load() }, [load])

  const handleSearch = () => {
    setPage(0)
    setSearch(searchInput)
  }

  const handleSiteChange = (val: number | '') => {
    setPage(0)
    setSiteFilter(val)
  }

  const items = result?.content ?? []
  const totalPages = result?.totalPages ?? 0
  const totalElements = result?.totalElements ?? 0

  return (
    <div>
      <div className="page-header">
        <h2>Items Extraidos</h2>
        <button className="btn btn-outline" onClick={load}>Refrescar</button>
      </div>

      <div className="items-toolbar">
        <div className="items-search">
          <input
            type="text"
            placeholder="Buscar por titulo, URL, sitio o propiedad..."
            value={searchInput}
            onChange={e => setSearchInput(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && handleSearch()}
          />
          <button className="btn btn-primary btn-sm" onClick={handleSearch}>Buscar</button>
          {search && (
            <button className="btn btn-outline btn-sm" onClick={() => { setSearchInput(''); setSearch(''); setPage(0) }}>
              Limpiar
            </button>
          )}
        </div>
        <select
          value={siteFilter}
          onChange={e => handleSiteChange(e.target.value ? Number(e.target.value) : '')}
        >
          <option value="">Todos los sitios</option>
          {sites.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
        </select>
      </div>

      {search && (
        <div style={{ fontSize: 13, color: '#6b7280', marginBottom: 12 }}>
          {totalElements} resultado{totalElements !== 1 ? 's' : ''} para "<strong>{search}</strong>"
        </div>
      )}

      {loading ? (
        <div className="empty-state">Cargando...</div>
      ) : items.length === 0 ? (
        <div className="empty-state">No hay items extraidos.</div>
      ) : (
        <>
          <div className="table-wrap">
            <table>
              <thead>
                <tr>
                  <th>ID</th>
                  <th>Titulo</th>
                  <th>URL</th>
                  <th>Sitio</th>
                  <th>Extraido</th>
                  <th>Propiedades</th>
                </tr>
              </thead>
              <tbody>
                {items.map(item => {
                  const isExpanded = expandedId === item.id
                  const props = parseProps(item.properties)
                  const keyCount = props ? Object.keys(props).length : 0
                  return (
                    <tr key={item.id} style={{ verticalAlign: 'top' }}>
                      <td>{item.id}</td>
                      <td style={{ maxWidth: 200 }}>{item.title}</td>
                      <td style={{ maxWidth: 250, fontSize: 12, wordBreak: 'break-all' }}>
                        <a href={item.url} target="_blank" rel="noreferrer">{item.url}</a>
                      </td>
                      <td>{item.siteName}</td>
                      <td style={{ fontSize: 12, whiteSpace: 'nowrap' }}>{new Date(item.extractedAt).toLocaleString()}</td>
                      <td style={{ fontSize: 12, minWidth: 280 }}>
                        {keyCount === 0 ? (
                          <span style={{ color: '#9ca3af' }}>-</span>
                        ) : isExpanded ? (
                          <div>
                            <button className="btn btn-sm btn-outline" style={{ marginBottom: 8 }}
                              onClick={() => setExpandedId(null)}>
                              Colapsar ({keyCount} campos)
                            </button>
                            <PropsPanel raw={item.properties} />
                          </div>
                        ) : (
                          <button className="btn btn-sm btn-outline"
                            onClick={() => setExpandedId(item.id)}>
                            Ver {keyCount} campos
                          </button>
                        )}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>

          <div className="pagination">
            <button
              className="btn btn-sm btn-outline"
              disabled={page === 0}
              onClick={() => setPage(0)}
            >
              &laquo;
            </button>
            <button
              className="btn btn-sm btn-outline"
              disabled={page === 0}
              onClick={() => setPage(p => p - 1)}
            >
              &lsaquo; Anterior
            </button>
            <span className="pagination-info">
              Pagina {page + 1} de {totalPages} ({totalElements} items)
            </span>
            <button
              className="btn btn-sm btn-outline"
              disabled={page >= totalPages - 1}
              onClick={() => setPage(p => p + 1)}
            >
              Siguiente &rsaquo;
            </button>
            <button
              className="btn btn-sm btn-outline"
              disabled={page >= totalPages - 1}
              onClick={() => setPage(totalPages - 1)}
            >
              &raquo;
            </button>
          </div>
        </>
      )}
    </div>
  )
}
