import { useEffect, useState, type FormEvent } from 'react'
import { useAuth } from './AuthContext'
import PasswordField from '../components/PasswordField'
import client, { extractErrorMessage } from '../api/client'
import type { AppointmentHistoryItem, AppUser } from '../types'

/** Formatea una fecha ISO (YYYY-MM-DD) como texto legible en español (ej. "26/08/2026"). */
function formatDate(iso: string): string {
  const [year, month, day] = iso.split('-')
  return `${day}/${month}/${year}`
}

/** Pestaña para editar el nombre y el teléfono del usuario autenticado. */
function DataSection() {
  const { completeProfile } = useAuth()
  const [firstName, setFirstName] = useState('')
  const [lastName, setLastName] = useState('')
  const [phone, setPhone] = useState('')
  const [loadingInitial, setLoadingInitial] = useState(true)
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  useEffect(() => {
    client
      .get<AppUser>('/users/me')
      .then(({ data }) => {
        const [first, ...rest] = (data.name ?? '').split(' ')
        setFirstName(first ?? '')
        setLastName(rest.join(' '))
        setPhone(data.phone ?? '')
      })
      .catch((err) => setError(extractErrorMessage(err, 'No se pudieron cargar tus datos')))
      .finally(() => setLoadingInitial(false))
  }, [])

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    setSaved(false)
    setLoading(true)
    try {
      await completeProfile(`${firstName.trim()} ${lastName.trim()}`, phone)
      setSaved(true)
    } catch (err) {
      setError(extractErrorMessage(err, 'No se pudieron guardar los datos'))
    } finally {
      setLoading(false)
    }
  }

  if (loadingInitial) {
    return (
      <div className="profile-section">
        <h3>Tus datos</h3>
        <p>Cargando...</p>
      </div>
    )
  }

  return (
    <form className="profile-section" onSubmit={handleSubmit}>
      <h3>Tus datos</h3>
      <label htmlFor="profile-name">Nombre</label>
      <input id="profile-name" type="text" value={firstName} onChange={(e) => setFirstName(e.target.value)} required />
      <label htmlFor="profile-lastname">Apellidos</label>
      <input id="profile-lastname" type="text" value={lastName} onChange={(e) => setLastName(e.target.value)} required />
      <label htmlFor="profile-phone">Teléfono</label>
      <input id="profile-phone" type="tel" value={phone} onChange={(e) => setPhone(e.target.value)} required />
      {error && <p className="error-text">{error}</p>}
      {saved && <p className="hint-text">Datos guardados.</p>}
      <button type="submit" className="primary" disabled={loading}>
        {loading ? 'Guardando...' : 'Guardar datos'}
      </button>
    </form>
  )
}

/** Pestaña para cambiar la contraseña del usuario autenticado. */
function PasswordSection() {
  const { changePassword } = useAuth()
  const [currentPassword, setCurrentPassword] = useState('')
  const [newPassword, setNewPassword] = useState('')
  const [newPasswordConfirm, setNewPasswordConfirm] = useState('')
  const [loading, setLoading] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [saved, setSaved] = useState(false)

  const handleSubmit = async (e: FormEvent) => {
    e.preventDefault()
    setError(null)
    setSaved(false)
    if (newPassword !== newPasswordConfirm) {
      setError('Las contrasenas nuevas no coinciden')
      return
    }
    setLoading(true)
    try {
      await changePassword(currentPassword, newPassword)
      setCurrentPassword('')
      setNewPassword('')
      setNewPasswordConfirm('')
      setSaved(true)
    } catch (err) {
      setError(extractErrorMessage(err, 'No se pudo cambiar la contrasena'))
    } finally {
      setLoading(false)
    }
  }

  return (
    <form className="profile-section" onSubmit={handleSubmit}>
      <h3>Cambiar contraseña</h3>
      <p className="hint-text">Si inicias sesión con Google, tu cuenta no tiene contraseña propia.</p>
      <PasswordField
        placeholder="Contrasena actual"
        value={currentPassword}
        onChange={setCurrentPassword}
        required
        autoComplete="current-password"
      />
      <PasswordField
        placeholder="Contrasena nueva (minimo 6 caracteres)"
        value={newPassword}
        onChange={setNewPassword}
        minLength={6}
        required
        autoComplete="new-password"
      />
      <PasswordField
        placeholder="Repite la contrasena nueva"
        value={newPasswordConfirm}
        onChange={setNewPasswordConfirm}
        minLength={6}
        required
        autoComplete="new-password"
      />
      {error && <p className="error-text">{error}</p>}
      {saved && <p className="hint-text">Contraseña actualizada.</p>}
      <button type="submit" className="primary" disabled={loading}>
        {loading ? 'Guardando...' : 'Cambiar contraseña'}
      </button>
    </form>
  )
}

/** Pestaña con el historico de citas (reservadas y canceladas) del usuario autenticado. */
function HistorySection() {
  const [items, setItems] = useState<AppointmentHistoryItem[] | null>(null)
  const [error, setError] = useState<string | null>(null)

  useEffect(() => {
    client
      .get<AppointmentHistoryItem[]>('/appointments/history')
      .then(({ data }) => setItems(data))
      .catch((err) => setError(extractErrorMessage(err, 'No se pudo cargar el historico de citas')))
  }, [])

  return (
    <div className="profile-section">
      <h3>Histórico de citas</h3>
      {error && <p className="error-text">{error}</p>}
      {!error && !items && <p>Cargando...</p>}
      {items && items.length === 0 && <p className="hint-text">Todavía no has reservado ninguna cita.</p>}
      {items && items.length > 0 && (
        <div className="history-list">
          {items.map((item) => (
            <div className="history-row" key={item.id}>
              <div className="history-row-info">
                <span className="history-row-title">{item.treatmentName}</span>
                <span className="history-row-meta">
                  {formatDate(item.date)} · {item.startTime}-{item.endTime}
                  {item.workerName ? ` · ${item.workerName}` : ''}
                </span>
                {item.notes && <span className="history-row-meta">{item.notes}</span>}
              </div>
              <span className={`history-status history-status--${item.status.toLowerCase()}`}>
                {item.status === 'CANCELLED' ? 'Cancelada' : item.completed ? 'Hecha' : 'Reservada'}
              </span>
            </div>
          ))}
        </div>
      )}
    </div>
  )
}

/** Pagina "Mi perfil": editar datos, cambiar contrasena y consultar el historico de citas. */
export default function ProfilePage() {
  return (
    <div>
      <h1>Mi perfil</h1>
      <DataSection />
      <PasswordSection />
      <HistorySection />
    </div>
  )
}
