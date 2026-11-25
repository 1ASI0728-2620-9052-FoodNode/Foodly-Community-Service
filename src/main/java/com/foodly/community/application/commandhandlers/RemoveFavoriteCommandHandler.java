package com.foodly.community.application.commandhandlers;

import com.foodly.community.application.commands.RemoveFavoriteCommand;
import com.foodly.community.application.errors.FavoriteNotFoundException;
import com.foodly.community.application.ports.CurrentExplorer;
import com.foodly.community.domain.model.FavoriteId;
import com.foodly.community.domain.repository.IFavoriteRepository;

import java.util.Objects;

public final class RemoveFavoriteCommandHandler {

    private final IFavoriteRepository repository;
    private final CurrentExplorer currentExplorer;

    public RemoveFavoriteCommandHandler(IFavoriteRepository repository, CurrentExplorer currentExplorer) {
        this.repository = Objects.requireNonNull(repository);
        this.currentExplorer = Objects.requireNonNull(currentExplorer);
    }

    public void handle(RemoveFavoriteCommand command) {
        Objects.requireNonNull(command, "command is required");
        var id = new FavoriteId(currentExplorer.requireExplorerId(), command.huariqueId());
        if (!repository.deleteById(id)) {
            throw new FavoriteNotFoundException(id);
        }
    }
}
