import { useState } from 'react'
import { Link, Navigate, NavLink, Route, Routes } from 'react-router-dom'
import { useAuth } from './auth/AuthContext'
import ProtectedRoute from './routes/ProtectedRoute'
import AdminRoute from './routes/AdminRoute'
import RequireAuthOnly from './routes/RequireAuthOnly'
import LoginPage from './auth/LoginPage'
import CompleteRegistrationPage from './auth/CompleteRegistrationPage'
import CompleteProfilePage from './auth/CompleteProfilePage'
import ForgotPasswordPage from './auth/ForgotPasswordPage'
import ResetPasswordPage from './auth/ResetPasswordPage'
import ProfilePage from './auth/ProfilePage'
import BookingPage from './booking/BookingPage'
import AdminPage from './admin/AdminPage'
import HelpModal from './components/HelpModal'
import Footer from './components/Footer'
import PrivacyPolicyPage from './legal/PrivacyPolicyPage'
import TermsPage from './legal/TermsPage'
import AcceptTermsPage from './legal/AcceptTermsPage'

/** Barra superior con el titulo, los enlaces de navegacion (segun el rol) y el boton de cerrar sesion; no se muestra si no hay usuario logueado. */
function NavBar() {
  const { user, logout } = useAuth()
  const [showHelp, setShowHelp] = useState(false)
  if (!user) return null

  return (
    <header className="navbar">
      <img src="/branding/navbar-icon.png" alt="Castor Barber Shop" className="navbar-logo" />
      <nav className="navbar-links">
        <NavLink to="/" end className={({ isActive }) => (isActive ? 'navbar-link-active' : undefined)}>
          Reservar
        </NavLink>
        {(user.role === 'ADMIN' || user.role === 'SUPERADMIN') && (
          <NavLink to="/admin" className={({ isActive }) => (isActive ? 'navbar-link-active' : undefined)}>
            Panel admin
          </NavLink>
        )}
      </nav>
      <div className="navbar-user">
        {user.role === 'USER' && (
          <button className="help-icon-button" onClick={() => setShowHelp(true)} aria-label="Ayuda">
            <img src="/branding/informacion.png" alt="Ayuda" className="help-icon-image" />
          </button>
        )}
        <Link to="/perfil" className="navbar-user-name">
          {user.name || user.email}
        </Link>
        <button onClick={logout}>Salir</button>
      </div>
      {showHelp && <HelpModal onClose={() => setShowHelp(false)} />}
    </header>
  )
}

/** Componente raiz de la aplicacion: define todas las rutas (login, registro, reserva, panel admin) y protege cada una segun corresponda. */
export default function App() {
  return (
    <div className="app-shell">
      <NavBar />
      <main className="app-main">
        <Routes>
          <Route path="/login" element={<LoginPage />} />
          <Route path="/completar-registro" element={<CompleteRegistrationPage />} />
          <Route path="/recuperar-contrasena" element={<ForgotPasswordPage />} />
          <Route path="/restablecer-contrasena" element={<ResetPasswordPage />} />
          <Route path="/privacidad" element={<PrivacyPolicyPage />} />
          <Route path="/terminos" element={<TermsPage />} />
          <Route element={<RequireAuthOnly />}>
            <Route path="/completar-perfil" element={<CompleteProfilePage />} />
            <Route path="/aceptar-terminos" element={<AcceptTermsPage />} />
          </Route>
          <Route element={<ProtectedRoute />}>
            <Route path="/" element={<BookingPage />} />
            <Route path="/perfil" element={<ProfilePage />} />
          </Route>
          <Route element={<AdminRoute />}>
            <Route path="/admin" element={<AdminPage />} />
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </Routes>
      </main>
      <Footer />
    </div>
  )
}
