package com.datespets.citas;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.datespets.common.ApiException;
import com.datespets.common.Issue;
import com.datespets.common.SupabaseClient;
import com.datespets.common.Validacion;

@RestController
@RequestMapping("/api/citas")
public class CitasController {

    private static final String TABLA = "citas";
    private static final String COLUMNAS = "id,mascota_id,fecha,motivo,estado";
    private static final Set<String> ESTADOS = Set.of("Pendiente", "Confirmada", "Completada", "Cancelada");
    private static final Pattern FECHA = Pattern.compile("^\\d{4}-\\d{2}-\\d{2}$");

    private final SupabaseClient db;

    public CitasController(SupabaseClient db) {
        this.db = db;
    }

    private static boolean fechaValida(Object valor) {
        if (!(valor instanceof String texto) || !FECHA.matcher(texto).matches()) {
            return false;
        }

        try {
            LocalDate.parse(texto);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }

    /** parcial=true valida solo los campos enviados (equivale a CitaUpdate). */
    private static Map<String, Object> validar(Map<String, Object> recibido, boolean parcial) {
        Map<String, Object> cuerpo = Validacion.cuerpo(recibido);
        Map<String, Object> datos = new LinkedHashMap<>();
        List<Issue> issues = new ArrayList<>();

        for (String campo : List.of("mascota_id", "fecha", "motivo", "estado")) {
            Object valor = cuerpo.get(campo);

            if (valor == null) {
                if (campo.equals("estado") && !parcial) {
                    datos.put("estado", "Pendiente");
                } else if (!parcial) {
                    issues.add(Issue.invalid(campo, "Field required"));
                }
                continue;
            }

            switch (campo) {
                case "mascota_id" -> {
                    Long id = Validacion.entero(valor);
                    if (id == null) {
                        issues.add(Issue.invalid(campo, "Input should be a valid integer"));
                    } else if (id <= 0) {
                        issues.add(Issue.invalid(campo, "Input should be greater than 0"));
                    }
                }
                case "fecha" -> {
                    if (!fechaValida(valor)) {
                        issues.add(Issue.invalid(campo, "Input should be a valid date"));
                    }
                }
                case "motivo" -> Validacion.texto(campo, valor, 2, 200, issues);
                case "estado" -> {
                    if (!(valor instanceof String estado) || !ESTADOS.contains(estado)) {
                        issues.add(Issue.invalid(campo,
                                "String should match pattern '^(Pendiente|Confirmada|Completada|Cancelada)$'"));
                    }
                }
                default -> {
                }
            }

            datos.put(campo, valor);
        }

        if (!issues.isEmpty()) {
            throw Validacion.invalido(issues);
        }
        return datos;
    }

    private void verificarMascota(Object mascotaId) {
        List<Map<String, Object>> filas = db.select("mascotas", "id", Map.of("id", mascotaId), null);

        if (filas.isEmpty()) {
            throw new ApiException(404, "La mascota indicada no existe");
        }
    }

    @GetMapping({"", "/"})
    public List<Map<String, Object>> listar() {
        return db.select(TABLA, COLUMNAS, Map.of(), "id");
    }

    @GetMapping("/mascota/{mascotaId}")
    public List<Map<String, Object>> listarDeMascota(@PathVariable String mascotaId) {
        int id = Validacion.id(mascotaId, "mascota_id");

        verificarMascota(id);

        return db.select(TABLA, COLUMNAS, Map.of("mascota_id", id), "fecha");
    }

    @GetMapping("/{citaId}")
    public Map<String, Object> obtener(@PathVariable String citaId) {
        int id = Validacion.id(citaId, "cita_id");
        List<Map<String, Object>> filas = db.select(TABLA, COLUMNAS, Map.of("id", id), null);

        if (filas.isEmpty()) {
            throw new ApiException(404, "Cita no encontrada");
        }
        return filas.get(0);
    }

    @PostMapping({"", "/"})
    public ResponseEntity<Map<String, Object>> crear(@RequestBody(required = false) Map<String, Object> cuerpo) {
        Map<String, Object> datos = validar(cuerpo, false);

        verificarMascota(datos.get("mascota_id"));

        List<Map<String, Object>> filas = db.insert(TABLA, datos, COLUMNAS);

        if (filas.isEmpty()) {
            throw new ApiException(400, "No fue posible crear la cita");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(filas.get(0));
    }

    @PutMapping("/{citaId}")
    public Map<String, Object> actualizar(
            @PathVariable String citaId, @RequestBody(required = false) Map<String, Object> cuerpo) {
        int id = Validacion.id(citaId, "cita_id");
        Map<String, Object> cambios = validar(cuerpo, true);

        if (db.select(TABLA, COLUMNAS, Map.of("id", id), null).isEmpty()) {
            throw new ApiException(404, "Cita no encontrada");
        }

        if (cambios.isEmpty()) {
            throw new ApiException(400, "No se enviaron datos para actualizar");
        }

        if (cambios.containsKey("mascota_id")) {
            verificarMascota(cambios.get("mascota_id"));
        }

        List<Map<String, Object>> filas = db.update(TABLA, Map.of("id", id), cambios, COLUMNAS);

        if (filas.isEmpty()) {
            throw new ApiException(404, "No fue posible actualizar la cita");
        }
        return filas.get(0);
    }

    @DeleteMapping("/{citaId}")
    public ResponseEntity<Void> eliminar(@PathVariable String citaId) {
        int id = Validacion.id(citaId, "cita_id");
        List<Map<String, Object>> filas = db.delete(TABLA, Map.of("id", id), "id");

        if (filas.isEmpty()) {
            throw new ApiException(404, "Cita no encontrada");
        }
        return ResponseEntity.noContent().build();
    }
}
