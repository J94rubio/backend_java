package com.datespets.common;

import java.util.List;

/** Formato de error de validación compatible con FastAPI. */
public record Issue(List<String> loc, String msg, String type) {

    public static Issue invalid(String campo, String msg) {
        return new Issue(List.of("body", campo), msg, "value_error");
    }
}
