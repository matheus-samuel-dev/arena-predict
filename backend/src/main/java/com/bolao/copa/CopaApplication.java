package com.bolao.copa;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
public class CopaApplication {
    public static void main(String[] args) {
        SpringApplication.run(CopaApplication.class, args);
    }
}
