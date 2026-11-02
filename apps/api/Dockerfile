# syntax=docker/dockerfile:1

FROM maven:3.10.0-eclipse-temurin-17 AS deps
WORKDIR /build
COPY pom.xml pom.xml
RUN --mount=type=cache,id=api-maven-repository,target=/root/.m2 \
    mvn -B -ntp dependency:go-offline -DskipTests

FROM deps AS package
COPY src/ src/
RUN --mount=type=cache,id=api-maven-repository,target=/root/.m2 \
    mvn -B -ntp package -DskipTests && \
    mv target/*.jar target/app.jar

FROM package AS extract
RUN java -Djarmode=tools -jar target/app.jar extract --layers --launcher --destination target/extracted

FROM eclipse-temurin:17-jre-jammy AS final
WORKDIR /application

ARG UID=10001
RUN adduser \
    --disabled-password \
    --gecos "" \
    --home "/nonexistent" \
    --shell "/sbin/nologin" \
    --no-create-home \
    --uid "${UID}" \
    appuser
USER appuser

COPY --from=extract /build/target/extracted/dependencies/ ./
COPY --from=extract /build/target/extracted/spring-boot-loader/ ./
COPY --from=extract /build/target/extracted/snapshot-dependencies/ ./
COPY --from=extract /build/target/extracted/application/ ./

EXPOSE 8080
ENTRYPOINT ["java", "org.springframework.boot.loader.launch.JarLauncher"]
