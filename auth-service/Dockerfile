FROM maven:3.9.16-eclipse-temurin-21-alpine@sha256:d6390509b774df0a07d009a194a020aeff9c715afe1e3d2cd5ecb3bf6344700f AS build
WORKDIR /workspace
COPY . .
RUN mvn -q -pl auth-service -am package -DskipTests

FROM eclipse-temurin:24.0.2_12-jre-alpine@sha256:4044b6c87cb088885bcd0220f7dc7a8a4aab76577605fa471945d2e98270741f
# brotli-libs 1.2.1 fixes CVE-2026-111497; drop this once the pinned base image ships it.
RUN apk upgrade --no-cache brotli-libs \
    && addgroup -S -g 10001 clinicflow \
    && adduser -S -D -H -u 10001 -G clinicflow clinicflow
WORKDIR /app
COPY --from=build /workspace/auth-service/target/auth-service-0.1.0-SNAPSHOT.jar app.jar
USER 10001:10001
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
