import type { SegmentType } from '../types'
import type { OpenInterval } from '../businessHours'
import { parseTimeToMinutes } from '../dateUtils'

export interface AvailableTime {
  time: string
  disabled?: boolean
  onClick: () => void
}

export interface TimelineSegment {
  type: SegmentType
  startMinutes: number
  endMinutes: number
}

export interface TimelineItem {
  key: string
  startMinutes: number
  endMinutes: number
  segments: TimelineSegment[]
  title: string
  /** Linea siempre visible justo bajo el titulo (normalmente el trabajador), con su punto de color si lo hay. */
  meta?: string
  subtitle?: string
  variant: 'mine' | 'booked' | 'block' | 'occupied'
  color?: string
  action?: { label: string; onClick: () => void; disabled?: boolean }
  /** Segundo boton opcional junto al primero (ej. citas pasadas: "Marcar hecha" + "No presentado"). */
  secondaryAction?: { label: string; onClick: () => void; disabled?: boolean }
  onClick?: () => void
}

export const PX_PER_MIN = 2.2
const MIN_BLOCK_HEIGHT = 26
const MAX_ITEM_WIDTH = 100

/** Reparte los elementos que se solapan en el tiempo en "carriles" horizontales para poder dibujarlos uno junto a otro. */
function assignLanes(items: TimelineItem[]): { laneOf: Map<string, number>; totalLanes: number } {
  const sorted = [...items].sort((a, b) => a.startMinutes - b.startMinutes)
  const laneEnds: number[] = []
  const laneOf = new Map<string, number>()
  for (const item of sorted) {
    let lane = laneEnds.findIndex((end) => end <= item.startMinutes)
    if (lane === -1) {
      lane = laneEnds.length
      laneEnds.push(item.endMinutes)
    } else {
      laneEnds[lane] = item.endMinutes
    }
    laneOf.set(item.key, lane)
  }
  return { laneOf, totalLanes: laneEnds.length || 1 }
}

/**
 * Dibuja la agenda visual de un dia (citas, bloqueos, huecos ocupados) como
 * bloques posicionados de forma absoluta sobre un rango de horas comun
 * (`range`, compartido con la regla de horas y el resto de dias de la
 * semana), en vez de apilar cada tramo abierto por separado. Esto permite
 * que dias con horarios distintos entre si se sigan alineando visualmente.
 * Si se pasan `availableTimes`, se dibuja ademas una columna de botones a la
 * izquierda, cada uno a la altura real de su hora, para reservar esa hora.
 */
export default function DayTimeline({
  intervals,
  items,
  range,
  availableTimes,
}: {
  intervals: OpenInterval[]
  items: TimelineItem[]
  range: { start: number; end: number }
  availableTimes?: AvailableTime[]
}) {
  if (intervals.length === 0) {
    return <p className="hint-text">Cerrado</p>
  }

  const { laneOf, totalLanes } = assignLanes(items)
  const totalHeight = (range.end - range.start) * PX_PER_MIN

  return (
    <div className="day-timeline-row">
      {availableTimes && (
        <div className="times-dots" style={{ height: totalHeight }}>
          {availableTimes.map((t) => (
            <button
              key={t.time}
              className="time-dot"
              style={{ top: (parseTimeToMinutes(t.time) - range.start) * PX_PER_MIN }}
              disabled={t.disabled}
              onClick={t.onClick}
            >
              {t.time}
            </button>
          ))}
        </div>
      )}
      <div className="day-timeline" style={{ height: totalHeight }}>
      {intervals.map((interval) => {
        const zoneItems = items.filter(
          (item) => item.startMinutes < interval.endMinutes && item.endMinutes > interval.startMinutes,
        )
        const top = (interval.startMinutes - range.start) * PX_PER_MIN
        const height = (interval.endMinutes - interval.startMinutes) * PX_PER_MIN

        return (
          <div className="timeline-open-zone" key={interval.label} style={{ top, height }}>
            <div className="timeline-interval-label">{interval.label}</div>
            {zoneItems.length === 0 && <div className="timeline-empty">Libre</div>}
          </div>
        )
      })}

      {items.map((item) => {
        const top = (item.startMinutes - range.start) * PX_PER_MIN
        const height = Math.max((item.endMinutes - item.startMinutes) * PX_PER_MIN, MIN_BLOCK_HEIGHT)
        const lane = laneOf.get(item.key) ?? 0
        const laneWidth = 100 / totalLanes

        return (
          <div
            className={`timeline-item timeline-item--${item.variant}${item.onClick ? ' timeline-item--clickable' : ''}${item.color ? ' timeline-item--colored' : ''}`}
            key={item.key}
            style={{
              top,
              height,
              left: `calc(${lane * laneWidth}% + 3px)`,
              width: `calc(${laneWidth}% - 6px)`,
              maxWidth: MAX_ITEM_WIDTH,
              borderLeftColor: item.color,
            }}
            onClick={item.onClick}
          >
            <div className="timeline-item-segments">
              {item.segments.map((segment, i) => {
                const segTop = (segment.startMinutes - item.startMinutes) * PX_PER_MIN
                const segHeight = (segment.endMinutes - segment.startMinutes) * PX_PER_MIN
                return (
                  <div
                    key={i}
                    className={`timeline-segment timeline-segment--${segment.type.toLowerCase()}`}
                    style={{ top: segTop, height: segHeight }}
                  />
                )
              })}
            </div>
            {item.color && <div className="timeline-item-color-wash" style={{ backgroundColor: item.color }} />}
            <div className="timeline-item-content">
              <span className="timeline-item-title">{item.title}</span>
              {item.meta && (
                <span className="timeline-item-subtitle timeline-item-meta">
                  {item.color && <span className="timeline-item-dot" style={{ backgroundColor: item.color }} />}
                  {item.meta}
                </span>
              )}
              {item.subtitle && <span className="timeline-item-subtitle">{item.subtitle}</span>}
              {(item.action || item.secondaryAction) && (
                <div className="timeline-item-actions">
                  {item.action && (
                    <button
                      disabled={item.action.disabled}
                      onClick={(e) => {
                        e.stopPropagation()
                        item.action!.onClick()
                      }}
                    >
                      {item.action.label}
                    </button>
                  )}
                  {item.secondaryAction && (
                    <button
                      disabled={item.secondaryAction.disabled}
                      onClick={(e) => {
                        e.stopPropagation()
                        item.secondaryAction!.onClick()
                      }}
                    >
                      {item.secondaryAction.label}
                    </button>
                  )}
                </div>
              )}
            </div>
          </div>
        )
      })}
      </div>
    </div>
  )
}
