package org.soa8.freight.compartment.client;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.soa8.freight.common.model.ValidationResultDto;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.util.Optional;

/**
 * Authoritative client to retrieve persisted validation records from validation-service.
 */
@Component
public class ValidationServiceClient {

    private static final Logger log = LoggerFactory.getLogger(ValidationServiceClient.class);

    private final RestTemplate restTemplate;
    private final String validationServiceUrl;

    public ValidationServiceClient(RestTemplateBuilder builder,
                                   @Value("${services.validation.url:http://localhost:8086}") String validationServiceUrl) {
        this.restTemplate = builder
                .setConnectTimeout(Duration.ofMillis(1500))
                .setReadTimeout(Duration.ofMillis(2500))
                .build();
        this.validationServiceUrl = validationServiceUrl;
    }

    /**
     * Retrieve the authoritative validation run from validation-service.
     * Returns Optional.empty() if the record is missing, corrupted, or unreachable.
     */
    public Optional<ValidationResultDto> getValidationRun(String validationId) {
        if (validationId == null || validationId.isBlank()) {
            return Optional.empty();
        }

        try {
            String url = validationServiceUrl.replaceAll("/+$", "") + "/api/v1/validations/" + validationId.trim();
            ValidationResultDto dto = restTemplate.getForObject(url, ValidationResultDto.class);
            return Optional.ofNullable(dto);
        } catch (HttpClientErrorException.NotFound e) {
            log.warn("Validation record [{}] not found on validation service", validationId);
            return Optional.empty();
        } catch (Exception e) {
            log.error("Failed to query validation service at {} for validation [{}]: {}",
                    validationServiceUrl, validationId, e.getMessage());
            return Optional.empty();
        }
    }
}
