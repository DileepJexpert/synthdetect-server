package com.synthdetect;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@EnableAsync
public class SynthDetectApplication {

    public static void main(String[] args) {
        SpringApplication.run(SynthDetectApplication.class, args);
    }
}
