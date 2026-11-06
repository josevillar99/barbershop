import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { MemoryRouter } from 'react-router-dom'
import { beforeEach, describe, expect, test, vi } from 'vitest'
import ResetPasswordPage from './ResetPasswordPage'
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
    <MemoryRouter initialEntries={[`/restablecer-contrasena?token=${token}`]}>
      <AuthProvider>
        <ResetPasswordPage />
      </AuthProvider>
    </MemoryRouter>,
  )
}

describe('ResetPasswordPage', () => {
  beforeEach(() => {
    vi.mocked(client.post).mockReset()
  })

  test('sin token en la URL pide pedir un enlace nuevo, sin mostrar el formulario', () => {
    renderPage('')
    expect(screen.getByText('Enlace invalido')).toBeInTheDocument()
    expect(screen.queryByPlaceholderText(/Contrasena nueva/i)).not.toBeInTheDocument()
  })

  test('avisa si las dos contrasenas no coinciden y no llama a la API', async () => {
    const user = userEvent.setup()
    renderPage()

    await user.type(screen.getByPlaceholderText(/Contrasena nueva/i), 'Password123')
    await user.type(screen.getByPlaceholderText(/Repite la contrasena/i), 'OtraDistinta9')
    await user.click(screen.getByRole('button', { name: /Guardar contrasena/i }))

    expect(await screen.findByText('Las contrasenas no coinciden')).toBeInTheDocument()
    expect(client.post).not.toHaveBeenCalled()
  })

  test('con contrasenas iguales llama a la API con el token y muestra la confirmacion', async () => {
    vi.mocked(client.post).mockResolvedValueOnce({ data: undefined })
    const user = userEvent.setup()
    renderPage('el-token-del-email')

    await user.type(screen.getByPlaceholderText(/Contrasena nueva/i), 'Password123')
    await user.type(screen.getByPlaceholderText(/Repite la contrasena/i), 'Password123')
    await user.click(screen.getByRole('button', { name: /Guardar contrasena/i }))

    expect(await screen.findByText('Contrasena actualizada')).toBeInTheDocument()
    expect(client.post).toHaveBeenCalledWith('/auth/reset-password', {
      token: 'el-token-del-email',
      newPassword: 'Password123',
    })
  })

  test('si la API rechaza el token muestra el mensaje de error, no la confirmacion', async () => {
    vi.mocked(client.post).mockRejectedValueOnce(new Error('rechazado'))
    const user = userEvent.setup()
    renderPage()

    await user.type(screen.getByPlaceholderText(/Contrasena nueva/i), 'Password123')
    await user.type(screen.getByPlaceholderText(/Repite la contrasena/i), 'Password123')
    await user.click(screen.getByRole('button', { name: /Guardar contrasena/i }))

    expect(await screen.findByText('No se pudo restablecer la contrasena')).toBeInTheDocument()
    expect(screen.queryByText('Contrasena actualizada')).not.toBeInTheDocument()
  })
})
