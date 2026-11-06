package com.peluqueria.common;

import com.peluqueria.config.AppProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/** Avisa por email cuando ocurre un error inesperado (500) en el backend, para poder localizarlo luego en los logs. */
@Slf4j
@Service
@RequiredArgsConstructor
public class ErrorAlertService {

    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm:ss").withZone(ZoneId.systemDefault());

    private final JavaMailSender mailSender;
    private final AppProperties appProperties;

    /**
     * Envia el aviso de forma asincrona (no debe retrasar ni romper la respuesta de error que ya
     * recibe el usuario). Un fallo al enviar el correo se registra en el log pero no se propaga.
     */
    @Async
    public void alert(String method, String path, Throwable ex) {
        String to = appProperties.alerts().email();
        if (to == null || to.isBlank()) {
            return;
        }
        String timestamp = TIMESTAMP_FORMAT.format(Instant.now());
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(appProperties.mail().from());
        message.setTo(to);
        message.setSubject("Error en Peluqueria - " + timestamp);
        message.setText("Se ha producido un error inesperado en el backend.\n\n"
                + "Fecha y hora: " + timestamp + "\n"
                + "Peticion: " + method + " " + path + "\n"
                + "Error: " + ex.getClass().getSimpleName() + ": " + ex.getMessage() + "\n\n"
                + "Busca esta fecha y hora exactas en los logs del servidor para ver la traza completa.");
        try {
            mailSender.send(message);
        } catch (Exception mailEx) {
            log.error("No se pudo enviar el email de aviso de error a {}", to, mailEx);
        }
    }
}
