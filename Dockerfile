#FROM clojure:openjdk-17 AS build
FROM ubuntu:22:04 AS build

RUN apt-cache search wget
RUN apt-get install wget

WORKDIR /

RUN /usr/bin/wget https://download.clojure.org/install/linux-install-1.11.1.1182.sh \
  && chmod +x linux-install-1.11.1.1182.sh \
  && ./linux-install-1.11.1.1182.sh

# install from nodesource using apt-get
# RUN /usr/bin/curl -sL https://deb.nodesource.com/setup | bash - && apt-get install -yq nodejs build-essential

RUN npm install

RUN npx shadow-cljs compile frontend

COPY . /

RUN clj -Sforce -T:build all

FROM azul/zulu-openjdk-alpine:17

COPY --from=build /target/exerciser-standalone.jar /exerciser/exerciser-standalone.jar

EXPOSE 3000

ENTRYPOINT exec java $JAVA_OPTS -jar /exerciser/exerciser-standalone.jar
