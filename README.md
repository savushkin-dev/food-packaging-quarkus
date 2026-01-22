# Food Packaging (Java, Quarkus, Maven)

Schedule food packaging orders to manufacturing lines, to minimize downtime and fulfill all orders in time.

- [Run the application](#run-the-application)
- [Configuration](#configuration)
- [REST API](#rest-api)
- [Run the packaged application](#run-the-packaged-application)

## Prerequisites

Install Java **21** and Maven, for example with [Sdkman](https://sdkman.io):

```shell
sdk install java 21.0.0-tem
sdk install maven
```

## Run the application

Clone the repo and navigate to this directory:

```shell
git clone https://github.com/savushkin-dev/food-packaging-quarkus.git
cd food-packaging-quarkus
```

Start the application with Maven:

```shell
mvn quarkus:dev -Dquarkus.profile=local
```

Visit `http://localhost:8080` in your browser.

Click on the **Solve** button.

Then try _live coding_:

- Make some changes in the source code.
- Refresh your browser (F5).

Notice that those changes are immediately in effect.

## Configuration

This app connects to **MS SQL Server**. Quarkus devservices are disabled, so you must provide DB connection settings via environment variables (see `src/main/resources/application.properties`).

### Profiles

- **local**: `-Dquarkus.profile=local` (default port 8080)
- **prod**: `-Dquarkus.profile=prod`

### Required environment variables

At minimum you need .env file with:

- **DB_USERNAME**: DB login
- **DB_PASSWORD**: DB password
- **DB_URL**: JDBC URL for Quarkus datasource
- **CORS_ORIGIN / CORS_ORIGIN_PROD / ...**: allowed origin(s) for browser clients (profile-specific)
- **HTTP_PORT**: DB login

Example (local profile):

```shell
mvn quarkus:dev -Dquarkus.profile=your_profile
```

## REST API

Base path: `/schedule`

### Session

Most endpoints use the request header **`X-Session-Id`** to isolate schedules per user/session.

### Common endpoints

- **GET `/schedule`**: get current schedule for session
- **POST `/schedule/init`**: build/load schedule for a start date (request: `LoadRequest`)
- **POST `/schedule/solve`**: start solver
- **POST `/schedule/stopSolving`**: stop solver
- **POST `/schedule/maintenance`**: add/update/remove maintenance job (request: `MaintenanceRequest`)
- **POST `/schedule/moveJobs`**: move jobs (request: `MoveJobsRequest`)
- **POST `/schedule/pin`**: pin/unpin jobs (request: `PinRequest`)
- **POST `/schedule/save`**: persist the current plan to DB

Example (init):

```shell
curl -X POST "http://localhost:8080/schedule/init" ^
  -H "Content-Type: application/json" ^
  -H "X-Session-Id: default" ^
  -d "{\"startDate\":\"2026-01-19\"}"
```
Example (maintenance add):

```shell
curl -X POST "http://localhost:8080/schedule/maintenance" ^
  -H "Content-Type: application/json" ^
  -H "X-Session-Id: default" ^
  -d "{\"lineId\":\"170610020000\",\"insertIndex\":0,\"durationMinutes\":30,\"maintenanceTypeId\": 2, \"maintenanceNote\":\"Note\"}"
```

## Run the packaged application

When you're done iterating in `quarkus:dev` mode, package the application to run as a conventional jar file.

Compile it with Maven:

```shell
mvn clean package
```

Run it:

```shell
$env:DB_URL = "your_mssql_url"
$env:DB_USERNAME = "your_username"
$env:DB_PASSWORD = "your_password"
$env:HTTP_PORT = "your_port"

java -jar ./target/quarkus-app/quarkus-run.jar
```

To run it on port 8081 instead, add `-Dquarkus.http.port=8081`.

Visit `http://localhost:8080` in your browser and click on the **Solve** button.

## More information

Visit https://timefold.ai.

