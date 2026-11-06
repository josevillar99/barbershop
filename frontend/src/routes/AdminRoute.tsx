import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

/** Protege las rutas de administracion: exige sesion iniciada, perfil completo, terminos aceptados y rol ADMIN antes de mostrar el contenido. */
export default function AdminRoute() {
  const { user } = useAuth()
  if (!user) return <Navigate to="/login" replace />
  if (!user.profileComplete) return <Navigate to="/completar-perfil" replace />
  if (!user.termsAccepted) return <Navigate to="/aceptar-terminos" replace />
  if (user.role !== 'ADMIN' && user.role !== 'SUPERADMIN') return <Navigate to="/" replace />
  return <Outlet />
}
