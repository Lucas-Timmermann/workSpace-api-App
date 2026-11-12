package com.example.api_tp.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


import java.time.Clock;

@Configuration
@EnableConfigurationProperties(ArcaProperties.class)
public class ArcaConfig {

    @Bean
    public Clock arcaClock() {
        return Clock.systemDefaultZone();
    }
}
