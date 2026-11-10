package com.hostel.ordering.exception;

import com.hostel.ordering.model.ErrorResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;

import com.hostel.ordering.pms.PmsException;

import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@ControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDeniedException(AccessDeniedException ex) {
        log.warn("Access denied: {}", ex.getMessage());
        return new ResponseEntity<>(
                new ErrorResponse("You do not have permission to perform this action"),
                HttpStatus.FORBIDDEN);
    }

    @ExceptionHandler(AuthenticationException.class)
    public ResponseEntity<ErrorResponse> handleAuthenticationException(AuthenticationException ex) {
        log.warn("Authentication failed: {}", ex.getMessage());
        return new ResponseEntity<>(
                new ErrorResponse("Authentication required or session expired"),
                HttpStatus.UNAUTHORIZED);
    }

    /**
     * Field-level validation messages are written for the person filling the form, so they are
     * safe and useful to return. The raw exception text is not - it carries the bound object
     * and its class name.
     */
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(MethodArgumentNotValidException ex) {
        String fields = ex.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining("; "));
        return new ResponseEntity<>(
                new ErrorResponse(fields.isBlank() ? "Validation failed" : fields),
                HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ErrorResponse> handleResponseStatusException(ResponseStatusException ex) {
        return new ResponseEntity<>(new ErrorResponse(ex.getReason()), ex.getStatusCode());
    }

    /**
     * Messages on IllegalArgumentException are deliberate and addressed to the caller - "Username
     * is already taken!", "Cannot delete the last administrator" - so they are passed through.
     */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException ex) {
        return new ResponseEntity<>(new ErrorResponse(ex.getMessage()), HttpStatus.BAD_REQUEST);
    }

    @ExceptionHandler(ChargeUnconfirmedException.class)
    public ResponseEntity<ErrorResponse> handleChargeUnconfirmed(ChargeUnconfirmedException ex) {
        return new ResponseEntity<>(new ErrorResponse(ex.getMessage()), HttpStatus.CONFLICT);
    }

    @ExceptionHandler(com.hostel.ordering.ezee.EzeeUnavailableException.class)
    public ResponseEntity<ErrorResponse> handleEzeeUnavailable(com.hostel.ordering.ezee.EzeeUnavailableException ex) {
        log.warn("eZee unavailable: {}", ex.getMessage());
        return new ResponseEntity<>(
                new ErrorResponse("eZee is not reachable right now. Try again in a moment."),
                HttpStatus.BAD_GATEWAY);
    }

    /**
     * Body is {errorCode, message} with a fixed text per code. The exception message may carry
     * eZee response detail, so it is logged only and never returned.
     */
    @ExceptionHandler(PmsException.class)
    public ResponseEntity<Map<String, String>> handlePms(PmsException ex) {
        log.warn("PMS {}: {}", ex.code(), ex.getMessage());
        HttpStatus status;
        String message;
        switch (ex.code()) {
            case "PMS_AUTH" -> { status = HttpStatus.BAD_GATEWAY; message = "The PMS rejected our credentials."; }
            case "PMS_UPSTREAM" -> { status = HttpStatus.BAD_GATEWAY; message = "The PMS returned an error. Try again in a moment."; }
            case "PMS_BUSY" -> { status = HttpStatus.SERVICE_UNAVAILABLE; message = "The PMS is busy. Try again in a moment."; }
            case "PMS_NOT_ENABLED" -> { status = HttpStatus.NOT_IMPLEMENTED; message = "This PMS feature is not enabled."; }
            case "PMS_FEATURE_DISABLED" -> { status = HttpStatus.FORBIDDEN; message = "This PMS action is switched off."; }
            case "PMS_UNCONFIRMED" -> { status = HttpStatus.CONFLICT; message = "The PMS did not confirm the change. Check before retrying."; }
            case "PMS_IN_PROGRESS" -> { status = HttpStatus.CONFLICT; message = "This request is already in progress."; }
            case "PMS_TARGET_BUSY" -> { status = HttpStatus.CONFLICT; message = "Another change to this target is in progress."; }
            case "IDEMPOTENCY_MISMATCH" -> { status = HttpStatus.CONFLICT; message = "This request key was already used for a different request."; }
            default -> { status = HttpStatus.BAD_GATEWAY; message = "The PMS request failed."; }
        }
        return new ResponseEntity<>(Map.of("errorCode", ex.code(), "message", message), status);
    }

    /**
     * Anything unhandled. The message MUST NOT reach the client: it is written for developers and
     * routinely contains internal detail. A Mongo authentication failure here once served the
     * database username and auth mechanism from an unauthenticated /health request.
     *
     * <p>The reference is returned instead so a staff report can be tied to the logged stack.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleAllExceptions(Exception ex) {
        String reference = UUID.randomUUID().toString().substring(0, 8);
        log.error("Unhandled exception [ref={}]", reference, ex);
        return new ResponseEntity<>(
                new ErrorResponse("Something went wrong. Reference: " + reference),
                HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
