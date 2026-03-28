import { useState } from 'react'
import type { Category } from '../types'

interface Props {
  category?: Category
  onSave: (data: Partial<Category>) => void
  onClose: () => void
}

export default function CategoryFormModal({ category, onSave, onClose }: Props) {
  const [name, setName] = useState(category?.name || '')
  const [url, setUrl] = useState(category?.url || '')
  const [paginationType, setPaginationType] = useState(category?.paginationType || 'PAGE_PARAM')
  const [itemLinkSelector, setItemLinkSelector] = useState(category?.itemLinkSelector || '')
  const [linkExtractionType, setLinkExtractionType] = useState(category?.linkExtractionType || 'HREF')
  const [linkAttribute, setLinkAttribute] = useState(category?.linkAttribute || '')
  const [nextPageSelector, setNextPageSelector] = useState(category?.nextPageSelector || '')
  const [enabled, setEnabled] = useState(category?.enabled ?? true)

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    onSave({
      name,
      url,
      paginationType,
      itemLinkSelector,
      linkExtractionType,
      linkAttribute: linkAttribute || null,
      nextPageSelector: nextPageSelector || null,
      enabled,
    })
  }

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal" onClick={e => e.stopPropagation()}>
        <h3>{category ? 'Editar Categoria' : 'Nueva Categoria'}</h3>
        <form onSubmit={handleSubmit}>
          <div className="form-row">
            <div className="form-group">
              <label>Nombre</label>
              <input value={name} onChange={e => setName(e.target.value)} required />
            </div>
            <div className="form-group">
              <label>URL</label>
              <input value={url} onChange={e => setUrl(e.target.value)} required />
            </div>
          </div>

          <div className="form-row">
            <div className="form-group">
              <label>Tipo de paginacion</label>
              <select value={paginationType} onChange={e => setPaginationType(e.target.value as any)}>
                <option value="PAGE_PARAM">PAGE_PARAM</option>
                <option value="CURSOR">CURSOR</option>
                <option value="SCROLL_AJAX">SCROLL_AJAX</option>
              </select>
            </div>
            <div className="form-group">
              <label>Tipo de extraccion de links</label>
              <select value={linkExtractionType} onChange={e => setLinkExtractionType(e.target.value as any)}>
                <option value="HREF">HREF</option>
                <option value="ONCLICK">ONCLICK</option>
                <option value="DATA_ATTRIBUTE">DATA_ATTRIBUTE</option>
              </select>
            </div>
          </div>

          <div className="form-group">
            <label>Item Link Selector (CSS)</label>
            <input value={itemLinkSelector} onChange={e => setItemLinkSelector(e.target.value)} required placeholder="a.product-link" />
          </div>

          <div className="form-row">
            <div className="form-group">
              <label>Next Page Selector (CSS, opcional)</label>
              <input value={nextPageSelector} onChange={e => setNextPageSelector(e.target.value)} placeholder="a.next-page" />
            </div>
            {linkExtractionType === 'DATA_ATTRIBUTE' && (
              <div className="form-group">
                <label>Link Attribute</label>
                <input value={linkAttribute} onChange={e => setLinkAttribute(e.target.value)} placeholder="data-href" />
              </div>
            )}
          </div>

          <div className="form-group form-check">
            <input type="checkbox" checked={enabled} onChange={e => setEnabled(e.target.checked)} id="cat-enabled" />
            <label htmlFor="cat-enabled" style={{ marginBottom: 0 }}>Habilitada</label>
          </div>

          <div className="modal-actions">
            <button type="button" className="btn btn-outline" onClick={onClose}>Cancelar</button>
            <button type="submit" className="btn btn-primary">{category ? 'Guardar' : 'Crear'}</button>
          </div>
        </form>
      </div>
    </div>
  )
}
