package com.github.lhotari.dss25.listener;

import com.github.lhotari.dss25.env.TestEnvironment;
import com.github.lhotari.dss25.retry.MessageListenerRetries;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.concurrent.atomic.AtomicInteger;
import org.apache.pulsar.client.api.ClientBuilder;
import org.apache.pulsar.client.api.Consumer;
import org.apache.pulsar.client.api.ConsumerBuilder;
import org.apache.pulsar.client.api.KeySharedPolicy;
import org.apache.pulsar.client.api.Message;
import org.apache.pulsar.client.api.MessageListener;
import org.apache.pulsar.client.api.MessageListenerExecutor;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.SubscriptionType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;
import picocli.CommandLine.Command;
import picocli.CommandLine.Option;

@Command(name = "pulsar-listener", mixinStandardHelpOptions = true)
public class PulsarListenerApp implements Callable<Integer> {
    private static final Logger log = LoggerFactory.getLogger(PulsarListenerApp.class);
    @Option(names = { "-c", "--concurrency" }, description = "Concurrency limit. When virtual threads aren't used, this is the number of listener threads.")
    private int concurrency = 100;
    @Option(names = { "-vt", "--virtual-threads" }, description = "Use virtual threads")
    private boolean useVirtualThreads = false;
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
    private URI processingEndpoint = URI.create("http://localhost:8888/api/slow");
    private HttpClient httpClient;
    private AtomicInteger messagesProcessed = new AtomicInteger();
    private long startTimeMillis = System.currentTimeMillis();

    @Override
    public Integer call() throws Exception {
        initialize();
        try {
            ClientBuilder clientBuilder = PulsarClient.builder()
                    .serviceUrl(pulsarServiceUrl);
            if (!useVirtualThreads) {
                clientBuilder.listenerThreads(concurrency);
            }
            try (PulsarClient pulsarClient = clientBuilder
                    .build()) {
                createConsumer(pulsarClient);
                Thread.currentThread().join();
            }
            return 0;
        } finally {
            shutdown();
        }
    }

    private void initialize() {
        httpClient = HttpClient.newHttpClient();
    }

    private void shutdown() {
        httpClient.close();
    }

    private Consumer<byte[]> createConsumer(PulsarClient pulsarClient) throws PulsarClientException {
        // create a message listener wrapper with infinite retries
        MessageListener<byte[]> messageListener =
                MessageListenerRetries.wrapFunctionWithInfiniteRetries(String.format("consumer.%s", consumerName),
                        this::handleMessage);
        log.info("Creating consumer: {} on topic: {}", consumerName, topic);
        ConsumerBuilder<byte[]> consumerBuilder = pulsarClient.newConsumer();
        if (useVirtualThreads) {
            consumerBuilder.messageListenerExecutor(new VirtualThreadsMessageListenerExecutor(concurrency));
        }
        Consumer<byte[]> consumer = consumerBuilder
                .topic(topic)
                .subscriptionType(SubscriptionType.Key_Shared)
                .keySharedPolicy(KeySharedPolicy.autoSplitHashRange())
                .consumerName(consumerName)
                .subscriptionName(subscriptionName)
                .receiverQueueSize(receiverQueueSize)
                .messageListener(messageListener)
                .subscribe();
        return consumer;
    }

    private void handleMessage(Consumer<byte[]> consumer, Message<byte[]> message)
            throws IOException, InterruptedException {
        byte[] keyBytes = message.getOrderingKey();
        int messageKey = -1;
        if (keyBytes != null && keyBytes.length == 4) {
            messageKey = bytesToInt(keyBytes);
        }
        // value is expected to include the message number in the first 4 bytes
        int messageNumber = bytesToInt(message.getValue());
        //log.info("Processing message: {} with key: {}", messageNumber, messageKey);
        var request = HttpRequest.newBuilder()
                .uri(processingEndpoint)
                .timeout(Duration.of(10, ChronoUnit.SECONDS))
                .build();
        HttpResponse<String> httpResponse = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        //log.info("Received response: {}", httpResponse.body());
        consumer.acknowledgeAsync(message);
        int currentMessagesProcessed = messagesProcessed.incrementAndGet();
        if (currentMessagesProcessed % 100 == 0) {
            long elapsedMillis = System.currentTimeMillis() - startTimeMillis;
            long elapsedSeconds = elapsedMillis / 1000L;
            double ratePerSecond = (double) currentMessagesProcessed / elapsedSeconds;
            log.info("Messages processed: {} time elapsed: {} seconds. rate {} msg/s", currentMessagesProcessed, elapsedSeconds, String.format("%.2f", ratePerSecond));
        }
    }

    private int bytesToInt(byte[] bytes) {
        return ByteBuffer.wrap(bytes).getInt();
    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new PulsarListenerApp()).execute(args);
        System.exit(exitCode);
    }

    /**
     * Simple VirtualThreads based MessageListenerExecutor.
     * Thank you to Philipp Dolif for the inspiration in his Pulsar Virtual Threads Message Listener project,
     * https://github.com/pdolif/pulsar-virtual-threads-message-listener
     */
    private static class VirtualThreadsMessageListenerExecutor implements MessageListenerExecutor {
        private final Map<BytesKey, Executor> perKeyExecutors = new ConcurrentHashMap<>();
        private final Semaphore concurrencyLimiter;

        public VirtualThreadsMessageListenerExecutor(int concurrencyLimit) {
            this.concurrencyLimiter = new Semaphore(concurrencyLimit);
        }

        @Override
        public void execute(Message<?> message, Runnable runnable) {
            Executor executor = perKeyExecutors.computeIfAbsent(
                    new BytesKey(peekMessageKey(message)), k -> Executors.newVirtualThreadPerTaskExecutor());
            executor.execute(() -> {
                concurrencyLimiter.acquireUninterruptibly();
                try {
                    runnable.run();
                } finally {
                    concurrencyLimiter.release();
                }
            });
        }

        record BytesKey(byte[] key) {}

        static final byte[] NONE_KEY = "NONE_KEY".getBytes(StandardCharsets.UTF_8);
        // copied from org.apache.pulsar.client.impl.ConsumerBase.peekMessageKey
        private static  byte[] peekMessageKey(Message<?> msg) {
            byte[] key = NONE_KEY;
            if (msg.hasOrderingKey()) {
                key = msg.getOrderingKey();
            } else if (msg.hasKey()) {
                key = msg.getKeyBytes();
            } else if (msg.getProducerName() != null) {
                String fallbackKey = msg.getProducerName() + "-" + msg.getSequenceId();
                key = fallbackKey.getBytes(StandardCharsets.UTF_8);
            }
            return key;
        }
    }
}
