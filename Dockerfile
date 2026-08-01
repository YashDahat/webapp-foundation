FROM node:20-alpine AS frontend-build
WORKDIR /app/frontend
COPY frontend/package*.json ./
# npm ci is faster + reproducible from package-lock.json; --prefer-offline uses cached layers
RUN npm ci --prefer-offline || npm install
COPY frontend/ .
RUN npm run build

FROM maven:3.9-eclipse-temurin-17 AS backend-build
WORKDIR /app
COPY backend/ .
COPY --from=frontend-build /app/frontend/dist ./src/main/resources/static
RUN mvn package -q -DskipTests \
        -Dmaven.wagon.http.retryHandler.count=3 \
        --no-transfer-progress

FROM eclipse-temurin:17-jre-jammy
RUN groupadd -r app && useradd -r -g app app
WORKDIR /app
COPY --from=backend-build /app/target/*.jar app.jar
USER app
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080' || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
