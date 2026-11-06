import { addDays, parseTimeToMinutes } from './dateUtils'

export interface OpenInterval {
  label: string
  startMinutes: number
  endMinutes: number
}

export interface OpeningHour {
  dayOfWeek: number // 1=lunes .. 7=domingo (ISO, tal y como lo devuelve el backend)
  startTime: string
  endTime: string
}

// Horario de respaldo mientras se carga el real, para que la pantalla no quede vacia un instante.
const FALLBACK_RANGE = { start: 9 * 60, end: 20 * 60 }

/** Agrupa los tramos planos que devuelve el backend en un mapa por dia de la semana (formato JS: 0=domingo .. 6=sabado). */
export function buildWeekSchedule(hours: OpeningHour[]): Map<number, OpenInterval[]> {
  const schedule = new Map<number, OpenInterval[]>()
  for (const h of hours) {
    const jsDay = h.dayOfWeek % 7 // ISO 7 (domingo) -> JS 0; el resto coincide
    const interval: OpenInterval = {
      label: `${h.startTime} - ${h.endTime}`,
      startMinutes: parseTimeToMinutes(h.startTime),
      endMinutes: parseTimeToMinutes(h.endTime),
    }
    const list = schedule.get(jsDay) ?? []
    list.push(interval)
    schedule.set(jsDay, list)
  }
  for (const list of schedule.values()) {
    list.sort((a, b) => a.startMinutes - b.startMinutes)
  }
  return schedule
}

/** Devuelve los tramos de horario de apertura de esa fecha segun el horario configurado (vacio si esta cerrado). */
export function intervalsForDate(date: Date, schedule: Map<number, OpenInterval[]>): OpenInterval[] {
  return schedule.get(date.getDay()) ?? []
}

/**
 * Rango continuo (en minutos del dia) que cubre todos los tramos configurados
 * en la semana, usado como escala compartida por la regla de horas y por el
 * calendario de cada dia para que todos se alineen aunque cada dia tenga
 * tramos distintos.
 */
export function scheduleRange(schedule: Map<number, OpenInterval[]>): { start: number; end: number } {
  let start = Infinity
  let end = -Infinity
  for (const intervals of schedule.values()) {
    for (const interval of intervals) {
      start = Math.min(start, interval.startMinutes)
      end = Math.max(end, interval.endMinutes)
    }
  }
  return Number.isFinite(start) && Number.isFinite(end) ? { start, end } : FALLBACK_RANGE
}

// Tope de dias a recorrer buscando dias abiertos, para no colgar el navegador si el horario
// configurado dejara la semana entera (o mas) cerrada.
const MAX_SCAN_DAYS = 60

/** Recorre hacia adelante desde `start` (incluido) y devuelve los primeros `count` dias con horario de apertura. */
export function takeOpenDaysForward(start: Date, count: number, schedule: Map<number, OpenInterval[]>): Date[] {
  const days: Date[] = []
  let cursor = start
  for (let i = 0; i < MAX_SCAN_DAYS && days.length < count; i++) {
    if (intervalsForDate(cursor, schedule).length > 0) {
      days.push(cursor)
    }
    cursor = addDays(cursor, 1)
  }
  return days
}

/** Recorre hacia atras desde el dia anterior a `beforeDate` y devuelve los `count` dias abiertos mas cercanos, en orden ascendente. */
export function takeOpenDaysBackward(beforeDate: Date, count: number, schedule: Map<number, OpenInterval[]>): Date[] {
  const days: Date[] = []
  let cursor = addDays(beforeDate, -1)
  for (let i = 0; i < MAX_SCAN_DAYS && days.length < count; i++) {
    if (intervalsForDate(cursor, schedule).length > 0) {
      days.unshift(cursor)
    }
    cursor = addDays(cursor, -1)
  }
  return days
}
