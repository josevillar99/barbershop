import { useState, type FormEvent } from 'react'
import { useNavigate, useSearchParams } from 'react-router-dom'
import { useAuth } from './AuthContext'
import PasswordField from '../components/PasswordField'
import { extractErrorMessage } from '../api/client'

/** Pantalla final del registro por email: valida el token del enlace recibido y pide contrasena, nombre y telefono para crear la cuenta. */
export default function CompleteRegistrationPage() {
  const { completeSignup } = useAuth()
  const navigate = useNavigate()
  const [searchParams] = useSearchParams()
  const token = searchParams.get('token') ?? ''

  const [password, setPassword] = useState('')
  const [passwordConfirm, setPasswordConfirm] = useState('')
  const [firstName, setFirstName] = useState('')
  const [lastName, setLastName] = useState('')
  const [phone, setPhone] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  if (!token) {
    return (
      <div className="login-card">
        <h1>Enlace invalido</h1>
        <p className="hint-text">
          Este enlace no es valido. Pide uno nuevo desde la pantalla de crear cuenta.
        </p>
      </div>
    )
  }

  // Envia el token, la contrasena elegida y los datos personales para terminar de crear la cuenta.
  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    if (password !== passwordConfirm) {
      setError('Las contrasenas no coinciden')
      return
    }
    setLoading(true)
    try {
      await completeSignup(token, password, `${firstName.trim()} ${lastName.trim()}`, phone)
      navigate('/')
    } catch (err) {
      setError(extractErrorMessage(err, 'No se pudo completar el registro'))
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="login-card">
      <h1>Completa tu registro</h1>
      <p className="hint-text">Ya hemos confirmado tu email. Elige una contrasena y rellena tus datos.</p>
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
        <PasswordField
          placeholder="Contrasena (minimo 6 caracteres)"
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
          {loading ? 'Creando cuenta...' : 'Crear cuenta'}
        </button>
      </form>
    </div>
  )
}
