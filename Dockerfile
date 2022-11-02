FROM debian:bullseye

# Purpose: Build RADmapper exerciser including fresh app.js (using shadow-cljs).

# ToDo:
#   - The above is NOT where I'd like to start!
#     See  https://github.com/Quantisan/docker-clojure/blob/master/target/debian-bullseye-17/tools-deps/Dockerfile
#     What is the image being made?

ENV JAVA_HOME=/opt/java/openjdk
COPY --from=eclipse-temurin:17 $JAVA_HOME $JAVA_HOME
ENV PATH="${JAVA_HOME}/bin:${PATH}"

ENV CLOJURE_VERSION=1.11.1.1182

WORKDIR /tmp

RUN \
apt-get update && \
apt-get install -y curl make rlwrap wget git && \
rm -rf /var/lib/apt/lists/* && \
wget https://download.clojure.org/install/linux-install-$CLOJURE_VERSION.sh && \
sha256sum linux-install-$CLOJURE_VERSION.sh && \
echo "0e80041419bb91e142e2e8683e4dad6faf79958b603bb63b2a93bdd62c2a4f14 *linux-install-$CLOJURE_VERSION.sh" | sha256sum -c - && \
chmod +x linux-install-$CLOJURE_VERSION.sh && \
./linux-install-$CLOJURE_VERSION.sh && \
rm linux-install-$CLOJURE_VERSION.sh && \
clojure -e "(clojure-version)"

# Install nodejs from nodesource using apt-get
RUN /usr/bin/curl -sL https://deb.nodesource.com/setup_19.x | bash - && apt-get install -yq nodejs build-essential

WORKDIR /

RUN git clone https://github.com/pdenno/RADmapperExerciser.git

WORKDIR RADmapperExerciser

RUN npm install

RUN npx shadow-cljs compile frontend

RUN clj -Sforce -T:build all

EXPOSE 3000

ENTRYPOINT exec java $JAVA_OPTS -jar target/exerciser-standalone.jar
