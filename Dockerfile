# Многоэтапная сборка для оптимизации размера образа
# Этап 1: Сборка приложения
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /build

# Копируем файлы Maven
COPY pom.xml .
COPY src ./src

# Устанавливаем Maven
RUN apk add --no-cache maven

# Собираем приложение (пропускаем тесты для ускорения сборки)
RUN mvn clean package -DskipTests

# Этап 2: Финальный образ
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Создаем непривилегированного пользователя для безопасности
RUN addgroup -S spring && adduser -S spring -G spring

# Создаем директорию для логов и даем права пользователю spring
RUN mkdir -p /app/logs && chown -R spring:spring /app/logs

USER spring:spring

# Копируем собранный jar из этапа сборки
COPY --from=builder /build/target/family-calendar-bot-*.jar app.jar

# Открываем порт приложения
EXPOSE 8080

# Настройки JVM для оптимальной работы в контейнере
ENTRYPOINT ["java", \
    "-XX:+UseContainerSupport", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", \
    "app.jar"]
