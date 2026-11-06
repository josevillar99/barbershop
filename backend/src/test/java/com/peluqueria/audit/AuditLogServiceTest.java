package com.peluqueria.audit;

import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuditLogServiceTest {

    @AfterEach
    void limpiarContextoDePeticion() {
        // RequestContextHolder usa un ThreadLocal: si un test lo deja puesto, contaminaria
        // los siguientes tests que corran en el mismo hilo.
        RequestContextHolder.resetRequestAttributes();
    }

    @Test
    void sinPeticionHttpEnCursoGuardaLaDescripcionTalCual() {
        // El test corre en el hilo del test, no dentro de una peticion HTTP real: no hay
        // RequestContextHolder que consultar, asi que no debe intentar anadir IP.
        AuditLogRepository repository = mock(AuditLogRepository.class);
        AuditLogService service = new AuditLogService(repository);

        service.log("cliente@example.com", AuditAction.LOGIN, "Inicio sesion");

        AuditLog saved = captureSaved(repository);
        assertEquals("cliente@example.com", saved.getActorEmail());
        assertEquals(AuditAction.LOGIN, saved.getAction());
        assertEquals("Inicio sesion", saved.getDetails());
    }

    @Test
    void conPeticionHttpEnCursoAnadeLaIpALaDescripcion() {
        HttpServletRequest request = mock(HttpServletRequest.class);
        when(request.getHeader("X-Forwarded-For")).thenReturn(null);
        when(request.getRemoteAddr()).thenReturn("203.0.113.9");
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));

        AuditLogRepository repository = mock(AuditLogRepository.class);
        AuditLogService service = new AuditLogService(repository);

        service.log("admin@example.com", AuditAction.ADMIN_BLOCK_CREATE, "Creo un bloqueo");

        AuditLog saved = captureSaved(repository);
        assertEquals("Creo un bloqueo (IP: 203.0.113.9)", saved.getDetails());
    }

    private AuditLog captureSaved(AuditLogRepository repository) {
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(repository).save(captor.capture());
        return captor.getValue();
    }
}
