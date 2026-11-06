import { useState } from 'react'
import { useModalA11y } from '../hooks/useModalA11y'

interface HelpStep {
  image: string
  title: string
  text: string
}

const STEPS: HelpStep[] = [
  {
    image: '/help/01-login.jpg',
    title: '1. Inicia sesion',
    text: 'Entra con tu email y contrasena, o con tu cuenta de Google.',
  },
  {
    image: '/help/02-servicios.png',
    title: '2. Elige el servicio',
    text: 'Marca uno o varios servicios a la vez (por ejemplo, corte y peinado juntos). El calendario de la semana aparece debajo.',
  },
  {
    image: '/help/03-elegir-hora.png',
    title: '3. Elige una hora libre',
    text: 'En cuanto eliges un servicio, aparecen puntos con las horas disponibles junto a cada dia. Pulsa la hora que prefieras.',
  },
  {
    image: '/help/04-confirmar.png',
    title: '4. Confirma la reserva',
    text: 'Elige con que trabajador quieres la cita y anade observaciones si quieres (obligatorio indicar el color si el servicio es tinte o mechas). Pulsa "Confirmar reserva".',
  },
  {
    image: '/help/05-reservada.png',
    title: '5. Tu cita ya esta reservada',
    text: 'Aparece en el calendario con el color del trabajador que la atendera. Las citas de otros clientes se ven como "Ocupado", sin datos personales.',
  },
  {
    image: '/help/06-cancelar-boton.png',
    title: '6. Cancelar una cita',
    text: 'Dentro de tu propia cita hay un boton "Cancelar". Solo puedes cancelar citas que todavia no hayan pasado.',
  },
]

/** Modal de ayuda para clientes: mini tutorial paginado con capturas de la app explicando como reservar y cancelar una cita. */
export default function HelpModal({ onClose }: { onClose: () => void }) {
  const [index, setIndex] = useState(0)
  const step = STEPS[index]
  const isFirst = index === 0
  const isLast = index === STEPS.length - 1
  const modalRef = useModalA11y<HTMLDivElement>(true, onClose)

  return (
    <div className="modal-overlay" onClick={onClose}>
      <div
        className="modal-box help-modal-box"
        role="dialog"
        aria-modal="true"
        ref={modalRef}
        onClick={(e) => e.stopPropagation()}
      >
        <h2>Como funciona la reserva de citas</h2>
        <div className="help-page">
          <img src={step.image} alt={step.title} className="help-page-image" />
          <div className="help-page-nav">
            <button
              className="help-page-arrow"
              onClick={() => setIndex((i) => i - 1)}
              disabled={isFirst}
              aria-label="Paso anterior"
            >
              ←
            </button>
            <span className="help-page-indicator">
              {index + 1} / {STEPS.length}
            </span>
            <button
              className="help-page-arrow"
              onClick={() => setIndex((i) => i + 1)}
              disabled={isLast}
              aria-label="Siguiente paso"
            >
              →
            </button>
          </div>
          <h3>{step.title}</h3>
          <p>{step.text}</p>
        </div>
        <div className="modal-actions">
          <button className="primary" onClick={onClose}>
            Entendido
          </button>
        </div>
      </div>
    </div>
  )
}
