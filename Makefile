.PHONY: build test run run-pg up down docker clean

build:
	mvn -B -q package -DskipTests

test:
	mvn -B verify

run:
	mvn -B spring-boot:run

# needs `make up` (or any postgres) first
run-pg:
	DB_PASSWORD=webhooks mvn -B spring-boot:run -Dspring-boot.run.profiles=postgres

up:
	docker compose up -d --build

down:
	docker compose down -v

docker:
	docker build -t payment-webhooks:dev .

clean:
	mvn -B -q clean
