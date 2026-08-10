package com.example.toolqueue.messaging;
import org.springframework.data.jpa.repository.JpaRepository;
public interface ProcessedMessageRepository extends JpaRepository<ProcessedMessage,String>{}
