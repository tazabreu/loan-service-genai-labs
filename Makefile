SHELL := /bin/bash

COMPOSE := docker compose -f docker/docker-compose.yml

.PHONY: build up down logs smoke test e2e e2e-ts e2e-ts-keep

build:
	mvn -DskipTests package

up:
	$(COMPOSE) up --build -d

down:
	$(COMPOSE) down -v

logs:
	$(COMPOSE) logs -f

smoke:
	bash scripts/dev_smoke.sh

test:
	mvn test

e2e: e2e-ts

e2e-ts:
	cd e2e && npm ci && npm run e2e:local

e2e-ts-keep:
	cd e2e && npm ci && npm run e2e:local:keep
