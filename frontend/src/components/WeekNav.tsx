interface WeekNavProps {
  label: string
  onPrev: () => void
  onNext: () => void
  onToday?: () => void
}

/** Barra de navegacion del calendario: muestra el rango o dia actual y botones para retroceder, avanzar o volver a hoy. */
export default function WeekNav({ label, onPrev, onNext, onToday }: WeekNavProps) {
  return (
    <div className="week-nav">
      <button onClick={onPrev}>&larr; Anterior</button>
      <span className="week-nav-label">{label}</span>
      {onToday && (
        <button onClick={onToday}>Hoy</button>
      )}
      <button onClick={onNext}>Siguiente &rarr;</button>
    </div>
  )
}
