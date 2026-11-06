# Multi-stage image: build with JDK 21, run on a small JRE 21 image as a non-root user.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl \
    && addgroup -S smartlib \
    && adduser -S smartlib -G smartlib
WORKDIR /app
COPY --from=build /app/target/smartlib-backend-0.0.1-SNAPSHOT.jar app.jar
RUN chown -R smartlib:smartlib /app
USER smartlib
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -fsS http://localhost:8080/actuator/health || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
