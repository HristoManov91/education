# Educational multi-stage build. Base tags are mutable: pin both images to verified digests
# in a real release pipeline and automate digest update PRs with regression tests.
FROM maven:3.9.11-eclipse-temurin-25 AS build
WORKDIR /src
COPY pom.xml .
RUN mvn --batch-mode --no-transfer-progress dependency:go-offline
COPY src ./src
RUN mvn --batch-mode --no-transfer-progress -DskipTests package

FROM eclipse-temurin:25-jre-noble
WORKDIR /app
COPY --from=build --chown=10001:10001     /src/target/kubernetes-java-lab-1.0-SNAPSHOT.jar /app/app.jar
USER 10001:10001
EXPOSE 8080
# exec form makes Java PID 1, so Kubernetes SIGTERM reaches the JVM.
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
