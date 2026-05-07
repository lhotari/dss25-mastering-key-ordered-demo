# DSS25 demo command runner.
#
# Each app recipe is variadic: extra arguments after `--` are passed straight
# through to the underlying program, e.g.
#
#   just pulsar-listener -- --virtual-threads --concurrency 500
#   just generator -- --number-of-messages 200000 --key-space-size 500
#   just http-server -- --delay 500 --jitter 0.2
#
# Run `just --list` to see all recipes.

# List all available recipes
default:
    @just --list

# Start a Pulsar standalone broker via docker-compose (foreground, ports 6650/8080)
pulsar:
    docker compose up pulsar

# Stop the Pulsar broker container started by `just pulsar` (keeps volumes)
pulsar-stop:
    -docker compose stop pulsar


# Build install distributions for every backend module (creates the launcher
# scripts that the run recipes below exec). Re-run after Java code changes.
backend-install:
    ./gradlew installDist

# Install demo-ui dependencies (run once, or after package.json changes)
ui-install:
    cd demo-ui && npm install

# One-shot setup: backend launcher scripts + demo-ui npm deps.
install: backend-install ui-install

# Start the Vite dev server for the demo UI on http://localhost:5173
ui:
    cd demo-ui && npm run dev

# Run the slow HTTP service. Pass extra args after `--`.
# Prerequisite: `just install` (or `just backend-install`).
# Example: just http-server -- --delay 500 --jitter 0.2
http-server *ARGS:
    ./http-server/build/install/http-server/bin/http-server {{ trim_start_match(ARGS, "-- ") }}

# Run the generator. Pass extra args after `--`.
# Prerequisite: `just install` (or `just backend-install`).
# Example: just generator -- --number-of-messages 200000 --key-space-size 500
generator *ARGS:
    ./generator/build/install/generator/bin/generator {{ trim_start_match(ARGS, "-- ") }}

# Run the classic MessageListener consumer. Pass extra args after `--`.
# Prerequisite: `just install` (or `just backend-install`).
# Example: just pulsar-listener -- --virtual-threads --concurrency 500
pulsar-listener *ARGS:
    ./pulsar-listener/build/install/pulsar-listener/bin/pulsar-listener {{ trim_start_match(ARGS, "-- ") }}

# Run the reactive-client consumer. Pass extra args after `--`.
# Prerequisite: `just install` (or `just backend-install`).
# Example: just reactive-client-impl -- --concurrency 500 --queue 1000
reactive-client-impl *ARGS:
    ./reactive-client-impl/build/install/reactive-client-impl/bin/reactive-client-impl {{ trim_start_match(ARGS, "-- ") }}

# Launch the full tmuxp-driven demo session
demo:
    tmuxp load demo-tmuxp.yaml

# Stop the demo: stop the tmux session and the Pulsar broker (state preserved)
stop: pulsar-stop
    -tmux kill-session -t dss25-demo

# Wipe all Pulsar state: stops the demo if running, then removes containers AND
# named volumes (pulsardata, pulsarconf). Use this when you want a fresh broker.
clean: stop
    -docker compose down -v --remove-orphans
