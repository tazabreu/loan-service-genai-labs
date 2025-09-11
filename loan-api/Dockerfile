FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY loan-api/pom.xml loan-api/pom.xml
COPY loan-events/pom.xml loan-events/pom.xml
COPY loan-notification-service/pom.xml loan-notification-service/pom.xml
COPY e2e-tests/pom.xml e2e-tests/pom.xml
RUN mvn -q -DskipTests dependency:go-offline
COPY . .
RUN mvn -q -DskipTests -pl loan-api -am package

FROM eclipse-temurin:21-jre
ENV JAVA_OPTS=""
# Copy the Spring Boot repackage artifact with the exec classifier (the runnable jar)
COPY --from=build /workspace/loan-api/target/*-exec.jar /app/app.jar
EXPOSE 8081
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/app.jar"]
