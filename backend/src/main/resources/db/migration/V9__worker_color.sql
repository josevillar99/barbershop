-- Color asignado a cada trabajador, usado para distinguir sus citas en el calendario.
-- Por defecto el primero de la paleta fija que valida el backend (ver WorkerService).
ALTER TABLE worker ADD COLUMN color VARCHAR(7) NOT NULL DEFAULT '#2563eb';
