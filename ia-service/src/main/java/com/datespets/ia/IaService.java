package com.datespets.ia;

import java.io.IOException;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import jakarta.annotation.PostConstruct;

/** Conversación con Gemini (API interactions) con llamadas a funciones. */
@Service
public class IaService {

    private static final int MAX_RONDAS = 10;

    private final ObjectMapper json;
    private final DatosMascotas datos;
    private final String clave;
    private final String modelo;
    private final String urlInteracciones;

    private RestClient cliente;
    private String systemInstruction;
    private JsonNode tools;

    public IaService(
            ObjectMapper json,
            DatosMascotas datos,
            @Value("${GEMINI_API_KEY:}") String clave,
            @Value("${GEMINI_MODEL:gemini-3.5-flash}") String modelo,
            @Value("${GEMINI_BASE_URL:https://generativelanguage.googleapis.com/v1beta}") String baseUrl) {
        this.json = json;
        this.datos = datos;
        this.clave = clave.trim();
        this.modelo = modelo.trim();
        this.urlInteracciones = baseUrl.trim().replaceAll("/+$", "") + "/interactions";
    }

    @PostConstruct
    void iniciar() throws IOException {
        if (clave.isEmpty()) {
            throw new IllegalStateException("No se encontró GEMINI_API_KEY en el entorno");
        }

        systemInstruction = new String(
                new ClassPathResource("prompt.txt").getInputStream().readAllBytes(), StandardCharsets.UTF_8).trim();
        tools = json.readTree(new ClassPathResource("tools.json").getInputStream());

        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build());
        fabrica.setReadTimeout(Duration.ofSeconds(90));
        cliente = RestClient.builder().requestFactory(fabrica).build();
    }

    private JsonNode crearInteraccion(ObjectNode extra) {
        ObjectNode cuerpo = json.createObjectNode();
        cuerpo.put("model", modelo);
        cuerpo.set("tools", tools);
        cuerpo.put("system_instruction", systemInstruction);
        cuerpo.setAll(extra);

        try {
            return cliente.post()
                    .uri(urlInteracciones)
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("x-goog-api-key", clave)
                    .body(cuerpo)
                    .retrieve()
                    .body(JsonNode.class);
        } catch (RestClientResponseException e) {
            throw new GeminiException(e.getStatusCode().value(), e.getResponseBodyAsString());
        }
    }

    private Object ejecutarFuncion(String nombre, JsonNode argumentos) {
        switch (nombre) {
            case "buscar_mascota":
                return datos.buscarMascota(argumentos.path("nombre").asText(""));
            case "obtener_citas_mascota":
                JsonNode id = argumentos.path("mascota_id");
                int mascotaId = id.isNumber() ? id.asInt() : Integer.parseInt(id.asText().trim());
                return datos.obtenerCitasMascota(mascotaId);
            default:
                throw new IllegalArgumentException("Función desconocida: " + nombre);
        }
    }

    /** Equivale a interaction.output_text del SDK: texto del último bloque de salida del modelo. */
    static String textoDeSalida(JsonNode interaccion) {
        JsonNode pasos = interaccion.path("steps");
        List<String> partes = new ArrayList<>();
        boolean recolectando = false;

        for (int i = pasos.size() - 1; i >= 0; i--) {
            JsonNode paso = pasos.get(i);
            String tipo = paso.path("type").asText();

            if (tipo.equals("user_input")) {
                break;
            }

            if (!tipo.equals("model_output") || !paso.path("content").isArray()) {
                if (recolectando) {
                    break;
                }
                continue;
            }

            JsonNode contenido = paso.get("content");
            for (int j = contenido.size() - 1; j >= 0; j--) {
                JsonNode bloque = contenido.get(j);

                if (bloque.path("type").asText().equals("text")) {
                    recolectando = true;
                    partes.add(bloque.path("text").asText(""));
                } else if (recolectando) {
                    return unirInvertido(partes);
                }
            }
        }

        return unirInvertido(partes);
    }

    private static String unirInvertido(List<String> partes) {
        StringBuilder sb = new StringBuilder();
        for (int i = partes.size() - 1; i >= 0; i--) {
            sb.append(partes.get(i));
        }
        return sb.toString();
    }

    public Map<String, Object> generarRespuesta(String mensaje, String previoId) throws IOException {
        ObjectNode parametros = json.createObjectNode();
        parametros.put("input", mensaje);
        if (previoId != null && !previoId.isEmpty()) {
            parametros.put("previous_interaction_id", previoId);
        }

        JsonNode interaccion = crearInteraccion(parametros);

        for (int ronda = 0; ronda < MAX_RONDAS; ronda++) {
            List<Map<String, Object>> resultados = new ArrayList<>();

            for (JsonNode paso : interaccion.path("steps")) {
                if (!paso.path("type").asText().equals("function_call")) {
                    continue;
                }

                String nombre = paso.path("name").asText();
                Object salida = ejecutarFuncion(nombre, paso.path("arguments"));

                Map<String, Object> resultado = new LinkedHashMap<>();
                resultado.put("type", "function_result");
                resultado.put("name", nombre);
                resultado.put("call_id", paso.path("id").asText());
                resultado.put("result", List.of(Map.of("type", "text", "text", json.writeValueAsString(salida))));
                resultados.add(resultado);
            }

            if (resultados.isEmpty()) {
                break;
            }

            ObjectNode siguiente = json.createObjectNode();
            siguiente.put("previous_interaction_id", interaccion.path("id").asText());
            siguiente.set("input", json.valueToTree(resultados));
            interaccion = crearInteraccion(siguiente);
        }

        String respuesta = textoDeSalida(interaccion);

        if (respuesta.isEmpty()) {
            throw new IllegalStateException("Gemini no devolvió contenido en la respuesta.");
        }

        Map<String, Object> resultado = new LinkedHashMap<>();
        resultado.put("respuesta", respuesta);
        resultado.put("interaction_id", interaccion.path("id").asText());
        return resultado;
    }
}
