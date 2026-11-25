package com.foodly.community.application.queryhandlers;

import com.foodly.community.application.queries.GetReviewSummaryByHuariqueQuery;
import com.foodly.community.application.queries.ReviewSummary;
import com.foodly.community.domain.repository.IReviewRepository;
import com.foodly.community.domain.service.ReputationService;

import java.util.List;
import java.util.Objects;

public final class GetReviewSummaryByHuariqueQueryHandler {

    private final IReviewRepository repository;
    private final ReputationService reputationService;

    public GetReviewSummaryByHuariqueQueryHandler(IReviewRepository repository, ReputationService reputationService) {
        this.repository = Objects.requireNonNull(repository);
        this.reputationService = Objects.requireNonNull(reputationService);
    }

    public ReviewSummary handle(GetReviewSummaryByHuariqueQuery query) {
        Objects.requireNonNull(query, "query is required");
        var reviews = List.copyOf(repository.findByHuariqueId(query.huariqueId()));
        return new ReviewSummary(query.huariqueId(), reviews.size(),
                reputationService.calculateAverage(query.huariqueId(), reviews));
    }
}
