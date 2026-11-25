package com.foodly.community.application.ports;

/** Provee la identidad nueva de la reseña sin imponer formato ni tecnología al caso de uso. */
@FunctionalInterface
public interface ReviewIdGenerator {

    String nextId();
}
