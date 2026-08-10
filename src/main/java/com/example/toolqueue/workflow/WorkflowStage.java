package com.example.toolqueue.workflow;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

@Entity @Table(name="workflow_stages")
public class WorkflowStage {
    @Id private String id;
    @Column(name="workflow_id",nullable=false) private String workflowId;
    @Column(name="stage_name",nullable=false) private String stageName;
    @Column(name="sequence_number",nullable=false) private int sequenceNumber;
    @Column(nullable=false) private String status;
    @Column(nullable=false) private int attempt;
    @Column(name="started_at") private Instant startedAt;
    @Column(name="completed_at") private Instant completedAt;
    protected WorkflowStage(){}
    public WorkflowStage(String workflowId,String stageName,int sequenceNumber){this.id=UUID.randomUUID().toString();this.workflowId=workflowId;this.stageName=stageName;this.sequenceNumber=sequenceNumber;this.status="PENDING";this.attempt=1;}
    public void dispatch(){if(!"PENDING".equals(status))return;status="DISPATCHED";startedAt=Instant.now();}
    public void succeed(){status="SUCCEEDED";completedAt=Instant.now();}
    public void fail(){status="FAILED";completedAt=Instant.now();}
    public String getId(){return id;} public String getWorkflowId(){return workflowId;} public String getStageName(){return stageName;}
    public int getSequenceNumber(){return sequenceNumber;} public String getStatus(){return status;} public int getAttempt(){return attempt;}
}
