FROM maven:3.9.16-eclipse-temurin-21-alpine@sha256:d6390509b774df0a07d009a194a020aeff9c715afe1e3d2cd5ecb3bf6344700f AS build
WORKDIR /workspace
COPY . .
RUN mvn -q -pl appointment-service -am package -DskipTests

FROM eclipse-temurin:21.0.12.1_1-jre-alpine@sha256:b48e02e0ea88d7bfb305335df94923bdef394905f4147dccd52e873cf17b1088
# brotli-libs 1.2.1 fixes CVE-2026-111497; drop this once the pinned base image ships it.
RUN apk upgrade --no-cache brotli-libs \
    && addgroup -S -g 10001 clinicflow \
    && adduser -S -D -H -u 10001 -G clinicflow clinicflow
WORKDIR /app
COPY --from=build /workspace/appointment-service/target/appointment-service-0.1.0-SNAPSHOT.jar app.jar
USER 10001:10001
EXPOSE 8083
ENTRYPOINT ["java", "-jar", "app.jar"]
