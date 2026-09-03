package com.jarvis.commerce.refund;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RefundRequestedConsumerTests {
    @Mock RefundService refundService;
    @Mock RefundGateway gateway;
    @InjectMocks RefundRequestedConsumer consumer;

    private final RefundRequestedMessage message =
            new RefundRequestedMessage("event-1", "REF001", OffsetDateTime.now());
    private final RefundSubmission submission =
            new RefundSubmission("REF001", "PAY001", new BigDecimal("88.00"), "不再需要");

    @Test
    void submitsToGatewayThenMarksProcessing() {
        when(refundService.requireSubmission("REF001")).thenReturn(submission);

        consumer.consume(message);

        var ordered = inOrder(gateway, refundService);
        ordered.verify(gateway).submit(submission);
        ordered.verify(refundService).markProcessing("REF001");
    }

    @Test
    void propagatesGatewayFailureSoRabbitCanRetry() {
        when(refundService.requireSubmission("REF001")).thenReturn(submission);
        doThrow(new IllegalStateException("channel unavailable")).when(gateway).submit(submission);

        assertThrows(IllegalStateException.class, () -> consumer.consume(message));

        verify(refundService, never()).markProcessing(anyString());
    }

    @Test
    void skipsDuplicateMessageAfterRefundIsTerminal() {
        when(refundService.requireSubmission("REF001")).thenReturn(null);

        consumer.consume(message);

        verifyNoInteractions(gateway);
        verify(refundService, never()).markProcessing(anyString());
    }
}
