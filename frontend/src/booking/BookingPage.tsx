import { useEffect, useMemo, useState } from 'react'
import client, { extractErrorMessage, isConflict } from '../api/client'
import { useAuth } from '../auth/AuthContext'
import type {
  AdminAppointment,
  Appointment,
  AvailabilityDay,
  AvailableWorker,
  BlockedInterval,
  OccupiedDay,
  OccupiedInterval,
  ScheduleBlock,
  Segment,
  Treatment,
} from '../types'
import WeekNav from '../components/WeekNav'
import DayTimeline, { type TimelineItem } from '../components/DayTimeline'
import TimeRuler from '../components/TimeRuler'
import { buildWeekSchedule, intervalsForDate, scheduleRange, type OpeningHour } from '../businessHours'
import { useResponsiveDays } from '../hooks/useResponsiveDays'
import { useModalA11y } from '../hooks/useModalA11y'
import Checkbox from '../components/Checkbox'
import Select from '../components/Select'
import { formatDayLabel, isPastDay, isPastDateTime, parseTimeToMinutes, toIsoDate } from '../dateUtils'

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

/** Convierte una cita propia del usuario en un bloque para dibujar en la agenda visual del dia, con su boton de cancelar si procede. */
function toTimelineItem(
  appt: Appointment,
  onCancel: (appointmentId: number) => void,
  pendingKey: string | null,
  past: boolean,
): TimelineItem {
  let cursor = parseTimeToMinutes(appt.startTime)
  const segments = appt.segments.map((s) => {
    const segment = { type: s.type, startMinutes: cursor, endMinutes: cursor + s.minutes }
    cursor += s.minutes
    return segment
  })
  return {
    key: `appt-${appt.id}`,
    startMinutes: parseTimeToMinutes(appt.startTime),
    endMinutes: parseTimeToMinutes(appt.endTime),
    segments,
    title: appt.treatmentName,
    meta: appt.workerName ?? undefined,
    variant: 'mine',
    color: appt.workerColor ?? undefined,
    action: past
      ? undefined
      : {
          label: 'Cancelar',
          onClick: () => onCancel(appt.id),
          disabled: pendingKey === `cancel-${appt.id}`,
        },
  }
}

/**
 * Convierte cualquier cita (propia o de otro cliente) en un bloque con todos
 * los datos, tal y como la ve el superadministrador tambien en la pantalla
 * normal de reserva: solo se puede cancelar la propia desde aqui.
 */
function toAdminTimelineItem(
  appt: AdminAppointment,
  currentUserEmail: string | undefined,
  onCancel: (appointmentId: number) => void,
  pendingKey: string | null,
  past: boolean,
): TimelineItem {
  const mine = !!currentUserEmail && appt.bookedByEmail?.toLowerCase() === currentUserEmail.toLowerCase()
  let cursor = parseTimeToMinutes(appt.startTime)
  const segments = appt.segments.map((s) => {
    const segment = { type: s.type, startMinutes: cursor, endMinutes: cursor + s.minutes }
    cursor += s.minutes
    return segment
  })
  return {
    key: `appt-${appt.id}`,
    startMinutes: parseTimeToMinutes(appt.startTime),
    endMinutes: parseTimeToMinutes(appt.endTime),
    segments,
    title: appt.treatmentName,
    meta: appt.workerName ?? undefined,
    subtitle: [`${appt.clientName} · ${appt.clientPhone}`, appt.notes].filter(Boolean).join(' · '),
    variant: mine ? 'mine' : 'booked',
    color: appt.workerColor ?? undefined,
    action:
      mine && !past
        ? {
            label: 'Cancelar',
            onClick: () => onCancel(appt.id),
            disabled: pendingKey === `cancel-${appt.id}`,
          }
        : undefined,
  }
}

/** Convierte un bloqueo de horario (visto por el superadministrador, con motivo y a quien afecta) en un bloque "No disponible". */
function toAdminBlockedTimelineItem(block: ScheduleBlock): TimelineItem {
  const startMinutes = parseTimeToMinutes(block.startTime)
  const endMinutes = parseTimeToMinutes(block.endTime)
  return {
    key: `block-${block.id}`,
    startMinutes,
    endMinutes,
    segments: [{ type: 'WORK', startMinutes, endMinutes }],
    title: 'No disponible',
    subtitle: [block.workerName ?? 'Todo el salon', block.reason].filter(Boolean).join(' · '),
    variant: 'block',
  }
}

