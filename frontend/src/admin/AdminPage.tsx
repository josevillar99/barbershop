import { useEffect, useMemo, useState } from 'react'
import client, { extractErrorMessage, isConflict } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import type {
  AdminAppointment,
  AppUser,
  AuditAction,
  AuditLogEntry,
  AvailabilityDay,
  AvailableWorker,
  CashSummaryView,
  Page,
  PaymentMethod,
  ScheduleBlock,
  Segment,
  SegmentType,
  StatsResponse,
  Treatment,
  Worker,
} from '../types'
import WeekNav from '../components/WeekNav'
import DayTimeline, { type TimelineItem } from '../components/DayTimeline'
import TimeRuler from '../components/TimeRuler'
import { buildWeekSchedule, intervalsForDate, scheduleRange, type OpeningHour } from '../businessHours'
import { useResponsiveDays } from '../hooks/useResponsiveDays'
import { useModalA11y } from '../hooks/useModalA11y'
import { WORKER_COLOR_PALETTE } from '../workerColors'
import Checkbox from '../components/Checkbox'
import Select from '../components/Select'
import TimeSelect from '../components/TimeSelect'
import DatePickerField from '../components/DatePickerField'
import ConfirmModal from '../components/ConfirmModal'
import {
  addDays,
  formatDayLabel,
  isPastDay,
  isPastDateTime,
  parseTimeToMinutes,
  toIsoDate,
} from '../dateUtils'

type Tab = 'schedule' | 'treatments' | 'cash' | 'workers' | 'users' | 'stats' | 'logs'

const PAYMENT_METHOD_LABELS: Record<PaymentMethod, string> = {
  EFECTIVO: 'Efectivo',
  TARJETA: 'Tarjeta',
  OTRO: 'Otro',
}

/** Formatea la lista de tramos de un servicio como texto legible (ej. "30 min + 15 min de espera"). */
function formatSegments(segments: Segment[]): string {
  return segments
    .map((s) => `${s.minutes} min${s.type === 'WAIT' ? ' de espera' : ''}`)
    .join(' + ')
}

/** Formatea un precio numerico como texto en euros (ej. "12.50 €"). */
function formatPrice(price: number): string {
  return `${price.toFixed(2)} €`
}

interface Draft {
  id: number | null
  name: string
  active: boolean
  price: number
  segments: Segment[]
  workerIds: number[]
}

/** Convierte un servicio guardado en un borrador editable para el formulario de administracion de servicios. */
function toDraft(t: Treatment): Draft {
  return {
    id: t.id,
    name: t.name,
    active: t.active,
    price: t.price,
    segments: t.segments.map((s) => ({ ...s })),
    workerIds: [...t.workerIds],
  }
}

/** Crea un borrador vacio de servicio, con un tramo de trabajo de 30 minutos por defecto, para dar de alta un servicio nuevo. */
function blankDraft(): Draft {
  return { id: null, name: '', active: true, price: 0, segments: [{ type: 'WORK', minutes: 30 }], workerIds: [] }
}

/** Panel de administracion: pantalla con pestanas para gestionar la agenda de reservas y para configurar los servicios ofrecidos. */
export default function AdminPage() {
  const { user } = useAuth()
  const [tab, setTab] = useState<Tab>('schedule')
  const [error, setError] = useState<string | null>(null)
  const isSuperAdmin = user?.role === 'SUPERADMIN'

  return (
    <div>
      <h1>Panel de administracion</h1>
      <div className="admin-tabs">
        <button
          className={tab === 'schedule' ? 'active' : ''}
          onClick={() => setTab('schedule')}
        >
          Reservas
        </button>
        <button
          className={tab === 'treatments' ? 'active' : ''}
          onClick={() => setTab('treatments')}
        >
          Servicios
        </button>
        <button
          className={tab === 'cash' ? 'active' : ''}
          onClick={() => setTab('cash')}
        >
          Caja
        </button>
        {isSuperAdmin && (
          <button
            className={tab === 'workers' ? 'active' : ''}
            onClick={() => setTab('workers')}
          >
            Trabajadores
          </button>
        )}
        {isSuperAdmin && (
          <button
            className={tab === 'users' ? 'active' : ''}
            onClick={() => setTab('users')}
          >
            Usuarios
          </button>
        )}
        {isSuperAdmin && (
          <button
            className={tab === 'stats' ? 'active' : ''}
            onClick={() => setTab('stats')}
          >
            Estadisticas
          </button>
        )}
        {isSuperAdmin && (
          <button
            className={tab === 'logs' ? 'active' : ''}
            onClick={() => setTab('logs')}
          >
            Logs
          </button>
        )}
      </div>
      {error && <p className="error-text">{error}</p>}
      {tab === 'schedule' && <ScheduleTab onError={setError} />}
      {tab === 'treatments' && <TreatmentsTab onError={setError} />}
      {tab === 'cash' && <CashClosingTab onError={setError} />}
      {tab === 'workers' && isSuperAdmin && <WorkersTab onError={setError} />}
      {tab === 'users' && isSuperAdmin && <UsersTab onError={setError} />}
      {tab === 'stats' && isSuperAdmin && <StatsTab onError={setError} />}
      {tab === 'logs' && isSuperAdmin && <LogsTab onError={setError} />}
    </div>
  )
}

interface PendingAdminBooking {
  date: string
  time: string
  workerId: number | null
}

