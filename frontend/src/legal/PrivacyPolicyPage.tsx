import { Link } from 'react-router-dom'

/**
 * Politica de privacidad. Texto de partida generico (RGPD/LOPDGDD) con los huecos propios del
 * negocio marcados entre corchetes: hay que rellenarlos y, antes de publicar esto de verdad,
 * que lo revise alguien con conocimientos legales - esto no es asesoramiento legal.
 */
export default function PrivacyPolicyPage() {
  return (
    <div className="legal-page">
      <div className="legal-draft-banner">
        ⚠️ Texto de partida, no definitivo. Rellena los datos entre corchetes y pide que lo revise
        alguien con conocimientos legales antes de publicarlo.
      </div>

      <h1>Política de privacidad</h1>
      <p className="hint-text">Última actualización: [fecha]</p>

      <h2>1. Responsable del tratamiento</h2>
      <p>
        [Nombre completo o razón social], con [NIF/CIF], domicilio en [dirección], es la responsable del
        tratamiento de los datos personales que se recogen a través de esta aplicación de reservas de
        Castor Barber Shop. Puedes contactar en <a href="mailto:[email-de-contacto]">[email de contacto]</a>.
      </p>

      <h2>2. Qué datos tratamos</h2>
      <p>
        Al crear una cuenta y reservar citas tratamos: tu nombre, teléfono, email, contraseña (cifrada,
        nunca en texto plano) y el historial de las citas que reservas (servicio, fecha, hora, trabajador
        asignado y observaciones que añadas). Si inicias sesión con Google, recibimos tu nombre y email
        de tu cuenta de Google. Registramos también la IP y la fecha de ciertas acciones (inicio de
        sesión, reservas, cambios de contraseña) en un log interno de seguridad.
      </p>

      <h2>3. Con qué finalidad tratamos tus datos</h2>
      <ul>
        <li>Gestionar tu cuenta y tu acceso a la aplicación.</li>
        <li>Gestionar las citas que reservas: crearlas, recordarlas y permitir que las canceles.</li>
        <li>Contactarte por email para confirmaciones, avisos y recuperación de contraseña.</li>
        <li>Prevenir el uso fraudulento o abusivo de la aplicación (control de intentos de acceso).</li>
        <li>Cumplir con obligaciones legales aplicables al negocio.</li>
      </ul>

      <h2>4. Legitimación</h2>
      <p>
        La base legal para tratar tus datos es la ejecución del contrato de prestación de servicios
        (gestionar tu cuenta y tus citas), tu consentimiento (al crear la cuenta y aceptar esta política)
        y el interés legítimo en mantener segura la aplicación.
      </p>

      <h2>5. A quién se comunican tus datos</h2>
      <p>
        No cedemos tus datos a terceros salvo obligación legal. Usamos proveedores externos que actúan
        como encargados del tratamiento para prestar el servicio (por ejemplo, el proveedor de
        alojamiento/hosting y el proveedor de envío de emails [nombre del proveedor de correo]), con
        contrato de encargo del tratamiento cuando aplica.
      </p>

      <h2>6. Cuánto tiempo conservamos tus datos</h2>
      <p>
        Conservamos tus datos mientras mantengas la cuenta activa. Si solicitas la eliminación de tu
        cuenta, se eliminan tus datos personales identificativos; el historial de citas puede conservarse
        de forma disociada (sin vincularse a tu identidad) para estadísticas y obligaciones contables o
        fiscales que apliquen al negocio.
      </p>

      <h2>7. Tus derechos</h2>
      <p>Puedes ejercer en cualquier momento los siguientes derechos:</p>
      <ul>
        <li><strong>Acceso:</strong> saber qué datos tuyos tratamos.</li>
        <li><strong>Rectificación:</strong> corregir datos inexactos (nombre, teléfono, desde tu perfil).</li>
        <li><strong>Supresión:</strong> pedir que se eliminen tus datos ("derecho al olvido").</li>
        <li><strong>Oposición y limitación:</strong> oponerte a un tratamiento concreto o pedir que se limite.</li>
        <li><strong>Portabilidad:</strong> recibir tus datos en un formato estructurado.</li>
      </ul>
      <p>
        Puedes gestionar tu nombre y teléfono, cambiar tu contraseña y ver tu historial de citas desde{' '}
        <Link to="/perfil">tu perfil</Link>. Para pedir la eliminación completa de tu cuenta, o cualquier
        otro derecho, escribe a <a href="mailto:[email-de-contacto]">[email de contacto]</a>.
      </p>
      <p>
        Si consideras que no hemos atendido correctamente tu solicitud, puedes reclamar ante la Agencia
        Española de Protección de Datos (<a href="https://www.aepd.es" target="_blank" rel="noreferrer">www.aepd.es</a>).
      </p>

      <h2>8. Seguridad</h2>
      <p>
        Las contraseñas se guardan cifradas, las comunicaciones viajan cifradas (HTTPS) y limitamos los
        intentos de acceso para dificultar ataques automatizados. Ningún sistema es invulnerable al
        100%, pero aplicamos medidas razonables acordes al tamaño de este negocio.
      </p>

      <Link to="/login" className="auth-back-link">
        Volver
      </Link>
    </div>
  )
}
