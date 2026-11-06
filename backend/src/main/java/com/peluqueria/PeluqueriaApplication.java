package com.peluqueria;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Punto de entrada de la aplicacion Spring Boot de la peluqueria.
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
@ConfigurationPropertiesScan
public class PeluqueriaApplication {

    /** Arranca la aplicacion Spring Boot. */
    public static void main(String[] args) {
        SpringApplication.run(PeluqueriaApplication.class, args);
    }
}
