import type { SelectHTMLAttributes } from 'react'

/** Select con estilo propio (flecha e interior personalizados), sobre un &lt;select&gt; nativo para conservar accesibilidad y teclado. */
export default function Select({ className, children, ...rest }: SelectHTMLAttributes<HTMLSelectElement>) {
  return (
    <div className="select-wrapper">
      <select className={`select ${className ?? ''}`} {...rest}>
        {children}
      </select>
    </div>
  )
}
