FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml .
COPY gateway-service gateway-service
RUN mvn -f gateway-service/pom.xml -q -Dmaven.test.skip=true package

FROM eclipse-temurin:17-jre-alpine
WORKDIR /app
COPY --from=build /workspace/gateway-service/target/gateway-service-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
