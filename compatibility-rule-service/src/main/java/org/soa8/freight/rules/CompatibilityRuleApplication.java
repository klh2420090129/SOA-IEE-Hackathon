package org.soa8.freight.rules;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class CompatibilityRuleApplication {
    public static void main(String[] args) {
        SpringApplication.run(CompatibilityRuleApplication.class, args);
    }
}
