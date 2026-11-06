import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter, Route, Routes } from 'react-router-dom'
import { beforeEach, describe, expect, test, vi } from 'vitest'
import CompleteRegistrationPage from './CompleteRegistrationPage'
import { AuthProvider } from './AuthContext'
import client from '../api/client'

vi.mock('../api/client', () => ({
  default: { post: vi.fn(), get: vi.fn(), put: vi.fn(), delete: vi.fn() },
  TOKEN_KEY: 'peluqueria_token',
  USER_KEY: 'peluqueria_user',
  extractErrorMessage: (_err: unknown, fallback: string) => fallback,
  isConflict: () => false,
}))

function renderPage(token = 'abc123') {
  return render(
    <MemoryRouter initialEntries={[`/completar-registro?token=${token}`]}>
      <AuthProvider>
        <Routes>
          <Route path="/completar-registro" element={<CompleteRegistrationPage />} />
          <Route path="/" element={<div>PANTALLA_PRINCIPAL</div>} />
        </Routes>
      </AuthProvider>
    </MemoryRouter>,
  )
}

async function rellenarFormulario(
  user: ReturnType<typeof userEvent.setup>,
  { password, passwordConfirm }: { password: string; passwordConfirm: string },
) {
  await user.type(screen.getByPlaceholderText('Nombre'), 'Cliente')
  await user.type(screen.getByPlaceholderText('Apellidos'), 'de prueba')
  await user.type(screen.getByPlaceholderText('Telefono'), '611222333')
  await user.type(screen.getByPlaceholderText(/Contrasena \(minimo/i), password)
  await user.type(screen.getByPlaceholderText(/Repite la contrasena/i), passwordConfirm)
  await user.click(screen.getByRole('button', { name: /Crear cuenta/i }))
}

describe('CompleteRegistrationPage', () => {
  beforeEach(() => {
    vi.mocked(client.post).mockReset()
  })

  test('avisa si las dos contrasenas no coinciden y no llama a la API', async () => {
    const user = userEvent.setup()
    renderPage()

    await rellenarFormulario(user, { password: 'Password123', passwordConfirm: 'Distinta456' })

    expect(await screen.findByText('Las contrasenas no coinciden')).toBeInTheDocument()
    expect(client.post).not.toHaveBeenCalled()
  })

  test('con contrasenas iguales crea la cuenta y navega a la pantalla principal', async () => {
    vi.mocked(client.post).mockResolvedValueOnce({
      data: { token: 'jwt-de-prueba', email: 'cliente@example.com', name: 'Cliente de prueba', role: 'USER', profileComplete: true },
    })
    const user = userEvent.setup()
    renderPage('token-del-email')

    await rellenarFormulario(user, { password: 'Password123', passwordConfirm: 'Password123' })

    expect(await screen.findByText('PANTALLA_PRINCIPAL')).toBeInTheDocument()
    expect(client.post).toHaveBeenCalledWith('/auth/signup/complete', {
      token: 'token-del-email',
      password: 'Password123',
      name: 'Cliente de prueba',
      phone: '611222333',
    })
  })
})
