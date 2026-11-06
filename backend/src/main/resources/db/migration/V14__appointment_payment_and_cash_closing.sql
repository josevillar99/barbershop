-- Precio esperado (snapshot al reservar) e importe/metodo realmente cobrado al marcar una cita como hecha.
ALTER TABLE appointment ADD COLUMN expected_price NUMERIC(8,2);
ALTER TABLE appointment ADD COLUMN amount_charged NUMERIC(8,2);
ALTER TABLE appointment ADD COLUMN payment_method VARCHAR(20);

-- Cierre de caja diario: efectivo contado fisicamente, para comparar con lo cobrado segun las citas.
CREATE TABLE daily_cash_closing (
    id BIGSERIAL PRIMARY KEY,
    closing_date DATE NOT NULL UNIQUE,
    counted_cash NUMERIC(8,2) NOT NULL,
    closed_by_email VARCHAR(255) NOT NULL,
    closed_at TIMESTAMP NOT NULL
);
