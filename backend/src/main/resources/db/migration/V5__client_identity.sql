ALTER TABLE appointment
    ADD COLUMN client_name  VARCHAR(255) NULL,
    ADD COLUMN client_phone VARCHAR(30) NULL;

UPDATE appointment a
SET client_name = u.name,
    client_phone = u.phone
FROM app_user u
WHERE u.id = a.booked_by AND a.booked_by IS NOT NULL;

UPDATE appointment SET client_name = 'Desconocido' WHERE client_name IS NULL;
UPDATE appointment SET client_phone = '-' WHERE client_phone IS NULL;

ALTER TABLE appointment
    ALTER COLUMN client_name SET NOT NULL,
    ALTER COLUMN client_phone SET NOT NULL;
