-- =============================================================================
-- reset-db.sql
--
-- Reinicia la base de datos a un estado limpio y predecible, pensado para
-- usarse despues de desplegar cambios o despues de una tanda de pruebas.
--
-- Que hace:
--   1. Borra TODAS las citas.
--   2. Borra TODOS los usuarios excepto el/los superadministrador.
--   3. Reconstruye el catalogo de servicios exactamente igual que en local
--      (mismos nombres, tiempos y precios de las migraciones V2/V4).
--   4. Crea 10 usuarios de prueba: 2 trabajadores + 8 clientes.
--   5. Genera citas reales para 16 dias, empezando 3 dias antes de hoy,
--      repartidas entre ambos trabajadores y los 8 clientes, respetando el
--      horario del salon y los servicios que sabe hacer cada trabajador.
--
-- La cuenta superadmin (y su contrasena) no se toca.
-- Contrasena de TODAS las cuentas de prueba creadas por este script:
--   TempTest1234!
--
-- Uso:
--   Local (Docker):
--     docker exec -i peluqueria-postgres-db-1 psql -U peluqueria -d peluqueria < scripts/reset-db.sql
--
--   Produccion (Render):
--     psql "<cadena_de_conexion_de_render>" -f scripts/reset-db.sql
--
-- Es seguro ejecutarlo varias veces seguidas: cada ejecucion vuelve a
-- dejar la base de datos exactamente en el mismo estado.
-- =============================================================================

\set ON_ERROR_STOP on

BEGIN;

-- ---------------------------------------------------------------------------
-- 1. Borrar todas las citas (appointment_segment cae en cascada).
-- ---------------------------------------------------------------------------
DELETE FROM appointment;

-- ---------------------------------------------------------------------------
-- 2. Borrar horario y bloqueos propios de cualquier trabajador. Las filas
--    con worker_id NULL son la plantilla del salon y no se tocan.
-- ---------------------------------------------------------------------------
DELETE FROM business_hours WHERE worker_id IS NOT NULL;
DELETE FROM schedule_block WHERE worker_id IS NOT NULL;

-- ---------------------------------------------------------------------------
-- 3. Borrar todos los perfiles de trabajador (treatment_worker cae en cascada).
-- ---------------------------------------------------------------------------
DELETE FROM worker;

-- ---------------------------------------------------------------------------
-- 4. Borrar todos los usuarios excepto el/los superadministrador.
-- ---------------------------------------------------------------------------
DELETE FROM app_user WHERE role <> 'SUPERADMIN';

-- ---------------------------------------------------------------------------
-- 5. Reconstruir el catalogo de servicios (igual que en local: ver
--    backend/src/main/resources/db/migration/V2__catalog_and_appointments.sql
--    y V4__price_and_notes.sql).
-- ---------------------------------------------------------------------------
DELETE FROM treatment;
ALTER TABLE treatment ALTER COLUMN id RESTART WITH 1;

INSERT INTO treatment (id, name, active, price) VALUES (1, 'Corte', TRUE, 15.00);
INSERT INTO treatment_segment (treatment_id, segment_order, type, minutes) VALUES (1, 0, 'WORK', 45);

INSERT INTO treatment (id, name, active, price) VALUES (2, 'Peinado', TRUE, 12.00);
INSERT INTO treatment_segment (treatment_id, segment_order, type, minutes) VALUES (2, 0, 'WORK', 30);

INSERT INTO treatment (id, name, active, price) VALUES (3, 'Barba', TRUE, 8.00);
INSERT INTO treatment_segment (treatment_id, segment_order, type, minutes) VALUES (3, 0, 'WORK', 20);

INSERT INTO treatment (id, name, active, price) VALUES (4, 'Tinte', TRUE, 35.00);
INSERT INTO treatment_segment (treatment_id, segment_order, type, minutes) VALUES (4, 0, 'WORK', 20);
INSERT INTO treatment_segment (treatment_id, segment_order, type, minutes) VALUES (4, 1, 'WAIT', 30);
INSERT INTO treatment_segment (treatment_id, segment_order, type, minutes) VALUES (4, 2, 'WORK', 15);

INSERT INTO treatment (id, name, active, price) VALUES (5, 'Mechas', TRUE, 55.00);
INSERT INTO treatment_segment (treatment_id, segment_order, type, minutes) VALUES (5, 0, 'WORK', 30);
INSERT INTO treatment_segment (treatment_id, segment_order, type, minutes) VALUES (5, 1, 'WAIT', 60);
INSERT INTO treatment_segment (treatment_id, segment_order, type, minutes) VALUES (5, 2, 'WORK', 30);

