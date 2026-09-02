package com.bolao.copa;

import com.bolao.copa.config.DemoProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(DemoProperties.class)
public class CopaApplication {
    public static void main(String[] args) {
        SpringApplication.run(CopaApplication.class, args);
    }
}
