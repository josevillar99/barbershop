import { PX_PER_MIN } from './DayTimeline'

interface Tick {
  label: string
  top: number
}

interface MinorTick {
  top: number
  size: 'half' | 'quarter'
}

// Calcula las horas en punto dentro del rango y su posicion vertical en pixeles.
function ticksForRange(range: { start: number; end: number }): Tick[] {
  const ticks: Tick[] = []
  const firstHour = Math.ceil(range.start / 60)
  const lastHour = Math.floor(range.end / 60)
  for (let hour = firstHour; hour <= lastHour; hour++) {
    const minutes = hour * 60
    ticks.push({ label: `${hour}:00`, top: (minutes - range.start) * PX_PER_MIN })
  }
  return ticks
}

// Calcula las marcas de los cuartos de hora (y15, y30, y45) dentro del rango, sin las horas en punto.
function minorTicksForRange(range: { start: number; end: number }): MinorTick[] {
  const ticks: MinorTick[] = []
  const first = Math.ceil(range.start / 15) * 15
  for (let minutes = first; minutes < range.end; minutes += 15) {
    if (minutes % 60 === 0) continue
    ticks.push({
      top: (minutes - range.start) * PX_PER_MIN,
      size: minutes % 30 === 0 ? 'half' : 'quarter',
    })
  }
  return ticks
}

/**
 * Columna fija a la izquierda de la semana con las horas en punto, sobre un
 * rango continuo compartido con DayTimeline (`range`), para poder leer de un
 * vistazo que hora corresponde a cada bloque en cualquier dia de la semana,
 * aunque cada dia tenga su propio horario. Ademas de las horas, marca los
 * cuartos de hora con un guion (mas grande en las y media que en y15/y45).
 */
export default function TimeRuler({ range }: { range: { start: number; end: number } }) {
  const totalHeight = (range.end - range.start) * PX_PER_MIN

  return (
    <div className="time-ruler">
      <h3>&nbsp;</h3>
      <div className="time-ruler-track" style={{ height: totalHeight }}>
        {ticksForRange(range).map((tick) => (
          <div className="time-ruler-tick" key={tick.label} style={{ top: tick.top }}>
            <span className="time-ruler-tick-label">{tick.label}</span>
            <span className="time-ruler-tick-line" />
          </div>
        ))}
        {minorTicksForRange(range).map((tick) => (
          <span
            key={tick.top}
            className={`time-ruler-minor-tick time-ruler-minor-tick--${tick.size}`}
            style={{ top: tick.top }}
          />
        ))}
      </div>
    </div>
  )
}
