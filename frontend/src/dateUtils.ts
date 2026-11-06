/** Convierte una fecha a formato ISO (AAAA-MM-DD) usando la hora local. */
export function toIsoDate(date: Date): string {
  const year = date.getFullYear()
  const month = `${date.getMonth() + 1}`.padStart(2, '0')
  const day = `${date.getDate()}`.padStart(2, '0')
  return `${year}-${month}-${day}`
}

/** Suma (o resta) un numero de dias a una fecha, devolviendo una fecha nueva. */
export function addDays(date: Date, amount: number): Date {
  const d = new Date(date.getFullYear(), date.getMonth(), date.getDate())
  d.setDate(d.getDate() + amount)
  return d
}

/** Calcula el lunes de la semana a la que pertenece la fecha dada. */
export function getMonday(date: Date): Date {
  const d = new Date(date.getFullYear(), date.getMonth(), date.getDate())
  const day = d.getDay()
  const diff = day === 0 ? -6 : 1 - day
  d.setDate(d.getDate() + diff)
  return d
}

/** Indica si la fecha dada es anterior al dia de hoy. */
export function isPastDay(date: Date): boolean {
  const today = new Date()
  const todayStart = new Date(today.getFullYear(), today.getMonth(), today.getDate())
  return date.getTime() < todayStart.getTime()
}

/** Indica si una fecha+hora concreta (ej. el fin de una cita) ya ha pasado, a diferencia de isPastDay que solo mira el dia entero. */
export function isPastDateTime(dateIso: string, timeHHmm: string): boolean {
  return new Date(`${dateIso}T${timeHHmm}:00`).getTime() < Date.now()
}

/** Formatea una fecha como etiqueta legible en espanol (ej. "lunes, 24"). */
export function formatDayLabel(date: Date): string {
  return date.toLocaleDateString('es-ES', { weekday: 'long', day: 'numeric' })
}

/** Formatea el rango de una semana (fecha inicio - fecha fin) para mostrarlo en el selector de semana. */
export function formatWeekRange(start: Date, end: Date): string {
  const startLabel = start.toLocaleDateString('es-ES', { day: 'numeric', month: 'short' })
  const endLabel = end.toLocaleDateString('es-ES', { day: 'numeric', month: 'short', year: 'numeric' })
  return `${startLabel} - ${endLabel}`
}

/** Convierte una hora en formato "HH:MM" al numero de minutos transcurridos desde medianoche. */
export function parseTimeToMinutes(time: string): number {
  const [hours, minutes] = time.split(':').map(Number)
  return hours * 60 + minutes
}
