import { useEffect, useRef } from 'react'

interface GoogleCredentialResponse {
  credential: string
}

interface GoogleAccountsId {
  initialize: (config: { client_id: string; callback: (response: GoogleCredentialResponse) => void }) => void
  renderButton: (parent: HTMLElement, options: { theme: string; size: string; width: number }) => void
}

declare global {
  interface Window {
    google?: { accounts: { id: GoogleAccountsId } }
  }
}

const SCRIPT_ID = 'google-identity-services'

/** Boton de "Continuar con Google": carga el script de Google Identity Services y renderiza su boton oficial de inicio de sesion. */
export default function GoogleSignInButton({ onCredential }: { onCredential: (idToken: string) => void }) {
  const divRef = useRef<HTMLDivElement>(null)
  const clientId = import.meta.env.VITE_GOOGLE_CLIENT_ID as string | undefined

  useEffect(() => {
    if (!clientId) return

    // Inicializa el widget de Google y lo dibuja en el contenedor una vez el script esta cargado.
    const initialize = () => {
      if (!window.google || !divRef.current) return
      window.google.accounts.id.initialize({
        client_id: clientId,
        callback: (response) => onCredential(response.credential),
      })
      window.google.accounts.id.renderButton(divRef.current, { theme: 'outline', size: 'large', width: 280 })
    }

    if (document.getElementById(SCRIPT_ID)) {
      initialize()
      return
    }

    const script = document.createElement('script')
    script.id = SCRIPT_ID
    script.src = 'https://accounts.google.com/gsi/client'
    script.async = true
    script.defer = true
    script.onload = initialize
    document.body.appendChild(script)
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [clientId])

  if (!clientId) {
    return null
  }

  return <div ref={divRef} className="google-signin-button" />
}
