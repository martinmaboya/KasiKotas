# Use Eclipse Temurin base image with Java 17
FROM eclipse-temurin:17

# Set working directory in container
WORKDIR /app

# Copy the JAR file into the container
COPY target/KasiKotas-1.0-SNAPSHOT.jar app.jar

# Expose port that Spring Boot will run on
EXPOSE 8080

# Run the application
ENTRYENTRYPOINT ["java", "-jar", "app.jar"]