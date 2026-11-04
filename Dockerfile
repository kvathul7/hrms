# AXVON HRMS — container image for the backend API.
#
# This is NOT how the product ships. AXVON ships as a Windows .exe built by
# BUILD_AXVON.bat, which bundles a Java runtime and Electron so the customer installs
# nothing. This image exists for the cases a desktop installer cannot cover: running the
# API in CI, pointing a dev frontend at a disposable backend, or giving a reviewer
# something to curl. BUILD_AXVON.bat and the Electron packaging are untouched by it.
#
# Scope is deliberately the backend only. The frontend is served by Electron in the
# product and by the vite dev server in development, and the backend sets
# server.servlet.context-path=/api — so bolting static hosting onto this image would
# create a third, differently-behaving way to serve the UI for no real gain.
#
#   docker build -t axvon-hrms .
#   docker run -p 17823:17823 \
#     -e AXVON_JWT_SECRET=<32+ chars> \
#     -e ANTHROPIC_API_KEY=sk-ant-... \
#     -v axvon-data:/data axvon-hrms
#
# Without ANTHROPIC_API_KEY the HR Assistant reports itself switched off and every other
# module works normally — the same behaviour as the desktop build.

# ── build ────────────────────────────────────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /src
# Dependencies first: this layer is invalidated by a pom change, not by every edit to a
# controller. Spring AI pulls ~70 MB of ONNX runtime, so caching it matters.
COPY backend/pom.xml .
RUN mvn -B -q dependency:go-offline
COPY backend/src ./src
# Tests run here on purpose. An image that boots but fails the leave-authorisation,
# payroll or assistant-scoping tests is not a build worth shipping.
RUN mvn -B clean package

# ── run ──────────────────────────────────────────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine
# wget is needed by HEALTHCHECK below; alpine's busybox wget is already present, but
# curl is not — do not add a healthcheck that assumes it.
RUN addgroup -S axvon && adduser -S axvon -G axvon
WORKDIR /app
COPY --from=build /src/target/axvon-hrms.jar app.jar

# H2, the logs, the per-install JWT key, the launch backups and the policy index all hang
# off user.home. Pointing it at the volume keeps them together — and a container
# restarted WITHOUT a volume mounted here discards the HR database.
RUN mkdir -p /data && chown -R axvon:axvon /data /app
VOLUME /data
USER axvon
ENV JAVA_TOOL_OPTIONS="-Duser.home=/data"
# The ONNX embedding model behind policy search is memory-resident; 512m is too tight.
ENV JAVA_OPTS="-Xmx768m"

# Rule 1: the port is fixed at 17823. Remap on the host side with -p if it clashes.
EXPOSE 17823
# /setup/status, NOT /dashboard/health: the latter requires a token and answers 401,
# which wget treats as a failure, so the container would report unhealthy forever while
# serving perfectly. /setup/status is permitAll (SecurityConfig) and returns a real 200.
HEALTHCHECK --interval=30s --timeout=4s --start-period=70s --retries=3 \
  CMD wget -qO- http://localhost:17823/api/setup/status >/dev/null 2>&1 || exit 1

ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS -jar /app/app.jar"]
