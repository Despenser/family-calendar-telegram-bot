# Руководство по настройке логирования

## Обзор

Приложение использует многоуровневую систему логирования с различными настройками для разных окружений.

## Профили логирования

### 1. Development (dev)
**Использование:** Локальная разработка  
**Уровень логирования:** DEBUG  
**Особенности:**
- Подробное логирование всех компонентов
- SQL запросы с параметрами
- Цветной вывод в консоль
- Логирование в файл `logs/family-calendar-bot.log`

**Активация:**
```bash
# Локально
export SPRING_PROFILES_ACTIVE=dev
mvn spring-boot:run

# Docker
SPRING_PROFILES_ACTIVE=dev docker-compose up
```

### 2. Production (prod)
**Использование:** Production окружение  
**Уровень логирования:** WARN/INFO  
**Особенности:**
- Минимальное логирование (только важные события)
- DEBUG логи полностью отключены
- Логирование в `/var/log/family-calendar-bot/application.log`
- Ротация логов (50MB файлы, 90 дней хранения)
- Оптимизировано для производительности

**Активация:**
```bash
# Docker (рекомендуется)
SPRING_PROFILES_ACTIVE=prod docker-compose up

# Локально
export SPRING_PROFILES_ACTIVE=prod
java -jar target/family-calendar-bot-*.jar
```

### 3. Test (test)
**Использование:** Автоматические тесты  
**Уровень логирования:** WARN  
**Особенности:**
- Минимальное логирование для быстрого выполнения тестов
- Только консольный вывод
- Автоматически активируется при запуске тестов

## Уровни логирования по профилям

| Компонент | dev | prod | test |
|-----------|-----|------|------|
| Приложение (ru.golubyatnikov.*) | DEBUG | INFO | INFO |
| Spring Framework | DEBUG | WARN | WARN |
| Hibernate SQL | DEBUG | WARN | WARN |
| Flyway | DEBUG | INFO | WARN |
| Telegram API | DEBUG | INFO | WARN |
| Root | INFO | WARN | WARN |

## Конфигурационные файлы

### logback-spring.xml
Основной файл конфигурации логирования. Определяет:
- Форматы вывода (консоль и файл)
- Ротацию файлов
- Асинхронное логирование
- Профиль-специфичные настройки

### application.yml
Базовые настройки логирования (переопределяются в logback-spring.xml)

### application-dev.yml
Настройки для разработки:
- DEBUG уровень для приложения
- Подробное логирование SQL
- Цветной вывод

### application-prod.yml
Настройки для production:
- WARN/INFO уровень
- Минимальное логирование
- Оптимизация производительности

### application-test.yml
Настройки для тестов:
- WARN уровень для быстрого выполнения
- Только консольный вывод

## Проблемы и решения

### Проблема: Слишком много DEBUG логов в production

**Причина:** Используется профиль `dev` вместо `prod`

**Решение:**
```bash
# Проверьте текущий профиль
docker-compose exec app env | grep SPRING_PROFILES

# Измените в .env файле
SPRING_PROFILES_ACTIVE=prod

# Пересоздайте контейнер
docker-compose down
docker-compose up -d
```

### Проблема: Логи не пишутся в файл

**Причина:** Недостаточно прав или неправильный путь

**Решение для Docker:**
```yaml
# В docker-compose.yml уже настроен volume
volumes:
  - app_logs:/app/logs

# Проверить логи
docker-compose exec app ls -la /app/logs
```

### Проблема: Файлы логов слишком большие

**Решение:** Настроена автоматическая ротация:
- dev: 10MB файлы, 30 дней хранения, макс 1GB
- prod: 50MB файлы, 90 дней хранения, макс 5GB

## Просмотр логов

### Docker
```bash
# Последние 100 строк
docker-compose logs --tail=100 app

# Следить за логами в реальном времени
docker-compose logs -f app

# Логи с временными метками
docker-compose logs -t app

# Логи конкретного сервиса
docker-compose logs postgres
```

