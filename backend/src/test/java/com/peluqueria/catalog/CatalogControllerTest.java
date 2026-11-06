package com.peluqueria.catalog;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.peluqueria.auth.JwtService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Prueba los servicios (catalogo): validacion de tramos al crear/editar, y que el listado
 * publico solo devuelve los servicios activos. No repite aqui toda la logica de
 * disponibilidad/reserva (ver BookingEngineIntegrationTest), solo la capa HTTP de este controlador.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
@Rollback
class CatalogControllerTest {

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private ObjectMapper objectMapper;
    @Autowired
    private JwtService jwtService;

    private MockHttpServletRequestBuilder asRole(MockHttpServletRequestBuilder builder, String role) {
        return builder.header("Authorization", "Bearer " + jwtService.generateToken("admin-" + UUID.randomUUID() + "@example.com", role));
    }

    private MockHttpServletRequestBuilder postTreatment(Object body) throws Exception {
        return asRole(post("/api/admin/treatments"), "ADMIN")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(body));
    }

    @Test
    void unServicioCuyoPrimerTramoEsDeEsperaSeRechaza() throws Exception {
        Map<String, Object> body = Map.of(
                "name", "Servicio invalido " + UUID.randomUUID(),
                "active", true,
                "price", 10,
                "segments", List.of(
                        Map.of("type", "WAIT", "minutes", 15),
                        Map.of("type", "WORK", "minutes", 30)));

        mockMvc.perform(postTreatment(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("El primer y el ultimo tramo deben ser de trabajo, no de espera"));
    }

    @Test
    void unServicioValidoQuedaGuardadoConSusTramosYPrecio() throws Exception {
        String name = "Corte exprés " + UUID.randomUUID();
        Map<String, Object> body = Map.of(
                "name", name,
                "active", true,
                "price", 12.5,
                "segments", List.of(Map.of("type", "WORK", "minutes", 25)));

        mockMvc.perform(postTreatment(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value(name))
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.price").value(12.5))
                .andExpect(jsonPath("$.totalMinutes").value(25))
                .andExpect(jsonPath("$.segments[0].type").value("WORK"))
                .andExpect(jsonPath("$.segments[0].minutes").value(25));
    }

    @Test
    void elListadoPublicoSoloMuestraServiciosActivos() throws Exception {
        String activeName = "Activo publico " + UUID.randomUUID();
        String inactiveName = "Inactivo oculto " + UUID.randomUUID();

        mockMvc.perform(postTreatment(Map.of(
                        "name", activeName, "active", true, "price", 5,
                        "segments", List.of(Map.of("type", "WORK", "minutes", 15)))))
                .andExpect(status().isOk());
        mockMvc.perform(postTreatment(Map.of(
                        "name", inactiveName, "active", false, "price", 5,
                        "segments", List.of(Map.of("type", "WORK", "minutes", 15)))))
                .andExpect(status().isOk());

        String responseJson = mockMvc.perform(asRole(get("/api/treatments"), "USER"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();

        assertTrue(responseJson.contains(activeName), "El servicio activo deberia aparecer en el listado publico");
        assertFalse(responseJson.contains(inactiveName), "El servicio inactivo no deberia aparecer en el listado publico");
    }
}
