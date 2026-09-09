# ---------- build ----------
# One stage builds the whole thing: the frontend-maven-plugin pulls its own Node
# (v20.18.0) during `package` and writes the SPA into the jar, so all we need here
# is a JDK + Maven.
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY . .
# Tests run in CI on every push; skip them here to keep deploys fast.
RUN mvn -B -DskipTests clean package

# ---------- run ----------
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/target/priority-backlog-tracker-*.jar app.jar

# Prod profile: real Mongo via MONGODB_URI, no embedded server, no dev seed user.
# MaxRAMPercentage keeps the JVM inside the container's memory limit (512 MB on
# Render's free tier).
ENV SPRING_PROFILES_ACTIVE=prod \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0"

# Render provides $PORT; application.yml already reads it (server.port: ${PORT:8080}).
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
