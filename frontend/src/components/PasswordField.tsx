import { useState } from 'react'

interface PasswordFieldProps {
  id?: string
  value: string
  onChange: (value: string) => void
  placeholder?: string
  minLength?: number
  required?: boolean
  autoComplete?: string
}

/** Input de contrasena con boton para mostrar/ocultar el texto escrito, para que el usuario pueda comprobar que no se ha equivocado. */
export default function PasswordField({
  id,
  value,
  onChange,
  placeholder,
  minLength,
  required,
  autoComplete,
}: PasswordFieldProps) {
  const [visible, setVisible] = useState(false)

  return (
    <div className="password-field">
      <input
        id={id}
        type={visible ? 'text' : 'password'}
        placeholder={placeholder}
        value={value}
        minLength={minLength}
        required={required}
        autoComplete={autoComplete}
        onChange={(e) => onChange(e.target.value)}
      />
      <button
        type="button"
        className="password-field-toggle"
        tabIndex={-1}
        onClick={() => setVisible((v) => !v)}
        aria-label={visible ? 'Ocultar contrasena' : 'Mostrar contrasena'}
      >
        {visible ? '🙈' : '👁️'}
      </button>
    </div>
  )
}
