.PHONY: test run clean db-up db-down db-status db-logs redis-up redis-down redis-status redis-logs redis-cli rabbit-up rabbit-down rabbit-status rabbit-logs observe-up observe-down observe-status observe-logs infra-up infra-down

test:
	./mvnw test

run:
	./mvnw spring-boot:run

clean:
	./mvnw clean

db-up:
	docker compose up -d postgres

db-down:
	docker compose stop postgres

db-status:
	docker compose ps postgres

db-logs:
	docker compose logs -f postgres

redis-up:
	docker compose up -d redis

redis-down:
	docker compose stop redis

redis-status:
	docker compose ps redis

redis-logs:
	docker compose logs -f redis

redis-cli:
	docker compose exec redis redis-cli

rabbit-up:
	docker compose up -d rabbitmq

rabbit-down:
	docker compose stop rabbitmq

rabbit-status:
	docker compose ps rabbitmq

rabbit-logs:
	docker compose logs -f rabbitmq

observe-up:
	mkdir -p logs
	docker compose up -d prometheus loki alloy grafana

observe-down:
	docker compose stop prometheus loki alloy grafana

observe-status:
	docker compose ps prometheus loki alloy grafana

observe-logs:
	docker compose logs -f prometheus loki alloy grafana

infra-up:
	mkdir -p logs
	docker compose up -d postgres redis rabbitmq prometheus loki alloy grafana

infra-down:
	docker compose stop postgres redis rabbitmq prometheus loki alloy grafana
