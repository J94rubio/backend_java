package com.datespets.common;

import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.context.annotation.Lazy;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.annotation.PostConstruct;

/** Acceso a Supabase mediante su API REST (PostgREST). Solo se crea en los servicios que lo usan. */
@Lazy
@Component
public class SupabaseClient {

    private static final ParameterizedTypeReference<List<Map<String, Object>>> FILAS =
            new ParameterizedTypeReference<>() {
            };

    private final String baseUrl;
    private final String clave;
    private final boolean urlDefinida;
    private RestClient cliente;

    public SupabaseClient(@Value("${SUPABASE_URL:}") String url, @Value("${SUPABASE_KEY:}") String clave) {
        this.baseUrl = url.trim().replaceAll("/+$", "") + "/rest/v1";
        this.clave = clave.trim();
        this.urlDefinida = !url.isBlank();
    }

    @PostConstruct
    void iniciar() {
        if (!urlDefinida || clave.isEmpty()) {
            throw new IllegalStateException("No se encontraron SUPABASE_URL y SUPABASE_KEY en el entorno");
        }

        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
        fabrica.setReadTimeout(Duration.ofSeconds(20));

        this.cliente = RestClient.builder()
                .requestFactory(fabrica)
                .defaultHeader("apikey", clave)
                .defaultHeader("Authorization", "Bearer " + clave)
                .build();
    }

    private URI uri(String tabla, String columnas, Map<String, Object> filtros, String orden) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl + "/" + tabla);

        if (columnas != null) {
            builder.queryParam("select", columnas);
        }
        filtros.forEach((campo, valor) -> builder.queryParam(campo, "eq." + valor));
        if (orden != null) {
            builder.queryParam("order", orden + ".asc");
        }

        return builder.build().toUri();
    }

    public List<Map<String, Object>> select(String tabla, String columnas, Map<String, Object> filtros, String orden) {
        List<Map<String, Object>> filas = cliente.get()
                .uri(uri(tabla, columnas, filtros, orden))
                .retrieve()
                .body(FILAS);

        return filas == null ? List.of() : filas;
    }

    public List<Map<String, Object>> insert(String tabla, Map<String, Object> cuerpo, String columnas) {
        List<Map<String, Object>> filas = cliente.post()
                .uri(uri(tabla, columnas, Map.of(), null))
                .contentType(MediaType.APPLICATION_JSON)
                .header("Prefer", "return=representation")
                .body(cuerpo)
                .retrieve()
                .body(FILAS);

        return filas == null ? List.of() : filas;
    }

    public List<Map<String, Object>> update(
            String tabla, Map<String, Object> filtros, Map<String, Object> cuerpo, String columnas) {
        List<Map<String, Object>> filas = cliente.patch()
                .uri(uri(tabla, columnas, filtros, null))
                .contentType(MediaType.APPLICATION_JSON)
                .header("Prefer", "return=representation")
                .body(cuerpo)
                .retrieve()
                .body(FILAS);

        return filas == null ? List.of() : filas;
    }

    public List<Map<String, Object>> delete(String tabla, Map<String, Object> filtros, String columnas) {
        List<Map<String, Object>> filas = cliente.delete()
                .uri(uri(tabla, columnas, filtros, null))
                .header("Prefer", "return=representation")
                .retrieve()
                .body(FILAS);

        return filas == null ? List.of() : filas;
    }
}
