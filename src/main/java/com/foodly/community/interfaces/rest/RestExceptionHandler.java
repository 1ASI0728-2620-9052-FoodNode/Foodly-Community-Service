package com.foodly.community.interfaces.rest;

import com.foodly.community.application.errors.AuthenticationRequiredException;
import com.foodly.community.application.errors.FavoriteNotFoundException;
import com.foodly.community.domain.repository.DuplicateFavoriteException;
import com.foodly.community.interfaces.dto.ApiErrorResource;
import jakarta.validation.ConstraintViolationException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/** Traduce excepciones del transporte y casos de uso sin divulgar sus mensajes o causas. */
@RestControllerAdvice
public class RestExceptionHandler extends ResponseEntityExceptionHandler {

    @ExceptionHandler({IllegalArgumentException.class, ConstraintViolationException.class})
    public ResponseEntity<Object> invalidInput(Exception exception) {
        return error(HttpStatus.BAD_REQUEST, new HttpHeaders(), "INVALID_REQUEST", "Invalid request");
    }

    @ExceptionHandler(AuthenticationRequiredException.class)
    public ResponseEntity<Object> authenticationRequired(AuthenticationRequiredException exception) {
        var headers = new HttpHeaders();
        headers.set(HttpHeaders.WWW_AUTHENTICATE, "Bearer");
        return error(HttpStatus.UNAUTHORIZED, headers, "AUTHENTICATION_REQUIRED", "Authentication required");
    }

    @ExceptionHandler(FavoriteNotFoundException.class)
    public ResponseEntity<Object> favoriteNotFound(FavoriteNotFoundException exception) {
        return error(HttpStatus.NOT_FOUND, new HttpHeaders(), "FAVORITE_NOT_FOUND", "Favorite not found");
    }

    @ExceptionHandler(DuplicateFavoriteException.class)
    public ResponseEntity<Object> duplicateFavorite(DuplicateFavoriteException exception) {
        return error(HttpStatus.CONFLICT, new HttpHeaders(), "FAVORITE_ALREADY_EXISTS", "Favorite already exists");
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<Object> unexpected(Exception exception) {
        return error(HttpStatus.INTERNAL_SERVER_ERROR, new HttpHeaders(), "INTERNAL_ERROR", "Internal server error");
    }

    @Override
    protected ResponseEntity<Object> handleExceptionInternal(Exception exception, Object body,
            HttpHeaders headers, HttpStatusCode status, WebRequest request) {
        if (status.value() == 400) {
            return error(status, headers, "INVALID_REQUEST", "Invalid request");
        }
        return error(status, headers, "HTTP_ERROR", "Request could not be processed");
    }

    private ResponseEntity<Object> error(HttpStatusCode status, HttpHeaders headers, String code, String message) {
        return new ResponseEntity<>(new ApiErrorResource(status.value(), code, message), headers, status);
    }
}
