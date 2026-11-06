ALTER TABLE treatment ADD COLUMN price NUMERIC(8, 2) DEFAULT 0 NOT NULL;

UPDATE treatment SET price = 15.00 WHERE name = 'Corte';
UPDATE treatment SET price = 12.00 WHERE name = 'Peinado';
UPDATE treatment SET price = 8.00 WHERE name = 'Barba';
UPDATE treatment SET price = 35.00 WHERE name = 'Tinte';
UPDATE treatment SET price = 55.00 WHERE name = 'Mechas';

ALTER TABLE appointment ADD COLUMN notes VARCHAR(1000) NULL;
