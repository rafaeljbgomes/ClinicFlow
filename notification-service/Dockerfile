FROM maven:3.9.16-eclipse-temurin-21-alpine@sha256:d6390509b774df0a07d009a194a020aeff9c715afe1e3d2cd5ecb3bf6344700f AS build
WORKDIR /workspace
COPY . .
RUN mvn -q -pl notification-service -am package -DskipTests

FROM eclipse-temurin:21.0.13_9-jre-alpine@sha256:ac05c3c42b7c2f1a52c9bd52f01041fa1958231b15ebeae7420429cbc5baf474
# brotli-libs 1.2.1 fixes CVE-2026-111497 and libexpat 2.10.0 fixes CVE-2026-113351, CVE-2026-115506,
# CVE-2026-117610 and CVE-2026-118370; drop each once the pinned base image ships it.
RUN apk upgrade --no-cache brotli-libs libexpat \
    && addgroup -S -g 10001 clinicflow \
    && adduser -S -D -H -u 10001 -G clinicflow clinicflow
WORKDIR /app
COPY --from=build /workspace/notification-service/target/notification-service-0.1.0-SNAPSHOT.jar app.jar
USER 10001:10001
EXPOSE 8084
ENTRYPOINT ["java", "-jar", "app.jar"]
