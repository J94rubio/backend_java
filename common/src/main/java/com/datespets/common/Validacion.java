package com.datespets.common;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Utilidades de validación equivalentes a las de pydantic en el backend Python. */
public final class Validacion {

    private Validacion() {
    }

    public static Map<String, Object> cuerpo(Map<String, Object> recibido) {
        return recibido == null ? new LinkedHashMap<>() : recibido;
    }

    public static ApiException invalido(List<Issue> issues) {
        return new ApiException(422, issues);
    }

    public static int id(String valor, String nombre) {
        try {
            int id = Integer.parseInt(valor);
            if (id >= 0) {
                return id;
            }
        } catch (NumberFormatException ignorado) {
            // se informa abajo como error de validación
        }

        throw invalido(List.of(new Issue(
                List.of("path", nombre), "Input should be a valid integer", "int_parsing")));
    }

    public static void texto(String campo, Object valor, int min, int max, List<Issue> issues) {
        if (!(valor instanceof String texto)) {
            issues.add(Issue.invalid(campo, "Input should be a valid string"));
            return;
        }

        int longitud = texto.codePointCount(0, texto.length());

        if (longitud < min) {
            issues.add(Issue.invalid(campo, "String should have at least " + min + " characters"));
        } else if (longitud > max) {
            issues.add(Issue.invalid(campo, "String should have at most " + max + " characters"));
        }
    }

    /** Devuelve el entero o null si el valor no es un número entero. */
    public static Long entero(Object valor) {
        if (valor instanceof Integer || valor instanceof Long || valor instanceof Short) {
            return ((Number) valor).longValue();
        }
        return null;
    }
}
