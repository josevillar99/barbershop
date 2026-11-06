package com.peluqueria.common;

import org.springframework.http.HttpStatus;

/** Excepcion de negocio que lleva asociado el codigo HTTP con el que se debe responder al cliente. */
public class ApiException extends RuntimeException {

    private final HttpStatus status;

    /** Crea la excepcion con el codigo HTTP y el mensaje que se mostrara al cliente. */
    public ApiException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    /** Devuelve el codigo HTTP asociado a este error. */
    public HttpStatus getStatus() {
        return status;
    }
}
