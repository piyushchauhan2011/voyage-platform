# Stage 1: build the fat jar
FROM eclipse-temurin:21-jdk-alpine AS builder
WORKDIR /workspace

# Copy Maven wrapper first so the binary is cached on its own layer
COPY mvnw .
COPY .mvn .mvn
RUN chmod +x mvnw

# Copy all POM files before source so dependency resolution is a cached layer
COPY pom.xml .
COPY voyage-app/pom.xml voyage-app/pom.xml
COPY java-mastery/pom.xml java-mastery/pom.xml

# Download dependencies (re-run only when a pom changes)
RUN ./mvnw dependency:go-offline -pl voyage-app -q

# Copy application source
COPY voyage-app/src voyage-app/src

RUN ./mvnw clean package -pl voyage-app -DskipTests -q

# Stage 2: minimal runtime image
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S voyage && adduser -S voyage -G voyage

COPY --from=builder /workspace/voyage-app/target/voyage-app-0.0.1-SNAPSHOT.jar app.jar

USER voyage
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
