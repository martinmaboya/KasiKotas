# === BUILD STAGE ===
# Use a Maven image to build the application
FROM maven:3.8.7-openjdk-17 AS build

# Set the working directory inside the build container
WORKDIR /app

# Copy the pom.xml and download dependencies first to leverage Docker cache
# This means if only source code changes, not dependencies, this layer is cached
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy the entire project source code
COPY src ./src

# Package the application into a JAR
RUN mvn clean package -DskipTests

# === RUNTIME STAGE ===
# Use a lean Eclipse Temurin JRE base image for the final application
FROM eclipse-temurin:17-jre-focal

# Set working directory in container
WORKDIR /app

# Copy the JAR file from the build stage into the final container
COPY --from=build /app/target/KasiKotas-1.0-SNAPSHOT.jar app.jar

# Expose port that Spring Boot will run on
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]