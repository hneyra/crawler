import { useEffect, useState } from 'react'
import { getSites, getItems } from '../api'
import { ToastContext } from '../App'
import type { Site, ExtractedItem } from '../types'

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
  const [items, setItems] = useState<ExtractedItem[]>([])
  const [siteFilter, setSiteFilter] = useState<number | ''>('')
  const [expandedId, setExpandedId] = useState<number | null>(null)
  const [loading, setLoading] = useState(true)

  const load = async () => {
    setLoading(true)
    try {
      const [allSites, allItems] = await Promise.all([
        getSites(),
        getItems(100, siteFilter || undefined)
      ])
      setSites(allSites)
      setItems(allItems)
    } catch (e: any) {
      ToastContext.show('Error: ' + e.message, true)
    }
    setLoading(false)
  }

  useEffect(() => { load() }, [siteFilter])

  return (
    <div>
      <div className="page-header">
        <h2>Items Extraidos</h2>
        <div className="actions-bar">
          <select
            value={siteFilter}
            onChange={e => setSiteFilter(e.target.value ? Number(e.target.value) : '')}
            style={{ padding: '6px 10px', borderRadius: 6, border: '1px solid #d1d5db', fontSize: 13 }}
          >
            <option value="">Todos los sitios</option>
            {sites.map(s => <option key={s.id} value={s.id}>{s.name}</option>)}
          </select>
          <button className="btn btn-outline" onClick={load}>Refrescar</button>
        </div>
      </div>

      {loading ? (
        <div className="empty-state">Cargando...</div>
      ) : items.length === 0 ? (
        <div className="empty-state">No hay items extraidos.</div>
      ) : (
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
      )}
    </div>
  )
}
