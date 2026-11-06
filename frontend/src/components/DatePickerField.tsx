import { useEffect, useRef, useState } from 'react'
import { DayPicker } from 'react-day-picker'
import { es } from 'react-day-picker/locale'
import 'react-day-picker/style.css'
import { toIsoDate } from '../dateUtils'

interface DatePickerFieldProps {
  value: string
  onChange: (isoDate: string) => void
  id?: string
  minDate?: Date
}

/** Campo de fecha: input de solo lectura que abre un calendario (react-day-picker) para elegir el dia. */
export default function DatePickerField({ value, onChange, id, minDate }: DatePickerFieldProps) {
  const [open, setOpen] = useState(false)
  const containerRef = useRef<HTMLDivElement>(null)

  useEffect(() => {
    if (!open) return
    const handleClickOutside = (e: MouseEvent) => {
      if (containerRef.current && !containerRef.current.contains(e.target as Node)) {
        setOpen(false)
      }
    }
    document.addEventListener('mousedown', handleClickOutside)
    return () => document.removeEventListener('mousedown', handleClickOutside)
  }, [open])

  const selected = value ? new Date(`${value}T00:00:00`) : undefined

  return (
    <div className="date-picker-field" ref={containerRef}>
      <input
        id={id}
        type="text"
        readOnly
        className="date-picker-input"
        placeholder="Elige una fecha"
        value={selected ? selected.toLocaleDateString('es-ES', { day: '2-digit', month: '2-digit', year: 'numeric' }) : ''}
        onClick={() => setOpen((o) => !o)}
      />
      {open && (
        <div className="date-picker-popover">
          <DayPicker
            mode="single"
            locale={es}
            selected={selected}
            disabled={minDate ? { before: minDate } : undefined}
            onSelect={(date) => {
              if (date) {
                onChange(toIsoDate(date))
                setOpen(false)
              }
            }}
          />
        </div>
      )}
    </div>
  )
}
