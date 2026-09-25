FROM node:22-bookworm-slim

RUN apt-get update \
    && apt-get install -y --no-install-recommends zip unzip \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 builder \
    && useradd --uid 10001 --gid builder --create-home builder

WORKDIR /worker
COPY docker/vue-builder.mjs /worker/vue-builder.mjs

USER 10001:10001
EXPOSE 8128
CMD ["node", "/worker/vue-builder.mjs"]
