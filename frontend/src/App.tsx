import { Routes, Route, NavLink } from 'react-router-dom'
import { useState, useCallback } from 'react'
import Dashboard from './pages/Dashboard'
import SitesPage from './pages/SitesPage'
import SiteDetailPage from './pages/SiteDetailPage'
import ItemsPage from './pages/ItemsPage'

interface Toast {
  id: number
  message: string
  error?: boolean
}

export const ToastContext = {
  _listeners: [] as ((msg: string, error?: boolean) => void)[],
  show(msg: string, error?: boolean) {
    this._listeners.forEach(fn => fn(msg, error))
  }
}

export default function App() {
  const [toasts, setToasts] = useState<Toast[]>([])
  let nextId = 0

  const addToast = useCallback((message: string, error?: boolean) => {
    const id = ++nextId
    setToasts(prev => [...prev, { id, message, error }])
    setTimeout(() => setToasts(prev => prev.filter(t => t.id !== id)), 4000)
  }, [])

  ToastContext._listeners = [addToast]

  return (
    <div className="layout">
      <aside className="sidebar">
        <h1>Crawler Admin</h1>
        <nav>
          <NavLink to="/" end>Dashboard</NavLink>
          <NavLink to="/sites">Sitios</NavLink>
          <NavLink to="/items">Items Extraidos</NavLink>
        </nav>
      </aside>
      <main className="main-content">
        <Routes>
          <Route path="/" element={<Dashboard />} />
          <Route path="/sites" element={<SitesPage />} />
          <Route path="/sites/:id" element={<SiteDetailPage />} />
          <Route path="/items" element={<ItemsPage />} />
        </Routes>
      </main>
      <div className="toast-container">
        {toasts.map(t => (
          <div key={t.id} className={`toast ${t.error ? 'toast-error' : ''}`}>{t.message}</div>
        ))}
      </div>
    </div>
  )
}
