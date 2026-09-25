FROM debian:trixie-slim

RUN apt-get update \
    && apt-get install --yes --no-install-recommends \
        ca-certificates \
        chromium \
        chromium-driver \
        fonts-noto-cjk \
        fontconfig \
        openjdk-21-jre-headless \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 app \
    && useradd --uid 10001 --gid app --create-home app

WORKDIR /app
COPY --chown=10001:10001 releases/lin-ai-code-screenshot.jar app.jar

USER 10001:10001
EXPOSE 8127 50052
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
