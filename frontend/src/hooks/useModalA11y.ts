import { useEffect, useRef } from 'react'

const FOCUSABLE_SELECTOR =
  'a[href], button:not([disabled]), textarea:not([disabled]), input:not([disabled]), select:not([disabled]), [tabindex]:not([tabindex="-1"])'

/**
 * Accesibilidad de teclado para modales: cierra con Escape, atrapa el foco (Tab/Shift+Tab) dentro
 * de la caja del modal, enfoca su primer elemento al abrir y devuelve el foco a quien lo abrio al
 * cerrarse. Se aplica al elemento raiz del modal (normalmente `.modal-box`).
 *
 * `active` distingue modales montados/desmontados por su padre (pasar `true`: el efecto corre una
 * vez, al montar) de modales renderizados condicionalmente dentro de un componente que sigue vivo
 * (pasar la condicion, p.ej. `pendingBooking !== null`: el efecto corre cuando pasa a `true`/`false`).
 */
export function useModalA11y<T extends HTMLElement>(active: boolean, onClose: () => void) {
  const ref = useRef<T>(null)
  // En un ref (no en las dependencias del efecto) para que reabrir/cerrar sea lo unico que
  // reejecute el efecto, no un re-render con una `onClose` recien creada (p.ej. al escribir en un
  // campo de texto del modal), que robaria el foco de vuelta al primer elemento en cada tecla.
  const onCloseRef = useRef(onClose)
  onCloseRef.current = onClose

  useEffect(() => {
    if (!active) return

    const previouslyFocused = document.activeElement as HTMLElement | null
    const box = ref.current
    const focusables = box ? Array.from(box.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR)) : []
    focusables[0]?.focus()

    function handleKeyDown(e: KeyboardEvent) {
      if (e.key === 'Escape') {
        onCloseRef.current()
        return
      }
      if (e.key !== 'Tab' || !box) return
      const items = Array.from(box.querySelectorAll<HTMLElement>(FOCUSABLE_SELECTOR))
      if (items.length === 0) return
      const first = items[0]
      const last = items[items.length - 1]
      if (e.shiftKey && document.activeElement === first) {
        e.preventDefault()
        last.focus()
      } else if (!e.shiftKey && document.activeElement === last) {
        e.preventDefault()
        first.focus()
      }
    }

    document.addEventListener('keydown', handleKeyDown)
    return () => {
      document.removeEventListener('keydown', handleKeyDown)
      previouslyFocused?.focus()
    }
  }, [active])

  return ref
}
