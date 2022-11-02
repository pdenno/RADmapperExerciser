# syntax = docker/dockerfile:1.2
FROM clojure:openjdk-17 AS build

WORKDIR /
COPY . /

RUN clj -Sforce -T:build all

FROM azul/zulu-openjdk-alpine:17

COPY --from=build /target/exerciser-standalone.jar /exerciser/exerciser-standalone.jar

EXPOSE 3000

ENTRYPOINT exec java $JAVA_OPTS -jar /exerciser/exerciser-standalone.jar
