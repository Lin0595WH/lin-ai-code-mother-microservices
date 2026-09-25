FROM eclipse-temurin:21-jre-jammy

RUN groupadd --gid 10001 app \
    && useradd --uid 10001 --gid app --create-home app

WORKDIR /app
COPY --chown=10001:10001 releases/lin-ai-code-app.jar app.jar

USER 10001:10001
EXPOSE 8125 50053
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