/** Convierte un hueco ocupado por otro cliente en un bloque generico "Ocupado" para la agenda visual del dia. */
function toOccupiedTimelineItem(interval: OccupiedInterval, index: number): TimelineItem {
  const startMinutes = parseTimeToMinutes(interval.startTime)
  const endMinutes = parseTimeToMinutes(interval.endTime)
  return {
    key: `occupied-${index}-${interval.startTime}`,
    startMinutes,
    endMinutes,
    segments: [{ type: 'WORK', startMinutes, endMinutes }],
    title: 'Ocupado',
    meta: interval.workerName ?? undefined,
    variant: 'occupied',
    color: interval.workerColor ?? undefined,
  }
}

/** Convierte un bloqueo manual del admin en un bloque "No disponible" con el motivo, para la agenda visual del dia. */
function toBlockedTimelineItem(interval: BlockedInterval, index: number): TimelineItem {
  const startMinutes = parseTimeToMinutes(interval.startTime)
  const endMinutes = parseTimeToMinutes(interval.endTime)
  return {
    key: `blocked-${index}-${interval.startTime}`,
    startMinutes,
    endMinutes,
    segments: [{ type: 'WORK', startMinutes, endMinutes }],
    title: 'No disponible',
    subtitle: interval.reason ?? undefined,
    variant: 'block',
  }
}

interface PendingBooking {
  date: string
  time: string
  workerId: number | null
}

/** Marcador de posicion animado mientras se carga la semana, con la misma forma que la agenda real para que no salte el layout. */
function WeekGridSkeleton() {
  return (
    <div className="week-grid">
      <div className="time-ruler">
        <div className="skeleton-line" style={{ width: '60%', height: 16, marginBottom: 10 }} />
      </div>
      <div className="days-scroll">
        {Array.from({ length: 6 }).map((_, i) => (
          <div className="day-column day-column--compact" key={i}>
            <div className="skeleton-line" style={{ width: '70%', height: 16, marginBottom: 12 }} />
            <div className="skeleton-line" style={{ height: 320 }} />
          </div>
        ))}
      </div>
    </div>
  )
}

