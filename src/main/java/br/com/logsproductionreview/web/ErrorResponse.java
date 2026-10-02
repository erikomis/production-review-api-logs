package br.com.logsproductionreview.web;

import org.springframework.http.HttpStatus;

/** Mesmo formato de erro da API principal: {@code {message, httpStatus, statusCode}}. */
public record ErrorResponse(String message, String httpStatus, int statusCode) {

    public static ErrorResponse of(HttpStatus status, String message) {
        return new ErrorResponse(message, status.name(), status.value());
    }
}
