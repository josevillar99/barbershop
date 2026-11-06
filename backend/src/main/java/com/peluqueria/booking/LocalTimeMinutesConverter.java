package com.peluqueria.booking;

import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

import java.time.LocalTime;

/**
 * Persiste LocalTime como minutos desde medianoche (columna entera) en vez de
 * un tipo TIME nativo, para poder comparar y sumar horas facilmente y evitar
 * sorpresas de mapeo entre motores de base de datos bajo ddl-auto:validate.
 */
@Converter
public class LocalTimeMinutesConverter implements AttributeConverter<LocalTime, Integer> {

    /** Transforma una hora en el minuto del dia que se guarda en la columna de base de datos. */
    @Override
    public Integer convertToDatabaseColumn(LocalTime attribute) {
        return attribute == null ? null : attribute.getHour() * 60 + attribute.getMinute();
    }

    /** Reconstruye la hora a partir del minuto del dia leido de la base de datos. */
    @Override
    public LocalTime convertToEntityAttribute(Integer dbData) {
        return dbData == null ? null : LocalTime.of(dbData / 60, dbData % 60);
    }
}
