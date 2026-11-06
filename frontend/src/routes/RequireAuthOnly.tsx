import { Navigate, Outlet } from 'react-router-dom'
import { useAuth } from '../auth/AuthContext'

/** Protege rutas que solo exigen tener sesion iniciada, sin requerir el perfil completo (ej. la pantalla para completarlo). */
export default function RequireAuthOnly() {
  const { user } = useAuth()
  if (!user) return <Navigate to="/login" replace />
  return <Outlet />
}
