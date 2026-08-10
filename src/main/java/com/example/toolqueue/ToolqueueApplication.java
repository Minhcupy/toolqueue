package com.example.toolqueue;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class ToolqueueApplication {

    public static void main(String[] args) {
        SpringApplication.run(ToolqueueApplication.class, args);
    }

}
