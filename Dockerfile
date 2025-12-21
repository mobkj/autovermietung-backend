# -------- BUILD STAGE --------
FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app

# erst pom.xml kopieren (bessere Docker-Caching)
COPY pom.xml .
COPY src ./src

RUN mvn -DskipTests clean package

# -------- RUN STAGE --------
FROM eclipse-temurin:17-jre
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
EXPOSE 8080
CMD ["java", "-jar", "app.jar"]
