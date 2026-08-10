package com.example.toolqueue.workflow;

import com.example.toolqueue.messaging.ProcessedMessage;
import com.example.toolqueue.messaging.ProcessedMessageRepository;
import com.example.toolqueue.messaging.RabbitTopology;
import com.example.toolqueue.outbox.*;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.node.ObjectNode;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class WorkflowService {
    public static final List<String> PIPELINE=List.of("PROBE_MEDIA","EXTRACT_AUDIO","TRANSCRIBE","TRANSLATE","SYNTHESIZE_SPEECH","GENERATE_SUBTITLE","RENDER_VIDEO","QUALITY_CHECK");
    private final WorkflowInstanceRepository workflows; private final WorkflowStageRepository stages; private final ProcessedMessageRepository processed; private final OutboxRepository outbox; private final ObjectMapper json;
    public WorkflowService(WorkflowInstanceRepository workflows,WorkflowStageRepository stages,ProcessedMessageRepository processed,OutboxRepository outbox){this.workflows=workflows;this.stages=stages;this.processed=processed;this.outbox=outbox;this.json=new ObjectMapper();}

    @Transactional
    public void start(JsonNode command){
        String eventId=required(command,"eventId"); if(processed.existsById(eventId))return;
        String jobId=required(command,"jobId");
        if(workflows.findByJobId(jobId).isEmpty()){
            String correlation=command.path("correlationId").asText(UUID.randomUUID().toString());
            JsonNode initialInputs=command.path("payload").path("inputs");
            JsonNode config=command.path("payload").path("config");
            WorkflowInstance workflow=workflows.save(new WorkflowInstance(jobId,required(command,"projectId"),correlation,command.path("payload").path("pipelineVersion").asInt(1),initialInputs.toString(),config.toString()));
            for(int i=0;i<PIPELINE.size();i++)stages.save(new WorkflowStage(workflow.getId(),PIPELINE.get(i),i));
            dispatch(workflow,stages.findByWorkflowIdOrderBySequenceNumber(workflow.getId()).getFirst(),initialInputs,config);
        }
        processed.save(new ProcessedMessage(eventId));
    }

    @Transactional
    public void stageEvent(JsonNode event){
        String eventId=required(event,"eventId"); if(processed.existsById(eventId))return;
        WorkflowInstance workflow=workflows.findByJobId(required(event,"jobId")).orElseThrow();
        List<WorkflowStage> ordered=stages.findByWorkflowIdOrderBySequenceNumber(workflow.getId());
        String stageName=event.path("payload").path("stage").asText();
        WorkflowStage stage=ordered.stream().filter(s->s.getStageName().equals(stageName)).findFirst().orElseThrow();
        String type=event.path("eventType").asText();
        if(type.contains("completed")){
            stage.succeed();
            JsonNode accumulated=mergeArtifacts(workflow.getInputsJson(),event.path("payload").path("artifacts"));
            workflow.updateInputs(accumulated.toString());
            int next=stage.getSequenceNumber()+1;
            if(next>=ordered.size()){workflow.complete();outbox.save(new OutboxEvent(RabbitTopology.EVENTS,"pipeline.completed",pipelineEvent(workflow,"media.pipeline.completed",event.path("payload").path("artifacts"))));}
            else dispatch(workflow,ordered.get(next),accumulated,readJson(workflow.getConfigJson()));
        } else if(type.contains("failed")){stage.fail();workflow.fail();outbox.save(new OutboxEvent(RabbitTopology.EVENTS,"pipeline.failed",pipelineEvent(workflow,"media.pipeline.failed",event.path("payload"))));}
        processed.save(new ProcessedMessage(eventId));
    }

    private void dispatch(WorkflowInstance workflow,WorkflowStage stage,JsonNode inputs,JsonNode config){
        stage.dispatch();workflow.current(stage.getStageName());
        ObjectNode root=json.createObjectNode();root.put("eventId",UUID.randomUUID().toString());root.put("eventType","media.stage.execute");root.put("eventVersion",1);root.put("occurredAt",Instant.now().toString());root.put("correlationId",workflow.getCorrelationId());root.put("projectId",workflow.getProjectId());root.put("jobId",workflow.getJobId());root.put("attempt",stage.getAttempt());
        ObjectNode payload=root.putObject("payload");payload.put("stage",stage.getStageName());payload.set("inputs",inputs);payload.set("config",config);
        outbox.save(new OutboxEvent(RabbitTopology.COMMANDS,"stage.execute."+stage.getStageName().toLowerCase(),root.toString()));
    }
    private String pipelineEvent(WorkflowInstance workflow,String type,JsonNode payload){ObjectNode root=json.createObjectNode();root.put("eventId",UUID.randomUUID().toString());root.put("eventType",type);root.put("eventVersion",1);root.put("occurredAt",Instant.now().toString());root.put("correlationId",workflow.getCorrelationId());root.put("projectId",workflow.getProjectId());root.put("jobId",workflow.getJobId());root.set("payload",payload);return root.toString();}
    private JsonNode mergeArtifacts(String currentJson,JsonNode additions){var merged=(tools.jackson.databind.node.ArrayNode)readJson(currentJson).deepCopy();if(additions.isArray())additions.forEach(merged::add);return merged;}
    private JsonNode readJson(String value){try{return json.readTree(value);}catch(Exception exception){throw new IllegalStateException("Invalid workflow context",exception);}}
    private String required(JsonNode node,String field){String value=node.path(field).asText();if(value.isBlank())throw new IllegalArgumentException("Missing "+field);return value;}
}
