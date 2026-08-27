.PHONY: test run clean db-up db-down db-status db-logs

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
