package com.example.toolqueue.workflow;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="workflow_instances")
public class WorkflowInstance {
    @Id private String id;
    @Column(name="job_id",nullable=false,unique=true) private String jobId;
    @Column(name="project_id",nullable=false) private String projectId;
    @Column(name="correlation_id",nullable=false) private String correlationId;
    @Column(name="pipeline_version",nullable=false) private int pipelineVersion;
    @Lob @Column(name="inputs_json",nullable=false) private String inputsJson;
    @Lob @Column(name="config_json",nullable=false) private String configJson;
    @Column(nullable=false) private String status;
    @Column(name="current_stage") private String currentStage;
    @Column(name="created_at",nullable=false) private Instant createdAt;
    @Column(name="updated_at",nullable=false) private Instant updatedAt;
    protected WorkflowInstance() {}
    public WorkflowInstance(String jobId,String projectId,String correlationId,int pipelineVersion,String inputsJson,String configJson){this.id=UUID.randomUUID().toString();this.jobId=jobId;this.projectId=projectId;this.correlationId=correlationId;this.pipelineVersion=pipelineVersion;this.inputsJson=inputsJson;this.configJson=configJson;this.status="RUNNING";this.createdAt=this.updatedAt=Instant.now();}
    public void updateInputs(String inputsJson){this.inputsJson=inputsJson;this.updatedAt=Instant.now();}
    public void current(String stage){this.currentStage=stage;this.updatedAt=Instant.now();}
    public void complete(){this.status="COMPLETED";this.currentStage=null;this.updatedAt=Instant.now();}
    public void fail(){this.status="FAILED";this.updatedAt=Instant.now();}
    public String getId(){return id;} public String getJobId(){return jobId;} public String getProjectId(){return projectId;}
    public String getCorrelationId(){return correlationId;} public int getPipelineVersion(){return pipelineVersion;} public String getStatus(){return status;}
    public String getInputsJson(){return inputsJson;} public String getConfigJson(){return configJson;}
}
