package com.datespets.ia;

/** Error devuelto por la API de Gemini. */
public class GeminiException extends RuntimeException {

    private final int status;

    public GeminiException(int status, String cuerpo) {
        super("GeminiException: " + status + " " + cuerpo);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
