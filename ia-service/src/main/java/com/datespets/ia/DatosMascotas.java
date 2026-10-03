package com.datespets.ia;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

/** Herramientas que el asistente puede invocar: consultan los servicios de mascotas y citas. */
@Component
public class DatosMascotas {

    private static final ParameterizedTypeReference<List<Map<String, Object>>> LISTA =
            new ParameterizedTypeReference<>() {
            };

    private final String urlMascotas;
    private final String urlCitas;
    private final RestClient cliente;

    public DatosMascotas(
            @Value("${MASCOTAS_SERVICE_URL:http://127.0.0.1:8101/api/mascotas}") String urlMascotas,
            @Value("${CITAS_SERVICE_URL:http://127.0.0.1:8102/api/citas}") String urlCitas) {
        this.urlMascotas = urlMascotas.trim().replaceAll("/+$", "");
        this.urlCitas = urlCitas.trim().replaceAll("/+$", "");

        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
        fabrica.setReadTimeout(Duration.ofSeconds(30));
        this.cliente = RestClient.builder().requestFactory(fabrica).build();
    }

    public Map<String, Object> buscarMascota(String nombre) {
        List<Map<String, Object>> mascotas = cliente.get().uri(urlMascotas + "/").retrieve().body(LISTA);
        String busqueda = nombre.trim().toLowerCase();

        List<Map<String, Object>> resultados = (mascotas == null ? List.<Map<String, Object>>of() : mascotas)
                .stream()
                .filter(m -> String.valueOf(m.get("nombre")).trim().toLowerCase().contains(busqueda))
                .toList();

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("encontrada", !resultados.isEmpty());
        respuesta.put("cantidad", resultados.size());
        respuesta.put("mascotas", resultados);
        return respuesta;
    }

    public Map<String, Object> obtenerCitasMascota(int mascotaId) {
        List<Map<String, Object>> citas =
                cliente.get().uri(urlCitas + "/mascota/" + mascotaId).retrieve().body(LISTA);

        Map<String, Object> respuesta = new LinkedHashMap<>();
        respuesta.put("mascota_id", mascotaId);
        respuesta.put("citas", citas == null ? List.of() : citas);
        return respuesta;
    }
}
