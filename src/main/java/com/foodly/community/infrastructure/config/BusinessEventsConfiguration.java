package com.foodly.community.infrastructure.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.foodly.community.infrastructure.messaging.business.*;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.rabbit.connection.CachingConnectionFactory;
import org.springframework.amqp.rabbit.listener.SimpleMessageListenerContainer;
import org.springframework.boot.autoconfigure.amqp.RabbitProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.time.Clock;

@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(prefix = "community.integrations.business-events", name = "enabled", havingValue = "true")
@EnableConfigurationProperties({BusinessEventsProperties.class, RabbitProperties.class})
public class BusinessEventsConfiguration {

    @Bean(destroyMethod = "destroy")
    CachingConnectionFactory communityBusinessRabbit(RabbitProperties properties, Environment environment) throws Exception {
        if (!environment.getProperty("community.persistence.mongodb.enabled", Boolean.class, false)) {
            throw new IllegalStateException("Business consumer requires explicitly enabled MongoDB persistence");
        }
        if (blank(environment.getProperty("spring.rabbitmq.host")) || blank(environment.getProperty("spring.rabbitmq.username"))
                || blank(environment.getProperty("spring.rabbitmq.password")) || blank(environment.getProperty("spring.rabbitmq.virtual-host"))) {
            throw new IllegalStateException("Explicit RabbitMQ connection settings are required");
        }
        var client = new com.rabbitmq.client.ConnectionFactory();
        client.setAutomaticRecoveryEnabled(false);
        client.setHost(properties.getHost());
        client.setPort(properties.getPort());
        client.setUsername(properties.getUsername());
        client.setPassword(properties.getPassword());
        client.setVirtualHost(properties.getVirtualHost());
        client.setConnectionTimeout(5000);
        client.setRequestedHeartbeat(10);
        if (properties.getSsl().determineEnabled()) {
            client.useSslProtocol(javax.net.ssl.SSLContext.getDefault());
            client.enableHostnameVerification();
        }
        return new CachingConnectionFactory(client);
    }

    @Bean
    MongoBusinessEventStore communityBusinessStore(MongoTemplate mongo, Clock clock) {
        return new MongoBusinessEventStore(mongo, clock);
    }

    @Bean
    BusinessTopology communityBusinessTopology(CachingConnectionFactory connectionFactory,
                                               BusinessEventsProperties settings, MongoBusinessEventStore store) throws Exception {
        return new BusinessTopology(connectionFactory, settings);
    }

    @Bean
    SimpleMessageListenerContainer communityBusinessListener(CachingConnectionFactory connectionFactory,
            BusinessTopology topology, MongoBusinessEventStore store, ObjectMapper json, BusinessEventsProperties settings) {
        var listener = new SimpleMessageListenerContainer(connectionFactory);
        listener.setQueueNames(BusinessTopology.QUEUE);
        listener.setAcknowledgeMode(AcknowledgeMode.MANUAL);
        listener.setPrefetchCount(1);
        listener.setConcurrentConsumers(1);
        listener.setAutoDeclare(false);
        listener.setMissingQueuesFatal(true);
        listener.setDefaultRequeueRejected(false);
        listener.setMessageListener(new BusinessEventListener(new BusinessEventParser(json), store, settings));
        return listener;
    }

    private boolean blank(String value) { return value == null || value.isBlank(); }
}
