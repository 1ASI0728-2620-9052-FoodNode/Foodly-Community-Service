package com.foodly.community.interfaces.rest;

import com.foodly.community.application.commandhandlers.CreateReviewCommandHandler;
import com.foodly.community.application.commands.CreateReviewCommand;
import com.foodly.community.application.queries.GetReviewsByHuariqueQuery;
import com.foodly.community.application.queryhandlers.GetReviewsByHuariqueQueryHandler;
import com.foodly.community.interfaces.dto.CreateReviewResource;
import com.foodly.community.interfaces.dto.ReviewResource;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/community/huariques/{huariqueId}/reviews")
@ConditionalOnProperty(name = {"community.integrations.jwt.enabled", "community.persistence.mongodb.enabled"}, havingValue = "true")
public class ReviewController {

    private final CreateReviewCommandHandler createReview;
    private final GetReviewsByHuariqueQueryHandler getReviews;

    public ReviewController(CreateReviewCommandHandler createReview, GetReviewsByHuariqueQueryHandler getReviews) {
        this.createReview = createReview;
        this.getReviews = getReviews;
    }

    @PostMapping
    public ResponseEntity<ReviewResource> create(
            @PathVariable("huariqueId") @NotBlank String huariqueId,
            @Valid @RequestBody CreateReviewResource resource) {
        var review = createReview.handle(new CreateReviewCommand(huariqueId, resource.rating(), resource.comment()));
        return ResponseEntity.status(HttpStatus.CREATED).body(ReviewResource.from(review));
    }

    @GetMapping
    public List<ReviewResource> getByHuarique(@PathVariable("huariqueId") @NotBlank String huariqueId) {
        return getReviews.handle(new GetReviewsByHuariqueQuery(huariqueId)).stream()
                .map(ReviewResource::from).toList();
    }
}
