import { useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { useAuth } from './AuthContext'
import PasswordField from '../components/PasswordField'
import { extractErrorMessage } from '../api/client'

/** Pantalla final de recuperacion de contrasena: valida el token del enlace recibido por email y pide la contrasena nueva (dos veces, para evitar errores). */
export default function ResetPasswordPage() {
  const { resetPassword } = useAuth()
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token') ?? ''

  const [password, setPassword] = useState('')
  const [passwordConfirm, setPasswordConfirm] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [done, setDone] = useState(false)

  if (!token) {
    return (
      <div className="login-card">
        <h1>Enlace invalido</h1>
        <p className="hint-text">
          Este enlace no es valido. Pide uno nuevo desde la pantalla de recuperar contrasena.
        </p>
        <Link to="/recuperar-contrasena" className="auth-back-link">
          Pedir un enlace nuevo
        </Link>
      </div>
    )
  }

  if (done) {
    return (
      <div className="login-card">
        <h1>Contrasena actualizada</h1>
        <p className="hint-text">Ya puedes iniciar sesion con tu contrasena nueva.</p>
        <Link to="/login" className="auth-back-link">
          Ir a iniciar sesion
        </Link>
      </div>
    )
  }

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    if (password !== passwordConfirm) {
      setError('Las contrasenas no coinciden')
      return
    }
    setLoading(true)
    try {
      await resetPassword(token, password)
      setDone(true)
    } catch (err) {
      setError(extractErrorMessage(err, 'No se pudo restablecer la contrasena'))
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="login-card">
      <h1>Elige una contrasena nueva</h1>
      <form onSubmit={handleSubmit}>
        <PasswordField
          placeholder="Contrasena nueva (minimo 6 caracteres)"
          value={password}
          onChange={setPassword}
          minLength={6}
          required
          autoComplete="new-password"
        />
        <PasswordField
          placeholder="Repite la contrasena"
          value={passwordConfirm}
          onChange={setPasswordConfirm}
          minLength={6}
          required
          autoComplete="new-password"
        />
        {error && <p className="error-text">{error}</p>}
        <button type="submit" className="primary" disabled={loading}>
          {loading ? 'Guardando...' : 'Guardar contrasena'}
        </button>
      </form>
    </div>
  )
}
