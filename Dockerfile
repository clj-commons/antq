FROM clojure:temurin-26-tools-deps-trixie-slim

RUN apt-get update && apt-get install -y git && rm -rf /var/lib/apt/lists/*

WORKDIR /tmp/antq
COPY deps.edn /tmp/antq/deps.edn
COPY build.clj /tmp/antq/build.clj
COPY src/ /tmp/antq/src/
RUN clojure -T:build uberjar && mv /tmp/antq/target/antq-standalone.jar /tmp/antq/antq.jar

WORKDIR /tmp
ENTRYPOINT ["java", "-jar", "/tmp/antq/antq.jar"]
