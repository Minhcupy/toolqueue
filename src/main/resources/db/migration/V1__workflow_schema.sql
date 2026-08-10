CREATE TABLE workflow_instances (
    id VARCHAR(36) PRIMARY KEY,
    job_id VARCHAR(36) NOT NULL UNIQUE,
    project_id VARCHAR(36) NOT NULL,
    correlation_id VARCHAR(36) NOT NULL,
    pipeline_version INT NOT NULL,
    inputs_json TEXT NOT NULL,
    config_json TEXT NOT NULL,
    status VARCHAR(30) NOT NULL,
    current_stage VARCHAR(50),
    created_at TIMESTAMP NOT NULL,
    updated_at TIMESTAMP NOT NULL
);

CREATE TABLE workflow_stages (
    id VARCHAR(36) PRIMARY KEY,
    workflow_id VARCHAR(36) NOT NULL,
    stage_name VARCHAR(50) NOT NULL,
    sequence_number INT NOT NULL,
    status VARCHAR(30) NOT NULL,
    attempt INT NOT NULL,
    started_at TIMESTAMP,
    completed_at TIMESTAMP,
    CONSTRAINT fk_stage_workflow FOREIGN KEY(workflow_id) REFERENCES workflow_instances(id),
    CONSTRAINT uq_workflow_stage UNIQUE(workflow_id, stage_name)
);

CREATE TABLE processed_messages (
    event_id VARCHAR(36) PRIMARY KEY,
    processed_at TIMESTAMP NOT NULL
);

CREATE TABLE outbox_events (
    id VARCHAR(36) PRIMARY KEY,
    exchange_name VARCHAR(120) NOT NULL,
    routing_key VARCHAR(160) NOT NULL,
    payload TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL,
    published_at TIMESTAMP,
    attempts INT NOT NULL
);

CREATE INDEX idx_outbox_unpublished ON outbox_events(published_at, created_at);