/** Pantalla de reserva: permite elegir servicios, navegar por semanas, ver las horas disponibles y las citas propias, y reservar o cancelar una cita. */
export default function BookingPage() {
  const { user } = useAuth()
  const isSuperAdmin = user?.role === 'SUPERADMIN'
  const [treatments, setTreatments] = useState<Treatment[]>([])
  const [selectedIds, setSelectedIds] = useState<number[]>([])
  const [ownAppointments, setOwnAppointments] = useState<Appointment[]>([])
  const [adminAppointments, setAdminAppointments] = useState<AdminAppointment[]>([])
  const [adminBlocks, setAdminBlocks] = useState<ScheduleBlock[]>([])
  const [availability, setAvailability] = useState<AvailabilityDay[]>([])
  const [occupiedDays, setOccupiedDays] = useState<OccupiedDay[]>([])
  const [openingHours, setOpeningHours] = useState<OpeningHour[]>([])
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState<string | null>(null)
  const [pendingKey, setPendingKey] = useState<string | null>(null)
  const [pendingBooking, setPendingBooking] = useState<PendingBooking | null>(null)
  const [notes, setNotes] = useState('')
  const [conflictMessage, setConflictMessage] = useState<string | null>(null)
  const [availableWorkers, setAvailableWorkers] = useState<AvailableWorker[]>([])
  const [loadingWorkers, setLoadingWorkers] = useState(false)

  const schedule = useMemo(() => buildWeekSchedule(openingHours), [openingHours])
  const range = useMemo(() => scheduleRange(schedule), [schedule])
  const { visibleDays, goPrev, goNext, goToday, showToday, label } = useResponsiveDays(schedule)

  const from = toIsoDate(visibleDays[0] ?? new Date())
  const to = toIsoDate(visibleDays[visibleDays.length - 1] ?? new Date())

  const selectedTreatments = useMemo(
    () => selectedIds.map((id) => treatments.find((t) => t.id === id)).filter((t): t is Treatment => !!t),
    [selectedIds, treatments],
  )
  const combinedSegments = useMemo(
    () => selectedTreatments.flatMap((t) => t.segments),
    [selectedTreatments],
  )
  const combinedPrice = selectedTreatments.reduce((sum, t) => sum + t.price, 0)
  const combinedName = selectedTreatments.map((t) => t.name).join(' + ')
  const requiresColorNote = selectedTreatments.some((t) => t.name === 'Tinte' || t.name === 'Mechas')
  const treatmentIdsParam = selectedIds.join(',')
  const [hoursLoaded, setHoursLoaded] = useState(false)

  useEffect(() => {
    client.get<Treatment[]>('/treatments').then(({ data }) => setTreatments(data))
    client
      .get<OpeningHour[]>('/business-hours')
      .then(({ data }) => setOpeningHours(data))
      .finally(() => setHoursLoaded(true))
  }, [])

  // Anade o quita un servicio de la seleccion actual del usuario.
  const toggleTreatment = (id: number) => {
    setSelectedIds((prev) => (prev.includes(id) ? prev.filter((x) => x !== id) : [...prev, id]))
  }

  // Carga las citas propias del usuario dentro de la semana visible.
  const loadOwnAppointments = async () => {
    const { data } = await client.get<Appointment[]>('/appointments', { params: { from, to } })
    setOwnAppointments(data)
  }

  // Para el superadministrador: carga TODAS las citas y bloqueos (con datos completos), en vez de la vista anonimizada.
  const loadAdminView = async () => {
    const [apptRes, blockRes] = await Promise.all([
      client.get<AdminAppointment[]>('/admin/appointments', { params: { from, to } }),
      client.get<ScheduleBlock[]>('/admin/blocks', { params: { from, to } }),
    ])
    setAdminAppointments(apptRes.data)
    setAdminBlocks(blockRes.data)
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

  // Carga los huecos ya ocupados por otros clientes en la semana visible, para mostrarlos en la agenda.
  const loadOccupied = async () => {
    const { data } = await client.get<OccupiedDay[]>('/availability/occupied', { params: { from, to } })
    setOccupiedDays(data)
  }

  // Carga a la vez las citas, la disponibilidad y los huecos ocupados; se ejecuta al cambiar de semana o de servicios elegidos.
  // El superadministrador ve todas las citas con sus datos completos en vez de la vista anonimizada.
  const loadAll = async () => {
    setLoading(true)
    setError(null)
    try {
      if (isSuperAdmin) {
        await Promise.all([loadAdminView(), loadAvailability()])
      } else {
        await Promise.all([loadOwnAppointments(), loadAvailability(), loadOccupied()])
      }
    } catch (err) {
      setError(extractErrorMessage(err, 'No se pudieron cargar los datos'))
    } finally {
      setLoading(false)
    }
  }

  useEffect(() => {
    loadAll()
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [from, to, treatmentIdsParam])

  // Abre el dialogo de confirmacion de reserva para la fecha y hora elegidas, y carga los trabajadores libres en ese momento.
  const openBookingModal = async (date: string, time: string) => {
    setNotes('')
    setError(null)
    setPendingBooking({ date, time, workerId: null })
    setAvailableWorkers([])
    setLoadingWorkers(true)
    try {
      const { data } = await client.get<AvailableWorker[]>('/availability/workers', {
        params: { treatmentIds: treatmentIdsParam, date, startTime: time },
      })
      setAvailableWorkers(data)
    } catch (err) {
      setError(extractErrorMessage(err, 'No se pudieron cargar los trabajadores disponibles'))
    } finally {
      setLoadingWorkers(false)
    }
  }

  // Cierra el dialogo de reserva sin confirmar nada.
  const closeBookingModal = () => {
    setPendingBooking(null)
    setNotes('')
    setAvailableWorkers([])
  }

  const bookingModalRef = useModalA11y<HTMLFormElement>(pendingBooking !== null, closeBookingModal)
  const conflictModalRef = useModalA11y<HTMLDivElement>(conflictMessage !== null, () => setConflictMessage(null))

  // Envia la reserva de los servicios seleccionados para la fecha y hora elegidas, y refresca los datos.
  const confirmBooking = async () => {
    if (!pendingBooking || !pendingBooking.workerId) return
    const { date, time, workerId } = pendingBooking
    const key = `${date}-${time}`
    setPendingKey(key)
    setError(null)
    try {
      await client.post('/appointments', {
        treatmentIds: selectedIds,
        date,
        startTime: time,
        notes: notes.trim() || null,
        workerId,
      })
      setPendingBooking(null)
      setNotes('')
      setSelectedIds([])
      await loadAll()
    } catch (err) {
      if (isConflict(err)) {
        setPendingBooking(null)
        setNotes('')
        setConflictMessage('Vaya, justo se te ha adelantado otra persona: ya hay una cita reservada en esa fecha y hora. Elige otra hora libre en el calendario.')
      } else {
        setError(extractErrorMessage(err, 'No se pudo reservar esa hora'))
      }
      await loadAll()
    } finally {
      setPendingKey(null)
    }
  }

  // Cancela una cita propia y refresca los datos de la semana.
  const handleCancel = async (appointmentId: number) => {
    const key = `cancel-${appointmentId}`
    setPendingKey(key)
    setError(null)
    try {
      await client.post(`/appointments/${appointmentId}/cancel`)
      await loadAll()
    } catch (err) {
      setError(extractErrorMessage(err, 'No se pudo cancelar la cita'))
      await loadAll()
    } finally {
      setPendingKey(null)
    }
  }

  const appointmentsByDate = useMemo(() => {
    const map = new Map<string, Appointment[]>()
    for (const appt of ownAppointments) {
      const list = map.get(appt.date) ?? []
      list.push(appt)
      map.set(appt.date, list)
    }
    return map
  }, [ownAppointments])

  const adminAppointmentsByDate = useMemo(() => {
    const map = new Map<string, AdminAppointment[]>()
    for (const appt of adminAppointments) {
      const list = map.get(appt.date) ?? []
      list.push(appt)
      map.set(appt.date, list)
    }
    return map
  }, [adminAppointments])

  const adminBlocksByDate = useMemo(() => {
    const map = new Map<string, ScheduleBlock[]>()
    for (const block of adminBlocks) {
      const list = map.get(block.date) ?? []
      list.push(block)
      map.set(block.date, list)
    }
    return map
  }, [adminBlocks])

  const availabilityByDate = useMemo(() => {
    const map = new Map<string, string[]>()
    for (const day of availability) {
      map.set(day.date, day.times)
    }
    return map
  }, [availability])

  const occupiedByDate = useMemo(() => {
    const map = new Map<string, OccupiedInterval[]>()
    for (const day of occupiedDays) {
      map.set(day.date, day.occupied)
    }
    return map
  }, [occupiedDays])

  const blockedByDate = useMemo(() => {
    const map = new Map<string, BlockedInterval[]>()
    for (const day of occupiedDays) {
      map.set(day.date, day.blocked)
    }
    return map
  }, [occupiedDays])

  const hasAnyAvailabilityThisWeek = visibleDays.some(
    (date) => (availabilityByDate.get(toIsoDate(date)) ?? []).length > 0,
  )
  const showNoAvailabilityHint =
    !loading && hoursLoaded && selectedIds.length > 0 && visibleDays.length > 0 && !hasAnyAvailabilityThisWeek

  return (
    <div>
      <div className="schedule-tab">
      <h1>Reserva tu hora</h1>
      <p className="hint-text">
        Elige uno o varios servicios (por ejemplo, corte y peinado juntos) y luego una hora disponible.
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
          {combinedName} &middot; {segmentsTotalMinutes(combinedSegments)} min &middot; total{' '}
          <strong>{formatPrice(combinedPrice)}</strong>
        </p>
      )}
      </div>
      <WeekNav
        label={label}
        onPrev={goPrev}
        onNext={goNext}
        onToday={showToday ? goToday : undefined}
      />

      {error && !pendingBooking && <p className="error-banner">{error}</p>}
      {showNoAvailabilityHint && (
        <p className="empty-state-banner">
          No hay horas disponibles esta semana para {combinedName}. Prueba con otra semana o elige otro servicio.
        </p>
      )}
      {loading || !hoursLoaded ? (
        <WeekGridSkeleton />
      ) : visibleDays.length === 0 ? (
        <p className="hint-text">No hay dias disponibles en este rango.</p>
      ) : (
        <div className="week-grid">
        <TimeRuler range={range} />
        <div className="days-scroll">
          {visibleDays.map((date) => {
            const iso = toIsoDate(date)
            const past = isPastDay(date)
            const times = availabilityByDate.get(iso) ?? []

            let items: TimelineItem[]
            if (isSuperAdmin) {
              const dayAdminAppointments = adminAppointmentsByDate.get(iso) ?? []
              const dayAdminBlocks = adminBlocksByDate.get(iso) ?? []
              items = [
                ...dayAdminAppointments.map((appt) =>
                  toAdminTimelineItem(appt, user?.email, handleCancel, pendingKey, isPastDateTime(iso, appt.endTime)),
                ),
                ...dayAdminBlocks.map((block) => toAdminBlockedTimelineItem(block)),
              ]
            } else {
              const dayAppointments = appointmentsByDate.get(iso) ?? []
              const ownRanges = dayAppointments.map((appt) => ({
                start: parseTimeToMinutes(appt.startTime),
                end: parseTimeToMinutes(appt.endTime),
              }))
              const occupied = (occupiedByDate.get(iso) ?? []).filter((interval) => {
                const start = parseTimeToMinutes(interval.startTime)
                const end = parseTimeToMinutes(interval.endTime)
                return !ownRanges.some((r) => start >= r.start && end <= r.end)
              })
              const blocked = blockedByDate.get(iso) ?? []
              items = [
                ...dayAppointments.map((appt) =>
                  toTimelineItem(appt, handleCancel, pendingKey, isPastDateTime(iso, appt.endTime)),
                ),
                ...occupied.map((interval, index) => toOccupiedTimelineItem(interval, index)),
                ...blocked.map((interval, index) => toBlockedTimelineItem(interval, index)),
              ]
            }

            return (
              <div className={`day-column day-column--compact${past ? ' past' : ''}`} key={iso}>
                <h3>{formatDayLabel(date)}</h3>

                <DayTimeline
                  intervals={intervalsForDate(date, schedule)}
                  items={items}
                  range={range}
                  availableTimes={
                    !past && selectedIds.length > 0 && times.length > 0
                      ? times.map((time) => ({
                          time,
                          disabled: pendingKey === `${iso}-${time}`,
                          onClick: () => openBookingModal(iso, time),
                        }))
                      : undefined
                  }
                />
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
              confirmBooking()
            }}
          >
            <h2>Confirmar reserva</h2>
            <p className="hint-text">
              {combinedName} el {pendingBooking.date} a las {pendingBooking.time} &middot;{' '}
              {formatPrice(combinedPrice)}
            </p>

            <label htmlFor="booking-worker">¿Con quien quieres la cita?</label>
            {loadingWorkers ? (
              <p className="hint-text">Buscando trabajadores libres...</p>
            ) : availableWorkers.length === 0 ? (
              <p className="hint-text">No hay ningun trabajador libre a esa hora. Elige otra hora.</p>
            ) : (
              <Select
                id="booking-worker"
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

            <label htmlFor="booking-notes">
              Observaciones {requiresColorNote ? '(indica el color que quieres)' : '(opcional)'}
            </label>
            <textarea
              id="booking-notes"
              placeholder={
                requiresColorNote
                  ? 'Indica el color que quieres (obligatorio para tinte y mechas)'
                  : 'Cuentanos algo si quieres (alergias, preferencias...)'
              }
              value={notes}
              onChange={(e) => setNotes(e.target.value)}
              rows={4}
            />
            {error && <p className="error-text">{error}</p>}
            <div className="modal-actions">
              <button type="button" onClick={closeBookingModal}>
                Cancelar
              </button>
              <button
                type="submit"
                className="primary"
                disabled={
                  !pendingBooking.workerId ||
                  (requiresColorNote && !notes.trim()) ||
                  pendingKey === `${pendingBooking.date}-${pendingBooking.time}`
                }
              >
                Confirmar reserva
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
    </div>
  )
}

/** Suma la duracion total en minutos de todos los tramos de una seleccion de servicios. */
function segmentsTotalMinutes(segments: Segment[]): number {
  return segments.reduce((sum, s) => sum + s.minutes, 0)
}
