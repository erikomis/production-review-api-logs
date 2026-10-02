FROM eclipse-temurin:17-jdk AS build
WORKDIR /app

# baixa o Gradle e as dependências em camadas separadas para aproveitar o cache do Docker
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN chmod +x gradlew && ./gradlew --no-daemon dependencies > /dev/null

COPY src ./src
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:17-jre

ENV TZ=America/Sao_Paulo

# MONGODB_URI, KAFKA_BOOTSTRAP_SERVERS e LOGS_API_TOKEN vêm do ambiente (docker-compose / .env)
RUN useradd --system --uid 1001 app
USER app

COPY --from=build /app/build/libs/*.jar /app.jar

EXPOSE 8089

ENTRYPOINT ["java", "-jar", "/app.jar"]
