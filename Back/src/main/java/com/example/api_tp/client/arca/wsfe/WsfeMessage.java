package com.example.api_tp.client.arca.wsfe;


public class WsfeMessage {
    private final Integer code;
    private final String message;

    public WsfeMessage(Integer code, String message) {
        this.code = code;
        this.message = message;
    }

    public Integer getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        if (code == null && (message == null || message.isBlank())) {
            return "";
        }
        if (code == null) {
            return message;
        }
        return code + ": " + (message == null ? "" : message);
    }
}
