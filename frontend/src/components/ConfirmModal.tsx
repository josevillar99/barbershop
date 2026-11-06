import { useModalA11y } from '../hooks/useModalA11y'

interface ConfirmModalProps {
  title: string
  message: string
  confirmLabel?: string
  cancelLabel?: string
  danger?: boolean
  pending?: boolean
  onConfirm: () => void
  onCancel: () => void
}

/** Modal de confirmacion con el estilo propio de la app, para reemplazar los window.confirm() del navegador. */
export default function ConfirmModal({
  title,
  message,
  confirmLabel = 'Confirmar',
  cancelLabel = 'Cancelar',
  danger = false,
  pending = false,
  onConfirm,
  onCancel,
}: ConfirmModalProps) {
  const modalRef = useModalA11y<HTMLDivElement>(true, onCancel)
  return (
    <div className="modal-overlay" onClick={onCancel}>
      <div className="modal-box" role="dialog" aria-modal="true" ref={modalRef} onClick={(e) => e.stopPropagation()}>
        <h2>{title}</h2>
        <p className="hint-text">{message}</p>
        <div className="modal-actions">
          <button onClick={onCancel} disabled={pending}>
            {cancelLabel}
          </button>
          <button className={danger ? 'danger' : 'primary'} onClick={onConfirm} disabled={pending}>
            {confirmLabel}
          </button>
        </div>
      </div>
    </div>
  )
}
