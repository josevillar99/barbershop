package com.peluqueria.common;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ClientIpTest {

    @Test
    void usaXForwardedForCuandoEstaPresente() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.5");

        assertEquals("203.0.113.5", ClientIp.resolve(request));
    }

    @Test
    void seQuedaConLaPrimeraIpDeUnaCadenaDeProxies() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("203.0.113.5, 10.0.0.1, 10.0.0.2");

        assertEquals("203.0.113.5", ClientIp.resolve(request));
    }

    @Test
    void recortaEspaciosAlrededorDeLaIp() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("  203.0.113.5  , 10.0.0.1");

        assertEquals("203.0.113.5", ClientIp.resolve(request));
    }

    @Test
    void usaLaIpDeConexionDirectaSiNoHayCabecera() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        assertEquals("127.0.0.1", ClientIp.resolve(request));
    }

    @Test
    void usaLaIpDeConexionDirectaSiLaCabeceraEstaEnBlanco() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn("   ");
        when(request.getRemoteAddr()).thenReturn("127.0.0.1");

        assertEquals("127.0.0.1", ClientIp.resolve(request));
    }
}
