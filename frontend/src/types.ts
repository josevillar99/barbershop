// Tipos relacionados con el usuario y su sesion.
export type Role = 'USER' | 'ADMIN' | 'SUPERADMIN'

export interface AuthUser {
  email: string
  name: string | null
  role: Role
  profileComplete: boolean
  termsAccepted: boolean
}

// Ficha de un usuario registrado, tal y como la ve el superadministrador en la lista de usuarios.
export interface AppUser {
  id: number
  email: string
  role: Role
  name: string | null
  phone: string | null
  profileComplete: boolean
}

// Tipos relacionados con los servicios que se pueden reservar y sus tramos de duracion.
export type SegmentType = 'WORK' | 'WAIT'

export interface Segment {
  type: SegmentType
  minutes: number
}

export interface Treatment {
  id: number
  name: string
  active: boolean
  price: number
  segments: Segment[]
  totalMinutes: number
  workerIds: number[]
}

// Tipos relacionados con los trabajadores: su horario semanal propio y los
// servicios que estan cualificados para realizar.
export interface WeeklyHourSlot {
  dayOfWeek: number
  startTime: string
  endTime: string
}

export interface Worker {
  id: number
  userId: number
  name: string
  email: string
  active: boolean
  color: string
  treatmentIds: number[]
  weeklyHours: WeeklyHourSlot[]
}

export interface AvailableWorker {
  id: number
  name: string
}

// Tipos relacionados con la disponibilidad de horas y la ocupacion de la agenda.
export interface OccupiedInterval {
  startTime: string
  endTime: string
  workerName: string | null
  workerColor: string | null
}

export interface AvailabilityDay {
  date: string
  times: string[]
}

export interface BlockedInterval {
  startTime: string
  endTime: string
  reason: string | null
}

export interface OccupiedDay {
  date: string
  occupied: OccupiedInterval[]
  blocked: BlockedInterval[]
}

// Tipos relacionados con las citas (las del propio usuario y las que ve el administrador) y los bloqueos manuales de horario.
export interface Appointment {
  id: number
  date: string
  startTime: string
  endTime: string
  treatmentName: string
  segments: Segment[]
  notes: string | null
  mine: boolean
  workerId: number | null
  workerName: string | null
  workerColor: string | null
}

export interface AppointmentHistoryItem {
  id: number
  date: string
  startTime: string
  endTime: string
  treatmentName: string
  notes: string | null
  workerName: string | null
  workerColor: string | null
  status: 'BOOKED' | 'CANCELLED'
  completed: boolean
}

export type PaymentMethod = 'EFECTIVO' | 'TARJETA' | 'OTRO'

export interface AdminAppointment {
  id: number
  date: string
  startTime: string
  endTime: string
  treatmentName: string
  segments: Segment[]
  notes: string | null
  clientName: string
  clientPhone: string
  bookedByEmail: string | null
  workerId: number | null
  workerName: string | null
  workerColor: string | null
  completed: boolean
  expectedPrice: number | null
  amountCharged: number | null
  paymentMethod: PaymentMethod | null
}

export interface ScheduleBlock {
  id: number
  date: string
  startTime: string
  endTime: string
  reason: string | null
  workerId: number | null
  workerName: string | null
}

// Tipos relacionados con el log de auditoria (panel de superadmin).
export type AuditAction =
  | 'SIGNUP'
  | 'SIGNUP_COMPLETE'
  | 'LOGIN'
  | 'LOGIN_GOOGLE'
  | 'PASSWORD_RESET_REQUEST'
  | 'PASSWORD_RESET_COMPLETE'
  | 'APPOINTMENT_BOOK'
  | 'APPOINTMENT_CANCEL'
  | 'ADMIN_APPOINTMENT_BOOK'
  | 'ADMIN_APPOINTMENT_CANCEL'
  | 'ADMIN_APPOINTMENT_COMPLETED_UPDATED'
  | 'ADMIN_BLOCK_CREATE'
  | 'ADMIN_BLOCK_DELETE'
  | 'ADMIN_BUSINESS_HOURS_UPDATE'
  | 'ADMIN_TREATMENT_CREATE'
  | 'ADMIN_TREATMENT_UPDATE'
  | 'PROFILE_UPDATE'
  | 'PASSWORD_CHANGED'
  | 'TERMS_ACCEPTED'
  | 'SUPERADMIN_MAKE_ADMIN'
  | 'SUPERADMIN_REMOVE_ADMIN'
  | 'SUPERADMIN_DELETE_USER'
  | 'WORKER_INVITED'
  | 'WORKER_PROMOTED'
  | 'WORKER_PROFILE_REMOVED'
  | 'WORKER_HOURS_UPDATED'
  | 'WORKER_COLOR_UPDATED'
  | 'TREATMENT_WORKERS_UPDATED'
  | 'ADMIN_CASH_CLOSING_SAVED'

export interface AuditLogEntry {
  id: number
  actorEmail: string
  action: AuditAction
  details: string | null
  createdAt: string
}

// Tipos relacionados con el panel de estadisticas (solo superadmin).
export interface StatsSummary {
  totalBooked: number
  completed: number
  completedEligible: number
  cancelled: number
  cancellationRatePercent: number
}

export interface WorkerStats {
  workerId: number
  workerName: string
  workerColor: string
  appointmentCount: number
  completedCount: number
}

export interface TreatmentStats {
  treatmentName: string
  appointmentCount: number
}

export interface StatsResponse {
  summary: StatsSummary
  byWorker: WorkerStats[]
  byTreatment: TreatmentStats[]
}

// Resumen de caja de un dia (pestana "Caja" del panel de administracion).
export interface CashSummaryView {
  date: string
  efectivoTotal: number
  tarjetaTotal: number
  otroTotal: number
  total: number
  appointmentCount: number
  appointments: AdminAppointment[]
  countedCash: number | null
  difference: number | null
}

export interface Page<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}
