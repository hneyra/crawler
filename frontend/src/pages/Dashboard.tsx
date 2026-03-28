import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getSites, getSiteStats, launchCrawl, startDiscoverySite, startExtraction } from '../api'
import { ToastContext } from '../App'
import type { Site, SiteStats } from '../types'

export default function Dashboard() {
  const [sites, setSites] = useState<Site[]>([])
  const [stats, setStats] = useState<Record<number, SiteStats>>({})
  const [loading, setLoading] = useState(true)

  const load = async () => {
    setLoading(true)
    try {
      const allSites = await getSites()
      setSites(allSites)
      const statsMap: Record<number, SiteStats> = {}
      await Promise.all(allSites.map(async (s) => {
        statsMap[s.id] = await getSiteStats(s.id)
      }))
      setStats(statsMap)
    } catch (e: any) {
      ToastContext.show('Error cargando datos: ' + e.message, true)
    }
    setLoading(false)
  }

  useEffect(() => { load() }, [])

  const handleAction = async (action: () => Promise<any>, msg: string) => {
    try {
      await action()
      ToastContext.show(msg)
      setTimeout(load, 2000)
    } catch (e: any) {
      ToastContext.show('Error: ' + e.message, true)
    }
  }

  if (loading) return <div className="empty-state">Cargando...</div>

  return (
    <div>
      <div className="page-header">
        <h2>Dashboard</h2>
        <button className="btn btn-outline" onClick={load}>Refrescar</button>
      </div>

      {sites.length === 0 ? (
        <div className="empty-state">
          <p>No hay sitios configurados.</p>
          <Link to="/sites" className="btn btn-primary" style={{ marginTop: 12, display: 'inline-flex' }}>Agregar Sitio</Link>
        </div>
      ) : (
        <div className="card-grid">
          {sites.map(site => {
            const s = stats[site.id]
            return (
              <div key={site.id} className="card">
                <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'start', marginBottom: 8 }}>
                  <div>
                    <Link to={`/sites/${site.id}`} style={{ fontSize: 16, fontWeight: 600 }}>{site.name}</Link>
                    <div style={{ fontSize: 12, color: '#6b7280', marginTop: 2 }}>{site.baseUrl}</div>
                  </div>
                  <span className={`badge ${site.enabled ? 'badge-green' : 'badge-red'}`}>
                    {site.enabled ? 'Activo' : 'Inactivo'}
                  </span>
                </div>

                {s && (
                  <>
                    <div className="stat-row"><span className="stat-label">Total URLs</span><span className="stat-value">{s.totalUrls}</span></div>
                    <div className="stat-row"><span className="stat-label">Pendientes</span><span className="stat-value">{s.pending}</span></div>
                    <div className="stat-row"><span className="stat-label">En progreso</span><span className="stat-value">{s.inProgress}</span></div>
                    <div className="stat-row"><span className="stat-label">Completadas</span><span className="stat-value" style={{ color: '#059669' }}>{s.completed}</span></div>
                    <div className="stat-row"><span className="stat-label">Fallidas</span><span className="stat-value" style={{ color: '#dc2626' }}>{s.failed}</span></div>
                    <div className="stat-row"><span className="stat-label">Items extraidos</span><span className="stat-value">{s.extractedItems}</span></div>
                    <div className="stat-row"><span className="stat-label">Ultimo crawl</span><span className="stat-value">{s.lastCrawl ? new Date(s.lastCrawl).toLocaleString() : '-'}</span></div>
                  </>
                )}

                <div className="actions-bar" style={{ marginTop: 12 }}>
                  <button className="btn btn-sm btn-primary"
                    onClick={() => handleAction(() => launchCrawl(site.id), `Crawl iniciado para ${site.name}`)}>
                    Crawl Completo
                  </button>
                  <button className="btn btn-sm btn-success"
                    onClick={() => handleAction(() => startDiscoverySite(site.id), `Discovery iniciado para ${site.name}`)}>
                    Discovery
                  </button>
                  <button className="btn btn-sm btn-warning"
                    onClick={() => handleAction(() => startExtraction(site.id), `Extraction iniciado para ${site.name}`)}>
                    Extraction
                  </button>
                </div>
              </div>
            )
          })}
        </div>
      )}
    </div>
  )
}
