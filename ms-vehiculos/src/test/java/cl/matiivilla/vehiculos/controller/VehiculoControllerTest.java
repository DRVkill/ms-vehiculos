package cl.matiivilla.vehiculos.controller;

import cl.matiivilla.vehiculos.dto.VehiculoRequest;
import cl.matiivilla.vehiculos.dto.VehiculoResponse;
import cl.matiivilla.vehiculos.service.VehiculoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class VehiculoControllerTest {

    private static final String BODY_VALIDO = """
            {"patente":"ABCD12","marca":"Toyota","modelo":"Yaris","anio":2020,
             "color":"Rojo","tipo":"Sedan","disponible":true}
            """;

    @Mock
    private VehiculoService vehiculoService;

    @InjectMocks
    private VehiculoController vehiculoController;

    private MockMvc mockMvc;
    private VehiculoResponse response;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.standaloneSetup(vehiculoController, new HomeController()).build();
        response = VehiculoResponse.builder()
                .id(1L).patente("ABCD12").marca("Toyota").modelo("Yaris")
                .anio(2020).color("Rojo").tipo("Sedan").disponible(true)
                .build();
    }

    @Test
    void home_devuelveMensaje() throws Exception {
        mockMvc.perform(get("/"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value("Api-vehiculos, Version 1.0"));
    }

    @Test
    void listar_devuelve200() throws Exception {
        when(vehiculoService.findAll()).thenReturn(List.of(response));

        mockMvc.perform(get("/api/v1/vehiculos"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].patente").value("ABCD12"));
    }

    @Test
    void buscarPorId_inexistente_devuelve404() throws Exception {
        when(vehiculoService.findById(99L))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "no existe"));

        mockMvc.perform(get("/api/v1/vehiculos/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    void crear_valido_devuelve201() throws Exception {
        when(vehiculoService.create(any(VehiculoRequest.class))).thenReturn(response);

        mockMvc.perform(post("/api/v1/vehiculos").contentType(MediaType.APPLICATION_JSON).content(BODY_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(1));
    }

    @Test
    void crear_invalido_devuelve400SinLlamarAlServicio() throws Exception {
        String bodyInvalido = """
                {"patente":"","marca":"Toyota","modelo":"Yaris","anio":1900,"tipo":"Sedan","disponible":true}
                """;

        mockMvc.perform(post("/api/v1/vehiculos").contentType(MediaType.APPLICATION_JSON).content(bodyInvalido))
                .andExpect(status().isBadRequest());

        verify(vehiculoService, never()).create(any());
    }

    @Test
    void actualizar_devuelve200() throws Exception {
        when(vehiculoService.update(eq(1L), any(VehiculoRequest.class))).thenReturn(response);

        mockMvc.perform(put("/api/v1/vehiculos/1").contentType(MediaType.APPLICATION_JSON).content(BODY_VALIDO))
                .andExpect(status().isOk());
    }

    @Test
    void eliminar_devuelve204() throws Exception {
        mockMvc.perform(delete("/api/v1/vehiculos/1"))
                .andExpect(status().isNoContent());

        verify(vehiculoService).delete(1L);
    }
}
