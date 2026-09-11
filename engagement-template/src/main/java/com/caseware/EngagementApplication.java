package com.caseware;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@EnableAsync
@SpringBootApplication
public class EngagementApplication {

    public static void main(String[] args) {
        SpringApplication.run(EngagementApplication.class, args);
    }
}