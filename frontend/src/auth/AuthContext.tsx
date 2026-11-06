import { createContext, useContext, useMemo, useState, type ReactNode } from 'react'
import client, { TOKEN_KEY, USER_KEY } from '../api/client'
import type { AuthUser } from '../types'

interface AuthResponseData {
  token: string
  email: string
  name: string | null
  role: AuthUser['role']
  profileComplete: boolean
  termsAccepted: boolean
}

interface AuthContextValue {
  user: AuthUser | null
  signup: (email: string) => Promise<void>
  completeSignup: (token: string, password: string, name: string, phone: string) => Promise<void>
  login: (email: string, password: string) => Promise<void>
  loginWithGoogle: (idToken: string) => Promise<void>
  completeProfile: (name: string, phone: string) => Promise<void>
  changePassword: (currentPassword: string, newPassword: string) => Promise<void>
  acceptTerms: () => Promise<void>
  forgotPassword: (email: string) => Promise<void>
  resetPassword: (token: string, newPassword: string) => Promise<void>
  logout: () => void
}

const AuthContext = createContext<AuthContextValue | undefined>(undefined)

/** Recupera el usuario guardado en localStorage al cargar la app, para no perder la sesion al recargar la pagina. */
function loadStoredUser(): AuthUser | null {
  const raw = localStorage.getItem(USER_KEY)
  if (!raw) return null
  try {
    return JSON.parse(raw) as AuthUser
  } catch {
    return null
  }
}

/** Guarda el token y los datos del usuario recibidos del backend en localStorage tras iniciar sesion o registrarse. */
function storeAuth(data: AuthResponseData): AuthUser {
  const authUser: AuthUser = {
    email: data.email,
    name: data.name,
    role: data.role,
    profileComplete: data.profileComplete,
    termsAccepted: data.termsAccepted,
  }
  localStorage.setItem(TOKEN_KEY, data.token)
  localStorage.setItem(USER_KEY, JSON.stringify(authUser))
  return authUser
}

/** Proveedor de autenticacion: mantiene el usuario actual en memoria/localStorage y expone las acciones de login, registro y logout a toda la app. */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<AuthUser | null>(loadStoredUser())

  // Envia el email para iniciar el alta de un cliente nuevo (el backend manda el enlace de confirmacion por correo).
  const signup = async (email: string) => {
    await client.post('/auth/signup', { email })
  }

  // Termina el registro con el token del enlace recibido por email, fijando contrasena, nombre y telefono, y deja al usuario logueado.
  const completeSignup = async (token: string, password: string, name: string, phone: string) => {
    const { data } = await client.post<AuthResponseData>('/auth/signup/complete', { token, password, name, phone })
    setUser(storeAuth(data))
  }

  // Inicia sesion con email y contrasena.
  const login = async (email: string, password: string) => {
    const { data } = await client.post<AuthResponseData>('/auth/login', { email, password })
    setUser(storeAuth(data))
  }

  // Inicia sesion usando el token de identidad de Google (boton "Continuar con Google").
  const loginWithGoogle = async (idToken: string) => {
    const { data } = await client.post<AuthResponseData>('/auth/google', { idToken })
    setUser(storeAuth(data))
  }

  // Completa o actualiza el perfil (nombre y telefono) del usuario logueado: se usa tanto la primera vez
  // (ej. tras registrarse con Google) como despues, desde la pagina de "Mi perfil".
  const completeProfile = async (name: string, phone: string) => {
    const { data } = await client.put('/users/me/profile', { name, phone })
    const patch = { name: data.name, profileComplete: data.profileComplete, termsAccepted: data.termsAccepted }
    setUser((prev) => (prev ? { ...prev, ...patch } : prev))
    if (user) {
      localStorage.setItem(USER_KEY, JSON.stringify({ ...user, ...patch }))
    }
  }

  // Cambia la contrasena del usuario logueado, comprobando el backend que conoce la contrasena actual.
  const changePassword = async (currentPassword: string, newPassword: string) => {
    await client.put('/users/me/password', { currentPassword, newPassword })
  }

  // Registra la aceptacion de los terminos y la politica de privacidad vigentes.
  const acceptTerms = async () => {
    const { data } = await client.post('/users/me/accept-terms')
    setUser((prev) => (prev ? { ...prev, termsAccepted: data.termsAccepted } : prev))
    if (user) {
      localStorage.setItem(USER_KEY, JSON.stringify({ ...user, termsAccepted: data.termsAccepted }))
    }
  }

  // Pide al backend que envie un enlace para restablecer la contrasena, si existe una cuenta con ese email.
  const forgotPassword = async (email: string) => {
    await client.post('/auth/forgot-password', { email })
  }

  // Fija una contrasena nueva a partir del token recibido por email.
  const resetPassword = async (token: string, newPassword: string) => {
    await client.post('/auth/reset-password', { token, newPassword })
  }

  // Cierra la sesion actual borrando el token y los datos guardados.
  const logout = () => {
    localStorage.removeItem(TOKEN_KEY)
    localStorage.removeItem(USER_KEY)
    setUser(null)
  }

  const value = useMemo(
    () => ({
      user,
      signup,
      completeSignup,
      login,
      loginWithGoogle,
      completeProfile,
      changePassword,
      acceptTerms,
      forgotPassword,
      resetPassword,
      logout,
    }),
    // eslint-disable-next-line react-hooks/exhaustive-deps
    [user],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}

/** Hook para acceder al usuario actual y a las acciones de autenticacion desde cualquier componente. */
export function useAuth(): AuthContextValue {
  const ctx = useContext(AuthContext)
  if (!ctx) throw new Error('useAuth debe usarse dentro de AuthProvider')
  return ctx
}
