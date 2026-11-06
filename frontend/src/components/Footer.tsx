import { useState } from 'react'
import { Link } from 'react-router-dom'
import AttributionsModal from './AttributionsModal'

/** Pie de pagina visible en toda la app (dentro y fuera de sesion): copyright, enlaces legales y
 * el acceso al modal con los iconos y sus atribuciones (ver AttributionsModal). */
export default function Footer() {
  const [showAttributions, setShowAttributions] = useState(false)

  return (
    <footer className="app-footer">
      <div className="app-footer-row">
        <span>© {new Date().getFullYear()} Castor Barber Shop</span>
        <nav className="app-footer-links">
          <Link to="/privacidad">Política de privacidad</Link>
          <Link to="/terminos">Términos y condiciones</Link>
          <button type="button" className="link-button-inline" onClick={() => setShowAttributions(true)}>
            Atribuciones
          </button>
        </nav>
      </div>
      {showAttributions && <AttributionsModal onClose={() => setShowAttributions(false)} />}
    </footer>
  )
}
