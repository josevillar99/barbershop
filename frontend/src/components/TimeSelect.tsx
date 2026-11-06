import Select from './Select'

/** Genera las horas del dia en pasos fijos (ej. "00:00", "00:15", "00:30"...) para el desplegable de hora. */
function generateTimes(stepMinutes: number): string[] {
  const times: string[] = []
  for (let m = 0; m < 24 * 60; m += stepMinutes) {
    times.push(`${String(Math.floor(m / 60)).padStart(2, '0')}:${String(m % 60).padStart(2, '0')}`)
  }
  return times
}

const TIMES = generateTimes(15)

interface TimeSelectProps {
  value: string
  onChange: (value: string) => void
  id?: string
}

/** Desplegable de horas del dia en pasos de 15 minutos, con el mismo estilo que el resto de selects. */
export default function TimeSelect({ value, onChange, id }: TimeSelectProps) {
  return (
    <Select id={id} value={value} onChange={(e) => onChange(e.target.value)}>
      {TIMES.map((t) => (
        <option key={t} value={t}>
          {t}
        </option>
      ))}
    </Select>
  )
}
