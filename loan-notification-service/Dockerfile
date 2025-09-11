FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /workspace
COPY pom.xml ./
COPY loan-api/pom.xml loan-api/pom.xml
COPY loan-events/pom.xml loan-events/pom.xml
COPY loan-notification-service/pom.xml loan-notification-service/pom.xml
RUN mvn -q -DskipTests dependency:go-offline
COPY . .
RUN mvn -q -DskipTests -pl loan-notification-service -am package

FROM eclipse-temurin:25-jre
ENV JAVA_OPTS=""
COPY --from=build /workspace/loan-notification-service/target/loan-notification-service-*.jar /app/app.jar
EXPOSE 8082
ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/app.jar"]

