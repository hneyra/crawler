import { useState } from 'react'
import type { Site, ExtractionConfig } from '../types'
import KeyValueEditor from './KeyValueEditor'

interface Props {
  site?: Site
  onSave: (data: Partial<Site>) => void
  onClose: () => void
}

export default function SiteFormModal({ site, onSave, onClose }: Props) {
  const [name, setName] = useState(site?.name || '')
  const [baseUrl, setBaseUrl] = useState(site?.baseUrl || '')
  const [enabled, setEnabled] = useState(site?.enabled ?? true)
  const [politenessDelayMs, setPolitenessDelayMs] = useState(site?.politenessDelayMs ?? 1000)
  const [detailStrategy, setDetailStrategy] = useState<ExtractionConfig['detailStrategy']>(
    site?.extractionConfig?.detailStrategy || 'HTML'
  )
  const [fieldSelectors, setFieldSelectors] = useState<Record<string, string>>(
    site?.extractionConfig?.fieldSelectors || {}
  )
  const [scriptPatterns, setScriptPatterns] = useState(
    (site?.extractionConfig?.scriptPatterns || []).join('\n')
  )
  const [jsonPaths, setJsonPaths] = useState<Record<string, string>>(
    site?.extractionConfig?.jsonPaths || {}
  )
  const [interceptPatterns, setInterceptPatterns] = useState(
    (site?.extractionConfig?.interceptPatterns || []).join('\n')
  )

  const handleSubmit = (e: React.FormEvent) => {
    e.preventDefault()
    onSave({
      name,
      baseUrl,
      enabled,
      politenessDelayMs,
      extractionConfig: {
        detailStrategy,
        fieldSelectors,
        scriptPatterns: scriptPatterns.split('\n').map(s => s.trim()).filter(Boolean),
        jsonPaths,
        interceptPatterns: interceptPatterns.split('\n').map(s => s.trim()).filter(Boolean),
      }
    })
  }

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div className="modal" onClick={e => e.stopPropagation()}>
        <h3>{site ? 'Editar Sitio' : 'Nuevo Sitio'}</h3>
        <form onSubmit={handleSubmit}>
          <div className="form-row">
            <div className="form-group">
              <label>Nombre</label>
              <input value={name} onChange={e => setName(e.target.value)} required />
            </div>
            <div className="form-group">
              <label>URL Base</label>
              <input value={baseUrl} onChange={e => setBaseUrl(e.target.value)} required />
            </div>
          </div>

          <div className="form-row">
            <div className="form-group">
              <label>Delay entre requests (ms)</label>
              <input type="number" value={politenessDelayMs} onChange={e => setPolitenessDelayMs(Number(e.target.value))} min={0} />
            </div>
            <div className="form-group">
              <label>Estrategia de extraccion</label>
              <select value={detailStrategy} onChange={e => setDetailStrategy(e.target.value as any)}>
                <option value="HTML">HTML</option>
                <option value="SCRIPT_JSON">SCRIPT_JSON</option>
                <option value="AJAX">AJAX</option>
              </select>
            </div>
          </div>

          <div className="form-group form-check">
            <input type="checkbox" checked={enabled} onChange={e => setEnabled(e.target.checked)} id="site-enabled" />
            <label htmlFor="site-enabled" style={{ marginBottom: 0 }}>Habilitado</label>
          </div>

          <div className="form-group">
            <label>Field Selectors (campo → selector CSS)</label>
            <KeyValueEditor value={fieldSelectors} onChange={setFieldSelectors} keyPlaceholder="campo" valuePlaceholder="selector CSS" />
          </div>

          {detailStrategy === 'SCRIPT_JSON' && (
            <>
              <div className="form-group">
                <label>Script Patterns (uno por linea)</label>
                <textarea value={scriptPatterns} onChange={e => setScriptPatterns(e.target.value)} placeholder="__NEXT_DATA__" />
              </div>
              <div className="form-group">
                <label>JSON Paths (campo → JSONPath)</label>
                <KeyValueEditor value={jsonPaths} onChange={setJsonPaths} keyPlaceholder="campo" valuePlaceholder="$.path.to.value" />
              </div>
            </>
          )}

          {detailStrategy === 'AJAX' && (
            <div className="form-group">
              <label>Intercept Patterns (uno por linea)</label>
              <textarea value={interceptPatterns} onChange={e => setInterceptPatterns(e.target.value)} placeholder="/api/product/*" />
            </div>
          )}

          <div className="modal-actions">
            <button type="button" className="btn btn-outline" onClick={onClose}>Cancelar</button>
            <button type="submit" className="btn btn-primary">{site ? 'Guardar' : 'Crear'}</button>
          </div>
        </form>
      </div>
    </div>
  )
}
