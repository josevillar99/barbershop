import { Link } from 'react-router-dom'

/**
 * Terminos y condiciones de uso. Texto de partida generico, con huecos propios del negocio
 * marcados entre corchetes: hay que rellenarlos y, antes de publicar esto de verdad, que lo
 * revise alguien con conocimientos legales - esto no es asesoramiento legal.
 */
export default function TermsPage() {
  return (
    <div className="legal-page">
      <div className="legal-draft-banner">
        ⚠️ Texto de partida, no definitivo. Rellena los datos entre corchetes y pide que lo revise
        alguien con conocimientos legales antes de publicarlo.
      </div>

      <h1>Términos y condiciones de uso</h1>
      <p className="hint-text">Última actualización: [fecha]</p>

      <h2>1. Objeto</h2>
      <p>
        Estos términos regulan el uso de la aplicación de reservas online de Castor Barber Shop,
        titularidad de [nombre completo o razón social], con [NIF/CIF] (en adelante, "nosotros" o "el
        salón"). Al crear una cuenta aceptas estos términos y la <Link to="/privacidad">política de
        privacidad</Link>.
      </p>

      <h2>2. Registro y cuenta</h2>
      <p>
        Para reservar citas necesitas crear una cuenta con un email válido y una contraseña, o iniciar
        sesión con Google. Eres responsable de mantener tu contraseña en secreto y de la actividad que
        ocurra desde tu cuenta. Debes proporcionar datos de contacto (nombre, teléfono) veraces para que
        podamos gestionar tu cita.
      </p>

      <h2>3. Reservas y cancelaciones</h2>
      <p>
        Las citas se reservan según la disponibilidad mostrada en cada momento y pueden cancelarse desde
        la propia aplicación mientras no hayan pasado. Nos reservamos el derecho a cancelar o modificar
        una cita por causas de fuerza mayor, avisando con la mayor antelación posible.
      </p>
      <p>
        [Indica aquí, si aplica, la política de no-shows o cancelaciones tardías: por ejemplo, si tras un
        número de faltas sin avisar se puede restringir la reserva online.]
      </p>

      <h2>4. Precios</h2>
      <p>
        Los precios mostrados junto a cada servicio son orientativos y pueden variar; el precio final se
        confirma en el propio establecimiento.
      </p>

      <h2>5. Uso correcto de la aplicación</h2>
      <p>
        No está permitido usar la aplicación para fines distintos a reservar tus propias citas, ni
        intentar acceder a cuentas ajenas, saturar el sistema con peticiones automatizadas, ni realizar
        cualquier otro uso que perjudique el funcionamiento del servicio o a otras personas usuarias.
      </p>

      <h2>6. Propiedad intelectual</h2>
      <p>
        El diseño, los textos, el logo y demás elementos de la aplicación pertenecen a [nombre completo o
        razón social] o se usan con la licencia correspondiente (ver el pie de página para la atribución
        de los iconos usados).
      </p>

      <h2>7. Limitación de responsabilidad</h2>
      <p>
        Hacemos lo posible por mantener la aplicación disponible y funcionando correctamente, pero no
        garantizamos que esté libre de interrupciones o errores. No nos responsabilizamos de daños
        derivados de un uso indebido de la aplicación.
      </p>

      <h2>8. Modificaciones</h2>
      <p>
        Podemos actualizar estos términos y la política de privacidad; si el cambio es relevante, te
        pediremos que los aceptes de nuevo la próxima vez que inicies sesión.
      </p>

      <h2>9. Contacto</h2>
      <p>
        Para cualquier duda sobre estos términos, escríbenos a{' '}
        <a href="mailto:[email-de-contacto]">[email de contacto]</a>.
      </p>

      <Link to="/login" className="auth-back-link">
        Volver
      </Link>
    </div>
  )
}
