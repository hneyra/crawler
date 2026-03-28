import { useEffect, useState } from 'react'
import { getSites, getItems } from '../api'
import { ToastContext } from '../App'
import type { Site, ExtractedItem } from '../types'

export default function ItemsPage() {
  const [sites, setSites] = useState<Site[]>([])
  const [items, setItems] = useState<ExtractedItem[]>([])
  const [siteFilter, setSiteFilter] = useState<number | ''>('')
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
              {items.map(item => (
                <tr key={item.id}>
                  <td>{item.id}</td>
                  <td style={{ maxWidth: 200, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>{item.title}</td>
                  <td style={{ maxWidth: 250, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', fontSize: 12 }}>
                    <a href={item.url} target="_blank" rel="noreferrer">{item.url}</a>
                  </td>
                  <td>{item.siteName}</td>
                  <td style={{ fontSize: 12 }}>{new Date(item.extractedAt).toLocaleString()}</td>
                  <td style={{ maxWidth: 300, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap', fontSize: 12, color: '#6b7280' }}
                    title={item.properties || ''}>
                    {item.properties ? truncate(item.properties, 80) : '-'}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}

function truncate(s: string, n: number) {
  return s.length > n ? s.substring(0, n) + '...' : s
}
