FROM gradle:8.10-jdk17 AS build

WORKDIR /app

COPY build.gradle settings.gradle /app/
COPY src /app/src

RUN gradle clean build

FROM eclipse-temurin:17

COPY --from=build /app/build/libs/*.jar /app/app.jar

ENV TZ=America/Sao_Paulo

EXPOSE 8089

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
