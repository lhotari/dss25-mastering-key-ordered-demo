package com.github.lhotari.dss25.retry;

import static org.slf4j.LoggerFactory.getLogger;
import io.github.resilience4j.core.IntervalFunction;
import io.github.resilience4j.core.functions.CheckedConsumer;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.MessageListener;
import org.apache.pulsar.client.api.PulsarClientException;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;

/**
 * Utility class for wrapping Pulsar client message listeners with retries using Resilience4j library.
 */
public class MessageListenerRetries {
    private static final Logger log = getLogger(MessageListenerRetries.class);
    // Retry config for infinite retries
    private static final RetryConfig INFINITE_RETRY_CONFIG = RetryConfig.custom()
            // retry forever to retain key-order
            .maxAttempts(Integer.MAX_VALUE)
            // Don't retry on interrupted exception and PulsarClientException.AlreadyClosedException
            .ignoreExceptions(InterruptedException.class,
                    PulsarClientException.AlreadyClosedException.class)
            // Use exponential backoff with randomization factor of 0.1
            .intervalFunction(IntervalFunction.ofExponentialRandomBackoff(
                    Duration.of(IntervalFunction.DEFAULT_INITIAL_INTERVAL, ChronoUnit.MILLIS),
                    IntervalFunction.DEFAULT_MULTIPLIER,
                    0.1,
                    // max interval is 30 seconds
                    Duration.ofSeconds(30)))
            .build();
    private static final int ATTEMPT_COUNT_LOGGING_THRESHOLD = 3;

    private MessageListenerRetries() {
        // utility class with static methods only
    }

    /**
     * Wraps the given listener with infinite retries.
     * @param name name of the retry operation, for example the consumer name
     * @param listener the listener to wrap
     * @return the wrapped listener
     * @param <T> the message type
     */
    public static <T> MessageListener<T> wrapListenerWithInfiniteRetries(String name, MessageListener<T> listener) {
        return wrapListenerWithRetries(listener, createInfiniteRetryWithWarnLogging(name));
    }

    @NotNull
    private static Retry createInfiniteRetryWithWarnLogging(String name) {
        Retry retry = Retry.of(name, INFINITE_RETRY_CONFIG);
        retry.getEventPublisher().onRetry(event -> {
            if (event.getNumberOfRetryAttempts() > ATTEMPT_COUNT_LOGGING_THRESHOLD) {
                Throwable lastThrowable = event.getLastThrowable();
                if (lastThrowable instanceof MessageListenerException messageListenerException) {
                    ConsumerRecord<?> consumerRecord = messageListenerException.consumerRecord;
                    String exceptionMsg = lastThrowable.getCause().getMessage();
                    if (exceptionMsg == null) {
                        exceptionMsg = lastThrowable.getCause().getClass().getSimpleName();
                    }
                    log.warn("Retrying message {} for consumer {} (attempts: {}, last error: {})",
                            consumerRecord.message().getMessageId(),
                            consumerRecord.consumer.getConsumerName(),
                            event.getNumberOfRetryAttempts(), exceptionMsg);
                } else {
                    log.warn("Retrying on unexpected exception. attempts: {}, last error: {}",
                            event.getLastThrowable());
                }
            }
        });
        return retry;
    }

    /**
     * Wraps the given listener with a specific Retry configuration.
     * @param listener the listener to wrap
     * @param retry the Retry configuration to use
     * @return the wrapped listener
     * @param <T> the message type
     */
    public static <T> MessageListener<T> wrapListenerWithRetries(MessageListener<T> listener, Retry retry) {
        return toMessageListener(Retry.decorateCheckedConsumer(retry, consumerRecord -> {
            try {
                listener.received(consumerRecord.consumer(), consumerRecord.message());
            } catch (Exception e) {
                throw new MessageListenerException(consumerRecord, e);
            }
        }));
    }

    static class MessageListenerException extends RuntimeException {
        private final ConsumerRecord<?> consumerRecord;

        public MessageListenerException(ConsumerRecord<?> consumerRecord, Throwable cause) {
            super(cause);
            this.consumerRecord = consumerRecord;
        }
    }

    /**
     * Wraps the given listener function with infinite retries.
     * @param name name of the retry operation, for example the consumer name
     * @param listenerFunction the listener function to wrap
     * @return the wrapped listener
     * @param <T> the message type
     */
    public static <T> MessageListener<T> wrapFunctionWithInfiniteRetries(String name,
                                                                         CheckedBiConsumer<Consumer<T>, Message<T>> listenerFunction) {
        return wrapFunctionWithRetries(listenerFunction, createInfiniteRetryWithWarnLogging(name));
    }

    /**
     * Wraps the given listener function with a specific Retry configuration.
     * @param listenerFunction the listener function to wrap
     * @param retry the Retry configuration to use
     * @return the wrapped listener
     * @param <T> the message type
     */
    public static <T> @NotNull MessageListener<T> wrapFunctionWithRetries(
            CheckedBiConsumer<Consumer<T>, Message<T>> listenerFunction, Retry retry) {
        return toMessageListener(Retry.decorateCheckedConsumer(retry, consumerRecord -> {
            try {
                listenerFunction.accept(consumerRecord.consumer(), consumerRecord.message());
            } catch (Exception e) {
                throw new MessageListenerException(consumerRecord, e);
            }
        }));
    }

    public interface CheckedBiConsumer<T, U> {
        void accept(T t, U u) throws Throwable;
    }

    private static <T> MessageListener<T> toMessageListener(
            CheckedConsumer<ConsumerRecord<T>> decoratedConsumer) {
        return (consumer, msg) -> {
            decoratedConsumer.unchecked().accept(new ConsumerRecord(consumer, msg));
        };
    }

    record ConsumerRecord<T>(Consumer<T> consumer, Message<T> message) {
    }
}


