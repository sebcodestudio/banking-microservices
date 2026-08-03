# syntax=docker/dockerfile:1

# ---- Etapa de build: compila con Maven + JDK 17 ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

COPY pom.xml .
COPY .mvn .mvn
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# ---- Etapa de runtime: solo JRE 17, sin herramientas de build ----
FROM eclipse-temurin:17-jre-alpine AS runtime
WORKDIR /app

RUN addgroup -S banco && adduser -S banco -G banco
COPY --from=build /build/target/customer-service-0.0.1-SNAPSHOT.jar app.jar
USER banco

EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
