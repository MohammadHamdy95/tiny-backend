FROM eclipse-temurin:23-jdk AS build
COPY security-trust.sh /tmp/security-trust.sh
RUN sh /tmp/security-trust.sh && rm /tmp/security-trust.sh
WORKDIR /app
COPY gradlew settings.gradle build.gradle ./
COPY gradle ./gradle
RUN ./gradlew --no-daemon dependencies --quiet > /dev/null || true
COPY src ./src
RUN ./gradlew --no-daemon bootJar

FROM eclipse-temurin:23-jre
COPY security-trust.sh /tmp/security-trust.sh
RUN sh /tmp/security-trust.sh && rm /tmp/security-trust.sh
WORKDIR /app
COPY --from=build /app/build/libs/*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
