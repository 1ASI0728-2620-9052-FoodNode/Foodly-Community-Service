package com.foodly.community.application.queryhandlers;

import com.foodly.community.application.queries.GetReviewsByHuariqueQuery;
import com.foodly.community.domain.model.Review;
import com.foodly.community.domain.repository.IReviewRepository;

import java.util.List;
import java.util.Objects;

public final class GetReviewsByHuariqueQueryHandler {

    private final IReviewRepository repository;

    public GetReviewsByHuariqueQueryHandler(IReviewRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public List<Review> handle(GetReviewsByHuariqueQuery query) {
        Objects.requireNonNull(query, "query is required");
        return List.copyOf(repository.findByHuariqueId(query.huariqueId()));
    }
}
