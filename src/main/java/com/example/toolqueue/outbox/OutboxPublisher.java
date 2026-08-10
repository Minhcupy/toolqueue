package com.example.toolqueue.outbox;

import org.springframework.amqp.core.MessageBuilder;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import java.nio.charset.StandardCharsets;

@Component
public class OutboxPublisher {
    private final OutboxRepository outbox; private final RabbitTemplate rabbit;
    public OutboxPublisher(OutboxRepository outbox,RabbitTemplate rabbit){this.outbox=outbox;this.rabbit=rabbit;rabbit.setMandatory(true);}
    @Scheduled(fixedDelayString="${app.outbox.poll-delay-ms:500}") @Transactional
    public void publishBatch(){
        for(OutboxEvent event:outbox.findByPublishedAtIsNullOrderByCreatedAt(PageRequest.of(0,50))){
            event.attempted();
            rabbit.convertAndSend(event.getExchangeName(),event.getRoutingKey(), MessageBuilder.withBody(event.getPayload().getBytes(StandardCharsets.UTF_8)).setContentType(MessageProperties.CONTENT_TYPE_JSON).setMessageId(event.getId()).build());
            event.published();
        }
    }
}
