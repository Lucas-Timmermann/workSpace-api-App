package com.example.api_tp.client.arca.exception;


public class ArcaCommunicationException extends ArcaException {
    private final Integer httpStatus;
    private final String responseBody;

    public ArcaCommunicationException(String message, Throwable cause) {
        super(message, cause);
        this.httpStatus = null;
        this.responseBody = null;
    }

    public ArcaCommunicationException(String message, Integer httpStatus, String responseBody) {
        super(message);
        this.httpStatus = httpStatus;
        this.responseBody = responseBody;
    }

    public Integer getHttpStatus() {
        return httpStatus;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public boolean isTimeout() {
        Throwable c = getCause();
        return c instanceof java.net.http.HttpTimeoutException
                || c instanceof java.net.SocketTimeoutException;
    }
}
