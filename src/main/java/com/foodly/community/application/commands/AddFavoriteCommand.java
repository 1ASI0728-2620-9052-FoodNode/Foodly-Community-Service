package com.foodly.community.application.commands;

/** Agrega un huarique a los favoritos del explorador obtenido del contexto de seguridad. */
public record AddFavoriteCommand(String huariqueId) {
}
