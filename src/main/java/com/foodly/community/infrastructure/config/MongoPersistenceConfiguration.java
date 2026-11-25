package com.foodly.community.infrastructure.config;

import com.foodly.community.infrastructure.persistence.mongo.FavoriteRepository;
import com.foodly.community.infrastructure.persistence.mongo.ReviewRepository;
import com.mongodb.ConnectionString;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.autoconfigure.mongo.MongoProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.mongodb.core.MongoTemplate;

/** Composición explícita de persistencia; no registra handlers, seguridad ni mensajería. */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "community.persistence.mongodb", name = "enabled", havingValue = "true")
@EnableConfigurationProperties(MongoProperties.class)
public class MongoPersistenceConfiguration {

    @Bean(destroyMethod = "close")
    MongoClient communityMongoClient(MongoProperties properties) {
        if (properties.getUri() == null || properties.getUri().isBlank()) {
            throw new IllegalStateException("MONGODB_URI is required when MongoDB persistence is enabled");
        }
        if (!"foodly-community".equals(properties.getDatabase())) {
            throw new IllegalStateException("MONGODB_DATABASE must be foodly-community");
        }
        var connection = new ConnectionString(properties.getUri());
        if (connection.getDatabase() != null && !properties.getDatabase().equals(connection.getDatabase())) {
            throw new IllegalStateException("The URI database must match MONGODB_DATABASE");
        }
        return MongoClients.create(connection);
    }

    @Bean
    MongoTemplate communityMongoTemplate(MongoClient client, MongoProperties properties) {
        return new MongoTemplate(client, properties.getDatabase());
    }

    @Bean
    ReviewRepository reviewRepository(MongoTemplate template) {
        return new ReviewRepository(template);
    }

    @Bean
    FavoriteRepository favoriteRepository(MongoTemplate template) {
        return new FavoriteRepository(template);
    }
}
