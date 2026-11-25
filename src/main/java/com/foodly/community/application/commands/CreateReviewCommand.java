package com.foodly.community.application.commands;

/** La identidad del explorador, reviewId y createdAt no son entradas del cliente. */
public record CreateReviewCommand(String huariqueId, int rating, String comment) {
}
