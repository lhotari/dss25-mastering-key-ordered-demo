package com.github.lhotari.dss25.generator;

import com.github.lhotari.dss25.env.TestEnvironment;
import java.nio.ByteBuffer;
import java.util.Random;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import org.apache.pulsar.client.api.BatcherBuilder;
import org.apache.pulsar.client.api.Producer;
import org.apache.pulsar.client.api.PulsarClient;
import org.apache.pulsar.client.api.PulsarClientException;
import org.apache.pulsar.client.api.SizeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import picocli.CommandLine;

@CommandLine.Command(name = "generator", mixinStandardHelpOptions = true)
public class Generator implements Callable<Integer> {
    private static final Logger log = LoggerFactory.getLogger(Generator.class);
    @CommandLine.Option(names = { "-u", "--url" }, description = "Pulsar service URL")
    private String pulsarServiceUrl = TestEnvironment.PULSAR_SERVICE_URL;
    @CommandLine.Option(names = { "-t", "--topic" }, description = "Pulsar topic name")
    private String topic = "persistent://public/default/test";
    @CommandLine.Option(names = { "-n", "--producer" }, description = "Pulsar producer name")
    private String producerName = "test-producer";
    @CommandLine.Option(names = { "-b", "--batch" }, description = "Enable batching")
    private boolean enableBatching;
    private int batchMaxMessages = 1000;
    private int batchDelayMillis = 100;
    @CommandLine.Option(names = { "-nm", "--number-of-messages" }, description = "Number of messages to produce")
    private int maxMessages = 1_000_000;
    @CommandLine.Option(names = { "-ms", "--message-size" }, description = "Message size")
    private int messageSize = 64;
    @CommandLine.Option(names = { "-ks", "--key-space-size" }, description = "Keyspace size")
    private int keySpaceSize = 500;
    @CommandLine.Option(names = { "-km", "--key-generation" }, description = "Key generation mode")
    private KeyGenerationMode keyGenerationMode = KeyGenerationMode.GAUSSIAN_RANDOM;
    enum KeyGenerationMode {
        UNIFORM_ROUND_ROBIN,
        UNIFORM_RANDOM,
        GAUSSIAN_RANDOM;
    }
    private AtomicInteger messagesInFlight = new AtomicInteger();
    @CommandLine.Option(names = { "-mf", "--messages-in-flight" }, description = "Maximum messages in flight")
    private int maxMessagesInFlight = 50000;
    private final Random random = new Random(0);
    private final CountDownLatch sendingDone = new CountDownLatch(1);

    @Override
    public Integer call() throws Exception {
        initialize();
        try {
            try (PulsarClient pulsarClient = PulsarClient.builder()
                    .serviceUrl(pulsarServiceUrl)
                    .memoryLimit(0, SizeUnit.BYTES)
                    .build()) {
                produceMessages(pulsarClient);
                sendingDone.await();
            }
            return 0;
        } finally {
            shutdown();
        }
    }

    private void initialize() {

    }

    private void shutdown() {

    }

    public static void main(String[] args) {
        int exitCode = new CommandLine(new Generator()).execute(args);
        System.exit(exitCode);
    }

    private void produceMessages(PulsarClient pulsarClient) throws PulsarClientException {
        try (Producer<byte[]> producer = pulsarClient.newProducer()
                .topic(topic)
                .enableBatching(enableBatching)
                .batcherBuilder(BatcherBuilder.KEY_BASED)
                .batchingMaxPublishDelay(batchDelayMillis, TimeUnit.MILLISECONDS)
                .batchingMaxMessages(batchMaxMessages)
                .blockIfQueueFull(true)
                .sendTimeout(0, TimeUnit.MILLISECONDS)
                .create()) {
            AtomicReference<Throwable> sendFailure = new AtomicReference<>();
            for (int i = 1; i <= maxMessages; i++) {
                byte[] value = intToBytes(i, messageSize);
                byte[] key = intToBytes(generateKey(i));
                producer.newMessage().orderingKey(key).value(value)
                        .sendAsync().whenComplete((messageId, throwable) -> {
                            messagesInFlight.decrementAndGet();
                            if (throwable != null) {
                                log.error("Failed to send message to topic {}", topic, throwable);
                                sendFailure.set(throwable);
                            }
                        });
                int currentMessagesInFlight = messagesInFlight.incrementAndGet();
                while (currentMessagesInFlight > maxMessagesInFlight && !Thread.currentThread().isInterrupted()) {
                    // throttling
                    try {
                        Thread.sleep(100);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    currentMessagesInFlight = messagesInFlight.get();
                }
                if (i % 1000 == 0) {
                    log.info("Sent {} msgs", i);
                }
                Throwable throwable = sendFailure.get();
                if (throwable != null) {
                    throw new RuntimeException("Sending failing", throwable);
                }
                if (Thread.currentThread().isInterrupted()) {
                    break;
                }
            }
            log.info("Flushing");
            producer.flush();
        } finally {
            log.info("Done sending.");
            sendingDone.countDown();
        }
    }

    int generateKey(int messageNumber) {
        switch (keyGenerationMode) {
            case UNIFORM_ROUND_ROBIN:
                return messageNumber % keySpaceSize;
            case UNIFORM_RANDOM:
                return random.nextInt(keySpaceSize);
            case GAUSSIAN_RANDOM:
                return gaussianInRange(random, 0, keySpaceSize - 1);
            default:
                throw new IllegalArgumentException("Unknown key generation mode: " + keyGenerationMode);
        }
    }

    // fully spread out gaussian (normal) distribution around the mean value
    static int gaussianInRange(Random random, int min, int max) {
        double mean = (min + max) / 2.0;
        // 99.7% within range
        // see 68–95–99.7 rule, https://en.wikipedia.org/wiki/68%E2%80%9395%E2%80%9399.7_rule
        double stdDev = (max - min) / 6.0;
        double value = mean + stdDev * random.nextGaussian();
        value = Math.max(min, Math.min(max, value));
        return (int) Math.round(value);
    }

    byte[] intToBytes(final int i) {
        return intToBytes(i, 4);
    }

    byte[] intToBytes(final int i, int messageSize) {
        return ByteBuffer.allocate(Math.max(4, messageSize)).putInt(i).array();
    }

}
