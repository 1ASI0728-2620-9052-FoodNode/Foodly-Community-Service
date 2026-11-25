package com.foodly.community.domain.model;

/**
 * Contenido inmutable sin restricciones de longitud ni recorte de texto.
 * La ausencia (null) se representa como contenido vacío, pues el texto no es obligatorio.
 */
public record ReviewComment(String value) {

    public ReviewComment {
        value = value == null ? "" : value;
    }
}
