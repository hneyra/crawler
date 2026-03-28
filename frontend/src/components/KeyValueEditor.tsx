import { useState } from 'react'

interface Props {
  value: Record<string, string>
  onChange: (value: Record<string, string>) => void
  keyPlaceholder?: string
  valuePlaceholder?: string
}

export default function KeyValueEditor({ value, onChange, keyPlaceholder = 'key', valuePlaceholder = 'value' }: Props) {
  const entries = Object.entries(value)
  const [newKey, setNewKey] = useState('')
  const [newValue, setNewValue] = useState('')

  const add = () => {
    if (!newKey.trim()) return
    onChange({ ...value, [newKey.trim()]: newValue.trim() })
    setNewKey('')
    setNewValue('')
  }

  const remove = (key: string) => {
    const copy = { ...value }
    delete copy[key]
    onChange(copy)
  }

  return (
    <div className="kv-editor">
      {entries.map(([k, v]) => (
        <div key={k} className="kv-row">
          <input value={k} readOnly style={{ background: '#f9fafb' }} />
          <input value={v} onChange={e => onChange({ ...value, [k]: e.target.value })} />
          <button type="button" className="btn btn-sm btn-danger" onClick={() => remove(k)}>×</button>
        </div>
      ))}
      <div className="kv-row">
        <input value={newKey} onChange={e => setNewKey(e.target.value)} placeholder={keyPlaceholder}
          onKeyDown={e => e.key === 'Enter' && (e.preventDefault(), add())} />
        <input value={newValue} onChange={e => setNewValue(e.target.value)} placeholder={valuePlaceholder}
          onKeyDown={e => e.key === 'Enter' && (e.preventDefault(), add())} />
        <button type="button" className="btn btn-sm btn-success" onClick={add}>+</button>
      </div>
    </div>
  )
}
