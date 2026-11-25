package com.foodly.community.application.queries;

public record GetReviewSummaryByHuariqueQuery(String huariqueId) {

    public GetReviewSummaryByHuariqueQuery {
        if (huariqueId == null || huariqueId.isBlank()) {
            throw new IllegalArgumentException("huariqueId must not be null or blank");
        }
    }
}
