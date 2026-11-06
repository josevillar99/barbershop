// Paleta fija de colores asignables a un trabajador (evita rojo/verde, ya usados
// en la app para cancelar/disponible). Debe coincidir exactamente con la paleta
// del backend (backend/.../worker/WorkerService.java, COLOR_PALETTE).
export const WORKER_COLOR_PALETTE = [
  '#2563eb', // azul
  '#7c3aed', // violeta
  '#d97706', // ambar
  '#0891b2', // cian
  '#db2777', // rosa
  '#4f46e5', // indigo
  '#0e7490', // turquesa oscuro
  '#9333ea', // purpura
  '#be185d', // granate
  '#57534e', // gris calido
] as const
