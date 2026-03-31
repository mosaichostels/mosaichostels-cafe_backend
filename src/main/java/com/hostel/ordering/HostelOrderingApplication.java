package com.hostel.ordering;

import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
@EnableAsync
public class HostelOrderingApplication {

    public static void main(String[] args) {
        SpringApplication.run(HostelOrderingApplication.class, args);
    }
}
