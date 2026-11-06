import { useState, type FormEvent } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { useAuth } from './AuthContext'
import GoogleSignInButton from './GoogleSignInButton'
import PasswordField from '../components/PasswordField'
import { extractErrorMessage } from '../api/client'

type Tab = 'login' | 'signup'

/** Pantalla de acceso: permite iniciar sesion, pedir el enlace para crear una cuenta nueva, o entrar con Google. */
export default function LoginPage() {
  const { login, signup, loginWithGoogle } = useAuth()
  const navigate = useNavigate()

  const [tab, setTab] = useState<Tab>('login')
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [signupSent, setSignupSent] = useState(false)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)

  // Envia email y contrasena para iniciar sesion, y navega a la pagina principal si tiene exito.
  const handleLogin = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    setLoading(true)
    try {
      await login(email, password)
      navigate('/')
    } catch (err) {
      setError(extractErrorMessage(err, 'Email o contrasena incorrectos'))
    } finally {
      setLoading(false)
    }
  }

  // Pide al backend que envie el enlace de confirmacion de registro al email introducido.
  const handleSignup = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    setLoading(true)
    try {
      await signup(email)
      setSignupSent(true)
    } catch (err) {
      setError(extractErrorMessage(err, 'No se pudo enviar el enlace'))
    } finally {
      setLoading(false)
    }
  }

  // Recibe el token de Google del boton de inicio de sesion e inicia sesion en la app con el.
  const handleGoogle = async (idToken: string) => {
    setError(null)
    try {
      await loginWithGoogle(idToken)
      navigate('/')
    } catch (err) {
      setError(extractErrorMessage(err, 'No se pudo iniciar sesion con Google'))
    }
  }

  // Cambia entre la pestana de "Iniciar sesion" y "Crear cuenta", limpiando el error y el mensaje de envio previo.
  const switchTab = (next: Tab) => {
    setTab(next)
    setError(null)
    setSignupSent(false)
  }

  return (
    <div className="login-card">
      <img src="/branding/main-icon.png" alt="" className="login-logo" />
      <h1>Castor Barber Shop</h1>

      <div className="auth-tabs">
        <button className={tab === 'login' ? 'active' : ''} onClick={() => switchTab('login')}>
          Iniciar sesion
        </button>
        <button className={tab === 'signup' ? 'active' : ''} onClick={() => switchTab('signup')}>
          Crear cuenta
        </button>
      </div>

      {tab === 'login' && (
        <form onSubmit={handleLogin}>
          <input
            type="email"
            placeholder="tu@email.com"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            required
          />
          <PasswordField
            placeholder="Contrasena"
            value={password}
            onChange={setPassword}
            required
            autoComplete="current-password"
          />
          <Link to="/recuperar-contrasena" className="forgot-password-link">
            ¿Olvidaste tu contrasena?
          </Link>
          {error && <p className="error-text">{error}</p>}
          <button type="submit" className="primary" disabled={loading}>
            {loading ? 'Entrando...' : 'Entrar'}
          </button>
        </form>
      )}

      {tab === 'signup' &&
        (signupSent ? (
          <p className="hint-text">
            Te hemos enviado un enlace a <strong>{email}</strong> para completar tu registro. Revisa tu correo.
          </p>
        ) : (
          <form onSubmit={handleSignup}>
            <p className="hint-text">Introduce tu email y te enviaremos un enlace para crear tu cuenta.</p>
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
        ))}

      <div className="auth-divider">o</div>
      <GoogleSignInButton onCredential={handleGoogle} />
    </div>
  )
}
