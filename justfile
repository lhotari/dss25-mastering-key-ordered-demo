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


# Install demo-ui dependencies (run once, or after package.json changes)
ui-install:
    cd demo-ui && npm install

# Start the Vite dev server for the demo UI on http://localhost:5173
ui:
    cd demo-ui && npm run dev

# Run the slow HTTP service. Pass extra args after `--`.
# Example: just http-server -- --delay 500 --jitter 0.2
http-server *ARGS:
    ./gradlew :http-server:run {{ if ARGS == '' { '' } else { '--args=' + quote(ARGS) } }}

# Run the generator. Pass extra args after `--`.
# Example: just generator -- --number-of-messages 200000 --key-space-size 500
generator *ARGS:
    ./gradlew :generator:run {{ if ARGS == '' { '' } else { '--args=' + quote(ARGS) } }}

# Run the classic MessageListener consumer. Pass extra args after `--`.
# Example: just pulsar-listener -- --virtual-threads --concurrency 500
pulsar-listener *ARGS:
    ./gradlew :pulsar-listener:run {{ if ARGS == '' { '' } else { '--args=' + quote(ARGS) } }}

# Run the reactive-client consumer. Pass extra args after `--`.
# Example: just reactive-client-impl -- --concurrency 500 --queue 1000
reactive-client-impl *ARGS:
    ./gradlew :reactive-client-impl:run {{ if ARGS == '' { '' } else { '--args=' + quote(ARGS) } }}

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
