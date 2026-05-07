# dss25-mastering-key-ordered-demo

Demo for DSS25 ("Mastering Key-Ordered Processing") that wasn't presented due to time
constraints. It compares two ways of consuming a `Key_Shared` Pulsar subscription while
calling a slow downstream HTTP service:

- `pulsar-listener` — classic `MessageListener` with optional virtual-thread per-key dispatch
- `reactive-client-impl` — `pulsar-client-reactive` pipeline with `useKeyOrderedProcessing()`

A `generator` subproject produces test traffic into the topic, and `http-server` serves a
configurable slow HTTP endpoint that the consumers call for each message.

## Modules

| Module | Main class | Purpose |
| --- | --- | --- |
| `http-server` | `SlowService` | HTTP server on `:8888` with `/api/slow` (configurable delay) |
| `generator` | `Generator` | Publishes keyed messages into a Pulsar topic |
| `pulsar-listener` | `PulsarListenerApp` | `MessageListener`-based key-shared consumer |
| `reactive-client-impl` | `PulsarReactiveClientApp` | Reactive pipeline with key-ordered processing |
| `common` | — | Shared `TestEnvironment` and retry helpers |

## Prerequisites

- Java 21+ (the Gradle build uses `buildlogic.java-application-conventions`)
- Docker
- The provided `./gradlew` wrapper

## 1. Start Pulsar in Docker

Run a Pulsar standalone broker, exposing the binary protocol port `6650` and the admin
HTTP port `8080`:

```bash
docker run --rm -it \
  -p 6650:6650 \
  -p 8080:8080 \
  --name pulsar \
  apachepulsar/pulsar:latest \
  bin/pulsar standalone -nfw -nss
```

Wait until the log shows `messaging service is ready`. Verify with:

```bash
curl -s http://localhost:8080/admin/v2/clusters
# => ["standalone"]
```

## 2. Start the slow HTTP service (terminal 1)

In a separate terminal, start the downstream service the consumers will call. This is
the workload that makes concurrency settings observable:

```bash
./gradlew :http-server:run
```

By default it listens on `http://localhost:8888/api/slow` with a 100 ms delay and 10%
jitter. Tune it with `--args`:

```bash
# Slower endpoint — makes concurrency limits much more visible
./gradlew :http-server:run --args='--delay 500 --jitter 0.2'

# Very fast endpoint
./gradlew :http-server:run --args='--delay 10 --jitter 0.0'
```

`SlowService` options:

| Option | Default | Description |
| --- | --- | --- |
| `-d`, `--delay` | `100` | Per-request delay in milliseconds |
| `-j`, `--jitter` | `0.1` | Jitter factor (0.0–1.0), added on top of `--delay` |

## 3. Produce test messages (terminal 2)

Publish keyed messages into the default topic `persistent://public/default/test`:

```bash
./gradlew :generator:run --args='--number-of-messages 200000 --key-space-size 500'
```

`Generator` options:

| Option | Default | Description |
| --- | --- | --- |
| `-u`, `--url` | `pulsar://localhost:6650/` | Pulsar service URL |
| `-t`, `--topic` | `persistent://public/default/test` | Topic name |
| `-n`, `--producer` | `test-producer` | Producer name |
| `-b`, `--batch` | off | Enable batching (key-based batcher) |
| `-nm`, `--number-of-messages` | `1000000` | Total messages to send |
| `-ms`, `--message-size` | `64` | Payload size in bytes |
| `-ks`, `--key-space-size` | `500` | Number of distinct ordering keys |
| `-km`, `--key-generation` | `GAUSSIAN_RANDOM` | `UNIFORM_ROUND_ROBIN`, `UNIFORM_RANDOM`, or `GAUSSIAN_RANDOM` |
| `-mf`, `--messages-in-flight` | `50000` | Producer in-flight throttle |

Smaller key spaces increase contention on individual keys; `GAUSSIAN_RANDOM` produces
hot keys, while `UNIFORM_*` spreads load evenly.

## 4. Consume the topic (terminal 3)

Pick one of the two consumer implementations. Run them against the same topic +
subscription to compare behaviour.

### Option A — `pulsar-listener` (classic `MessageListener`)

```bash
./gradlew :pulsar-listener:run --args='--concurrency 100'
```

Key options (all in `PulsarListenerApp`):

| Option | Default | Description |
| --- | --- | --- |
| `-c`, `--concurrency` | `100` | Listener thread count, or virtual-thread semaphore limit when `-vt` is set |
| `-vt`, `--virtual-threads` | off | Use a per-key virtual-thread executor (`VirtualThreadsMessageListenerExecutor`) |
| `-q`, `--queue` | `1000` | Receiver queue size |
| `-e`, `--endpoint` | `http://localhost:8888/api/slow` | Downstream HTTP endpoint |
| `-u`, `--url` | `pulsar://localhost:6650/` | Pulsar service URL |
| `-t`, `--topic` | `persistent://public/default/test` | Topic |
| `-s`, `--subscription` | `test-subscription` | Subscription name |
| `-n`, `--consumer` | `test-consumer` | Consumer name |

Useful comparisons:

```bash
# Low concurrency: throughput limited by ~10 in-flight HTTP calls
./gradlew :pulsar-listener:run --args='--concurrency 10'

# High concurrency on platform threads: 200 listener threads
./gradlew :pulsar-listener:run --args='--concurrency 200'

# Virtual threads with per-key ordering, 500 in-flight max
./gradlew :pulsar-listener:run --args='--virtual-threads --concurrency 500'

# Smaller receiver queue — more visible backpressure
./gradlew :pulsar-listener:run --args='--virtual-threads --concurrency 500 --queue 100'
```

### Option B — `reactive-client-impl` (key-ordered reactive pipeline)

```bash
./gradlew :reactive-client-impl:run --args='--concurrency 100'
```

Same options as the listener, except there is no `--virtual-threads` flag — the reactive
pipeline uses `useKeyOrderedProcessing()` to interleave messages across keys while
preserving per-key ordering.

```bash
# Low concurrency
./gradlew :reactive-client-impl:run --args='--concurrency 10'

# Match the high-concurrency listener run for a side-by-side comparison
./gradlew :reactive-client-impl:run --args='--concurrency 500 --queue 1000'
```

## What to observe

Each consumer logs a running `msg/s` rate every 100 processed messages. Try the same
generator workload against:

- `pulsar-listener --concurrency 10` vs `--concurrency 200` — throughput scales until
  the downstream service or key skew becomes the bottleneck.
- `pulsar-listener --concurrency 500` vs `pulsar-listener --virtual-threads --concurrency 500`
  — virtual threads let many in-flight blocking HTTP calls coexist cheaply.
- `pulsar-listener --virtual-threads` vs `reactive-client-impl` at the same concurrency —
  both achieve high parallelism; the reactive variant does it without blocking threads.
- `generator --key-space-size 10` vs `--key-space-size 5000` — small key spaces cap
  effective parallelism in `Key_Shared` mode regardless of consumer concurrency.
- `http-server --delay 10` vs `--delay 500` — a slower downstream service makes the
  concurrency knob far more visible.

## Cleanup

Stop the consumer/generator/server with Ctrl-C, then stop the broker:

```bash
docker stop pulsar
```

To reset the topic between runs, delete and recreate the subscription via the admin API,
or use a fresh subscription name with `--subscription`.
