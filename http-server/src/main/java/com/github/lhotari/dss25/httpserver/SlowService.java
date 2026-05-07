package com.github.lhotari.dss25.httpserver;

import static java.nio.charset.StandardCharsets.UTF_8;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import picocli.CommandLine;

@CommandLine.Command(name = "slow-service", mixinStandardHelpOptions = true)
public class SlowService implements Callable<Integer> {
    @CommandLine.Option(names = { "-d", "--delay" }, description = "Delay in milliseconds")
    private long delay = 100L;
    @CommandLine.Option(names = { "-j", "--jitter" }, description = "Jitter factor (0.0-1.0)")
    private double jitterFactor  = 0.1;

    public static void main(String[] args) {
        int exitCode = new CommandLine(new SlowService()).execute(args);
        System.exit(exitCode);
    }

    public Integer call() throws IOException, InterruptedException {
        // Simple HTTP server with virtual threads (Java 21+)
        var server = HttpServer.create(new InetSocketAddress(8888), 0);
        server.setExecutor(Executors.newVirtualThreadPerTaskExecutor());

        server.createContext("/api/slow", exchange -> {
            // Each request runs on its own virtual thread
            // Simulate slow response
            delayWithJitter();
            var response = ("{\"timestamp\": " + System.currentTimeMillis() + "}").getBytes(UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "application/json");
            exchange.sendResponseHeaders(200, response.length);
            exchange.getResponseBody().write(response);
            exchange.close();
        });

        server.start();
        System.out.println("Started server at " + server.getAddress());
        try {
            Thread.currentThread().join();
        } catch (InterruptedException e) {
            // ignore intentionally
        }
        return 0;
    }

    private void delayWithJitter() {
        try {
            Thread.sleep(delay + (long) (delay * jitterFactor * Math.random()));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}