### Локально
```bash
# Просмотр файла логов
tail -f logs/family-calendar-bot.log

# Последние 100 строк
tail -n 100 logs/family-calendar-bot.log

# Поиск ошибок
grep ERROR logs/family-calendar-bot.log
```

## Рекомендации

### Для разработки
1. Используйте профиль `dev`
2. Следите за логами в консоли
3. Проверяйте файл логов при необходимости

### Для production
1. **ОБЯЗАТЕЛЬНО** используйте профиль `prod`
2. Настройте мониторинг логов (ELK, Splunk, CloudWatch)
3. Регулярно проверяйте размер логов
4. Настройте алерты на ERROR логи

### Для тестирования
1. Профиль `test` активируется автоматически
2. Минимальное логирование для скорости
3. При отладке тестов можно временно повысить уровень

## Мониторинг

### Spring Boot Actuator
Приложение включает Actuator endpoints:

```bash
# Health check
curl http://localhost:8080/actuator/health

# Metrics
curl http://localhost:8080/actuator/metrics

# Loggers (изменение уровня логирования на лету)
curl http://localhost:8080/actuator/loggers/ru.golubyatnikov.family.calendar.bot
```

### Изменение уровня логирования без перезапуска
```bash
# Установить DEBUG для конкретного пакета
curl -X POST http://localhost:8080/actuator/loggers/ru.golubyatnikov.family.calendar.bot \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel": "DEBUG"}'

# Вернуть обратно
curl -X POST http://localhost:8080/actuator/loggers/ru.golubyatnikov.family.calendar.bot \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel": "INFO"}'
```

## Структура логов

### Формат консоли (dev)
```
2025-12-30 14:00:00.123 [http-nio-8080-exec-1] INFO  r.g.f.c.b.service.EventService - Создано событие: День рождения
```

### Формат файла
```
2025-12-30 14:00:00.123 [http-nio-8080-exec-1] INFO r.g.f.c.b.service.EventService - Создано событие: День рождения
```

### Компоненты лог-записи
- **Timestamp:** Дата и время с миллисекундами
- **Thread:** Имя потока выполнения
- **Level:** Уровень логирования (TRACE, DEBUG, INFO, WARN, ERROR)
- **Logger:** Имя класса (сокращенное)
- **Message:** Сообщение лога
- **Exception:** Stack trace (если есть)

## Безопасность

### Что НЕ логировать
- Пароли и токены
- Персональные данные пользователей
- Секретные ключи
- Полные номера карт

### Что логировать
- Бизнес-события (создание/изменение/удаление)
- Ошибки и исключения
- Важные системные события
- Метрики производительности

## Производительность

### Асинхронное логирование
Включено для файлового логирования:
- Размер очереди: 512 (dev), 1024 (prod)
- Не блокирует основной поток
- Минимальное влияние на производительность

### Рекомендации
1. Используйте правильный уровень логирования
2. Избегайте логирования в циклах
3. Используйте параметризованные сообщения: `log.info("User {}", userId)`
4. Не вычисляйте значения для DEBUG логов в production

## Troubleshooting

### Логи не появляются
1. Проверьте профиль: `echo $SPRING_PROFILES_ACTIVE`
2. Проверьте уровень логирования в конфигурации
3. Проверьте права на запись в директорию логов

### Слишком много логов
1. Переключитесь на профиль `prod`
2. Повысьте уровень логирования до WARN
3. Проверьте ротацию логов

### Нужны DEBUG логи в production
```bash
# Временно включить через Actuator (без перезапуска)
curl -X POST http://localhost:8080/actuator/loggers/ru.golubyatnikov.family.calendar.bot \
  -H "Content-Type: application/json" \
  -d '{"configuredLevel": "DEBUG"}'

# Не забудьте вернуть обратно!
```
