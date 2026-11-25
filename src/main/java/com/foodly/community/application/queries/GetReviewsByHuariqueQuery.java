package com.foodly.community.application.queries;

public record GetReviewsByHuariqueQuery(String huariqueId) {

    public GetReviewsByHuariqueQuery {
        if (huariqueId == null || huariqueId.isBlank()) {
            throw new IllegalArgumentException("huariqueId must not be null or blank");
        }
    }
}
