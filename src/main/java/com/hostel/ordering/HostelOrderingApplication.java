package com.hostel.ordering;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
@Slf4j
public class HostelOrderingApplication {

    public static void main(String[] args) {
        SpringApplication.run(HostelOrderingApplication.class, args);
    }

    @Bean
    public CommandLineRunner startupSuccess() {
        return args -> log.info("🚀 SUCCESS: Hostel Ordering Application is fully initialized and ready!");
    }
}
