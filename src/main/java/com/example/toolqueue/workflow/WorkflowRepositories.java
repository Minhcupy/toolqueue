package com.example.toolqueue.workflow;

import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

interface WorkflowInstanceRepository extends JpaRepository<WorkflowInstance,String>{Optional<WorkflowInstance> findByJobId(String jobId);}
interface WorkflowStageRepository extends JpaRepository<WorkflowStage,String>{List<WorkflowStage> findByWorkflowIdOrderBySequenceNumber(String workflowId);}
