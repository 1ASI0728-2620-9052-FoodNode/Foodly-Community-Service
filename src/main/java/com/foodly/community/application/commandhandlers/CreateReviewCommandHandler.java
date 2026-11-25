package com.foodly.community.application.commandhandlers;

import com.foodly.community.application.commands.CreateReviewCommand;
import com.foodly.community.application.ports.CurrentExplorer;
import com.foodly.community.application.ports.ReviewIdGenerator;
import com.foodly.community.domain.model.Rating;
import com.foodly.community.domain.model.Review;
import com.foodly.community.domain.model.ReviewComment;
import com.foodly.community.domain.repository.IReviewRepository;

import java.time.Clock;
import java.util.Objects;

public final class CreateReviewCommandHandler {

    private final IReviewRepository repository;
    private final CurrentExplorer currentExplorer;
    private final ReviewIdGenerator idGenerator;
    private final Clock clock;

    public CreateReviewCommandHandler(IReviewRepository repository, CurrentExplorer currentExplorer,
                                      ReviewIdGenerator idGenerator, Clock clock) {
        this.repository = Objects.requireNonNull(repository);
        this.currentExplorer = Objects.requireNonNull(currentExplorer);
        this.idGenerator = Objects.requireNonNull(idGenerator);
        this.clock = Objects.requireNonNull(clock);
    }

    public Review handle(CreateReviewCommand command) {
        Objects.requireNonNull(command, "command is required");
        String explorerId = currentExplorer.requireExplorerId();
        var rating = new Rating(command.rating());
        var review = new Review(idGenerator.nextId(), explorerId, command.huariqueId(),
                rating, new ReviewComment(command.comment()), clock.instant());
        return repository.save(review);
    }
}
