# Учебен GOOD baseline. Това не е универсален production hardening template,
# а минимална стъпка: приложението не се нуждае от root privileges.
FROM eclipse-temurin:25-jre

WORKDIR /app

COPY target/testcontainers-learning-1.0-SNAPSHOT.jar app.jar

USER 10001:10001

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
