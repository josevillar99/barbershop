import type { ReactNode } from 'react'

interface CheckboxProps {
  checked: boolean
  onChange: (checked: boolean) => void
  label?: ReactNode
  disabled?: boolean
  id?: string
}

/** Checkbox con estilo propio (caja y marca dibujadas por CSS), manteniendo el input nativo por accesibilidad y teclado. */
export default function Checkbox({ checked, onChange, label, disabled, id }: CheckboxProps) {
  return (
    <label className={`checkbox${disabled ? ' checkbox--disabled' : ''}`}>
      <input
        id={id}
        type="checkbox"
        checked={checked}
        disabled={disabled}
        onChange={(e) => onChange(e.target.checked)}
      />
      <span className="checkbox-box" aria-hidden="true" />
      {label && <span className="checkbox-label">{label}</span>}
    </label>
  )
}
