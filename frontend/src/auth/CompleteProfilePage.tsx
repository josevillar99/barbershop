import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { useAuth } from './AuthContext'
import { extractErrorMessage } from '../api/client'

/** Pantalla para completar el perfil (nombre y telefono) de un usuario que ya inicio sesion pero le faltan esos datos, por ejemplo tras registrarse con Google. */
export default function CompleteProfilePage() {
  const { completeProfile } = useAuth()
  const navigate = useNavigate()

  const [firstName, setFirstName] = useState('')
  const [lastName, setLastName] = useState('')
  const [phone, setPhone] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // Envia el nombre completo y telefono al backend y, si todo va bien, lleva al usuario a la pantalla de reserva.
  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    setLoading(true)
    try {
      await completeProfile(`${firstName.trim()} ${lastName.trim()}`, phone)
      navigate('/')
    } catch (err) {
      setError(extractErrorMessage(err, 'No se pudo completar el perfil'))
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="login-card">
      <h1>Completa tu perfil</h1>
      <p className="hint-text">Nos falta tu nombre y telefono para terminar de configurar tu cuenta.</p>
      <form onSubmit={handleSubmit}>
        <input
          type="text"
          placeholder="Nombre"
          value={firstName}
          onChange={(e) => setFirstName(e.target.value)}
          required
        />
        <input
          type="text"
          placeholder="Apellidos"
          value={lastName}
          onChange={(e) => setLastName(e.target.value)}
          required
        />
        <input
          type="tel"
          placeholder="Telefono"
          value={phone}
          onChange={(e) => setPhone(e.target.value)}
          required
        />
        {error && <p className="error-text">{error}</p>}
        <button type="submit" className="primary" disabled={loading}>
          {loading ? 'Guardando...' : 'Continuar'}
        </button>
      </form>
    </div>
  )
}
