FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

# Add non-root user
RUN groupadd -r appgroup && useradd -r -g appgroup appuser

COPY build/libs/store-*.jar app.jar

RUN chown appuser:appgroup app.jar

USER appuser

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
