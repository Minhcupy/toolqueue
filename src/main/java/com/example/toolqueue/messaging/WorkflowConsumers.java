package com.example.toolqueue.messaging;

import com.example.toolqueue.workflow.WorkflowService;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class WorkflowConsumers {
    private final WorkflowService workflows; private final ObjectMapper json=new ObjectMapper();
    public WorkflowConsumers(WorkflowService workflows){this.workflows=workflows;}
    @RabbitListener(queues=RabbitTopology.PIPELINE_QUEUE)
    void pipeline(Message message) throws JacksonException {workflows.start(json.readTree(message.getBody()));}
    @RabbitListener(queues=RabbitTopology.STAGE_EVENTS_QUEUE)
    void stage(Message message) throws JacksonException {workflows.stageEvent(json.readTree(message.getBody()));}
}
