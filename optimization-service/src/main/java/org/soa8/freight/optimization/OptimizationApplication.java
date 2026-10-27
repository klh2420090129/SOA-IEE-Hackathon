package org.soa8.freight.optimization;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class OptimizationApplication {
    public static void main(String[] args) {
        SpringApplication.run(OptimizationApplication.class, args);
    }
}
