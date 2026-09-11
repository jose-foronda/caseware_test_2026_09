FROM gradle:9.2.1-jdk17 AS build
WORKDIR /app
COPY .. .
RUN gradle clean build -x test --no-daemon


FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
