# syntax=docker/dockerfile:1
FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

ARG QUARKUS_PROFILE=prod

COPY pom.xml .
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    mvn dependency:go-offline -B

COPY src ./src

RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    mvn clean package -Dquarkus.profile=${QUARKUS_PROFILE} -DskipTests -B

FROM eclipse-temurin:21-jdk

WORKDIR /app

COPY --from=build /build/target/quarkus-app/ /app/

CMD ["java", "-jar", "/app/quarkus-run.jar"]
