FROM maven:3.9.16-eclipse-temurin-21-alpine@sha256:d6390509b774df0a07d009a194a020aeff9c715afe1e3d2cd5ecb3bf6344700f AS build
WORKDIR /workspace
COPY . .
RUN mvn -q -pl patient-service -am package -DskipTests

FROM eclipse-temurin:21.0.12.1_1-jre-alpine@sha256:51ab5e3302e7141ce665ca3ea85e8b5cd648eafbc3c0c90dd79d6537684e4555
# libexpat 2.9.0 fixes CVE-2026-77214; drop this once the pinned base image ships it.
RUN apk upgrade --no-cache libexpat \
    && addgroup -S -g 10001 clinicflow \
    && adduser -S -D -H -u 10001 -G clinicflow clinicflow
WORKDIR /app
COPY --from=build /workspace/patient-service/target/patient-service-0.1.0-SNAPSHOT.jar app.jar
USER 10001:10001
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