ALTER TABLE treatment ALTER COLUMN id RESTART WITH 6;

-- ---------------------------------------------------------------------------
-- 6. Crear los 10 usuarios de prueba (8 clientes + 2 trabajadores).
--    Password hash bcrypt de "TempTest1234!" (el mismo usado en todas las
--    cuentas de prueba de este proyecto).
-- ---------------------------------------------------------------------------
INSERT INTO app_user (email, role, password_hash, name, phone) VALUES
  ('ana@example.com',     'USER',  '$2b$12$ZdZvVPBYZHrJVHC63FQcbuA1w0aTrYurFqoYB.pxMv0VgaUGi0.4C', 'Ana',     '611000001'),
  ('lucia@example.com',   'USER',  '$2b$12$ZdZvVPBYZHrJVHC63FQcbuA1w0aTrYurFqoYB.pxMv0VgaUGi0.4C', 'Lucia',   '611000002'),
  ('pablo@example.com',   'USER',  '$2b$12$ZdZvVPBYZHrJVHC63FQcbuA1w0aTrYurFqoYB.pxMv0VgaUGi0.4C', 'Pablo',   '611000003'),
  ('diego@example.com',   'USER',  '$2b$12$ZdZvVPBYZHrJVHC63FQcbuA1w0aTrYurFqoYB.pxMv0VgaUGi0.4C', 'Diego',   '611000004'),
  ('marta@example.com',   'USER',  '$2b$12$ZdZvVPBYZHrJVHC63FQcbuA1w0aTrYurFqoYB.pxMv0VgaUGi0.4C', 'Marta',   '611000005'),
  ('javier@example.com',  'USER',  '$2b$12$ZdZvVPBYZHrJVHC63FQcbuA1w0aTrYurFqoYB.pxMv0VgaUGi0.4C', 'Javier',  '611000006'),
  ('sergio@example.com',  'USER',  '$2b$12$ZdZvVPBYZHrJVHC63FQcbuA1w0aTrYurFqoYB.pxMv0VgaUGi0.4C', 'Sergio',  '611000007'),
  ('elena@example.com',   'USER',  '$2b$12$ZdZvVPBYZHrJVHC63FQcbuA1w0aTrYurFqoYB.pxMv0VgaUGi0.4C', 'Elena',   '611000008'),
  ('laura.martinez@example.com',   'ADMIN', '$2b$12$ZdZvVPBYZHrJVHC63FQcbuA1w0aTrYurFqoYB.pxMv0VgaUGi0.4C', 'Laura Martinez',  '622000001'),
  ('carlos.perez@example.com', 'ADMIN', '$2b$12$ZdZvVPBYZHrJVHC63FQcbuA1w0aTrYurFqoYB.pxMv0VgaUGi0.4C', 'Carlos Perez','622000002');

-- ---------------------------------------------------------------------------
-- 7. Crear los 2 perfiles de trabajador: horario semanal (lunes cerrado,
--    martes a viernes manana y tarde, sabado manana y tarde tambien,
--    domingo cerrado), un color de la paleta fija, y los servicios que sabe
--    hacer cada uno (entre los dos cubren los 5 servicios).
-- ---------------------------------------------------------------------------
INSERT INTO worker (user_id, active, color)
SELECT id, TRUE, '#2563eb' FROM app_user WHERE email = 'laura.martinez@example.com';

INSERT INTO worker (user_id, active, color)
SELECT id, TRUE, '#7c3aed' FROM app_user WHERE email = 'carlos.perez@example.com';

INSERT INTO business_hours (day_of_week, start_time, end_time, worker_id)
SELECT d.day_of_week, s.start_time, s.end_time, w.id
FROM worker w
CROSS JOIN (VALUES (2), (3), (4), (5), (6)) AS d(day_of_week) -- martes(2) a sabado(6); lunes(1) y domingo(7) cerrados
CROSS JOIN (VALUES (540, 840), (960, 1200)) AS s(start_time, end_time); -- manana 9-14, tarde 16-20

INSERT INTO treatment_worker (treatment_id, worker_id)
SELECT t.id, w.id
FROM treatment t, worker w, app_user u
WHERE w.user_id = u.id AND u.email = 'laura.martinez@example.com' AND t.name IN ('Corte', 'Peinado', 'Barba');

