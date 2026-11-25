package com.foodly.community.interfaces.dto;

/** Error público estable: no incluye mensajes internos, datos rechazados, rutas o trazas. */
public record ApiErrorResource(int status, String code, String message) {
}
