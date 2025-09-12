FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY loan-api/pom.xml loan-api/pom.xml
COPY loan-events/pom.xml loan-events/pom.xml
COPY loan-notification-service/pom.xml loan-notification-service/pom.xml
RUN mvn -q -DskipTests dependency:go-offline
COPY . .
RUN mvn -q -DskipTests -pl loan-notification-service -am package

FROM eclipse-temurin:21-jre
ENV JAVA_OPTS=""
# Download OpenTelemetry Java agent
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.7.0/opentelemetry-javaagent.jar /otel/opentelemetry-javaagent.jar
# Enable agent by default
ENV JAVA_TOOL_OPTIONS="-javaagent:/otel/opentelemetry-javaagent.jar"
# Copy the Spring Boot repackage artifact with the exec classifier (the runnable jar)
COPY --from=build /workspace/loan-notification-service/target/*-exec.jar /app/app.jar
EXPOSE 8082
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/app.jar"]
