# The class server. Students never build this — the teacher runs it, or it runs
# on a server for the whole term. Students' games and players dial in over TCP.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src

# Poms first, so a code-only change reuses the dependency layer.
COPY pom.xml ./
COPY textgame-protocol/pom.xml textgame-protocol/
COPY textgame-server/pom.xml   textgame-server/
COPY textgame-client/pom.xml   textgame-client/
COPY textgame-example/pom.xml  textgame-example/
RUN mvn -B -q dependency:go-offline -DskipTests || true

COPY . .
# Tests run here on purpose: a broken build must not become a running server.
RUN mvn -B -q package

FROM eclipse-temurin:21-jre
WORKDIR /app

# Nothing here needs root, and the server writes no files.
RUN useradd --system --create-home --shell /usr/sbin/nologin textgame
COPY --from=build /src/textgame-server/target/textgame-server.jar /app/textgame-server.jar
USER textgame

ENV TEXTGAME_PORT=4000
EXPOSE 4000

# A plain TCP accept is the only meaningful health signal: there is no HTTP here.
HEALTHCHECK --interval=30s --timeout=5s --start-period=10s --retries=3 \
    CMD timeout 3 bash -c "</dev/tcp/127.0.0.1/${TEXTGAME_PORT}" || exit 1

ENTRYPOINT ["sh", "-c", "exec java -jar /app/textgame-server.jar \"$TEXTGAME_PORT\""]
