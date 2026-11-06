import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

/** Protege las rutas normales de usuario: exige sesion iniciada, perfil completo y terminos aceptados antes de mostrar el contenido. */
export default function ProtectedRoute() {
  const { user } = useAuth()
  if (!user) return <Navigate to="/login" replace />
  if (!user.profileComplete) return <Navigate to="/completar-perfil" replace />
  if (!user.termsAccepted) return <Navigate to="/aceptar-terminos" replace />
  return <Outlet />
}
