import { useEffect, useState } from 'react'
import { useModalA11y } from '../hooks/useModalA11y'

// Iconos propios de la app (ver frontend/public/branding). Si se anade un icono nuevo con su
// propia atribucion, basta con anadir su ruta aqui para que aparezca en el modal.
const APP_ICONS = ['/branding/main-icon.png', '/branding/navbar-icon.png', '/branding/informacion.png']

/**
 * Modal con los iconos de la app y su atribucion (autor/licencia), para no ocupar espacio fijo
 * en el pie de pagina. El texto de atribucion se lee en tiempo real de
 * /branding/atribuciones.txt (formato Flaticon: una etiqueta <a> por icono): basta con anadir
 * lineas a ese fichero para que aparezcan aqui, sin tocar codigo.
 */
export default function AttributionsModal({ onClose }: { onClose: () => void }) {
  const [lines, setLines] = useState<string[] | null>(null)
  const modalRef = useModalA11y<HTMLDivElement>(true, onClose)

  useEffect(() => {
    let cancelled = false
    fetch('/branding/atribuciones.txt')
      .then((res) => (res.ok ? res.text() : ''))
      .then((text) => {
        if (cancelled) return
        setLines(
          text
            .split(/\r?\n/)
            .map((line) => line.trim())
            .filter(Boolean),
        )
      })
      .catch(() => {
        if (!cancelled) setLines([])
      })
    return () => {
      cancelled = true
    }
  }, [])

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div
        className="modal-box attributions-modal-box"
        role="dialog"
        aria-modal="true"
        ref={modalRef}
        onClick={(e) => e.stopPropagation()}
      >
        <h2>Iconos y atribuciones</h2>
        <div className="attributions-icons">
          {APP_ICONS.map((src) => (
            <img key={src} src={src} alt="" className="attributions-icon" />
          ))}
        </div>
        {lines === null ? (
          <p className="hint-text">Cargando...</p>
        ) : lines.length === 0 ? (
          <p className="hint-text">Todavía no hay atribuciones registradas.</p>
        ) : (
          <ul className="attributions-list">
            {lines.map((line, i) => (
              // eslint-disable-next-line react/no-danger
              <li key={i} dangerouslySetInnerHTML={{ __html: line }} />
            ))}
          </ul>
        )}
        <div className="modal-actions">
          <button className="primary" onClick={onClose}>
            Cerrar
          </button>
        </div>
      </div>
    </div>
  )
}
