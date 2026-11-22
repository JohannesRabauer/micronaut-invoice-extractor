FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app
COPY pom.xml .

COPY src src
RUN mvn -q -B -DskipTests package && ls -l target/*.jar

# helper programs (sample generator, evaluation): same jar, plus fonts for drawing the receipts
FROM eclipse-temurin:25-jre AS tools
RUN apt-get update && apt-get install -y --no-install-recommends fontconfig fonts-dejavu-core && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/target/invoice-extractor-0.1.jar app.jar

FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/target/invoice-extractor-0.1.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
