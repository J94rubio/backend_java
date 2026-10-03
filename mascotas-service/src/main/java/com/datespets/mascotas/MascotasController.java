package com.datespets.mascotas;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
@RequestMapping("/api/mascotas")
public class MascotasController {

    private static final String TABLA = "mascotas";
    private static final String COLUMNAS = "id,nombre,especie,raza,edad,propietario";
    private static final List<String> CAMPOS = List.of("nombre", "especie", "raza", "edad", "propietario");
    private static final Map<String, int[]> LIMITES = Map.of(
            "nombre", new int[] {2, 100},
            "especie", new int[] {2, 50},
            "raza", new int[] {2, 100},
            "propietario", new int[] {2, 150});

    private final SupabaseClient db;

    public MascotasController(SupabaseClient db) {
        this.db = db;
    }

    /** parcial=true valida solo los campos enviados (equivale a MascotaUpdate). */
    private static Map<String, Object> validar(Map<String, Object> recibido, boolean parcial) {
        Map<String, Object> cuerpo = Validacion.cuerpo(recibido);
        Map<String, Object> datos = new LinkedHashMap<>();
        List<Issue> issues = new ArrayList<>();

        for (String campo : CAMPOS) {
            Object valor = cuerpo.get(campo);

            if (valor == null) {
                if (!parcial) {
                    issues.add(Issue.invalid(campo, "Field required"));
                }
                continue;
            }

            if (campo.equals("edad")) {
                Long edad = Validacion.entero(valor);
                if (edad == null) {
                    issues.add(Issue.invalid(campo, "Input should be a valid integer"));
                } else if (edad < 0) {
                    issues.add(Issue.invalid(campo, "Input should be greater than or equal to 0"));
                }
            } else {
                int[] limites = LIMITES.get(campo);
                Validacion.texto(campo, valor, limites[0], limites[1], issues);
            }

            datos.put(campo, valor);
        }

        if (!issues.isEmpty()) {
            throw Validacion.invalido(issues);
        }
        return datos;
    }

    @GetMapping({"", "/"})
    public List<Map<String, Object>> listar() {
        return db.select(TABLA, COLUMNAS, Map.of(), "id");
    }

    @GetMapping("/{mascotaId}")
    public Map<String, Object> obtener(@PathVariable String mascotaId) {
        int id = Validacion.id(mascotaId, "mascota_id");
        List<Map<String, Object>> filas = db.select(TABLA, COLUMNAS, Map.of("id", id), null);

        if (filas.isEmpty()) {
            throw new ApiException(404, "Mascota no encontrada");
        }
        return filas.get(0);
    }

    @PostMapping({"", "/"})
    public ResponseEntity<Map<String, Object>> crear(@RequestBody(required = false) Map<String, Object> cuerpo) {
        Map<String, Object> datos = validar(cuerpo, false);
        List<Map<String, Object>> filas = db.insert(TABLA, datos, COLUMNAS);

        if (filas.isEmpty()) {
            throw new ApiException(400, "No fue posible crear la mascota");
        }
        return ResponseEntity.status(HttpStatus.CREATED).body(filas.get(0));
    }

    @PutMapping("/{mascotaId}")
    public Map<String, Object> actualizar(
            @PathVariable String mascotaId, @RequestBody(required = false) Map<String, Object> cuerpo) {
        int id = Validacion.id(mascotaId, "mascota_id");
        Map<String, Object> cambios = validar(cuerpo, true);

        if (cambios.isEmpty()) {
            throw new ApiException(400, "No se enviaron datos para actualizar");
        }

        List<Map<String, Object>> filas = db.update(TABLA, Map.of("id", id), cambios, COLUMNAS);

        if (filas.isEmpty()) {
            throw new ApiException(404, "Mascota no encontrada");
        }
        return filas.get(0);
    }

    @DeleteMapping("/{mascotaId}")
    public ResponseEntity<Void> eliminar(@PathVariable String mascotaId) {
        int id = Validacion.id(mascotaId, "mascota_id");
        List<Map<String, Object>> filas = db.delete(TABLA, Map.of("id", id), "id");

        if (filas.isEmpty()) {
            throw new ApiException(404, "Mascota no encontrada");
        }
        return ResponseEntity.noContent().build();
    }
}
