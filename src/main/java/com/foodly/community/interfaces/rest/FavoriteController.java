package com.foodly.community.interfaces.rest;

import com.foodly.community.application.commandhandlers.AddFavoriteCommandHandler;
import com.foodly.community.application.commandhandlers.RemoveFavoriteCommandHandler;
import com.foodly.community.application.commands.AddFavoriteCommand;
import com.foodly.community.application.commands.RemoveFavoriteCommand;
import com.foodly.community.application.queries.GetFavoritesByExplorerQuery;
import com.foodly.community.application.queryhandlers.GetFavoritesByExplorerQueryHandler;
import com.foodly.community.interfaces.dto.FavoriteResource;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/community/favorites")
@ConditionalOnProperty(name = {"community.integrations.jwt.enabled", "community.persistence.mongodb.enabled"}, havingValue = "true")
public class FavoriteController {

    private final AddFavoriteCommandHandler addFavorite;
    private final RemoveFavoriteCommandHandler removeFavorite;
    private final GetFavoritesByExplorerQueryHandler getFavorites;

    public FavoriteController(AddFavoriteCommandHandler addFavorite, RemoveFavoriteCommandHandler removeFavorite,
                              GetFavoritesByExplorerQueryHandler getFavorites) {
        this.addFavorite = addFavorite;
        this.removeFavorite = removeFavorite;
        this.getFavorites = getFavorites;
    }

    @PostMapping("/{huariqueId}")
    public ResponseEntity<FavoriteResource> add(
            @PathVariable("huariqueId") @NotBlank String huariqueId,
            @RequestBody(required = false) Map<String, Object> body) {
        rejectBody(body);
        var favorite = addFavorite.handle(new AddFavoriteCommand(huariqueId));
        return ResponseEntity.status(HttpStatus.CREATED).body(FavoriteResource.from(favorite));
    }

    @DeleteMapping("/{huariqueId}")
    public ResponseEntity<Void> remove(
            @PathVariable("huariqueId") @NotBlank String huariqueId,
            @RequestBody(required = false) Map<String, Object> body) {
        rejectBody(body);
        removeFavorite.handle(new RemoveFavoriteCommand(huariqueId));
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public List<FavoriteResource> getMine() {
        return getFavorites.handle(new GetFavoritesByExplorerQuery()).stream()
                .map(FavoriteResource::from).toList();
    }

    private void rejectBody(Map<String, Object> body) {
        if (body != null) {
            throw new IllegalArgumentException("This endpoint does not accept a request body");
        }
    }
}
