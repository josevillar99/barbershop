import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { useAuth } from './AuthContext'
import { extractErrorMessage } from '../api/client'

/** Pantalla para pedir el enlace de restablecimiento de contrasena: solo pide el email y avisa cuando el correo se ha enviado. */
export default function ForgotPasswordPage() {
  const { forgotPassword } = useAuth()

  const [email, setEmail] = useState('')
  const [sent, setSent] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    setLoading(true)
    try {
      await forgotPassword(email)
      setSent(true)
    } catch (err) {
      setError(extractErrorMessage(err, 'No se pudo enviar el enlace'))
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="login-card">
      <img src="/branding/main-icon.png" alt="" className="login-logo" />
      <h1>Recuperar contrasena</h1>

      {sent ? (
        <>
          <p className="hint-text">
            Si <strong>{email}</strong> tiene una cuenta con nosotros, te hemos enviado un enlace para elegir una
            contrasena nueva. Revisa tu correo.
          </p>
        </>
      ) : (
        <form onSubmit={handleSubmit}>
          <p className="hint-text">Introduce tu email y te enviaremos un enlace para restablecer tu contrasena.</p>
          <input
            type="email"
            placeholder="tu@email.com"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
          />
          {error && <p className="error-text">{error}</p>}
          <button type="submit" className="primary" disabled={loading}>
            {loading ? 'Enviando...' : 'Enviar enlace'}
          </button>
        </form>
      )}

      <Link to="/login" className="auth-back-link">
        Volver a iniciar sesion
      </Link>
    </div>
  )
}
