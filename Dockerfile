FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /workspace
COPY pom.xml mvnw ./
COPY .mvn .mvn
RUN chmod +x mvnw && ./mvnw -B -q dependency:go-offline
COPY src src
RUN ./mvnw -B -q package -DskipTests

FROM build AS test
RUN ./mvnw -B test

FROM eclipse-temurin:17-jre-jammy AS runtime
WORKDIR /app
RUN groupadd --system commercelink && useradd --system --gid commercelink --home-dir /app commercelink
COPY --from=build /workspace/target/commercelink-1.0.0.jar /app/commercelink.jar
USER commercelink
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "/app/commercelink.jar"]
CMD ["--transport", "http", "--port", "8082"]
