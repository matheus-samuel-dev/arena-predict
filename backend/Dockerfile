FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .

RUN mvn dependency:go-offline -B

COPY src ./src

RUN mvn clean package -DskipTests


FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

RUN addgroup -S arena && adduser -S arena -G arena

COPY --from=build --chown=arena:arena /app/target/*.jar app.jar

USER arena

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
