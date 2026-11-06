FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -q -B dependency:go-offline
COPY src ./src
RUN mvn -q -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /build/target/peluqueria-backend.jar app.jar
EXPOSE 8080
# TieredStopAtLevel=1: usa solo el compilador JIT rapido (C1), sin el C2 (mas lento
# de compilar pero mas optimizado); en un contenedor con poca CPU el tiempo que
# ahorra en arranque compensa el rendimiento en caliente que se pierde.
ENTRYPOINT ["java", "-XX:TieredStopAtLevel=1", "-jar", "app.jar"]
