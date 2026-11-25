package com.foodly.community.infrastructure.config;

import com.foodly.community.application.commandhandlers.AddFavoriteCommandHandler;
import com.foodly.community.application.commandhandlers.CreateReviewCommandHandler;
import com.foodly.community.application.commandhandlers.RemoveFavoriteCommandHandler;
import com.foodly.community.application.ports.CurrentExplorer;
import com.foodly.community.application.ports.ReviewIdGenerator;
import com.foodly.community.application.queryhandlers.GetFavoritesByExplorerQueryHandler;
import com.foodly.community.application.queryhandlers.GetReviewsByHuariqueQueryHandler;
import com.foodly.community.application.queryhandlers.GetReviewSummaryByHuariqueQueryHandler;
import com.foodly.community.domain.repository.IFavoriteRepository;
import com.foodly.community.domain.repository.IReviewRepository;
import com.foodly.community.domain.service.ReputationService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Clock;

/** Sin repositorios de reserva: dependencias obligatorias o arranque fallido. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = {"community.integrations.jwt.enabled", "community.persistence.mongodb.enabled"}, havingValue = "true")
public class CommunityApplicationConfiguration {

    @Bean
    CreateReviewCommandHandler createReviewCommandHandler(IReviewRepository repository, CurrentExplorer explorer,
                                                         ReviewIdGenerator ids, Clock clock) {
        return new CreateReviewCommandHandler(repository, explorer, ids, clock);
    }

    @Bean
    AddFavoriteCommandHandler addFavoriteCommandHandler(IFavoriteRepository repository, CurrentExplorer explorer) {
        return new AddFavoriteCommandHandler(repository, explorer);
    }

    @Bean
    RemoveFavoriteCommandHandler removeFavoriteCommandHandler(IFavoriteRepository repository, CurrentExplorer explorer) {
        return new RemoveFavoriteCommandHandler(repository, explorer);
    }

    @Bean
    GetReviewsByHuariqueQueryHandler getReviewsByHuariqueQueryHandler(IReviewRepository repository) {
        return new GetReviewsByHuariqueQueryHandler(repository);
    }

    @Bean
    GetFavoritesByExplorerQueryHandler getFavoritesByExplorerQueryHandler(IFavoriteRepository repository, CurrentExplorer explorer) {
        return new GetFavoritesByExplorerQueryHandler(repository, explorer);
    }

    @Bean
    GetReviewSummaryByHuariqueQueryHandler getReviewSummaryByHuariqueQueryHandler(IReviewRepository repository) {
        return new GetReviewSummaryByHuariqueQueryHandler(repository, new ReputationService());
    }
}
