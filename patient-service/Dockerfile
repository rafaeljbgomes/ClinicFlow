FROM maven:3.9.16-eclipse-temurin-21-alpine@sha256:d6390509b774df0a07d009a194a020aeff9c715afe1e3d2cd5ecb3bf6344700f AS build
WORKDIR /workspace
COPY . .
RUN mvn -q -pl patient-service -am package -DskipTests

FROM eclipse-temurin:21.0.11_10-jre-alpine@sha256:3f08b13888f595cc49edabea7250ba69499ba25602b267da591720769400e08c
WORKDIR /app
COPY --from=build /workspace/patient-service/target/patient-service-0.1.0-SNAPSHOT.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
