# Gün Sonu (End of Day)

A B2C marketplace where bakeries, cafés and restaurants sell discounted end-of-day surplus food, which customers order online and pick up within a set time window.

## About

Gün Sonu is the term project for BIL372 Database Systems at TOBB University of Economics and Technology. Businesses publish surplus food as discounted packages with a fixed pickup window, and customers reserve and pay for them online before collecting them in person. The project focuses on a carefully designed relational schema in MySQL, with constraints, triggers, stored procedures and reporting views backing a Spring Boot web application.

Team members: Yusuf Çakır, Yusuf Enes Kütük, Efe Deniz Kahvecioğlu.

## Tech stack

- Java 25 (Eclipse Temurin) and Spring Boot 4.1, built with the Maven Wrapper
- MySQL 8.4 LTS, run locally with Docker Compose
- Flyway for schema migrations
- Spring JdbcClient for data access
- Spring Security with session-based authentication
- Plain HTML, CSS and JavaScript (ES modules) for the frontend
- JUnit 5, AssertJ and Testcontainers for testing
- Python data generator for large test data sets

## Prerequisites

- JDK 25
- Docker Desktop (WSL 2 backend on Windows)
- Git
- Python 3.12+

## Getting started

1. Copy `.env.example` to `.env` and replace the placeholder passwords with your own values.
2. If port 3306 is already in use on your machine (for example by a local MySQL installation), set `MYSQL_PORT` in `.env` to a free port such as `3307`.
3. Start the database:

   ```shell
   docker compose up -d
   ```

4. Check that the `mysql` service reports `healthy`:

   ```shell
   docker compose ps
   ```

> **Warning:** `docker compose down -v` deletes the database volume; the init script runs again only on a fresh volume.

5. Start the backend with the `dev` profile. It reads the database settings from the `.env` file in the repository root.

   ```shell
   cd backend
   ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev
   ```

   On Windows, use the `mvnw.cmd` wrapper instead. In PowerShell, quote the property:

   ```powershell
   cd backend
   .\mvnw.cmd spring-boot:run "-Dspring-boot.run.profiles=dev"
   ```

6. Check that the application is up:

   ```shell
   curl http://localhost:8080/actuator/health
   ```

   The response contains `"status":"UP"`.

### Running tests

Docker Desktop must be running. From the `backend` directory, run:

```shell
./mvnw verify
```

On Windows, use `.\mvnw.cmd verify`. The build checks the code formatting, runs all tests and checks coverage. Integration tests start a throwaway MySQL container with the same image, server settings and init script as `compose.yaml`, so they do not use or change the local database.

Further setup steps will be added as the project infrastructure is completed.

## Repository structure

| Path | Description |
| --- | --- |
| `.github/` | CI workflow, issue and pull request templates, code owners and Dependabot configuration |
| `backend/` | Spring Boot application, including Flyway migrations, seed data and the static frontend |
| `database/` | Database and account initialization scripts and analytical queries |
| `datagen/` | Python generator for large data sets and its verification queries |
| `docs/` | Architecture, ERD, glossary, API, error codes and other project documentation |
| `compose.yaml` | Docker Compose definition of the local MySQL service |
| `.env.example` | Template for local environment variables |

## Contributing

See [CONTRIBUTING.md](CONTRIBUTING.md) for branching, commit, pull request and code style guidelines.

## License

All rights reserved. This repository is coursework and is not licensed for reuse.
