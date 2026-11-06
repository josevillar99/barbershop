import { useState } from 'react'
import { render, screen } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { describe, expect, test } from 'vitest'
import PasswordField from './PasswordField'

/** Envoltorio con estado propio, igual que lo usan las pantallas reales (login, registro...). */
function ControlledPasswordField() {
  const [value, setValue] = useState('')
  return <PasswordField placeholder="Contrasena" value={value} onChange={setValue} />
}

describe('PasswordField', () => {
  test('empieza oculto (type=password)', () => {
    render(<ControlledPasswordField />)
    expect(screen.getByPlaceholderText('Contrasena')).toHaveAttribute('type', 'password')
  })

  test('el boton de mostrar/ocultar alterna el tipo del input', async () => {
    const user = userEvent.setup()
    render(<ControlledPasswordField />)

    const toggle = screen.getByRole('button', { name: 'Mostrar contrasena' })
    await user.click(toggle)

    expect(screen.getByPlaceholderText('Contrasena')).toHaveAttribute('type', 'text')
    expect(screen.getByRole('button', { name: 'Ocultar contrasena' })).toBeInTheDocument()

    await user.click(screen.getByRole('button', { name: 'Ocultar contrasena' }))
    expect(screen.getByPlaceholderText('Contrasena')).toHaveAttribute('type', 'password')
  })

  test('escribir en el input actualiza el valor (componente controlado)', async () => {
    const user = userEvent.setup()
    render(<ControlledPasswordField />)

    const input = screen.getByPlaceholderText('Contrasena')
    await user.type(input, 'Secreto123')

    expect(input).toHaveValue('Secreto123')
  })

  test('el boton de mostrar/ocultar no participa en el orden de tabulacion', () => {
    render(<ControlledPasswordField />)
    expect(screen.getByRole('button', { name: 'Mostrar contrasena' })).toHaveAttribute('tabindex', '-1')
  })
})
