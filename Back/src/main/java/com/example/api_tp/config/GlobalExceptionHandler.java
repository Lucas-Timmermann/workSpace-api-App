package com.example.api_tp.config;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.BindException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.example.api_tp.client.arca.exception.ArcaCommunicationException;
import com.example.api_tp.client.arca.exception.ArcaConfigurationException;
import com.example.api_tp.client.arca.exception.ArcaException;

import jakarta.validation.ConstraintViolationException;


@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
        HttpStatus status = HttpStatus.resolve(ex.getStatusCode().value());
        return error(status == null ? HttpStatus.INTERNAL_SERVER_ERROR : status, "SOLICITUD",
                ex.getReason() == null ? "La operación no pudo completarse" : ex.getReason(), List.of());
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleMethodArgumentNotValid(MethodArgumentNotValidException ex) {
        return error(HttpStatus.BAD_REQUEST, "VALIDACION", "La solicitud contiene datos inválidos",
                detalles(ex.getBindingResult().getFieldErrors()));
    }

    @ExceptionHandler(BindException.class)
    public ResponseEntity<Map<String, Object>> handleBind(BindException ex) {
        return error(HttpStatus.BAD_REQUEST, "VALIDACION", "La solicitud contiene datos inválidos",
                detalles(ex.getBindingResult().getFieldErrors()));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<Map<String, Object>> handleConstraintViolation(ConstraintViolationException ex) {
        List<String> detalles = ex.getConstraintViolations().stream()
                .map(v -> v.getPropertyPath() + ": " + v.getMessage())
                .toList();
        return error(HttpStatus.BAD_REQUEST, "VALIDACION", "La solicitud contiene datos inválidos", detalles);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleNotReadable(HttpMessageNotReadableException ex) {
        Throwable causa = ex.getMostSpecificCause();
        String detalle = causa.getMessage() == null || causa.getMessage().isBlank()
                ? "Formato JSON inválido o propiedad no permitida"
                : causa.getMessage();
        return error(HttpStatus.BAD_REQUEST, "FORMATO",
                "El cuerpo de la solicitud no es válido o contiene propiedades no permitidas",
                List.of(detalle));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    public ResponseEntity<Map<String, Object>> handleTypeMismatch(MethodArgumentTypeMismatchException ex) {
        return error(HttpStatus.BAD_REQUEST, "PARAMETRO",
                "El parámetro '" + ex.getName() + "' tiene un formato inválido", List.of());
    }

    @ExceptionHandler(MissingServletRequestParameterException.class)
    public ResponseEntity<Map<String, Object>> handleMissingParameter(MissingServletRequestParameterException ex) {
        return error(HttpStatus.BAD_REQUEST, "PARAMETRO",
                "Falta el parámetro obligatorio '" + ex.getParameterName() + "'", List.of());
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        return error(HttpStatus.METHOD_NOT_ALLOWED, "METODO",
                "El método HTTP " + ex.getMethod() + " no está permitido para esta ruta", List.of());
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    public ResponseEntity<Map<String, Object>> handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        return error(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "CONTENIDO",
                "Content-Type no soportado. Use application/json", List.of());
    }

    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    public ResponseEntity<Map<String, Object>> handleMediaTypeNotAcceptable(HttpMediaTypeNotAcceptableException ex) {
        return error(HttpStatus.NOT_ACCEPTABLE, "CONTENIDO",
                "No se puede generar una respuesta con el tipo solicitado en Accept", List.of());
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<Map<String, Object>> handleNoResource(NoResourceFoundException ex) {
        return error(HttpStatus.NOT_FOUND, "NO_ENCONTRADO", "Recurso no encontrado", List.of());
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<Map<String, Object>> handleDataIntegrity(DataIntegrityViolationException ex) {
        return error(HttpStatus.CONFLICT, "CONFLICTO",
                "La operación viola una restricción de integridad de datos", List.of());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException ex) {
        return error(HttpStatus.BAD_REQUEST, "SOLICITUD",
                ex.getMessage() == null ? "Solicitud inválida" : ex.getMessage(), List.of());
    }

    @ExceptionHandler(ArcaCommunicationException.class)
    public ResponseEntity<Map<String, Object>> handleArcaCommunication(ArcaCommunicationException ex) {
        return error(HttpStatus.BAD_GATEWAY, "ARCA_COMUNICACION",
                ex.getMessage() == null ? "Error de comunicación con ARCA" : ex.getMessage(), List.of());
    }

    @ExceptionHandler(ArcaConfigurationException.class)
    public ResponseEntity<Map<String, Object>> handleArcaConfiguration(ArcaConfigurationException ex) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "ARCA_CONFIGURACION",
                ex.getMessage() == null ? "Configuración ARCA inválida" : ex.getMessage(), List.of());
    }

    @ExceptionHandler(ArcaException.class)
    public ResponseEntity<Map<String, Object>> handleArca(ArcaException ex) {
        return error(HttpStatus.BAD_GATEWAY, "ARCA",
                ex.getMessage() == null ? "ARCA rechazó la operación" : ex.getMessage(), List.of());
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception ex) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, "INTERNO",
                "Error interno del servidor", List.of());
    }

    private static List<String> detalles(List<FieldError> errores) {
        List<String> detalles = new ArrayList<>();
        for (FieldError error : errores) {
            detalles.add(error.getField() + ": " + error.getDefaultMessage());
        }
        return detalles;
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String tipo,
            String message, List<String> detalles) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("timestamp", OffsetDateTime.now().toString());
        body.put("status", status.value());
        body.put("error", status.getReasonPhrase());
        body.put("tipo", tipo);
        body.put("message", message);
        if (!detalles.isEmpty()) {
            body.put("detalles", detalles);
        }
        return ResponseEntity.status(status).body(body);
    }
}
