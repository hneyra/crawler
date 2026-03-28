import { useEffect, useState } from 'react'
import { useParams, Link } from 'react-router-dom'
import {
  getSite, updateSite, getCategories, createCategory, updateCategory, deleteCategory,
  getSiteStats, launchCrawl, startDiscoverySite, startDiscoveryCategory, startExtraction
} from '../api'
import { ToastContext } from '../App'
import SiteFormModal from '../components/SiteFormModal'
import CategoryFormModal from '../components/CategoryFormModal'
import type { Site, Category, SiteStats } from '../types'

export default function SiteDetailPage() {
  const { id } = useParams<{ id: string }>()
  const siteId = Number(id)
  const [site, setSite] = useState<Site | null>(null)
  const [categories, setCategories] = useState<Category[]>([])
  const [stats, setStats] = useState<SiteStats | null>(null)
  const [editSite, setEditSite] = useState(false)
  const [categoryModal, setCategoryModal] = useState<Category | 'new' | null>(null)

  const load = async () => {
    try {
      const [s, cats, st] = await Promise.all([
        getSite(siteId),
        getCategories(siteId),
        getSiteStats(siteId)
      ])
      setSite(s)
      setCategories(cats)
      setStats(st)
    } catch (e: any) {
      ToastContext.show('Error: ' + e.message, true)
    }
  }

  useEffect(() => { load() }, [siteId])

  const handleAction = async (action: () => Promise<any>, msg: string) => {
    try {
      await action()
      ToastContext.show(msg)
      setTimeout(load, 2000)
    } catch (e: any) {
      ToastContext.show('Error: ' + e.message, true)
    }
  }

  const handleUpdateSite = async (data: Partial<Site>) => {
    try {
      await updateSite(siteId, data)
      ToastContext.show('Sitio actualizado')
      setEditSite(false)
      load()
    } catch (e: any) {
      ToastContext.show('Error: ' + e.message, true)
    }
  }

  const handleSaveCategory = async (data: Partial<Category>) => {
    try {
      if (categoryModal === 'new') {
        await createCategory(siteId, data)
        ToastContext.show('Categoria creada')
      } else {
        await updateCategory(categoryModal!.id, data)
        ToastContext.show('Categoria actualizada')
      }
      setCategoryModal(null)
      load()
    } catch (e: any) {
      ToastContext.show('Error: ' + e.message, true)
    }
  }

  const handleDeleteCategory = async (cat: Category) => {
    if (!confirm(`Eliminar categoria "${cat.name}"?`)) return
    try {
      await deleteCategory(cat.id)
      ToastContext.show('Categoria eliminada')
      load()
    } catch (e: any) {
      ToastContext.show('Error: ' + e.message, true)
    }
  }

  if (!site) return <div className="empty-state">Cargando...</div>

  return (
    <div>
      <div className="page-header">
        <div>
          <div style={{ fontSize: 13, color: '#6b7280', marginBottom: 4 }}>
            <Link to="/sites">Sitios</Link> / {site.name}
          </div>
          <h2>{site.name}</h2>
        </div>
        <div className="actions-bar">
          <button className="btn btn-outline" onClick={load}>Refrescar</button>
          <button className="btn btn-primary" onClick={() => setEditSite(true)}>Editar Sitio</button>
        </div>
      </div>

      {/* Site info + stats */}
      <div className="card-grid" style={{ marginBottom: 24 }}>
        <div className="card">
          <h3 style={{ fontSize: 15, marginBottom: 12 }}>Informacion</h3>
          <div className="stat-row"><span className="stat-label">URL Base</span><span className="stat-value" style={{ fontSize: 12 }}>{site.baseUrl}</span></div>
          <div className="stat-row"><span className="stat-label">Estado</span><span className={`badge ${site.enabled ? 'badge-green' : 'badge-red'}`}>{site.enabled ? 'Activo' : 'Inactivo'}</span></div>
          <div className="stat-row"><span className="stat-label">Delay</span><span className="stat-value">{site.politenessDelayMs}ms</span></div>
          <div className="stat-row"><span className="stat-label">Estrategia</span><span className="badge badge-blue">{site.extractionConfig?.detailStrategy || 'HTML'}</span></div>
          {site.extractionConfig?.fieldSelectors && Object.keys(site.extractionConfig.fieldSelectors).length > 0 && (
            <div style={{ marginTop: 8 }}>
              <span className="stat-label" style={{ fontSize: 12 }}>Field Selectors:</span>
              <div style={{ fontSize: 12, color: '#6b7280', marginTop: 4 }}>
                {Object.entries(site.extractionConfig.fieldSelectors).map(([k, v]) => (
                  <div key={k}><code>{k}</code>: <code>{v}</code></div>
                ))}
              </div>
            </div>
          )}
        </div>

        {stats && (
          <div className="card">
            <h3 style={{ fontSize: 15, marginBottom: 12 }}>Estadisticas</h3>
            <div className="stat-row"><span className="stat-label">Total URLs</span><span className="stat-value">{stats.totalUrls}</span></div>
            <div className="stat-row"><span className="stat-label">Pendientes</span><span className="stat-value">{stats.pending}</span></div>
            <div className="stat-row"><span className="stat-label">En progreso</span><span className="stat-value">{stats.inProgress}</span></div>
            <div className="stat-row"><span className="stat-label">Completadas</span><span className="stat-value" style={{ color: '#059669' }}>{stats.completed}</span></div>
            <div className="stat-row"><span className="stat-label">Fallidas</span><span className="stat-value" style={{ color: '#dc2626' }}>{stats.failed}</span></div>
            <div className="stat-row"><span className="stat-label">Items extraidos</span><span className="stat-value">{stats.extractedItems}</span></div>
            <div className="stat-row"><span className="stat-label">Ultimo crawl</span><span className="stat-value">{stats.lastCrawl ? new Date(stats.lastCrawl).toLocaleString() : '-'}</span></div>
          </div>
        )}
      </div>

      {/* Actions */}
      <div className="card" style={{ marginBottom: 24 }}>
        <h3 style={{ fontSize: 15, marginBottom: 12 }}>Acciones</h3>
        <div className="actions-bar">
          <button className="btn btn-primary"
            onClick={() => handleAction(() => launchCrawl(siteId), `Crawl completo iniciado`)}>
            Crawl Completo
          </button>
          <button className="btn btn-success"
            onClick={() => handleAction(() => startDiscoverySite(siteId), `Discovery iniciado`)}>
            Discovery (todas las categorias)
          </button>
          <button className="btn btn-warning"
            onClick={() => handleAction(() => startExtraction(siteId), `Extraction iniciado`)}>
            Extraction
          </button>
        </div>
      </div>

      {/* Categories */}
      <div className="page-header">
        <h3>Categorias</h3>
        <button className="btn btn-primary btn-sm" onClick={() => setCategoryModal('new')}>Nueva Categoria</button>
      </div>

      {categories.length === 0 ? (
        <div className="empty-state" style={{ padding: 32 }}>No hay categorias.</div>
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>Nombre</th>
                <th>URL</th>
                <th>Paginacion</th>
                <th>Selector</th>
                <th>Estado</th>
                <th>Acciones</th>
              </tr>
            </thead>
            <tbody>
              {categories.map(cat => (
                <tr key={cat.id}>
                  <td>{cat.id}</td>
                  <td>{cat.name}</td>
                  <td style={{ fontSize: 12, maxWidth: 250, overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }}>
                    <a href={cat.url} target="_blank" rel="noreferrer">{cat.url}</a>
                  </td>
                  <td><span className="badge badge-gray">{cat.paginationType}</span></td>
                  <td style={{ fontSize: 12 }}><code>{cat.itemLinkSelector}</code></td>
                  <td><span className={`badge ${cat.enabled ? 'badge-green' : 'badge-red'}`}>{cat.enabled ? 'Activa' : 'Inactiva'}</span></td>
                  <td>
                    <div className="actions-bar">
                      <button className="btn btn-sm btn-success"
                        onClick={() => handleAction(() => startDiscoveryCategory(cat.id), `Discovery iniciado para ${cat.name}`)}>
                        Discovery
                      </button>
                      <button className="btn btn-sm btn-outline" onClick={() => setCategoryModal(cat)}>Editar</button>
                      <button className="btn btn-sm btn-danger" onClick={() => handleDeleteCategory(cat)}>Eliminar</button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {editSite && <SiteFormModal site={site} onSave={handleUpdateSite} onClose={() => setEditSite(false)} />}
      {categoryModal && (
        <CategoryFormModal
          category={categoryModal === 'new' ? undefined : categoryModal}
          onSave={handleSaveCategory}
          onClose={() => setCategoryModal(null)}
        />
      )}
    </div>
  )
}
