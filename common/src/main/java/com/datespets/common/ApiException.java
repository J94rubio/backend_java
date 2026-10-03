package com.datespets.common;

/** Error HTTP que se serializa como {"detail": ...}. */
public class ApiException extends RuntimeException {

    private final int status;
    private final transient Object detail;

    public ApiException(int status, Object detail) {
        super(String.valueOf(detail));
        this.status = status;
        this.detail = detail;
    }

    public int getStatus() {
        return status;
    }

    public Object getDetail() {
        return detail;
    }
}
