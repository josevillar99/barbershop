-- Marca si una cita ya pasada se realizo o no, para futuras estadisticas.
ALTER TABLE appointment ADD COLUMN completed BOOLEAN NOT NULL DEFAULT FALSE;
