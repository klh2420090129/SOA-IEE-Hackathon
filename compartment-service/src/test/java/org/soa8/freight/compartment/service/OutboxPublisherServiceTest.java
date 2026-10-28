package org.soa8.freight.compartment.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.soa8.freight.compartment.model.OutboxEventEntity;
import org.soa8.freight.compartment.repository.OutboxEventRepository;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class OutboxPublisherServiceTest {

    @Mock
    private OutboxEventRepository outboxEventRepository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private OutboxPublisherService outboxPublisherService;

    @BeforeEach
    void setUp() {
        outboxPublisherService = new OutboxPublisherService(outboxEventRepository, kafkaTemplate);
    }

    @Test
    @DisplayName("Should mark outbox event PUBLISHED when Kafka future completes with broker acknowledgement")
    void shouldMarkPublishedWhenKafkaFutureAcknowledged() {
        OutboxEventEntity event = new OutboxEventEntity(
                "evt-100", "CargoLoadApproved", "asmt-100", "Assessment", "test.topic", "{\"data\":\"val\"}"
        );
        when(outboxEventRepository.findByStatusOrderByCreatedAtAsc("PENDING"))
                .thenReturn(List.of(event));

        CompletableFuture<SendResult<String, String>> successFuture = CompletableFuture.completedFuture(mock(SendResult.class));
        when(kafkaTemplate.send(eq("test.topic"), eq("asmt-100"), eq("{\"data\":\"val\"}")))
                .thenReturn(successFuture);

        outboxPublisherService.publishPendingEvents();

        assertThat(event.getStatus()).isEqualTo("PUBLISHED");
        assertThat(event.getPublishedAt()).isNotNull();
        assertThat(event.getRetryCount()).isEqualTo(0);
        verify(outboxEventRepository).save(event);
    }

    @Test
    @DisplayName("Should increment retryCount and leave PENDING when Kafka future completes exceptionally")
    void shouldLeavePendingAndRetryWhenKafkaFutureFailsAsynchronously() {
        OutboxEventEntity event = new OutboxEventEntity(
                "evt-200", "CargoLoadApproved", "asmt-200", "Assessment", "test.topic", "{\"data\":\"val\"}"
        );
        when(outboxEventRepository.findByStatusOrderByCreatedAtAsc("PENDING"))
                .thenReturn(List.of(event));

        CompletableFuture<SendResult<String, String>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new RuntimeException("Broker disconnected"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(failedFuture);

        outboxPublisherService.publishPendingEvents();

        assertThat(event.getStatus()).isEqualTo("PENDING");
        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getRetryCount()).isEqualTo(1);
        verify(outboxEventRepository).save(event);
    }

    @Test
    @DisplayName("Should mark event FAILED after 5 failed retry attempts")
    void shouldMarkFailedWhenMaxRetriesExceeded() {
        OutboxEventEntity event = new OutboxEventEntity(
                "evt-300", "CargoLoadRejected", "asmt-300", "Assessment", "test.topic", "{\"data\":\"val\"}"
        );
        event.setRetryCount(4); // 5th failure will trigger FAILED status
        when(outboxEventRepository.findByStatusOrderByCreatedAtAsc("PENDING"))
                .thenReturn(List.of(event));

        CompletableFuture<SendResult<String, String>> failedFuture = new CompletableFuture<>();
        failedFuture.completeExceptionally(new TimeoutException("Kafka broker ACK timeout"));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(failedFuture);

        outboxPublisherService.publishPendingEvents();

        assertThat(event.getStatus()).isEqualTo("FAILED");
        assertThat(event.getPublishedAt()).isNull();
        assertThat(event.getRetryCount()).isEqualTo(5);
        verify(outboxEventRepository).save(event);
    }
}
