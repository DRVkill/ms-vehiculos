package cl.matiivilla.vehiculos;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
class VehiculoApiIT {

    @Value("${local.server.port}")
    private int port;

    private final HttpClient http = HttpClient.newHttpClient();

    private HttpResponse<String> send(String method, String path, String body) throws Exception {
        HttpRequest.Builder req = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Content-Type", "application/json");
        req.method(method, body == null
                ? HttpRequest.BodyPublishers.noBody()
                : HttpRequest.BodyPublishers.ofString(body));
        return http.send(req.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void healthProbesDisponibles() throws Exception {
        assertThat(send("GET", "/actuator/health/liveness", null).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/actuator/health/readiness", null).statusCode()).isEqualTo(200);
    }

    @Test
    void cicloCrudCompleto() throws Exception {
        String body = """
                {"patente":"ITEST1","marca":"Kia","modelo":"Rio","anio":2022,
                 "color":"Gris","tipo":"Hatchback","disponible":true}
                """;

        HttpResponse<String> creado = send("POST", "/api/v1/vehiculos", body);
        assertThat(creado.statusCode()).isEqualTo(201);
        assertThat(creado.body()).contains("\"patente\":\"ITEST1\"");
        String id = creado.body().replaceAll(".*\"id\":(\\d+).*", "$1");

        assertThat(send("POST", "/api/v1/vehiculos", body).statusCode())
                .as("patente duplicada").isEqualTo(409);

        assertThat(send("GET", "/api/v1/vehiculos/" + id, null).statusCode()).isEqualTo(200);
        assertThat(send("PUT", "/api/v1/vehiculos/" + id, body.replace("Gris", "Negro")).body())
                .contains("Negro");

        assertThat(send("DELETE", "/api/v1/vehiculos/" + id, null).statusCode()).isEqualTo(204);
        assertThat(send("GET", "/api/v1/vehiculos/" + id, null).statusCode()).isEqualTo(404);
    }

    @Test
    void validacionRechazaDatosInvalidos() throws Exception {
        assertThat(send("POST", "/api/v1/vehiculos", "{\"patente\":\"\"}").statusCode()).isEqualTo(400);
    }
}
