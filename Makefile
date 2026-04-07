COMPOSE_INFRA = \
	-f deploy/compose.infra.mysql.yml \
	-f deploy/compose.infra.redis.yml \
	-f deploy/compose.infra.kafka.yml \
	-f deploy/compose.infra.elasticsearch.yml \
	-f deploy/compose.infra.nacos.yml \
	-f deploy/compose.infra.xxl-job.yml \
	-f deploy/compose.infra.mailhog.yml \
	-f deploy/compose.infra.mock-data-studio-bootstrap.yml

COMPOSE_BASE = docker compose --env-file deploy/.env \
	-f deploy/compose.yml \
	$(COMPOSE_INFRA) \
	-f deploy/compose.runtime.yml
COMPOSE_DEBUG = $(COMPOSE_BASE) -f deploy/compose.debug.yml
COMPOSE_OBS = $(COMPOSE_BASE) -f deploy/compose.observability.yml
COMPOSE_ELASTIC = $(COMPOSE_BASE) -f deploy/compose.observability-elastic.yml
COMPOSE_ELASTIC_JSON = $(COMPOSE_ELASTIC) -f deploy/compose.json-logs.override.yml

.PHONY: up up-debug up-obs up-elastic up-elastic-json \
	down down-debug down-obs down-elastic down-elastic-json \
	ps ps-debug ps-obs ps-elastic ps-elastic-json \
	logs logs-debug logs-obs logs-elastic logs-elastic-json \
	config config-debug config-obs config-elastic config-elastic-json

up:
	$(COMPOSE_BASE) up -d --build

up-debug:
	$(COMPOSE_DEBUG) up -d --build

up-obs:
	$(COMPOSE_OBS) up -d --build

up-elastic:
	$(COMPOSE_ELASTIC) up -d --build

up-elastic-json:
	$(COMPOSE_ELASTIC_JSON) up -d --build

down:
	$(COMPOSE_BASE) down

down-debug:
	$(COMPOSE_DEBUG) down

down-obs:
	$(COMPOSE_OBS) down

down-elastic:
	$(COMPOSE_ELASTIC) down

down-elastic-json:
	$(COMPOSE_ELASTIC_JSON) down

ps:
	$(COMPOSE_BASE) ps

ps-debug:
	$(COMPOSE_DEBUG) ps

ps-obs:
	$(COMPOSE_OBS) ps

ps-elastic:
	$(COMPOSE_ELASTIC) ps

ps-elastic-json:
	$(COMPOSE_ELASTIC_JSON) ps

logs:
	$(COMPOSE_BASE) logs -f --tail=200

logs-debug:
	$(COMPOSE_DEBUG) logs -f --tail=200

logs-obs:
	$(COMPOSE_OBS) logs -f --tail=200

logs-elastic:
	$(COMPOSE_ELASTIC) logs -f --tail=200

logs-elastic-json:
	$(COMPOSE_ELASTIC_JSON) logs -f --tail=200

config:
	$(COMPOSE_BASE) config

config-debug:
	$(COMPOSE_DEBUG) config

config-obs:
	$(COMPOSE_OBS) config

config-elastic:
	$(COMPOSE_ELASTIC) config

config-elastic-json:
	$(COMPOSE_ELASTIC_JSON) config
