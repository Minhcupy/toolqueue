package com.example.toolqueue.messaging;
import jakarta.persistence.*;
import java.time.Instant;
@Entity @Table(name="processed_messages")
public class ProcessedMessage { @Id private String eventId; @Column(name="processed_at",nullable=false) private Instant processedAt; protected ProcessedMessage(){} public ProcessedMessage(String id){eventId=id;processedAt=Instant.now();} }