INSERT INTO treatment_worker (treatment_id, worker_id)
SELECT t.id, w.id
FROM treatment t, worker w, app_user u
WHERE w.user_id = u.id AND u.email = 'carlos.perez@example.com' AND t.name IN ('Corte', 'Tinte', 'Mechas');

-- ---------------------------------------------------------------------------
-- 8. Generar citas para 16 dias (de hoy-3 a hoy+12) de forma ALEATORIA pero
--    coherente, para que no todos los dias salgan identicos.
--
--    Para cada trabajador y cada turno (manana/tarde) se van colocando citas
--    una a una: se elige al azar un servicio de los que sabe hacer y un
--    hueco libre del turno donde quepa, hasta que ya no cabe nada mas, toca
--    parar por azar, o ya se ha llenado el 75% del turno (se deja siempre al
--    menos un 25% libre a proposito, como hueco real para poder anadir o
--    quitar citas a mano despues sin tener que borrar nada generado). Cuando un
--    servicio tiene un tramo de ESPERA (tinte/mechas), ese hueco intermedio
--    queda libre para que otra cita se cuele ahi - el trabajador solo esta
--    ocupado durante sus tramos de TRABAJO (ver SegmentTimeline.java), asi
--    que a veces sale el patron de "mechas + un corte mientras espera" y
--    otras veces no, segun toque. Mientras un trabajador hace lo suyo, el
--    otro esta con un servicio distinto en paralelo, como en un salon real.
--    Respeta los bloqueos ya existentes ese dia (de ese trabajador o de todo
--    el salon, p.ej. una reunion de equipo creada a mano): nunca genera una
--    cita encima de un bloqueo real.
--
--    Lunes y domingo cerrados. Las citas de dias ya pasados se marcan como
--    "hechas" con un 85% de probabilidad; las de hoy en adelante nunca,
--    porque todavia no han pasado.
-- ---------------------------------------------------------------------------
CREATE TEMP TABLE free_slot (
  id SERIAL PRIMARY KEY,
  start_min INT NOT NULL,
  end_min INT NOT NULL
) ON COMMIT DROP;

DO $$
DECLARE
  v_day_offset INT;
  v_appt_date DATE;
  v_dow INT;
  v_worker_rec RECORD;
  v_client_ids BIGINT[];
  v_window_base INT;
  v_window_length INT;
  v_treatment_id BIGINT;
  v_treatment_name TEXT;
  v_total_minutes INT;
  v_slot RECORD;
  v_start_time INT;
  v_client_id BIGINT;
  v_new_appt_id BIGINT;
  v_seg RECORD;
  v_cursor INT;
  v_is_completed BOOLEAN;
  v_attempt INT;
  v_placed BOOLEAN;
  v_remaining_free INT;
  v_block RECORD;
  v_fs RECORD;
