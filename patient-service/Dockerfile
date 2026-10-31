FROM maven:3.9-eclipse-temurin-26-alpine@sha256:4c85d516dd8c7e50d49a403d604e565e0dbb09a790186fe3245725d313ae7cb9 AS build
WORKDIR /workspace
COPY . .
RUN mvn -q -pl patient-service -am package -DskipTests

FROM eclipse-temurin:21.0.12.1_1-jre-alpine@sha256:b48e02e0ea88d7bfb305335df94923bdef394905f4147dccd52e873cf17b1088
# brotli-libs 1.2.1 fixes CVE-2026-111497; drop this once the pinned base image ships it.
RUN apk upgrade --no-cache brotli-libs \
    && addgroup -S -g 10001 clinicflow \
    && adduser -S -D -H -u 10001 -G clinicflow clinicflow
WORKDIR /app
COPY --from=build /workspace/patient-service/target/patient-service-0.1.0-SNAPSHOT.jar app.jar
USER 10001:10001
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
