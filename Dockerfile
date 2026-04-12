# -------- Build stage --------
FROM gradle:8.7-jdk21 AS build
WORKDIR /home/app
COPY build.gradle.kts gradlew ./
COPY gradle gradle
COPY src src
RUN ./gradlew clean bootJar -x test

# -------- Package stage --------
FROM eclipse-temurin:21-jdk-alpine
RUN apk update && apk upgrade && apk add ffmpeg
WORKDIR /usr/local/lib
COPY --from=build /home/app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/usr/local/lib/app.jar"]