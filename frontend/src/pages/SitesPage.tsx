import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { getSites, createSite, deleteSite } from '../api'
import { ToastContext } from '../App'
import SiteFormModal from '../components/SiteFormModal'
import type { Site } from '../types'

export default function SitesPage() {
  const [sites, setSites] = useState<Site[]>([])
  const [showModal, setShowModal] = useState(false)
  const [loading, setLoading] = useState(true)

  const load = async () => {
    setLoading(true)
    try {
      setSites(await getSites())
    } catch (e: any) {
      ToastContext.show('Error: ' + e.message, true)
    }
    setLoading(false)
  }

  useEffect(() => { load() }, [])

  const handleCreate = async (data: Partial<Site>) => {
    try {
      await createSite(data)
      ToastContext.show('Sitio creado')
      setShowModal(false)
      load()
    } catch (e: any) {
      ToastContext.show('Error: ' + e.message, true)
    }
  }

  const handleDelete = async (site: Site) => {
    if (!confirm(`Eliminar sitio "${site.name}"?`)) return
    try {
      await deleteSite(site.id)
      ToastContext.show('Sitio eliminado')
      load()
    } catch (e: any) {
      ToastContext.show('Error: ' + e.message, true)
    }
  }

  return (
    <div>
      <div className="page-header">
        <h2>Sitios</h2>
        <button className="btn btn-primary" onClick={() => setShowModal(true)}>Nuevo Sitio</button>
      </div>

      {loading ? (
        <div className="empty-state">Cargando...</div>
      ) : sites.length === 0 ? (
        <div className="empty-state">No hay sitios configurados.</div>
      ) : (
        <div className="table-wrap">
          <table>
            <thead>
              <tr>
                <th>ID</th>
                <th>Nombre</th>
                <th>URL Base</th>
                <th>Estado</th>
                <th>Estrategia</th>
                <th>Delay (ms)</th>
                <th>Creado</th>
                <th>Acciones</th>
              </tr>
            </thead>
            <tbody>
              {sites.map(site => (
                <tr key={site.id}>
                  <td>{site.id}</td>
                  <td><Link to={`/sites/${site.id}`}>{site.name}</Link></td>
                  <td style={{ fontSize: 12 }}>{site.baseUrl}</td>
                  <td><span className={`badge ${site.enabled ? 'badge-green' : 'badge-red'}`}>{site.enabled ? 'Activo' : 'Inactivo'}</span></td>
                  <td><span className="badge badge-blue">{site.extractionConfig?.detailStrategy || 'HTML'}</span></td>
                  <td>{site.politenessDelayMs}</td>
                  <td style={{ fontSize: 12 }}>{site.createdAt ? new Date(site.createdAt).toLocaleDateString() : '-'}</td>
                  <td>
                    <div className="actions-bar">
                      <Link to={`/sites/${site.id}`} className="btn btn-sm btn-outline">Ver</Link>
                      <button className="btn btn-sm btn-danger" onClick={() => handleDelete(site)}>Eliminar</button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}

      {showModal && <SiteFormModal onSave={handleCreate} onClose={() => setShowModal(false)} />}
    </div>
  )
}