BEGIN
  SELECT array_agg(id ORDER BY id) INTO v_client_ids FROM app_user WHERE role = 'USER';

  FOR v_day_offset IN -3..12 LOOP
    v_appt_date := CURRENT_DATE + v_day_offset;
    v_dow := EXTRACT(ISODOW FROM v_appt_date)::INT;

    IF v_dow IN (1, 7) THEN
      CONTINUE; -- lunes y domingo, salon cerrado
    END IF;

    FOR v_worker_rec IN SELECT w.id AS worker_id FROM worker w ORDER BY w.id LOOP
      FOREACH v_window_base IN ARRAY ARRAY[540, 960] LOOP -- manana (9-14) y tarde (16-20)
        v_window_length := CASE WHEN v_window_base = 540 THEN 300 ELSE 240 END;

        DELETE FROM free_slot;
        INSERT INTO free_slot (start_min, end_min) VALUES (v_window_base, v_window_base + v_window_length);

        -- recorta el hueco con los bloqueos ya existentes ese dia (propios de
        -- este trabajador o de todo el salon), para no generar citas encima
        -- de un bloqueo real (ej. una reunion de equipo).
        FOR v_block IN
          SELECT start_time AS b_start, end_time AS b_end FROM schedule_block
          WHERE block_date = v_appt_date AND (worker_id = v_worker_rec.worker_id OR worker_id IS NULL)
        LOOP
          FOR v_fs IN
            SELECT id, start_min, end_min FROM free_slot
            WHERE start_min < v_block.b_end AND end_min > v_block.b_start
          LOOP
            DELETE FROM free_slot WHERE id = v_fs.id;
            IF v_fs.start_min < v_block.b_start THEN
              INSERT INTO free_slot (start_min, end_min) VALUES (v_fs.start_min, v_block.b_start);
            END IF;
            IF v_fs.end_min > v_block.b_end THEN
              INSERT INTO free_slot (start_min, end_min) VALUES (v_block.b_end, v_fs.end_min);
            END IF;
          END LOOP;
        END LOOP;

        LOOP
          SELECT coalesce(sum(end_min - start_min), 0) INTO v_remaining_free FROM free_slot;
          EXIT WHEN v_remaining_free < v_window_length * 0.25; -- deja siempre un hueco real (min. 25% del turno) libre para citas manuales
          EXIT WHEN random() < 0.15; -- y para tambien por azar, para que no todos los turnos vayan igual de llenos

          v_placed := FALSE;
          FOR v_attempt IN 1..4 LOOP
            SELECT t.id, t.name INTO v_treatment_id, v_treatment_name
            FROM treatment_worker tw JOIN treatment t ON t.id = tw.treatment_id
            WHERE tw.worker_id = v_worker_rec.worker_id
            ORDER BY random() LIMIT 1;

            SELECT coalesce(sum(minutes), 0) INTO v_total_minutes
            FROM treatment_segment WHERE treatment_id = v_treatment_id;

            SELECT id, start_min, end_min INTO v_slot
            FROM free_slot WHERE end_min - start_min >= v_total_minutes
            ORDER BY random() LIMIT 1;

            IF FOUND THEN
              v_start_time := v_slot.start_min
                + floor(random() * (v_slot.end_min - v_slot.start_min - v_total_minutes + 1))::int;
              v_is_completed := v_appt_date < CURRENT_DATE AND random() < 0.85;
              v_client_id := v_client_ids[1 + floor(random() * array_length(v_client_ids, 1))::int];

              INSERT INTO appointment (
                appt_date, start_time, end_time, treatment_id, treatment_name, status,
                booked_by, booked_at, client_name, client_phone, worker_id, completed
              )
              SELECT v_appt_date, v_start_time, v_start_time + v_total_minutes, v_treatment_id, v_treatment_name,
                     'BOOKED', u.id, now(), u.name, u.phone, v_worker_rec.worker_id, v_is_completed
              FROM app_user u
              WHERE u.id = v_client_id
              RETURNING id INTO v_new_appt_id;

              -- inserta los tramos de la cita, y libera el tramo de ESPERA
              -- (si lo hay) como un hueco nuevo por si otra cita se cuela ahi.
              v_cursor := v_start_time;
              FOR v_seg IN
                SELECT segment_order, type, minutes FROM treatment_segment
                WHERE treatment_id = v_treatment_id ORDER BY segment_order
              LOOP
                INSERT INTO appointment_segment (appointment_id, segment_order, type, minutes)
                VALUES (v_new_appt_id, v_seg.segment_order, v_seg.type, v_seg.minutes);
                IF v_seg.type = 'WAIT' AND v_seg.minutes > 0 THEN
                  INSERT INTO free_slot (start_min, end_min) VALUES (v_cursor, v_cursor + v_seg.minutes);
                END IF;
                v_cursor := v_cursor + v_seg.minutes;
              END LOOP;

              -- el hueco elegido se consume: se quita y se devuelve lo que sobre a los lados.
              DELETE FROM free_slot WHERE id = v_slot.id;
              IF v_start_time - v_slot.start_min > 0 THEN
                INSERT INTO free_slot (start_min, end_min) VALUES (v_slot.start_min, v_start_time);
              END IF;
              IF v_slot.end_min - (v_start_time + v_total_minutes) > 0 THEN
                INSERT INTO free_slot (start_min, end_min) VALUES (v_start_time + v_total_minutes, v_slot.end_min);
              END IF;

              v_placed := TRUE;
              EXIT;
            END IF;
          END LOOP;

          EXIT WHEN NOT v_placed; -- tras varios intentos no cupo nada mas, se acaba el turno
        END LOOP;

      END LOOP;
    END LOOP;
  END LOOP;
END $$;

COMMIT;

-- ---------------------------------------------------------------------------
-- Resumen final (solo informativo).
-- ---------------------------------------------------------------------------
SELECT 'usuarios' AS tabla, count(*) AS total FROM app_user
UNION ALL
SELECT 'trabajadores', count(*) FROM worker
UNION ALL
SELECT 'citas', count(*) FROM appointment;
