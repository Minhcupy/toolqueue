package com.example.toolqueue.workflow;

import com.example.toolqueue.outbox.OutboxRepository;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;
import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest @Transactional
class WorkflowServiceTest {
    @Autowired WorkflowService service; @Autowired OutboxRepository outbox; @Autowired WorkflowStageRepository stages; @Autowired WorkflowInstanceRepository workflows;
    @Test void duplicatePipelineCommandCreatesOneWorkflow() throws Exception {
        var command=new ObjectMapper().readTree("{\"eventId\":\"7dc5db8e-b5ff-4a01-b9e3-54d16093211a\",\"jobId\":\"f7bc0ded-f257-461c-b544-ed569c89c742\",\"projectId\":\"6ab184f1-5f5a-49e1-a3ad-f6895789c53f\",\"payload\":{\"inputs\":[],\"config\":{}}}");
        service.start(command); service.start(command);
        assertEquals(1,workflows.count()); assertEquals(WorkflowService.PIPELINE.size(),stages.count()); assertEquals(1,outbox.count());
    }
}
