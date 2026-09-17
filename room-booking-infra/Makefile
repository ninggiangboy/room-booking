.PHONY: local-mini local down

# local-mini: the backend's dependencies plus the all-in-one observability container. No host-level
# setup beyond Docker. See docs/runbook-local.md.
local-mini:
	docker compose -f compose.local.yaml up -d

# local: local-mini plus Postgres/MinIO metrics and container log shipping. Needs the Docker `loki`
# logging driver installed once — see docs/runbook-local.md's Preconditions.
local:
	docker compose -f compose.local.yaml -f compose.local-instrumented.yaml up -d

# Stops whichever of the two is running. Passing both files to `down` is harmless when only the
# base file's services are up.
down:
	docker compose -f compose.local.yaml -f compose.local-instrumented.yaml down
