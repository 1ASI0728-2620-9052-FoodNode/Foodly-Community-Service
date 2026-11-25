package com.foodly.community.infrastructure.messaging.business;

import com.rabbitmq.client.Channel;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.listener.api.ChannelAwareMessageListener;

public final class BusinessEventListener implements ChannelAwareMessageListener {
    private final BusinessEventParser parser;
    private final MongoBusinessEventStore store;
    private final BusinessEventsProperties settings;

    public BusinessEventListener(BusinessEventParser parser, MongoBusinessEventStore store, BusinessEventsProperties settings) {
        this.parser = parser;
        this.store = store;
        this.settings = settings;
    }

    @Override
    public void onMessage(Message message, Channel channel) throws Exception {
        long tag = message.getMessageProperties().getDeliveryTag();
        try {
            store.process(parser.parse(message));
        } catch (InvalidBusinessEventException permanentFailure) {
            channel.basicReject(tag, false);
            return;
        } catch (RuntimeException processingFailure) {
            Object header = message.getMessageProperties().getHeaders().get("x-delivery-count");
            long redeliveries = header instanceof Number count ? Math.max(0, count.longValue()) : 0;
            if (redeliveries >= settings.maxAttempts() - 1L) {
                channel.basicReject(tag, false);
            } else {
                if (settings.retryDelayMs() > 0) Thread.sleep(settings.retryDelayMs());
                channel.basicNack(tag, false, true);
            }
            return;
        }
        // Fuera del catch de procesamiento: fallo/pérdida del ACK deja la entrega sin confirmar.
        channel.basicAck(tag, false);
    }
}
