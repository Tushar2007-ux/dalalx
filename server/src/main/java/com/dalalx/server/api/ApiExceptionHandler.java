package com.dalalx.server.api;

import com.dalalx.core.exchange.UnknownSymbolException;
import com.dalalx.server.api.Dtos.ApiError;
import java.util.concurrent.RejectedExecutionException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

/**
 * Turns exceptions into clean JSON errors with the right HTTP status, in one
 * place, instead of a try/catch in every controller method.
 *
 * <ul>
 *   <li>unknown stock → <b>404</b> Not Found</li>
 *   <li>invalid input (bad qty, missing price, unreadable JSON) → <b>400</b> Bad Request</li>
 *   <li>exchange shutting down → <b>503</b> Service Unavailable</li>
 *   <li>a controller's own {@link ResponseStatusException} (e.g. no such bot) → its status</li>
 * </ul>
 * The body is always {@code {"error": "..."}}, so the browser can show it to the user.
 */
@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(UnknownSymbolException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError unknownSymbol(UnknownSymbolException e) {
        return new ApiError(e.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError badRequest(IllegalArgumentException e) {
        return new ApiError(e.getMessage());
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError unreadable(Exception e) {
        return new ApiError("malformed request: check field names, types and values");
    }

    @ExceptionHandler({RejectedExecutionException.class, IllegalStateException.class})
    @ResponseStatus(HttpStatus.SERVICE_UNAVAILABLE)
    public ApiError unavailable(RuntimeException e) {
        return new ApiError(e.getMessage());
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiError> withStatus(ResponseStatusException e) {
        return ResponseEntity.status(e.getStatusCode()).body(new ApiError(e.getReason()));
    }
}
