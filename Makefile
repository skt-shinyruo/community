COMPOSE_BASE = docker compose --env-file deploy/.env \
	-f deploy/compose.yml \
	-f deploy/compose.infra.yml \
	-f deploy/compose.runtime.yml

.PHONY: up up-debug up-obs up-elastic up-elastic-json down ps logs config config-debug config-obs config-elastic config-elastic-json

up:
	$(COMPOSE_BASE) up -d --build

up-debug:
	$(COMPOSE_BASE) -f deploy/compose.debug.yml up -d --build

up-obs:
	$(COMPOSE_BASE) -f deploy/compose.observability.yml up -d --build

up-elastic:
	$(COMPOSE_BASE) -f deploy/compose.observability-elastic.yml up -d --build

up-elastic-json:
	$(COMPOSE_BASE) -f deploy/compose.observability-elastic.yml -f deploy/compose.json-logs.override.yml up -d --build

down:
	$(COMPOSE_BASE) down

ps:
	$(COMPOSE_BASE) ps

logs:
	$(COMPOSE_BASE) logs -f --tail=200

config:
	$(COMPOSE_BASE) config

config-debug:
	$(COMPOSE_BASE) -f deploy/compose.debug.yml config

config-obs:
	$(COMPOSE_BASE) -f deploy/compose.observability.yml config

config-elastic:
	$(COMPOSE_BASE) -f deploy/compose.observability-elastic.yml config

config-elastic-json:
	$(COMPOSE_BASE) -f deploy/compose.observability-elastic.yml -f deploy/compose.json-logs.override.yml config
