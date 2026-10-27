package org.soa8.freight.declaration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

@SpringBootApplication
@EnableDiscoveryClient
public class CargoDeclarationApplication {
    public static void main(String[] args) {
        SpringApplication.run(CargoDeclarationApplication.class, args);
    }
}
