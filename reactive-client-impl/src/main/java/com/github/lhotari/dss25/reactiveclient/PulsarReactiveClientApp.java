package com.github.lhotari.dss25.reactiveclient;

import com.github.lhotari.dss25.env.TestEnvironment;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.concurrent.Callable;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.pulsar.client.api.KeySharedPolicy;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.Schema;
import org.apache.pulsar.client.api.SubscriptionType;
import org.apache.pulsar.reactive.client.adapter.AdaptedReactivePulsarClientFactory;
import org.apache.pulsar.reactive.client.api.ReactiveMessagePipeline;
import org.apache.pulsar.reactive.client.api.ReactivePulsarClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;
import reactor.core.publisher.Mono;
import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

@Command(name = "pulsar-reactive-client-app")
public class PulsarReactiveClientApp implements Callable<Integer> {
    private static final Logger log = LoggerFactory.getLogger(PulsarReactiveClientApp.class);
    @Option(names = { "-c", "--concurrency" }, description = "Concurrency limit.")
    private int concurrency = 100;
    @Option(names = { "-u", "--url" }, description = "Pulsar service URL")
    private String pulsarServiceUrl = TestEnvironment.PULSAR_SERVICE_URL;
    @Option(names = { "-t", "--topic" }, description = "Pulsar topic name")
    private String topic = "persistent://public/default/test";
    @Option(names = { "-s", "--subscription" }, description = "Pulsar subscription name")
    private String subscriptionName = "test-subscription";
    @Option(names = { "-n", "--consumer" }, description = "Pulsar consumer name")
    private String consumerName = "test-consumer";
    @Option(names = { "-q", "--queue" }, description = "Pulsar receiver queue size")
    private int receiverQueueSize = 1000;
    @Option(names = { "-e", "--endpoint" }, description = "Processing endpoint")
    private String processingEndpoint = "http://localhost:8888/api/slow";
    private HttpClient httpClient;
    private AtomicInteger messagesProcessed = new AtomicInteger();
    private long startTimeMillis = System.currentTimeMillis();
    private ConnectionProvider connectionProvider;

    @Override
    public Integer call() throws Exception {
        initialize();
        try {
            try (PulsarClient pulsarClient = PulsarClient.builder()
                    .serviceUrl(pulsarServiceUrl)
                    .listenerThreads(concurrency)
                    .build()) {
                ReactivePulsarClient reactivePulsarClient = AdaptedReactivePulsarClientFactory.create(pulsarClient);
                ReactiveMessagePipeline messagePipeline = createMessagePipeline(reactivePulsarClient);
                log.info("Starting message pipeline");
                messagePipeline.start();
                log.info("Message pipeline started");
                try {
                    Thread.currentThread().join();
                } catch (InterruptedException e) {
                    // ignore intentionally
                }
                messagePipeline.stop();
            }
            return 0;
        } finally {
            shutdown();
        }
    }

    private void initialize() {
        connectionProvider = ConnectionProvider.builder(getClass().getSimpleName())
                .maxConnections(concurrency)
                .pendingAcquireMaxCount(concurrency)
                .pendingAcquireTimeout(Duration.ofSeconds(10))
                .maxIdleTime(Duration.ofSeconds(20))
                .build();
        httpClient = HttpClient.create(connectionProvider)
                .responseTimeout(Duration.of(10, ChronoUnit.SECONDS)); // set default timeout
    }

    private void shutdown() {

    }

    private ReactiveMessagePipeline createMessagePipeline(ReactivePulsarClient reactivePulsarClient) throws PulsarClientException {
        return reactivePulsarClient
                .messageConsumer(Schema.BYTES)
                .consumerName(consumerName)
                .receiverQueueSize(receiverQueueSize)
                .subscriptionName(subscriptionName)
                .subscriptionType(SubscriptionType.Key_Shared)
                .keySharedPolicy(KeySharedPolicy.autoSplitHashRange())
                .topic(topic)
                .build()
                .messagePipeline()
                .messageHandler((message) -> handleMessage(message))
                .concurrency(concurrency)
                .useKeyOrderedProcessing()
                .build();
    }

    private Mono<Void> handleMessage(Message<byte[]> message) {
        byte[] keyBytes = message.getOrderingKey();
        int messageKey = -1;
        if (keyBytes != null && keyBytes.length == 4) {
            messageKey = bytesToInt(keyBytes);
        }
        // value is expected to include the message number in the first 4 bytes
        int messageNumber = bytesToInt(message.getValue());
        //log.info("Processing message: {} with key: {}", messageNumber, messageKey);
        return httpClient.get().uri(processingEndpoint).responseSingle((resp, buf) -> {
            if (resp.status().code() >= 200 && resp.status().code() < 300) {
                return buf.asString().defaultIfEmpty("");
            } else {
                return buf.asString().defaultIfEmpty("")
                        .flatMap(body -> Mono.error(new IOException("HTTP " + resp.status() + " body: " + body)));
            }
        }).timeout(Duration.of(10, ChronoUnit.SECONDS)).then(Mono.fromRunnable(() -> {
            int currentMessagesProcessed = messagesProcessed.incrementAndGet();
            if (currentMessagesProcessed % 100 == 0) {
                long elapsedMillis = System.currentTimeMillis() - startTimeMillis;
                long elapsedSeconds = Math.max(1L, elapsedMillis / 1000L);
                double ratePerSecond = (double) currentMessagesProcessed / elapsedSeconds;
                log.info("Messages processed: {} time elapsed: {} seconds. rate {} msg/s", currentMessagesProcessed,
                        elapsedSeconds, String.format("%.2f", ratePerSecond));
            }
        }));
    }

    private int bytesToInt(byte[] bytes) {
        return ByteBuffer.wrap(bytes).getInt();
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new PulsarReactiveClientApp()).execute(args);
        System.exit(exitCode);
    }
}
