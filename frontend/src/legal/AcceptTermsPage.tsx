import { useState, type FormEvent } from 'react'
import { useNavigate, Link } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'
import Checkbox from '../components/Checkbox'
import { extractErrorMessage } from '../api/client'

/**
 * Pantalla que se interpone antes de dejar entrar a la app si el usuario todavia no ha
 * aceptado los terminos y la politica de privacidad vigentes (cuentas nuevas, o cuentas ya
 * existentes la primera vez que inician sesion tras añadir esta pantalla).
 */
export default function AcceptTermsPage() {
  const { acceptTerms, logout } = useAuth()
  const navigate = useNavigate()

  const [checked, setChecked] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    setLoading(true)
    try {
      await acceptTerms()
      navigate('/')
    } catch (err) {
      setError(extractErrorMessage(err, 'No se pudo registrar la aceptacion'))
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="login-card">
      <h1>Antes de continuar</h1>
      <p className="hint-text">
        Hemos actualizado nuestras condiciones. Para seguir usando Castor Barber Shop, revisa y acepta
        los siguientes documentos.
      </p>
      <form onSubmit={handleSubmit}>
        <p className="accept-terms-links">
          <Link to="/terminos" target="_blank" rel="noreferrer">
            Términos y condiciones
          </Link>
          {' · '}
          <Link to="/privacidad" target="_blank" rel="noreferrer">
            Política de privacidad
          </Link>
        </p>
        <div className="accept-terms-checkbox">
          <Checkbox
            checked={checked}
            onChange={setChecked}
            label="He leído y acepto los términos y condiciones y la política de privacidad"
          />
        </div>
        {error && <p className="error-text">{error}</p>}
        <button type="submit" className="primary" disabled={!checked || loading}>
          {loading ? 'Guardando...' : 'Continuar'}
        </button>
      </form>
      <button type="button" onClick={logout} className="link-button">
        Cerrar sesión
      </button>
    </div>
  )
}
