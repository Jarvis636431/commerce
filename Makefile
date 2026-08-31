.PHONY: test run clean db-up db-down db-status db-logs redis-up redis-down redis-status redis-logs redis-cli infra-up infra-down

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

infra-up:
	docker compose up -d postgres redis

infra-down:
	docker compose stop postgres redis
