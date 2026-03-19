FROM maven:3.9-eclipse-temurin-21 AS build

WORKDIR /build

COPY pom.xml .
RUN mvn dependency:go-offline

COPY src ./src

RUN mvn clean package -Dquarkus.profile=${QUARKUS_PROFILE} -DskipTests

FROM eclipse-temurin:21-jdk

WORKDIR /app

COPY --from=build /build/target/quarkus-app/ /app/

CMD ["java", "-jar", "/app/quarkus-run.jar"]
