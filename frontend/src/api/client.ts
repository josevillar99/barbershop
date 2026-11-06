import axios from 'axios'

export const TOKEN_KEY = 'peluqueria_token'
export const USER_KEY = 'peluqueria_user'

// Cliente HTTP compartido para hablar con la API del backend. Por defecto usa la ruta relativa
// /api (el proxy de Nginx/Vite la redirige al backend); en despliegues donde frontend y backend
// viven en dominios distintos (ej. Render), VITE_API_BASE_URL fija la URL completa del backend.
const client = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL || '/api'
})

// Anade automaticamente el token de sesion guardado a cada peticion saliente.
client.interceptors.request.use((config) => {
  const token = localStorage.getItem(TOKEN_KEY)
  if (token) {
    config.headers.Authorization = `Bearer ${token}`
  }
  return config
})

// Si el backend responde 401 (sesion invalida o caducada), borra la sesion local y manda al login.
client.interceptors.response.use(
  (response) => response,
  (error) => {
    if (error.response?.status === 401) {
      localStorage.removeItem(TOKEN_KEY)
      localStorage.removeItem(USER_KEY)
      if (window.location.pathname !== '/login') {
        window.location.href = '/login'
      }
    }
    return Promise.reject(error)
  }
)

export default client

/** Extrae el mensaje de error legible que envia el backend, o devuelve un mensaje por defecto si no lo hay. */
export function extractErrorMessage(error: unknown, fallback: string): string {
  if (axios.isAxiosError(error)) {
    const message = error.response?.data?.message
    if (typeof message === 'string') return message
  }
  return fallback
}

/** Indica si el error es un conflicto (409) del backend, p.ej. una hora que otro cliente acaba de reservar. */
export function isConflict(error: unknown): boolean {
  return axios.isAxiosError(error) && error.response?.status === 409
}
