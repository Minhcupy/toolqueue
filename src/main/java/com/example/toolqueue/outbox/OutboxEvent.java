package com.example.toolqueue.outbox;
import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;
@Entity @Table(name="outbox_events")
public class OutboxEvent {
 @Id private String id; @Column(name="exchange_name",nullable=false) private String exchangeName; @Column(name="routing_key",nullable=false) private String routingKey;
 @Lob @Column(nullable=false) private String payload; @Column(name="created_at",nullable=false) private Instant createdAt; @Column(name="published_at") private Instant publishedAt; @Column(nullable=false) private int attempts;
 protected OutboxEvent(){} public OutboxEvent(String exchange,String routing,String payload){id=UUID.randomUUID().toString();exchangeName=exchange;routingKey=routing;this.payload=payload;createdAt=Instant.now();}
 public void published(){publishedAt=Instant.now();} public void attempted(){attempts++;} public String getId(){return id;} public String getExchangeName(){return exchangeName;} public String getRoutingKey(){return routingKey;} public String getPayload(){return payload;}
}
