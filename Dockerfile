# === BUILD STAGE ===
# Use a Maven image with OpenJDK 17
# Using '3-openjdk-17' which is a widely available tag for Maven 3 with OpenJDK 17
FROM maven:3-openjdk-17 AS build

# Set the working directory inside the build container
WORKDIR /app

# Copy the pom.xml and download dependencies first to leverage Docker cache
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy the entire project source code
COPY src ./src

# Package the application into a JAR
RUN mvn clean package -DskipTests

# === RUNTIME STAGE ===
# Use a lean Eclipse Temurin JRE base image for the final application
# Using '17-jre-focal' for a smaller runtime image
FROM eclipse-temurin:17-jre-focal

# Set working directory in container
WORKDIR /app

# Copy the JAR file from the build stage into the final container
COPY --from=build /app/target/KasiKotas-1.0-SNAPSHOT.jar app.jar

# Expose port that Spring Boot will run on
EXPOSE 8080

# Run the application
ENTRYPOINT ["java", "-jar", "app.jar"]