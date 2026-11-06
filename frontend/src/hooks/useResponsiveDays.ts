import { useEffect, useState } from 'react'
import { intervalsForDate, takeOpenDaysBackward, takeOpenDaysForward, type OpenInterval } from '../businessHours'
import { addDays, formatDayLabel, formatWeekRange, getMonday, toIsoDate } from '../dateUtils'

const MOBILE_QUERY = '(max-width: 480px)'
const SMALL_TABLET_QUERY = '(max-width: 760px)'
const TABLET_QUERY = '(max-width: 1024px)'

/** Cuantos dias mostrar a la vez segun el ancho de pantalla: 1 en movil, 2 en tablet pequeña, 3 en tablet, 7 (semana) en escritorio. */
function getDaysPerView(): number {
  if (typeof window === 'undefined') return 7
  if (window.matchMedia(MOBILE_QUERY).matches) return 1
  if (window.matchMedia(SMALL_TABLET_QUERY).matches) return 2
  if (window.matchMedia(TABLET_QUERY).matches) return 3
  return 7
}

function startingAnchor(daysPerView: number): Date {
  return daysPerView === 7 ? getMonday(new Date()) : new Date()
}

/**
 * Calcula que dias mostrar en la agenda semanal segun el tamaño de pantalla (7 en
 * escritorio, 3 en tablet, 2 en tablet pequeña, 1 en movil), ocultando los dias sin
 * horario de apertura y exponiendo la navegacion (anterior/siguiente/hoy) adaptada
 * a cada modo.
 */
export function useResponsiveDays(schedule: Map<number, OpenInterval[]>) {
  const [daysPerView, setDaysPerView] = useState(getDaysPerView)
  const [anchor, setAnchor] = useState(() => startingAnchor(getDaysPerView()))

  useEffect(() => {
    const queries = [window.matchMedia(MOBILE_QUERY), window.matchMedia(SMALL_TABLET_QUERY), window.matchMedia(TABLET_QUERY)]
    const handleChange = () => {
      const next = getDaysPerView()
      setDaysPerView((prev) => {
        if (prev === next) return prev
        setAnchor(startingAnchor(next))
        return next
      })
    }
    queries.forEach((q) => q.addEventListener('change', handleChange))
    return () => queries.forEach((q) => q.removeEventListener('change', handleChange))
  }, [])

  const visibleDays =
    daysPerView === 7
      ? Array.from({ length: 7 }, (_, i) => addDays(getMonday(anchor), i)).filter(
          (d) => intervalsForDate(d, schedule).length > 0,
        )
      : takeOpenDaysForward(anchor, daysPerView, schedule)

  const goPrev = () => {
    if (daysPerView === 7) {
      setAnchor((a) => addDays(a, -7))
      return
    }
    const first = visibleDays[0] ?? anchor
    const prevDays = takeOpenDaysBackward(first, daysPerView, schedule)
    setAnchor(prevDays[0] ?? addDays(first, -daysPerView))
  }

  const goNext = () => {
    if (daysPerView === 7) {
      setAnchor((a) => addDays(a, 7))
      return
    }
    const last = visibleDays[visibleDays.length - 1] ?? anchor
    setAnchor(addDays(last, 1))
  }

  const goToday = () => setAnchor(startingAnchor(daysPerView))

  const todayIso = toIsoDate(new Date())
  const showToday = !visibleDays.some((d) => toIsoDate(d) === todayIso)

  const label =
    visibleDays.length === 0
      ? '-'
      : daysPerView === 1
        ? formatDayLabel(visibleDays[0])
        : formatWeekRange(visibleDays[0], visibleDays[visibleDays.length - 1])

  return { visibleDays, daysPerView, goPrev, goNext, goToday, showToday, label }
}
