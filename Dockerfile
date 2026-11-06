FROM clojure:temurin-27-tools-deps-trixie-slim AS build

RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*
RUN curl -sLO https://raw.githubusercontent.com/babashka/babashka/master/install \
    && chmod +x install \
    && ./install \
    && rm install

WORKDIR /tmp/antq
COPY deps.edn bb.edn ./
COPY src ./src/
COPY resources ./resources/
COPY bb ./bb/
RUN bb uberjar

FROM eclipse-temurin:27-jre

RUN apt-get update \
    && apt-get install -y --no-install-recommends git \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build /tmp/antq/target/antq-standalone.jar /tmp/antq/antq.jar
WORKDIR /tmp
ENTRYPOINT ["java", "-jar", "/tmp/antq/antq.jar"]
