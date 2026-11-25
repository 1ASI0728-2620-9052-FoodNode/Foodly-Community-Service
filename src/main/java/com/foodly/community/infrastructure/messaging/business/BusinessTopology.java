package com.foodly.community.infrastructure.messaging.business;

import org.springframework.amqp.core.*;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitAdmin;

/** Declara solo recursos Community; foodly.business se verifica pasivamente, nunca se redeclara. */
public final class BusinessTopology {
    public static final String EXCHANGE = "foodly.business";
    public static final String QUEUE = "foodly.community.business.events";
    public static final String DLX = "foodly.community.business.dlx";
    public static final String DLQ = "foodly.community.business.dlq";
    public static final String DEAD_KEY = "business.dead";

    public BusinessTopology(ConnectionFactory connectionFactory, BusinessEventsProperties settings) throws Exception {
        try (var connection = connectionFactory.createConnection(); var channel = connection.createChannel(false)) {
            channel.exchangeDeclarePassive(EXCHANGE);
        }
        var admin = new RabbitAdmin(connectionFactory);
        admin.setAutoStartup(false);
        admin.declareExchange(new DirectExchange(DLX, true, false));
        admin.declareQueue(QueueBuilder.durable(DLQ).quorum().build());
        admin.declareBinding(new Binding(DLQ, Binding.DestinationType.QUEUE, DLX, DEAD_KEY, null));
        var queue = QueueBuilder.durable(QUEUE).quorum()
                .deadLetterExchange(DLX).deadLetterRoutingKey(DEAD_KEY)
                .withArgument("x-delivery-limit", settings.maxAttempts() - 1)
                .withArgument("x-dead-letter-strategy", "at-least-once")
                .withArgument("x-overflow", "reject-publish").build();
        admin.declareQueue(queue);
        for (String type : BusinessEvent.TYPES) {
            admin.declareBinding(new Binding(QUEUE, Binding.DestinationType.QUEUE, EXCHANGE, "business." + type, null));
        }
    }
}
