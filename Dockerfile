FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app

# Copy pom.xml and download dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build
COPY src ./src
RUN mvn clean package -DskipTests

# Runtime stage
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Create log directory
RUN mkdir -p /var/log/app && chmod 755 /var/log/app

# Copy JAR from build stage
COPY --from=build /app/target/*.jar app.jar

# Run as non-root user
RUN addgroup -g 1001 appuser && \
    adduser -D -u 1001 -G appuser appuser && \
    chown -R appuser:appuser /app /var/log/app

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
