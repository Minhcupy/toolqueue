package com.example.toolqueue.messaging;

import org.springframework.amqp.core.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitTopology {
    public static final String COMMANDS="media.commands";
    public static final String EVENTS="media.events";
    public static final String DLX="media.dlx";
    public static final String PIPELINE_QUEUE="orchestrator.pipeline.requests.v1";
    public static final String STAGE_EVENTS_QUEUE="orchestrator.stage.events.v1";
    public static final String WORKER_QUEUE="worker.cpu.commands.v1";

    @Bean Declarables mediaTopology() {
        TopicExchange commands=new TopicExchange(COMMANDS,true,false);
        TopicExchange events=new TopicExchange(EVENTS,true,false);
        DirectExchange dlx=new DirectExchange(DLX,true,false);
        Queue pipeline=QueueBuilder.durable(PIPELINE_QUEUE).deadLetterExchange(DLX).deadLetterRoutingKey("orchestrator.dead").build();
        Queue stageEvents=QueueBuilder.durable(STAGE_EVENTS_QUEUE).deadLetterExchange(DLX).deadLetterRoutingKey("orchestrator.dead").build();
        Queue worker=QueueBuilder.durable(WORKER_QUEUE).deadLetterExchange(DLX).deadLetterRoutingKey("worker.dead").build();
        Queue dead=QueueBuilder.durable("orchestrator.dead.v1").build();
        return new Declarables(commands,events,dlx,pipeline,stageEvents,worker,dead,
                BindingBuilder.bind(pipeline).to(commands).with("pipeline.requested"),
                BindingBuilder.bind(stageEvents).to(events).with("stage.#"),
                BindingBuilder.bind(worker).to(commands).with("stage.execute.#"),
                BindingBuilder.bind(dead).to(dlx).with("orchestrator.dead"));
    }
}
