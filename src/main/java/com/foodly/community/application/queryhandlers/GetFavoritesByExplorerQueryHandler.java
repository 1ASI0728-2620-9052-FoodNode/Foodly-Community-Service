package com.foodly.community.application.queryhandlers;

import com.foodly.community.application.ports.CurrentExplorer;
import com.foodly.community.application.queries.GetFavoritesByExplorerQuery;
import com.foodly.community.domain.model.Favorite;
import com.foodly.community.domain.repository.IFavoriteRepository;

import java.util.List;
import java.util.Objects;

public final class GetFavoritesByExplorerQueryHandler {

    private final IFavoriteRepository repository;
    private final CurrentExplorer currentExplorer;

    public GetFavoritesByExplorerQueryHandler(IFavoriteRepository repository, CurrentExplorer currentExplorer) {
        this.repository = Objects.requireNonNull(repository);
        this.currentExplorer = Objects.requireNonNull(currentExplorer);
    }

    public List<Favorite> handle(GetFavoritesByExplorerQuery query) {
        Objects.requireNonNull(query, "query is required");
        return List.copyOf(repository.findByExplorerId(currentExplorer.requireExplorerId()));
    }
}
