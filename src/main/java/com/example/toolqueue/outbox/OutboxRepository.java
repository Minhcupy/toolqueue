package com.example.toolqueue.outbox;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
public interface OutboxRepository extends JpaRepository<OutboxEvent,String>{List<OutboxEvent> findByPublishedAtIsNullOrderByCreatedAt(Pageable pageable);}