/** Pestana "Reservas" del panel admin: muestra la agenda semanal con citas y bloqueos, permite crear citas a nombre de un cliente y crear bloqueos manuales de horario. */
function ScheduleTab({ onError }: { onError: (message: string | null) => void }) {
  const [appointments, setAppointments] = useState<AdminAppointment[]>([])
  const [blocks, setBlocks] = useState<ScheduleBlock[]>([])
  const [loading, setLoading] = useState(true)
  const [pendingKey, setPendingKey] = useState<string | null>(null)

  const [blockDate, setBlockDate] = useState('')
  const [blockStart, setBlockStart] = useState('13:00')
  const [blockEnd, setBlockEnd] = useState('14:00')
  const [blockReason, setBlockReason] = useState('')
  const [blockWorkerId, setBlockWorkerId] = useState('')

  const [treatments, setTreatments] = useState<Treatment[]>([])
  const [workers, setWorkers] = useState<Worker[]>([])
  const [openingHours, setOpeningHours] = useState<OpeningHour[]>([])
  const [selectedIds, setSelectedIds] = useState<number[]>([])
  const [availability, setAvailability] = useState<AvailabilityDay[]>([])
  const [pendingBooking, setPendingBooking] = useState<PendingAdminBooking | null>(null)
  const [clientName, setClientName] = useState('')
  const [clientPhone, setClientPhone] = useState('')
  const [bookingNotes, setBookingNotes] = useState('')
  const [selectedAppointment, setSelectedAppointment] = useState<AdminAppointment | null>(null)
  const [conflictMessage, setConflictMessage] = useState<string | null>(null)
  const [availableWorkers, setAvailableWorkers] = useState<AvailableWorker[]>([])
  const [loadingWorkers, setLoadingWorkers] = useState(false)
  const [confirmDelete, setConfirmDelete] = useState<
    { type: 'appointment'; id: number } | { type: 'block'; id: number } | null
  >(null)
  const [completingAppointment, setCompletingAppointment] = useState<AdminAppointment | null>(null)
  const [chargeAmount, setChargeAmount] = useState('')
  const [chargeMethod, setChargeMethod] = useState<PaymentMethod>('EFECTIVO')

  const selectedTreatments = useMemo(
    () => selectedIds.map((id) => treatments.find((t) => t.id === id)).filter((t): t is Treatment => !!t),
    [selectedIds, treatments],
  )
  const combinedName = selectedTreatments.map((t) => t.name).join(' + ')
  const combinedPrice = selectedTreatments.reduce((sum, t) => sum + t.price, 0)
  const requiresColorNote = selectedTreatments.some((t) => t.name === 'Tinte' || t.name === 'Mechas')
  const treatmentIdsParam = selectedIds.join(',')
  const [hoursLoaded, setHoursLoaded] = useState(false)

  useEffect(() => {
    client.get<Treatment[]>('/treatments').then(({ data }) => setTreatments(data))
    client.get<Worker[]>('/admin/workers').then(({ data }) => setWorkers(data))
    client
      .get<OpeningHour[]>('/business-hours')
      .then(({ data }) => setOpeningHours(data))
      .finally(() => setHoursLoaded(true))
  }, [])

  const schedule = useMemo(() => buildWeekSchedule(openingHours), [openingHours])
  const range = useMemo(() => scheduleRange(schedule), [schedule])
  const { visibleDays, goPrev, goNext, goToday, showToday, label } = useResponsiveDays(schedule)

  const from = toIsoDate(visibleDays[0] ?? new Date())
  const to = toIsoDate(visibleDays[visibleDays.length - 1] ?? new Date())

  // Anade o quita un servicio de la seleccion actual para la nueva cita que se va a crear.
  const toggleTreatment = (id: number) => {
    setSelectedIds((prev) => (prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]))
  }

  // Carga las horas disponibles para los servicios seleccionados dentro de la semana visible.
  const loadAvailability = async () => {
    if (selectedIds.length === 0) {
      setAvailability([])
      return
    }
    const { data } = await client.get<AvailabilityDay[]>('/availability', {
      params: { treatmentIds: treatmentIdsParam, from, to },
    })
    setAvailability(data)
  }

  // Carga las citas, los bloqueos y la disponibilidad de la semana visible.
  const load = async () => {
    setLoading(true)
    onError(null)
    try {
      const [apptRes, blockRes] = await Promise.all([
        client.get<AdminAppointment[]>('/admin/appointments', { params: { from, to } }),
        client.get<ScheduleBlock[]>('/admin/blocks', { params: { from, to } }),
      ])
      setAppointments(apptRes.data)
      setBlocks(blockRes.data)
      await loadAvailability()
    } catch (err) {
      onError(extractErrorMessage(err, 'No se pudieron cargar las reservas'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [from, to, treatmentIdsParam])

  const availabilityByDate = useMemo(() => {
    const map = new Map<string, string[]>()
    for (const day of availability) {
      map.set(day.date, day.times)
    }
    return map
  }, [availability])

  // Abre el dialogo para crear una cita a nombre de un cliente en la fecha y hora elegidas, y carga los trabajadores libres entonces.
  const openBookingModal = async (date: string, time: string) => {
    setClientName('')
    setClientPhone('')
    setBookingNotes('')
    onError(null)
    setPendingBooking({ date, time, workerId: null })
    setAvailableWorkers([])
    setLoadingWorkers(true)
    try {
      const { data } = await client.get<AvailableWorker[]>('/availability/workers', {
        params: { treatmentIds: treatmentIdsParam, date, startTime: time },
      })
      setAvailableWorkers(data)
    } catch (err) {
      onError(extractErrorMessage(err, 'No se pudieron cargar los trabajadores disponibles'))
    } finally {
      setLoadingWorkers(false)
    }
  }

  // Cierra el dialogo de creacion de cita sin confirmar nada.
  const closeBookingModal = () => {
    setPendingBooking(null)
    setAvailableWorkers([])
  }

  const bookingModalRef = useModalA11y<HTMLFormElement>(pendingBooking !== null, closeBookingModal)
  const conflictModalRef = useModalA11y<HTMLDivElement>(conflictMessage !== null, () => setConflictMessage(null))
  const appointmentModalRef = useModalA11y<HTMLDivElement>(
    selectedAppointment !== null,
    () => setSelectedAppointment(null),
  )

  // Valida los datos del cliente y crea la cita administrativa en la fecha y hora elegidas.
  const confirmAdminBooking = async () => {
    if (!pendingBooking || !pendingBooking.workerId) return
    if (!clientName.trim() || !clientPhone.trim()) {
      onError('El nombre y el telefono del cliente son obligatorios')
      return
    }
    const { date, time, workerId } = pendingBooking
    setPendingKey(`admin-book-${date}-${time}`)
    onError(null)
    try {
      await client.post('/admin/appointments', {
        treatmentIds: selectedIds,
        date,
        startTime: time,
        notes: bookingNotes.trim() || null,
        clientName: clientName.trim(),
        clientPhone: clientPhone.trim(),
        workerId,
      })
      setPendingBooking(null)
      setSelectedIds([])
      await load()
    } catch (err) {
      if (isConflict(err)) {
        setPendingBooking(null)
        setConflictMessage('Vaya, esa hora ya tiene una cita reservada (puede que se haya reservado justo ahora). Elige otra hora libre en el calendario.')
      } else {
        onError(extractErrorMessage(err, 'No se pudo crear la cita'))
      }
      await load()
    } finally {
      setPendingKey(null)
    }
  }

  // Cancela una cita existente desde el panel de administracion.
  // Cancela una cita y la quita del estado local (sin recargar toda la
  // agenda) para no perder la posicion de scroll.
  const cancelAppointment = async (id: number) => {
    setPendingKey(`appt-${id}`)
    onError(null)
    try {
      await client.post(`/admin/appointments/${id}/cancel`)
      setAppointments((prev) => prev.filter((a) => a.id !== id))
      setSelectedAppointment((prev) => (prev && prev.id === id ? null : prev))
    } catch (err) {
      onError(extractErrorMessage(err, 'No se pudo cancelar la cita'))
    } finally {
      setPendingKey(null)
    }
  }

  // Marca una cita pasada como que el cliente no se presento (o deshace un "hecha" anterior),
  // limpiando el importe/metodo de pago que tuviera. Actualiza solo esa cita en el estado local
  // (sin recargar toda la agenda) para no perder la posicion de scroll.
  const markNotShown = async (appt: AdminAppointment) => {
    setPendingKey(`appt-${appt.id}`)
    onError(null)
    try {
      await client.put(`/admin/appointments/${appt.id}/completed`, {
        completed: false,
        amountCharged: null,
        paymentMethod: null,
      })
      const patch = { completed: false, amountCharged: null, paymentMethod: null } as const
      setAppointments((prev) => prev.map((a) => (a.id === appt.id ? { ...a, ...patch } : a)))
      setSelectedAppointment((prev) => (prev && prev.id === appt.id ? { ...prev, ...patch } : prev))
    } catch (err) {
      onError(extractErrorMessage(err, 'No se pudo actualizar la cita'))
    } finally {
      setPendingKey(null)
    }
  }

  // Abre el dialogo para cobrar y marcar una cita como hecha, prellenando el importe con el
  // precio de catalogo que tenia el servicio al reservarse.
  const openCompleteDialog = (appt: AdminAppointment) => {
    setCompletingAppointment(appt)
    setChargeAmount(appt.expectedPrice != null ? String(appt.expectedPrice) : '')
    setChargeMethod('EFECTIVO')
  }

  const closeCompleteDialog = () => {
    setCompletingAppointment(null)
    setChargeAmount('')
    setChargeMethod('EFECTIVO')
  }

  // Confirma el importe y metodo de pago introducidos y marca la cita como hecha.
  const confirmComplete = async () => {
    if (!completingAppointment) return
    const id = completingAppointment.id
    const amount = Number(chargeAmount)
    setPendingKey(`appt-${id}`)
    onError(null)
    try {
      await client.put(`/admin/appointments/${id}/completed`, {
        completed: true,
        amountCharged: amount,
        paymentMethod: chargeMethod,
      })
      const patch = { completed: true, amountCharged: amount, paymentMethod: chargeMethod }
      setAppointments((prev) => prev.map((a) => (a.id === id ? { ...a, ...patch } : a)))
      setSelectedAppointment((prev) => (prev && prev.id === id ? { ...prev, ...patch } : prev))
      closeCompleteDialog()
    } catch (err) {
      onError(extractErrorMessage(err, 'No se pudo actualizar la cita'))
    } finally {
      setPendingKey(null)
    }
  }

  const completeModalRef = useModalA11y<HTMLFormElement>(completingAppointment !== null, closeCompleteDialog)

  // Elimina un bloqueo de horario existente.
  const deleteBlock = async (id: number) => {
    setPendingKey(`block-${id}`)
    onError(null)
    try {
      await client.delete(`/admin/blocks/${id}`)
      await load()
    } catch (err) {
      onError(extractErrorMessage(err, 'No se pudo eliminar el bloqueo'))
    } finally {
      setPendingKey(null)
    }
  }

  // Ejecuta la cancelacion de cita o el borrado de bloqueo confirmados en el dialogo.
  const confirmPendingDelete = async () => {
    if (!confirmDelete) return
    const { type, id } = confirmDelete
    setConfirmDelete(null)
    if (type === 'appointment') {
      await cancelAppointment(id)
    } else {
      await deleteBlock(id)
    }
  }

  // Crea un bloqueo manual de horario (vacaciones, recados...) para impedir reservas en ese tramo.
  const createBlock = async () => {
    if (!blockDate) {
      onError('Elige una fecha para el bloqueo')
      return
    }
    setPendingKey('new-block')
    onError(null)
    try {
      await client.post('/admin/blocks', {
        date: blockDate,
        startTime: blockStart,
        endTime: blockEnd,
        reason: blockReason || null,
        workerId: blockWorkerId ? Number(blockWorkerId) : null,
      })
      setBlockReason('')
      await load()
    } catch (err) {
      onError(extractErrorMessage(err, 'No se pudo crear el bloqueo'))
    } finally {
      setPendingKey(null)
    }
  }

  const appointmentsByDate = useMemo(() => {
    const map = new Map<string, AdminAppointment[]>()
    for (const appt of appointments) {
      const list = map.get(appt.date) ?? []
      list.push(appt)
      map.set(appt.date, list)
    }
    return map
  }, [appointments])

  const blocksByDate = useMemo(() => {
    const map = new Map<string, ScheduleBlock[]>()
    for (const block of blocks) {
      const list = map.get(block.date) ?? []
      list.push(block)
      map.set(block.date, list)
    }
    return map
  }, [blocks])

  const selectedAppointmentPast = selectedAppointment
    ? isPastDateTime(selectedAppointment.date, selectedAppointment.endTime)
    : false

  return (
    <div>
      <div className="schedule-tab">
      <p className="hint-text">
        Consulta las citas de la semana, crea una cita a nombre de un cliente (llamada, visita al local...) o
        crea bloqueos manuales (vacaciones, recados...).
      </p>

      <div className="treatment-checklist">
        {treatments.map((t) => (
          <div className="treatment-checklist-item" key={t.id}>
            <Checkbox
              checked={selectedIds.includes(t.id)}
              onChange={() => toggleTreatment(t.id)}
              label={
                <>
                  <span className="treatment-checklist-name">
                    {t.name} ({formatSegments(t.segments)})
                  </span>
                  <span className="treatment-checklist-price">{formatPrice(t.price)}</span>
                </>
              }
            />
          </div>
        ))}
      </div>

      {selectedTreatments.length > 0 && (
        <p className="treatment-price">
          Nueva cita: {combinedName} &middot; total <strong>{formatPrice(combinedPrice)}</strong> &middot; elige
          una hora disponible en la columna del dia que quieras
        </p>
      )}

      <h3 className="block-form-title">Crear bloqueo</h3>
      <form
        className="block-form"
        onSubmit={(e) => {
          e.preventDefault()
          createBlock()
        }}
      >
        <DatePickerField value={blockDate} onChange={setBlockDate} minDate={new Date()} />
        <TimeSelect value={blockStart} onChange={setBlockStart} />
        <TimeSelect value={blockEnd} onChange={setBlockEnd} />
        <Select value={blockWorkerId} onChange={(e) => setBlockWorkerId(e.target.value)}>
          <option value="">Todo el salon</option>
          {workers.map((w) => (
            <option key={w.id} value={w.id}>
              {w.name}
            </option>
          ))}
        </Select>
        <input
          type="text"
          placeholder="Motivo (opcional)"
          value={blockReason}
          onChange={(e) => setBlockReason(e.target.value)}
        />
        <button type="submit" disabled={pendingKey === 'new-block'}>
          Crear bloqueo
        </button>
      </form>
      </div>
      <WeekNav
        label={label}
        onPrev={goPrev}
        onNext={goNext}
        onToday={showToday ? goToday : undefined}
      />

      {loading || !hoursLoaded ? (
        <p>Cargando...</p>
      ) : visibleDays.length === 0 ? (
        <p className="hint-text">No hay dias disponibles en este rango.</p>
      ) : (
        <div className="week-grid">
        <TimeRuler range={range} />
        <div className="days-scroll">
          {visibleDays.map((date) => {
            const iso = toIsoDate(date)
            const past = isPastDay(date)
            const dayAppointments = appointmentsByDate.get(iso) ?? []
            const dayBlocks = blocksByDate.get(iso) ?? []

            const items: TimelineItem[] = [
              ...dayAppointments.map((appt) => {
                let cursor = parseTimeToMinutes(appt.startTime)
                const segments = appt.segments.map((s) => {
                  const segment = { type: s.type, startMinutes: cursor, endMinutes: cursor + s.minutes }
                  cursor += s.minutes
                  return segment
                })
                const apptPast = isPastDateTime(iso, appt.endTime)
                return {
                  key: `appt-${appt.id}`,
                  startMinutes: parseTimeToMinutes(appt.startTime),
                  endMinutes: parseTimeToMinutes(appt.endTime),
                  segments,
                  title: appt.treatmentName,
                  meta: appt.workerName ?? undefined,
                  subtitle: appt.clientName + (apptPast && appt.completed ? ' · ✓ Hecha' : ''),
                  variant: 'booked' as const,
                  color: appt.workerColor ?? undefined,
                  onClick: () => setSelectedAppointment(appt),
                  action: apptPast
                    ? {
                        label: 'Hecha',
                        onClick: () => openCompleteDialog(appt),
                        disabled: appt.completed || pendingKey === `appt-${appt.id}`,
                      }
                    : {
                        label: 'Cancelar',
                        onClick: () => setConfirmDelete({ type: 'appointment', id: appt.id }),
                        disabled: pendingKey === `appt-${appt.id}`,
                      },
                  secondaryAction: apptPast
                    ? {
                        label: 'No presentado',
                        onClick: () => markNotShown(appt),
                        disabled: !appt.completed || pendingKey === `appt-${appt.id}`,
                      }
                    : undefined,
                }
              }),
              ...dayBlocks.map((block) => ({
                key: `block-${block.id}`,
                startMinutes: parseTimeToMinutes(block.startTime),
                endMinutes: parseTimeToMinutes(block.endTime),
                segments: [
                  {
                    type: 'WORK' as const,
                    startMinutes: parseTimeToMinutes(block.startTime),
                    endMinutes: parseTimeToMinutes(block.endTime),
                  },
                ],
                title: 'Bloqueado',
                subtitle: [block.workerName ?? 'Todo el salon', block.reason].filter(Boolean).join(' · '),
                variant: 'block' as const,
                action: {
                  label: 'Eliminar',
                  onClick: () => setConfirmDelete({ type: 'block', id: block.id }),
                  disabled: pendingKey === `block-${block.id}`,
                },
              })),
            ]

            const times = availabilityByDate.get(iso) ?? []

            return (
              <div className={`day-column day-column--wide${past ? ' past' : ''}`} key={iso}>
                <h3>{formatDayLabel(date)}</h3>

                <DayTimeline
                  intervals={intervalsForDate(date, schedule)}
                  items={items}
                  range={range}
                  availableTimes={
                    !past && selectedIds.length > 0 && times.length > 0
                      ? times.map((time) => ({
                          time,
                          disabled: pendingKey === `admin-book-${iso}-${time}`,
                          onClick: () => openBookingModal(iso, time),
                        }))
                      : undefined
                  }
                />

                {items.length === 0 && <p className="hint-text">Sin citas</p>}
              </div>
            )
          })}
        </div>
        </div>
      )}

      {pendingBooking && (
        <div className="modal-overlay" onClick={closeBookingModal}>
          <form
            className="modal-box"
            role="dialog"
            aria-modal="true"
            ref={bookingModalRef}
            onClick={(e) => e.stopPropagation()}
            onSubmit={(e) => {
              e.preventDefault()
              confirmAdminBooking()
            }}
          >
            <h2>Nueva cita</h2>
            <p className="hint-text">
              {combinedName} el {pendingBooking.date} a las {pendingBooking.time} &middot;{' '}
              {formatPrice(combinedPrice)}
            </p>

            <label htmlFor="admin-booking-worker">¿Con que trabajador?</label>
            {loadingWorkers ? (
              <p className="hint-text">Buscando trabajadores libres...</p>
            ) : availableWorkers.length === 0 ? (
              <p className="hint-text">No hay ningun trabajador libre a esa hora. Elige otra hora.</p>
            ) : (
              <Select
                id="admin-booking-worker"
                value={pendingBooking.workerId ?? ''}
                onChange={(e) =>
                  setPendingBooking((prev) => (prev ? { ...prev, workerId: Number(e.target.value) } : prev))
                }
              >
                <option value="" disabled>
                  Elige un trabajador
                </option>
                {availableWorkers.map((w) => (
                  <option key={w.id} value={w.id}>
                    {w.name}
                  </option>
                ))}
              </Select>
            )}

            <label htmlFor="admin-client-name">Nombre del cliente</label>
            <input
              id="admin-client-name"
              type="text"
              value={clientName}
              onChange={(e) => setClientName(e.target.value)}
            />
            <label htmlFor="admin-client-phone">Telefono del cliente</label>
            <input
              id="admin-client-phone"
              type="tel"
              value={clientPhone}
              onChange={(e) => setClientPhone(e.target.value)}
            />
            <label htmlFor="admin-booking-notes">
              Observaciones {requiresColorNote ? '(indica el color que quiere)' : '(opcional)'}
            </label>
            <textarea
              id="admin-booking-notes"
              placeholder={requiresColorNote ? 'Indica el color (obligatorio para tinte y mechas)' : undefined}
              value={bookingNotes}
              onChange={(e) => setBookingNotes(e.target.value)}
              rows={3}
            />
            <div className="modal-actions">
              <button type="button" onClick={closeBookingModal}>
                Cancelar
              </button>
              <button
                type="submit"
                className="primary"
                disabled={
                  !pendingBooking.workerId ||
                  (requiresColorNote && !bookingNotes.trim()) ||
                  pendingKey === `admin-book-${pendingBooking.date}-${pendingBooking.time}`
                }
              >
                Crear cita
              </button>
            </div>
          </form>
        </div>
      )}

      {conflictMessage && (
        <div className="modal-overlay" onClick={() => setConflictMessage(null)}>
          <div
            className="modal-box"
            role="dialog"
            aria-modal="true"
            ref={conflictModalRef}
            onClick={(e) => e.stopPropagation()}
          >
            <h2>Esa hora ya no esta libre</h2>
            <p className="hint-text">{conflictMessage}</p>
            <div className="modal-actions">
              <button className="primary" onClick={() => setConflictMessage(null)}>
                Entendido
              </button>
            </div>
          </div>
        </div>
      )}

      {completingAppointment && (
        <div className="modal-overlay" onClick={closeCompleteDialog}>
          <form
            className="modal-box"
            role="dialog"
            aria-modal="true"
            ref={completeModalRef}
            onClick={(e) => e.stopPropagation()}
            onSubmit={(e) => {
              e.preventDefault()
              confirmComplete()
            }}
          >
            <h2>Marcar cita como hecha</h2>
            <p className="hint-text">
              {completingAppointment.treatmentName} · {completingAppointment.clientName}
            </p>
            <label htmlFor="charge-amount">Importe cobrado</label>
            <input
              id="charge-amount"
              type="number"
              step="0.01"
              min="0"
              value={chargeAmount}
              onChange={(e) => setChargeAmount(e.target.value)}
              autoFocus
              required
            />
            <label htmlFor="charge-method">Metodo de pago</label>
            <Select
              id="charge-method"
              value={chargeMethod}
              onChange={(e) => setChargeMethod(e.target.value as PaymentMethod)}
            >
              {(Object.keys(PAYMENT_METHOD_LABELS) as PaymentMethod[]).map((method) => (
                <option key={method} value={method}>
                  {PAYMENT_METHOD_LABELS[method]}
                </option>
              ))}
            </Select>
            <div className="modal-actions">
              <button type="button" onClick={closeCompleteDialog}>
                Cancelar
              </button>
              <button
                type="submit"
                className="primary"
                disabled={!chargeAmount.trim() || pendingKey === `appt-${completingAppointment.id}`}
              >
                Confirmar
              </button>
            </div>
          </form>
        </div>
      )}

      {selectedAppointment && (
        <div className="modal-overlay" onClick={() => setSelectedAppointment(null)}>
          <div
            className="modal-box"
            role="dialog"
            aria-modal="true"
            ref={appointmentModalRef}
            onClick={(e) => e.stopPropagation()}
          >
            <h2>{selectedAppointment.treatmentName}</h2>
            <p className="hint-text">
              {selectedAppointment.date} &middot; {selectedAppointment.startTime} - {selectedAppointment.endTime}
              {' · '}
              {formatSegments(selectedAppointment.segments)}
            </p>
            <p>
              <strong>Trabajador:</strong>{' '}
              {selectedAppointment.workerColor && (
                <span className="worker-color-dot" style={{ backgroundColor: selectedAppointment.workerColor }} />
              )}
              {selectedAppointment.workerName ?? 'Sin asignar'}
            </p>
            <p>
              <strong>Cliente:</strong> {selectedAppointment.clientName}
            </p>
            <p>
              <strong>Telefono:</strong> {selectedAppointment.clientPhone}
            </p>
            {selectedAppointment.bookedByEmail && (
              <p>
                <strong>Cuenta:</strong> {selectedAppointment.bookedByEmail}
              </p>
            )}
            <p>
              <strong>Observaciones:</strong> {selectedAppointment.notes || 'Sin observaciones'}
            </p>
            {selectedAppointmentPast && (
              <p>
                <strong>Estado:</strong> {selectedAppointment.completed ? '✓ Hecha' : 'Sin marcar'}
              </p>
            )}
            {selectedAppointment.completed && selectedAppointment.amountCharged != null && (
              <p>
                <strong>Cobrado:</strong> {formatPrice(selectedAppointment.amountCharged)}
                {selectedAppointment.paymentMethod && ` (${PAYMENT_METHOD_LABELS[selectedAppointment.paymentMethod]})`}
              </p>
            )}
            <div className="modal-actions">
              {selectedAppointmentPast ? (
                <>
                  <button
                    disabled={!selectedAppointment.completed || pendingKey === `appt-${selectedAppointment.id}`}
                    onClick={() => {
                      markNotShown(selectedAppointment)
                      setSelectedAppointment(null)
                    }}
                  >
                    No presentado
                  </button>
                  <button
                    className="primary"
                    disabled={selectedAppointment.completed || pendingKey === `appt-${selectedAppointment.id}`}
                    onClick={() => {
                      openCompleteDialog(selectedAppointment)
                      setSelectedAppointment(null)
                    }}
                  >
                    Marcar hecha
                  </button>
                </>
              ) : (
                <button
                  className="danger"
                  disabled={pendingKey === `appt-${selectedAppointment.id}`}
                  onClick={() => {
                    setConfirmDelete({ type: 'appointment', id: selectedAppointment.id })
                    setSelectedAppointment(null)
                  }}
                >
                  Cancelar cita
                </button>
              )}
              <button className="primary" onClick={() => setSelectedAppointment(null)}>
                Cerrar
              </button>
            </div>
          </div>
        </div>
      )}

      {confirmDelete && (
        <ConfirmModal
          title={confirmDelete.type === 'appointment' ? 'Cancelar cita' : 'Eliminar bloqueo'}
          message={
            confirmDelete.type === 'appointment'
              ? '¿Cancelar esta cita? Esta accion no se puede deshacer.'
              : '¿Eliminar este bloqueo de horario? Los horarios que cubria volveran a estar disponibles.'
          }
          confirmLabel={confirmDelete.type === 'appointment' ? 'Cancelar cita' : 'Eliminar'}
          cancelLabel="Volver"
          danger
          onConfirm={confirmPendingDelete}
          onCancel={() => setConfirmDelete(null)}
        />
      )}
    </div>
  )
}

/** Pestana "Servicios" del panel admin: permite crear, editar y configurar los servicios y sus tramos de duracion (trabajo/espera). */
function TreatmentsTab({ onError }: { onError: (message: string | null) => void }) {
  const [drafts, setDrafts] = useState<Draft[]>([])
  const [workers, setWorkers] = useState<Worker[]>([])
  const [loading, setLoading] = useState(true)
  const [pendingIndex, setPendingIndex] = useState<number | null>(null)

  // Carga la lista de servicios existentes (convertidos en borradores editables) y la lista de trabajadores.
  const load = async () => {
    setLoading(true)
    onError(null)
    try {
      const [treatmentsRes, workersRes] = await Promise.all([
        client.get<Treatment[]>('/admin/treatments'),
        client.get<Worker[]>('/admin/workers'),
      ])
      setDrafts(treatmentsRes.data.map(toDraft))
      setWorkers(workersRes.data)
    } catch (err) {
      onError(extractErrorMessage(err, 'No se pudieron cargar los servicios'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  // Actualiza el borrador de un servicio en la posicion dada aplicando la funcion de cambio recibida.
  const updateDraft = (index: number, updater: (draft: Draft) => Draft) => {
    setDrafts((prev) => prev.map((d, i) => (i === index ? updater(d) : d)))
  }

  // Anade un nuevo tramo de trabajo de 15 minutos al servicio en edicion.
  const addSegment = (index: number) => {
    updateDraft(index, (d) => ({ ...d, segments: [...d.segments, { type: 'WORK', minutes: 15 }] }))
  }

  // Elimina un tramo concreto del servicio en edicion.
  const removeSegment = (index: number, segIndex: number) => {
    updateDraft(index, (d) => ({ ...d, segments: d.segments.filter((_, i) => i !== segIndex) }))
  }

  // Reemplaza un tramo concreto del servicio en edicion con los nuevos valores.
  const updateSegment = (index: number, segIndex: number, segment: Segment) => {
    updateDraft(index, (d) => ({
      ...d,
      segments: d.segments.map((s, i) => (i === segIndex ? segment : s)),
    }))
  }

  // Comprueba que el borrador de servicio tiene datos validos antes de guardarlo; devuelve el mensaje de error o null si todo esta bien.
  const validate = (draft: Draft): string | null => {
    if (!draft.name.trim()) return 'El servicio necesita un nombre'
    if (draft.price < 0) return 'El precio no puede ser negativo'
    if (draft.segments.length === 0) return 'Anade al menos un tramo'
    if (draft.segments.some((s) => s.minutes <= 0)) return 'La duracion de cada tramo debe ser mayor que 0'
    if (draft.segments[0].type !== 'WORK' || draft.segments[draft.segments.length - 1].type !== 'WORK') {
      return 'El primer y el ultimo tramo deben ser de trabajo, no de espera'
    }
    return null
  }

  // Valida y guarda el servicio en edicion entero (datos, tramos y quien puede hacerlo),
  // creandolo si es nuevo o actualizandolo si ya existia.
  const save = async (index: number) => {
    const draft = drafts[index]
    const problem = validate(draft)
    if (problem) {
      onError(problem)
      return
    }
    setPendingIndex(index)
    onError(null)
    try {
      const body = { name: draft.name, active: draft.active, price: draft.price, segments: draft.segments }
      let treatmentId = draft.id
      if (treatmentId === null) {
        const { data } = await client.post<Treatment>('/admin/treatments', body)
        treatmentId = data.id
      } else {
        await client.put(`/admin/treatments/${treatmentId}`, body)
      }
      await client.put(`/admin/treatments/${treatmentId}/workers`, { workerIds: draft.workerIds })
      await load()
    } catch (err) {
      onError(extractErrorMessage(err, 'No se pudo guardar el servicio'))
    } finally {
      setPendingIndex(null)
    }
  }

  // Marca o desmarca un trabajador como cualificado para el servicio en edicion (solo en el borrador, hasta guardar).
  const toggleWorker = (index: number, workerId: number) => {
    updateDraft(index, (d) => ({
      ...d,
      workerIds: d.workerIds.includes(workerId)
        ? d.workerIds.filter((id) => id !== workerId)
        : [...d.workerIds, workerId],
    }))
  }

  return (
    <div>
      <p className="hint-text">
        Configura la duracion de cada servicio. Los tramos de espera no ocupan la agenda: el peluquero
        puede atender a otro cliente mientras tanto.
      </p>

      {loading ? (
        <p>Cargando...</p>
      ) : (
        <>
          {drafts.map((draft, index) => (
            <form
              className="treatment-card"
              key={draft.id ?? `new-${index}`}
              onSubmit={(e) => {
                e.preventDefault()
                save(index)
              }}
            >
              <div className="treatment-card-header">
                <input
                  type="text"
                  placeholder="Nombre del servicio"
                  value={draft.name}
                  onChange={(e) => updateDraft(index, (d) => ({ ...d, name: e.target.value }))}
                />
                <div className="treatment-price-input">
                  <input
                    type="number"
                    min={0}
                    step={0.5}
                    value={draft.price}
                    onChange={(e) => updateDraft(index, (d) => ({ ...d, price: Number(e.target.value) }))}
                  />
                  <span>€</span>
                </div>
                <Checkbox
                  checked={draft.active}
                  onChange={(checked) => updateDraft(index, (d) => ({ ...d, active: checked }))}
                  label="Activo"
                />
              </div>

              {draft.segments.map((segment, segIndex) => (
                <div className="segment-row" key={segIndex}>
                  <Select
                    value={segment.type}
                    onChange={(e) =>
                      updateSegment(index, segIndex, { ...segment, type: e.target.value as SegmentType })
                    }
                  >
                    <option value="WORK">Trabajo</option>
                    <option value="WAIT">Espera</option>
                  </Select>
                  <input
                    type="number"
                    min={1}
                    value={segment.minutes}
                    onChange={(e) =>
                      updateSegment(index, segIndex, { ...segment, minutes: Number(e.target.value) })
                    }
                  />
                  <span>min</span>
                  <button type="button" onClick={() => removeSegment(index, segIndex)}>
                    Quitar
                  </button>
                </div>
              ))}

              <button type="button" onClick={() => addSegment(index)}>
                ➕ Anadir tramo
              </button>

              {draft.id !== null && (
                <div className="treatment-card-workers">
                  <p className="hint-text">¿Quien puede hacerlo?</p>
                  {workers.length === 0 ? (
                    <p className="hint-text">Todavia no hay trabajadores dados de alta.</p>
                  ) : (
                    <div className="treatment-worker-checklist">
                      {workers.map((worker) => (
                        <Checkbox
                          key={worker.id}
                          checked={draft.workerIds.includes(worker.id)}
                          onChange={() => toggleWorker(index, worker.id)}
                          label={worker.name}
                        />
                      ))}
                    </div>
                  )}
                </div>
              )}

              <div className="treatment-card-actions">
                <button type="submit" className="primary" disabled={pendingIndex === index}>
                  {pendingIndex === index ? 'Guardando...' : 'Guardar'}
                </button>
              </div>
            </form>
          ))}

          <button type="button" onClick={() => setDrafts((prev) => [...prev, blankDraft()])}>
            ➕ Nuevo servicio
          </button>
        </>
      )}
    </div>
  )
}

const DAY_NAMES = ['Lunes', 'Martes', 'Miercoles', 'Jueves', 'Viernes', 'Sabado', 'Domingo']

interface HourInterval {
  startTime: string
  endTime: string
}

/** Agrupa los tramos que devuelve el backend por dia de la semana (1=lunes .. 7=domingo), para editarlos en el formulario. */
function groupByDay(hours: OpeningHour[]): Record<number, HourInterval[]> {
  const grouped: Record<number, HourInterval[]> = {}
  for (let day = 1; day <= 7; day++) {
    grouped[day] = []
  }
  for (const h of hours) {
    grouped[h.dayOfWeek].push({ startTime: h.startTime, endTime: h.endTime })
  }
  for (const day of Object.keys(grouped)) {
    grouped[Number(day)].sort((a, b) => a.startTime.localeCompare(b.startTime))
  }
  return grouped
}

/** Editor del horario semanal de un trabajador concreto (mismo patron de edicion que antes tenia el horario unico del salon). */
function WorkerHoursEditor({
  workerId,
  initialHours,
  onError,
  onSaved,
}: {
  workerId: number
  initialHours: HourInterval[][]
  onError: (message: string | null) => void
  onSaved: (hours: { dayOfWeek: number; startTime: string; endTime: string }[]) => void
}) {
  const [draft, setDraft] = useState<Record<number, HourInterval[]>>(() => {
    const grouped: Record<number, HourInterval[]> = {}
    for (let day = 1; day <= 7; day++) {
      grouped[day] = initialHours[day - 1] ?? []
    }
    return grouped
  })
  const [saving, setSaving] = useState(false)
  const [saved, setSaved] = useState(false)

  const addInterval = (day: number) => {
    setSaved(false)
    setDraft((prev) => ({ ...prev, [day]: [...(prev[day] ?? []), { startTime: '09:00', endTime: '14:00' }] }))
  }

  const removeInterval = (day: number, index: number) => {
    setSaved(false)
    setDraft((prev) => ({ ...prev, [day]: prev[day].filter((_, i) => i !== index) }))
  }

  const updateInterval = (day: number, index: number, field: keyof HourInterval, value: string) => {
    setSaved(false)
    setDraft((prev) => ({
      ...prev,
      [day]: prev[day].map((interval, i) => (i === index ? { ...interval, [field]: value } : interval)),
    }))
  }

  const save = async () => {
    setSaving(true)
    setSaved(false)
    onError(null)
    try {
      const body = Object.entries(draft).flatMap(([day, intervals]) =>
        intervals.map((interval) => ({
          dayOfWeek: Number(day),
          startTime: interval.startTime,
          endTime: interval.endTime,
        })),
      )
      await client.put(`/admin/workers/${workerId}/hours`, body)
      setSaved(true)
      onSaved(body)
    } catch (err) {
      onError(extractErrorMessage(err, 'No se pudo guardar el horario'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <form
      className="worker-hours-editor"
      onSubmit={(e) => {
        e.preventDefault()
        save()
      }}
    >
      {DAY_NAMES.map((name, i) => {
        const day = i + 1
        const intervals = draft[day] ?? []
        return (
          <div className="hours-day-card" key={day}>
            <h3>{name}</h3>
            {intervals.length === 0 && <p className="hint-text">Cerrado</p>}
            {intervals.map((interval, index) => (
              <div className="hours-interval-row" key={index}>
                <TimeSelect
                  value={interval.startTime}
                  onChange={(value) => updateInterval(day, index, 'startTime', value)}
                />
                <span>a</span>
                <TimeSelect
                  value={interval.endTime}
                  onChange={(value) => updateInterval(day, index, 'endTime', value)}
                />
                <button type="button" onClick={() => removeInterval(day, index)}>
                  Quitar
                </button>
              </div>
            ))}
            <button type="button" onClick={() => addInterval(day)}>
              ➕ Anadir tramo
            </button>
          </div>
        )
      })}

      <div className="hours-save-row">
        {saved && <p className="hint-text">Horario guardado.</p>}
        <button type="submit" className="primary" disabled={saving}>
          {saving ? 'Guardando...' : 'Guardar horario'}
        </button>
      </div>
    </form>
  )
}

/** Pestana exclusiva del superadministrador: lista los trabajadores, permite invitar cuentas nuevas y editar el horario semanal de cada uno. */
function WorkersTab({ onError }: { onError: (message: string | null) => void }) {
  const [workers, setWorkers] = useState<Worker[]>([])
  const [treatments, setTreatments] = useState<Treatment[]>([])
  const [users, setUsers] = useState<AppUser[]>([])
  const [loading, setLoading] = useState(true)
  const [pendingId, setPendingId] = useState<number | null>(null)
  const [showInvite, setShowInvite] = useState(false)
  const [showPromote, setShowPromote] = useState(false)
  const [editingHoursFor, setEditingHoursFor] = useState<number | null>(null)
  const [confirmTarget, setConfirmTarget] = useState<
    { type: 'delete' | 'remove-profile'; worker: Worker } | null
  >(null)

  const load = async () => {
    setLoading(true)
    onError(null)
    try {
      const [workersRes, treatmentsRes, usersRes] = await Promise.all([
        client.get<Worker[]>('/admin/workers'),
        client.get<Treatment[]>('/admin/treatments'),
        client.get<AppUser[]>('/superadmin/users'),
      ])
      setWorkers(workersRes.data)
      setTreatments(treatmentsRes.data)
      setUsers(usersRes.data)
    } catch (err) {
      onError(extractErrorMessage(err, 'No se pudieron cargar los trabajadores'))
    } finally {
      setLoading(false)
    }
  }

  const eligibleUsers = users.filter((u) => !workers.some((w) => w.userId === u.id))

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const deleteWorker = async (worker: Worker) => {
    setPendingId(worker.id)
    onError(null)
    try {
      await client.delete(`/superadmin/users/${worker.userId}`)
      await load()
    } catch (err) {
      onError(extractErrorMessage(err, 'No se pudo eliminar ese trabajador'))
    } finally {
      setPendingId(null)
    }
  }

  const removeWorkerProfile = async (worker: Worker) => {
    setPendingId(worker.id)
    onError(null)
    try {
      await client.delete(`/superadmin/workers/${worker.id}`)
      await load()
    } catch (err) {
      onError(extractErrorMessage(err, 'No se pudo quitar el perfil de trabajador'))
    } finally {
      setPendingId(null)
    }
  }

  const confirmPending = async () => {
    if (!confirmTarget) return
    const { type, worker } = confirmTarget
    setConfirmTarget(null)
    if (type === 'delete') {
      await deleteWorker(worker)
    } else {
      await removeWorkerProfile(worker)
    }
  }

  const updateColor = async (worker: Worker, color: string) => {
    if (color === worker.color) return
    setPendingId(worker.id)
    onError(null)
    try {
      await client.put(`/admin/workers/${worker.id}/color`, { color })
      setWorkers((prev) => prev.map((w) => (w.id === worker.id ? { ...w, color } : w)))
    } catch (err) {
      onError(extractErrorMessage(err, 'No se pudo cambiar el color'))
    } finally {
      setPendingId(null)
    }
  }

  const treatmentNames = (treatmentIds: number[]) =>
    treatmentIds
      .map((id) => treatments.find((t) => t.id === id)?.name)
      .filter((name): name is string => !!name)

  const hoursByDay = (worker: Worker): HourInterval[][] => {
    const grouped = groupByDay(worker.weeklyHours)
    return Array.from({ length: 7 }, (_, i) => grouped[i + 1] ?? [])
  }

  if (loading) {
    return <p>Cargando...</p>
  }

  return (
    <div>
      <div className="users-tab-header">
        <p className="hint-text">
          Trabajadores del salon: cada uno tiene su propio horario semanal y su propia cuenta (rol administrador).
          Que servicios sabe hacer cada uno se gestiona desde la pestaña "Servicios".
        </p>
        <div className="users-table-actions">
          <button onClick={() => setShowPromote(true)}>➕ Hacer trabajador a alguien</button>
          <button className="primary" onClick={() => setShowInvite(true)}>
            ➕ Nuevo trabajador
          </button>
        </div>
      </div>

      {workers.length === 0 && <p className="hint-text">Ningun trabajador todavia.</p>}

      {workers.map((worker) => (
        <div className="worker-card" key={worker.id}>
          <div className="worker-card-header">
            <div>
              <span className="worker-color-dot" style={{ backgroundColor: worker.color }} aria-hidden="true" />
              <strong>{worker.name}</strong> <span className="hint-text">{worker.email}</span>
              {!worker.active && <span className="hint-text"> · inactivo</span>}
            </div>
            <div className="users-table-actions">
              <button onClick={() => setEditingHoursFor(editingHoursFor === worker.id ? null : worker.id)}>
                {editingHoursFor === worker.id ? 'Ocultar horario' : 'Editar horario'}
              </button>
              <button
                disabled={pendingId === worker.id}
                onClick={() => setConfirmTarget({ type: 'remove-profile', worker })}
              >
                Dejar de ser trabajador
              </button>
              <button
                className="danger"
                disabled={pendingId === worker.id}
                onClick={() => setConfirmTarget({ type: 'delete', worker })}
              >
                Eliminar
              </button>
            </div>
          </div>
          <p className="hint-text">
            {treatmentNames(worker.treatmentIds).length > 0
              ? `Servicios: ${treatmentNames(worker.treatmentIds).join(', ')}`
              : 'Todavia no tiene ningun servicio asignado.'}
          </p>
          <div className="worker-color-picker">
            {WORKER_COLOR_PALETTE.map((color) => (
              <button
                key={color}
                type="button"
                className={`worker-color-swatch${worker.color === color ? ' worker-color-swatch--selected' : ''}`}
                style={{ backgroundColor: color }}
                aria-label={`Elegir color ${color}`}
                disabled={pendingId === worker.id}
                onClick={() => updateColor(worker, color)}
              />
            ))}
          </div>
          {editingHoursFor === worker.id && (
            <WorkerHoursEditor
              workerId={worker.id}
              initialHours={hoursByDay(worker)}
              onError={onError}
              onSaved={(hours) =>
                setWorkers((prev) => prev.map((w) => (w.id === worker.id ? { ...w, weeklyHours: hours } : w)))
              }
            />
          )}
        </div>
      ))}

      {showInvite && (
        <InviteWorkerModal
          onClose={() => setShowInvite(false)}
          onSent={() => {
            setShowInvite(false)
            load()
          }}
        />
      )}

      {showPromote && (
        <PromoteUserModal
          eligibleUsers={eligibleUsers}
          onClose={() => setShowPromote(false)}
          onDone={() => {
            setShowPromote(false)
            load()
          }}
        />
      )}

      {confirmTarget && (
        <ConfirmModal
          title={confirmTarget.type === 'delete' ? 'Eliminar cuenta' : 'Quitar como trabajador'}
          message={
            confirmTarget.type === 'delete'
              ? `¿Eliminar la cuenta de ${confirmTarget.worker.email}? Esta accion no se puede deshacer.`
              : `¿Quitar a ${confirmTarget.worker.name} como trabajador? Dejara de aparecer para reservar, pero conserva su cuenta y su rol.`
          }
          confirmLabel={confirmTarget.type === 'delete' ? 'Eliminar' : 'Quitar'}
          danger={confirmTarget.type === 'delete'}
          pending={pendingId === confirmTarget.worker.id}
          onConfirm={confirmPending}
          onCancel={() => setConfirmTarget(null)}
        />
      )}
    </div>
  )
}

/** Modal para convertir a un usuario ya registrado (que todavia no es trabajador) en trabajador. */
function PromoteUserModal({
  eligibleUsers,
  onClose,
  onDone,
}: {
  eligibleUsers: AppUser[]
  onClose: () => void
  onDone: () => void
}) {
  const [userId, setUserId] = useState('')
  const [sending, setSending] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const modalRef = useModalA11y<HTMLFormElement>(true, onClose)

  const promote = async () => {
    if (!userId) return
    setSending(true)
    setError(null)
    try {
      await client.post(`/superadmin/workers/${userId}/promote`)
      onDone()
    } catch (err) {
      setError(extractErrorMessage(err, 'No se pudo convertir en trabajador'))
    } finally {
      setSending(false)
    }
  }

  return (
    <div className="modal-overlay" onClick={onClose}>
      <form
        className="modal-box"
        role="dialog"
        aria-modal="true"
        ref={modalRef}
        onClick={(e) => e.stopPropagation()}
        onSubmit={(e) => {
          e.preventDefault()
          promote()
        }}
      >
        <h2>Hacer trabajador a alguien</h2>
        {eligibleUsers.length === 0 ? (
          <p className="hint-text">Todos los usuarios registrados ya son trabajadores.</p>
        ) : (
          <>
            <p className="hint-text">
              Elige una cuenta ya registrada. Si no es administrador, pasara a serlo automaticamente.
            </p>
            <Select value={userId} onChange={(e) => setUserId(e.target.value)}>
              <option value="" disabled>
                Elige un usuario
              </option>
              {eligibleUsers.map((u) => (
                <option key={u.id} value={u.id}>
                  {u.name ? `${u.name} (${u.email})` : u.email}
                </option>
              ))}
            </Select>
          </>
        )}
        {error && <p className="error-text">{error}</p>}
        <div className="modal-actions">
          <button type="button" onClick={onClose}>
            Cancelar
          </button>
          <button type="submit" className="primary" disabled={sending || !userId}>
            {sending ? 'Guardando...' : 'Hacer trabajador'}
          </button>
        </div>
      </form>
    </div>
  )
}

/** Modal para invitar por email a una persona a crear una cuenta de trabajador (rol administrador). */
function InviteWorkerModal({ onClose, onSent }: { onClose: () => void; onSent: () => void }) {
  const [email, setEmail] = useState('')
  const [sending, setSending] = useState(false)
  const [sent, setSent] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const modalRef = useModalA11y<HTMLFormElement>(true, onClose)

  const sendInvite = async () => {
    setSending(true)
    setError(null)
    try {
      await client.post('/superadmin/workers/invite', { email: email.trim() })
      setSent(true)
    } catch (err) {
      setError(extractErrorMessage(err, 'No se pudo enviar la invitacion'))
    } finally {
      setSending(false)
    }
  }

  return (
    <div className="modal-overlay" onClick={onClose}>
      <form
        className="modal-box"
        role="dialog"
        aria-modal="true"
        ref={modalRef}
        onClick={(e) => e.stopPropagation()}
        onSubmit={(e) => {
          e.preventDefault()
          sendInvite()
        }}
      >
        <h2>Nuevo trabajador</h2>
        {sent ? (
          <>
            <p className="hint-text">
              Hemos enviado un enlace a <strong>{email}</strong> para que cree su cuenta de trabajador.
            </p>
            <div className="modal-actions">
              <button type="button" className="primary" onClick={onSent}>
                Cerrar
              </button>
            </div>
          </>
        ) : (
          <>
            <p className="hint-text">
              Introduce el email de la persona; le llegara un enlace para crear su cuenta. Podras asignarle
              servicios desde la pestaña "Servicios" y ajustar su horario aqui una vez la haya creado.
            </p>
            <input
              type="email"
              placeholder="trabajador@email.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              autoFocus
            />
            {error && <p className="error-text">{error}</p>}
            <div className="modal-actions">
              <button type="button" onClick={onClose}>
                Cancelar
              </button>
              <button type="submit" className="primary" disabled={sending || !email.trim()}>
                {sending ? 'Enviando...' : 'Enviar invitacion'}
              </button>
            </div>
          </>
        )}
      </form>
    </div>
  )
}

interface UserRowAction {
  label: string
  onClick: (email: string, id: number) => void
  danger?: boolean
}

/** Tabla de usuarios de un mismo rol; cada fila puede tener sus propias acciones (ej. "Hacer admin", "Eliminar"). */
function UsersTable({
  users,
  pendingEmail,
  getActions,
}: {
  users: AppUser[]
  pendingEmail: string | null
  getActions?: (user: AppUser) => UserRowAction[]
}) {
  if (users.length === 0) {
    return <p className="hint-text">Ninguno todavia.</p>
  }
  return (
    <div className="user-list">
      {users.map((u) => {
        const actions = getActions ? getActions(u) : []
        return (
          <div className="user-row" key={u.email}>
            <div className="user-row-info">
              <span className="user-row-name">{u.name ?? u.email}</span>
              {u.name && <span className="user-row-meta">{u.email}</span>}
              <span className="user-row-meta">{u.phone ?? 'Sin telefono'}</span>
            </div>
            {getActions && (
              <div className="users-table-actions">
                {actions.map((a) => (
                  <button
                    key={a.label}
                    className={a.danger ? 'danger' : 'primary'}
                    disabled={pendingEmail === u.email}
                    onClick={() => a.onClick(u.email, u.id)}
                  >
                    {a.label}
                  </button>
                ))}
              </div>
            )}
          </div>
        )
      })}
    </div>
  )
}

/** Pestana exclusiva del superadministrador: lista todos los usuarios registrados, permite ascender/degradar/eliminar cuentas e invitar cuentas nuevas. */
function UsersTab({ onError }: { onError: (message: string | null) => void }) {
  const { user: currentUser } = useAuth()
  const [users, setUsers] = useState<AppUser[]>([])
  const [loading, setLoading] = useState(true)
  const [pendingEmail, setPendingEmail] = useState<string | null>(null)
  const [showInvite, setShowInvite] = useState(false)
  const [confirmDeleteTarget, setConfirmDeleteTarget] = useState<{ email: string; id: number } | null>(null)

  const load = async () => {
    setLoading(true)
    onError(null)
    try {
      const { data } = await client.get<AppUser[]>('/superadmin/users')
      setUsers(data)
    } catch (err) {
      onError(extractErrorMessage(err, 'No se pudo cargar la lista de usuarios'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const makeAdmin = async (email: string, id: number) => {
    setPendingEmail(email)
    onError(null)
    try {
      await client.post(`/superadmin/users/${id}/make-admin`)
      await load()
    } catch (err) {
      onError(extractErrorMessage(err, 'No se pudo ascender a ese usuario'))
    } finally {
      setPendingEmail(null)
    }
  }

  const removeAdmin = async (email: string, id: number) => {
    setPendingEmail(email)
    onError(null)
    try {
      await client.post(`/superadmin/users/${id}/remove-admin`)
      await load()
    } catch (err) {
      onError(extractErrorMessage(err, 'No se pudo quitar el rol de administrador'))
    } finally {
      setPendingEmail(null)
    }
  }

  const deleteUser = (email: string, id: number) => {
    setConfirmDeleteTarget({ email, id })
  }

  const confirmDeleteUser = async () => {
    if (!confirmDeleteTarget) return
    const { email, id } = confirmDeleteTarget
    setConfirmDeleteTarget(null)
    setPendingEmail(email)
    onError(null)
    try {
      await client.delete(`/superadmin/users/${id}`)
      await load()
    } catch (err) {
      onError(extractErrorMessage(err, 'No se pudo eliminar ese usuario'))
    } finally {
      setPendingEmail(null)
    }
  }

  if (loading) {
    return <p>Cargando...</p>
  }

  const superadmins = users.filter((u) => u.role === 'SUPERADMIN')
  const admins = users.filter((u) => u.role === 'ADMIN')
  const clients = users.filter((u) => u.role === 'USER')
  const deleteAction: UserRowAction = { label: 'Eliminar', onClick: deleteUser, danger: true }

  return (
    <div>
      <div className="users-tab-header">
        <p className="hint-text">Todos los usuarios registrados, agrupados por rol.</p>
        <button className="primary" onClick={() => setShowInvite(true)}>
          ➕ Nuevo usuario
        </button>
      </div>

      <h3>Superadministradores</h3>
      <UsersTable
        users={superadmins}
        pendingEmail={pendingEmail}
        getActions={(u) => (u.email === currentUser?.email ? [] : [deleteAction])}
      />

      <h3>Administradores</h3>
      <UsersTable
        users={admins}
        pendingEmail={pendingEmail}
        getActions={() => [{ label: 'Quitar admin', onClick: removeAdmin }, deleteAction]}
      />

      <h3>Clientes</h3>
      <UsersTable
        users={clients}
        pendingEmail={pendingEmail}
        getActions={() => [{ label: 'Hacer admin', onClick: makeAdmin }, deleteAction]}
      />

      {showInvite && (
        <InviteUserModal
          onClose={() => setShowInvite(false)}
          onSent={() => {
            setShowInvite(false)
            load()
          }}
        />
      )}

      {confirmDeleteTarget && (
        <ConfirmModal
          title="Eliminar cuenta"
          message={`¿Eliminar la cuenta de ${confirmDeleteTarget.email}? Esta accion no se puede deshacer.`}
          confirmLabel="Eliminar"
          danger
          pending={pendingEmail === confirmDeleteTarget.email}
          onConfirm={confirmDeleteUser}
          onCancel={() => setConfirmDeleteTarget(null)}
        />
      )}
    </div>
  )
}

/** Etiquetas en español para cada tipo de accion del log de auditoria. */
const AUDIT_ACTION_LABELS: Record<AuditAction, string> = {
  SIGNUP: 'Registro solicitado',
  SIGNUP_COMPLETE: 'Registro completado',
  LOGIN: 'Inicio de sesion',
  LOGIN_GOOGLE: 'Inicio de sesion con Google',
  PASSWORD_RESET_REQUEST: 'Solicito restablecer contrasena',
  PASSWORD_RESET_COMPLETE: 'Contrasena restablecida',
  APPOINTMENT_BOOK: 'Reserva de cita',
  APPOINTMENT_CANCEL: 'Cancelacion de cita',
  ADMIN_APPOINTMENT_BOOK: 'Reserva creada por admin',
  ADMIN_APPOINTMENT_CANCEL: 'Cancelacion por admin',
  ADMIN_APPOINTMENT_COMPLETED_UPDATED: 'Cita marcada hecha/no hecha',
  ADMIN_BLOCK_CREATE: 'Bloqueo de horario creado',
  ADMIN_BLOCK_DELETE: 'Bloqueo de horario eliminado',
  ADMIN_BUSINESS_HOURS_UPDATE: 'Horario de apertura actualizado',
  ADMIN_TREATMENT_CREATE: 'Servicio creado',
  ADMIN_TREATMENT_UPDATE: 'Servicio actualizado',
  PROFILE_UPDATE: 'Perfil actualizado',
  PASSWORD_CHANGED: 'Cambio de contrasena',
  TERMS_ACCEPTED: 'Acepto los terminos y la politica de privacidad',
  SUPERADMIN_MAKE_ADMIN: 'Ascenso a administrador',
  SUPERADMIN_REMOVE_ADMIN: 'Descenso de administrador',
  SUPERADMIN_DELETE_USER: 'Usuario eliminado',
  WORKER_INVITED: 'Trabajador invitado',
  WORKER_PROMOTED: 'Usuario convertido en trabajador',
  WORKER_PROFILE_REMOVED: 'Perfil de trabajador retirado',
  WORKER_HOURS_UPDATED: 'Horario de trabajador actualizado',
  WORKER_COLOR_UPDATED: 'Color de trabajador actualizado',
  TREATMENT_WORKERS_UPDATED: 'Capacidad de servicio actualizada',
  ADMIN_CASH_CLOSING_SAVED: 'Cierre de caja',
}

/** Formatea una fecha/hora ISO como texto legible en español (ej. "25/08/2026 10:15"). */
function formatDateTime(iso: string): string {
  return new Date(iso).toLocaleString('es-ES', {
    day: '2-digit',
    month: '2-digit',
    year: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

/** Pestana exclusiva del superadministrador: resumen de citas y desglose por trabajador y por servicio en un rango de fechas. */
function StatsTab({ onError }: { onError: (message: string | null) => void }) {
  const [from, setFrom] = useState(() => toIsoDate(addDays(new Date(), -30)))
  const [to, setTo] = useState(() => toIsoDate(new Date()))
  const [stats, setStats] = useState<StatsResponse | null>(null)
  const [loading, setLoading] = useState(true)

  const load = async () => {
    setLoading(true)
    onError(null)
    try {
      const { data } = await client.get<StatsResponse>('/superadmin/stats', { params: { from, to } })
      setStats(data)
    } catch (err) {
      onError(extractErrorMessage(err, 'No se pudieron cargar las estadisticas'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const maxWorkerCount = Math.max(1, ...(stats?.byWorker.map((w) => w.appointmentCount) ?? [0]))
  const maxTreatmentCount = Math.max(1, ...(stats?.byTreatment.map((t) => t.appointmentCount) ?? [0]))

  return (
    <div>
      <form
        className="logs-filters"
        onSubmit={(e) => {
          e.preventDefault()
          load()
        }}
      >
        <DatePickerField value={from} onChange={setFrom} />
        <span className="hint-text">a</span>
        <DatePickerField value={to} onChange={setTo} />
        <button type="submit" className="primary">
          Buscar
        </button>
      </form>

      {loading || !stats ? (
        <p>Cargando...</p>
      ) : (
        <>
          <div className="stats-kpi-row">
            <div className="stat-tile">
              <span className="stat-tile-label">Citas reservadas</span>
              <span className="stat-tile-value">{stats.summary.totalBooked}</span>
            </div>
            <div className="stat-tile">
              <span className="stat-tile-label">Hechas</span>
              <span className="stat-tile-value">
                {stats.summary.completed}/{stats.summary.completedEligible}
              </span>
            </div>
            <div className="stat-tile" title="Incluye canceladas y citas pasadas donde el cliente no se presento">
              <span className="stat-tile-label">Canceladas</span>
              <span className="stat-tile-value">{stats.summary.cancelled}</span>
            </div>
            <div className="stat-tile">
              <span className="stat-tile-label">Tasa de cancelacion</span>
              <span className="stat-tile-value">{stats.summary.cancellationRatePercent.toFixed(1)}%</span>
            </div>
          </div>

          <h3>Citas por trabajador</h3>
          {stats.byWorker.length === 0 ? (
            <p className="hint-text">No hay citas de ningun trabajador en este rango.</p>
          ) : (
            <div className="stats-bar-list">
              {stats.byWorker.map((w) => (
                <div className="stats-bar-row" key={w.workerId} title={`${w.appointmentCount} citas, ${w.completedCount} hechas`}>
                  <span className="stats-bar-label">{w.workerName}</span>
                  <div className="stats-bar-track">
                    <div
                      className="stats-bar-fill"
                      style={{ width: `${(w.appointmentCount / maxWorkerCount) * 100}%`, backgroundColor: w.workerColor }}
                    />
                  </div>
                  <span className="stats-bar-value">
                    {w.appointmentCount} <span className="hint-text">({w.completedCount} hechas)</span>
                  </span>
                </div>
              ))}
            </div>
          )}

          <h3>Citas por servicio</h3>
          {stats.byTreatment.length === 0 ? (
            <p className="hint-text">No hay citas en este rango.</p>
          ) : (
            <div className="stats-bar-list">
              {stats.byTreatment.map((t) => (
                <div className="stats-bar-row" key={t.treatmentName} title={`${t.appointmentCount} citas`}>
                  <span className="stats-bar-label">{t.treatmentName}</span>
                  <div className="stats-bar-track">
                    <div
                      className="stats-bar-fill stats-bar-fill--brand"
                      style={{ width: `${(t.appointmentCount / maxTreatmentCount) * 100}%` }}
                    />
                  </div>
                  <span className="stats-bar-value">{t.appointmentCount}</span>
                </div>
              ))}
            </div>
          )}
        </>
      )}
    </div>
  )
}

/**
 * Pestana "Caja": para un dia elegido, muestra cuanto se cobro por metodo de pago segun las
 * citas marcadas como hechas, y permite cerrar la caja anotando el efectivo contado fisicamente
 * (mostrando el descuadre frente al efectivo esperado).
 */
function CashClosingTab({ onError }: { onError: (message: string | null) => void }) {
  const [date, setDate] = useState(() => toIsoDate(new Date()))
  const [summary, setSummary] = useState<CashSummaryView | null>(null)
  const [loading, setLoading] = useState(true)
  const [countedCashInput, setCountedCashInput] = useState('')
  const [saving, setSaving] = useState(false)

  const load = async () => {
    setLoading(true)
    onError(null)
    try {
      const { data } = await client.get<CashSummaryView>('/admin/cash-closing', { params: { date } })
      setSummary(data)
      setCountedCashInput(data.countedCash != null ? String(data.countedCash) : '')
    } catch (err) {
      onError(extractErrorMessage(err, 'No se pudo cargar el resumen de caja'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [date])

  const saveClosing = async () => {
    setSaving(true)
    onError(null)
    try {
      const { data } = await client.put<CashSummaryView>(
        '/admin/cash-closing',
        { countedCash: Number(countedCashInput) },
        { params: { date } },
      )
      setSummary(data)
    } catch (err) {
      onError(extractErrorMessage(err, 'No se pudo guardar el cierre de caja'))
    } finally {
      setSaving(false)
    }
  }

  return (
    <div>
      <form
        className="logs-filters"
        onSubmit={(e) => {
          e.preventDefault()
          load()
        }}
      >
        <DatePickerField value={date} onChange={setDate} />
      </form>

      {loading || !summary ? (
        <p>Cargando...</p>
      ) : (
        <>
          <div className="stats-kpi-row">
            <div className="stat-tile">
              <span className="stat-tile-label">Efectivo</span>
              <span className="stat-tile-value">{formatPrice(summary.efectivoTotal)}</span>
            </div>
            <div className="stat-tile">
              <span className="stat-tile-label">Tarjeta</span>
              <span className="stat-tile-value">{formatPrice(summary.tarjetaTotal)}</span>
            </div>
            <div className="stat-tile">
              <span className="stat-tile-label">Otro</span>
              <span className="stat-tile-value">{formatPrice(summary.otroTotal)}</span>
            </div>
            <div className="stat-tile">
              <span className="stat-tile-label">Total</span>
              <span className="stat-tile-value">{formatPrice(summary.total)}</span>
            </div>
            <div className="stat-tile">
              <span className="stat-tile-label">Citas cobradas</span>
              <span className="stat-tile-value">{summary.appointmentCount}</span>
            </div>
          </div>

          <h3>Citas cobradas ese dia</h3>
          {summary.appointments.length === 0 ? (
            <p className="hint-text">No hay citas cobradas ese dia.</p>
          ) : (
            <table className="users-table cash-table">
              <thead>
                <tr>
                  <th>Hora</th>
                  <th>Cliente</th>
                  <th>Servicio</th>
                  <th>Importe</th>
                  <th>Metodo</th>
                </tr>
              </thead>
              <tbody>
                {summary.appointments.map((a) => (
                  <tr key={a.id}>
                    <td>{a.startTime}</td>
                    <td>{a.clientName}</td>
                    <td>{a.treatmentName}</td>
                    <td>{a.amountCharged != null ? formatPrice(a.amountCharged) : '-'}</td>
                    <td>{a.paymentMethod ? PAYMENT_METHOD_LABELS[a.paymentMethod] : '-'}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          )}

          <h3>Cierre de caja</h3>
          <label htmlFor="counted-cash">Efectivo contado</label>
          <input
            id="counted-cash"
            type="number"
            step="0.01"
            min="0"
            value={countedCashInput}
            onChange={(e) => setCountedCashInput(e.target.value)}
          />
          <button
            type="button"
            className="primary"
            disabled={saving || !countedCashInput.trim()}
            onClick={saveClosing}
          >
            {saving ? 'Guardando...' : 'Guardar cierre'}
          </button>

          {summary.difference != null && (
            <p className={Math.abs(summary.difference) < 0.01 ? 'cash-diff-ok' : 'cash-diff-bad'}>
              Descuadre: {formatPrice(summary.difference)}
            </p>
          )}
        </>
      )}
    </div>
  )
}

/** Pestana exclusiva del superadministrador: muestra el log de auditoria de todas las acciones, con filtro por usuario y por tipo de accion. */
function LogsTab({ onError }: { onError: (message: string | null) => void }) {
  const [logs, setLogs] = useState<AuditLogEntry[]>([])
  const [page, setPage] = useState(0)
  const [totalPages, setTotalPages] = useState(0)
  const [loading, setLoading] = useState(true)
  const [actorEmailInput, setActorEmailInput] = useState('')
  const [actionInput, setActionInput] = useState<AuditAction | ''>('')
  const [appliedFilters, setAppliedFilters] = useState<{ actorEmail: string; action: AuditAction | '' }>({
    actorEmail: '',
    action: '',
  })

  const load = async (targetPage: number, filters: { actorEmail: string; action: AuditAction | '' }, append: boolean) => {
    setLoading(true)
    onError(null)
    try {
      const { data } = await client.get<Page<AuditLogEntry>>('/superadmin/audit-logs', {
        params: {
          actorEmail: filters.actorEmail.trim() || undefined,
          action: filters.action || undefined,
          page: targetPage,
          size: 50,
        },
      })
      setLogs((prev) => (append ? [...prev, ...data.content] : data.content))
      setPage(data.number)
      setTotalPages(data.totalPages)
    } catch (err) {
      onError(extractErrorMessage(err, 'No se pudo cargar el log de auditoria'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    load(0, appliedFilters, false)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [])

  const search = () => {
    const filters = { actorEmail: actorEmailInput, action: actionInput }
    setAppliedFilters(filters)
    load(0, filters, false)
  }

  const loadMore = () => {
    load(page + 1, appliedFilters, true)
  }

  return (
    <div>
      <form
        className="logs-filters"
        onSubmit={(e) => {
          e.preventDefault()
          search()
        }}
      >
        <input
          type="text"
          placeholder="Buscar por email de usuario"
          value={actorEmailInput}
          onChange={(e) => setActorEmailInput(e.target.value)}
        />
        <Select value={actionInput} onChange={(e) => setActionInput(e.target.value as AuditAction | '')}>
          <option value="">Todas las acciones</option>
          {Object.entries(AUDIT_ACTION_LABELS).map(([value, label]) => (
            <option key={value} value={value}>
              {label}
            </option>
          ))}
        </Select>
        <button type="submit" className="primary">
          Buscar
        </button>
      </form>

      {loading && logs.length === 0 ? (
        <p>Cargando...</p>
      ) : logs.length === 0 ? (
        <p className="hint-text">No hay registros que coincidan con el filtro.</p>
      ) : (
        <>
          <table className="users-table">
            <thead>
              <tr>
                <th>Fecha</th>
                <th>Usuario</th>
                <th>Accion</th>
                <th>Detalle</th>
              </tr>
            </thead>
            <tbody>
              {logs.map((log) => (
                <tr key={log.id}>
                  <td>{formatDateTime(log.createdAt)}</td>
                  <td>{log.actorEmail}</td>
                  <td>{AUDIT_ACTION_LABELS[log.action]}</td>
                  <td>{log.details ?? '-'}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {page + 1 < totalPages && (
            <button disabled={loading} onClick={loadMore}>
              {loading ? 'Cargando...' : 'Cargar mas'}
            </button>
          )}
        </>
      )}
    </div>
  )
}

/** Modal para invitar por email a una cuenta nueva: envia el mismo enlace de registro que la pantalla publica de alta. */
function InviteUserModal({ onClose, onSent }: { onClose: () => void; onSent: () => void }) {
  const [email, setEmail] = useState('')
  const [sending, setSending] = useState(false)
  const [sent, setSent] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const modalRef = useModalA11y<HTMLFormElement>(true, onClose)

  const sendInvite = async () => {
    setSending(true)
    setError(null)
    try {
      await client.post('/auth/signup', { email: email.trim() })
      setSent(true)
    } catch (err) {
      setError(extractErrorMessage(err, 'No se pudo enviar la invitacion'))
    } finally {
      setSending(false)
    }
  }

  return (
    <div className="modal-overlay" onClick={onClose}>
      <form
        className="modal-box"
        role="dialog"
        aria-modal="true"
        ref={modalRef}
        onClick={(e) => e.stopPropagation()}
        onSubmit={(e) => {
          e.preventDefault()
          sendInvite()
        }}
      >
        <h2>Nuevo usuario</h2>
        {sent ? (
          <>
            <p className="hint-text">
              Hemos enviado un enlace a <strong>{email}</strong> para que cree su cuenta.
            </p>
            <div className="modal-actions">
              <button type="button" className="primary" onClick={onSent}>
                Cerrar
              </button>
            </div>
          </>
        ) : (
          <>
            <p className="hint-text">Introduce el email de la persona; le llegara un enlace para crear su cuenta.</p>
            <input
              type="email"
              placeholder="cliente@email.com"
              value={email}
              onChange={(e) => setEmail(e.target.value)}
              autoFocus
            />
            {error && <p className="error-text">{error}</p>}
            <div className="modal-actions">
              <button type="button" onClick={onClose}>
                Cancelar
              </button>
              <button type="submit" className="primary" disabled={sending || !email.trim()}>
                {sending ? 'Enviando...' : 'Enviar invitacion'}
              </button>
            </div>
          </>
        )}
      </form>
    </div>
  )
}
