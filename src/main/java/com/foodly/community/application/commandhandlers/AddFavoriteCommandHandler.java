package com.foodly.community.application.commandhandlers;

import com.foodly.community.application.commands.AddFavoriteCommand;
import com.foodly.community.application.ports.CurrentExplorer;
import com.foodly.community.domain.model.Favorite;
import com.foodly.community.domain.repository.IFavoriteRepository;

import java.util.Objects;

public final class AddFavoriteCommandHandler {

    private final IFavoriteRepository repository;
    private final CurrentExplorer currentExplorer;

    public AddFavoriteCommandHandler(IFavoriteRepository repository, CurrentExplorer currentExplorer) {
        this.repository = Objects.requireNonNull(repository);
        this.currentExplorer = Objects.requireNonNull(currentExplorer);
    }

    public Favorite handle(AddFavoriteCommand command) {
        Objects.requireNonNull(command, "command is required");
        var favorite = new Favorite(currentExplorer.requireExplorerId(), command.huariqueId());
        // save garantiza unicidad atómica; no se sustituye por un find previo.
        return repository.save(favorite);
    }
}
