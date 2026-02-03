# Design Document: Package Structure Refactoring

## Overview

Данный документ описывает дизайн рефакторинга структуры пакетов `service` и `handler` в проекте семейного календаря-бота на Telegram. Рефакторинг направлен на улучшение организации кода путем группировки классов по функциональным областям без изменения их функциональности.

Ключевые принципы рефакторинга:
- Группировка классов по функциональным областям (domain-driven organization)
- Сохранение всей существующей функциональности
- Автоматическое обновление всех импортов и ссылок
- Использование безопасных операций рефакторинга IDE

## Architecture

### Текущая структура

```
service/
├── [множество классов без подпакетов]
├── keyboard/
│   └── [классы клавиатур]
├── message/
│   └── [классы обработки сообщений]
└── myevents/
    └── [классы планировщика]

handler/
├── [command handlers в корне]
├── ReminderCallbackHandler
└── callback/
    └── [другие callback handlers]
```

### Целевая структура

```
service/
├── event/
│   ├── EventService
│   ├── EventCommandService
│   ├── EventQueryService
│   ├── EventDeletionService
│   ├── EventValidationService
│   ├── EventHistoryService
│   └── EventNotificationService
├── reminder/
│   ├── ReminderService
│   ├── ReminderConfigurationService
│   ├── ReminderCreationService
│   ├── ReminderNotificationService
│   └── ReminderSchedulingService
├── attachment/
│   ├── AttachmentService
│   └── AttachmentMessageContext
├── authorization/
│   ├── AuthorizationService
│   ├── AuthorizationMetricsService
│   └── WebhookSecurityService
├── conversation/
│   ├── ConversationService
│   ├── ConversationStateService
│   └── DraftCleanupService
├── telegram/
│   ├── MessageSender
│   ├── MessageRetryService
│   ├── TelegramMessageService
│   ├── UpdateProcessor
│   └── UnauthorizedMessageService
├── dispatcher/
│   ├── CommandDispatcher
│   ├── CallbackQueryDispatcher
│   └── CallbackQueryService
├── formatting/
│   └── MessageFormatter
├── search/
│   └── SearchService
├── statistics/
│   └── StatisticsService
├── trash/
│   └── TrashService
├── user/
│   └── UserService
├── notification/
│   └── NotificationService
├── keyboard/
│   └── [без изменений]
├── message/
│   └── [без изменений]
└── planner/
    ├── PlannerFormattingService
    ├── PlannerNavigationService
    └── PlannerQueryService

handler/
├── command/
│   ├── CommandHandler (interface)
│   ├── AddEventCommandHandler
│   ├── FilterCommandHandler
│   ├── HelpCommandHandler
│   ├── PlannerCommandHandler
│   ├── SearchCommandHandler
│   ├── StartCommandHandler
│   ├── StatsCommandHandler
│   ├── TodayCommandHandler
│   ├── TrashCommandHandler
│   ├── UpcomingEventsCommandHandler
│   └── WeekCommandHandler
└── callback/
    ├── ReminderCallbackHandler
    └── [остальные callback handlers]
```

### Принципы организации

1. **Функциональная группировка**: Классы группируются по функциональным областям (events, reminders, authorization и т.д.)
2. **Разделение ответственности**: Command и callback handlers разделены в разные подпакеты
3. **Согласованность именования**: Переименование myevents → planner для более точного отражения назначения
4. **Минимальная инвазивность**: Существующие подпакеты (keyboard, message, callback) остаются без изменений

## Components and Interfaces

### Группы компонентов для рефакторинга

#### 1. Event Management (service.event)
- **EventService**: Основной сервис управления событиями
- **EventCommandService**: Обработка команд создания/изменения событий
- **EventQueryService**: Запросы и поиск событий
- **EventDeletionService**: Удаление событий
- **EventValidationService**: Валидация данных событий
- **EventHistoryService**: История изменений событий
- **EventNotificationService**: Уведомления о событиях

#### 2. Reminder Management (service.reminder)
- **ReminderService**: Основной сервис напоминаний
- **ReminderConfigurationService**: Конфигурация напоминаний
- **ReminderCreationService**: Создание напоминаний
- **ReminderNotificationService**: Отправка напоминаний
- **ReminderSchedulingService**: Планирование напоминаний

#### 3. Attachment Handling (service.attachment)
- **AttachmentService**: Работа с вложениями
- **AttachmentMessageContext**: Контекст сообщений с вложениями

#### 4. Authorization & Security (service.authorization)
- **AuthorizationService**: Авторизация пользователей
- **AuthorizationMetricsService**: Метрики авторизации
- **WebhookSecurityService**: Безопасность webhook

#### 5. Conversation Management (service.conversation)
- **ConversationService**: Управление диалогами
- **ConversationStateService**: Состояние диалогов
- **DraftCleanupService**: Очистка черновиков

#### 6. Telegram Integration (service.telegram)
- **MessageSender**: Отправка сообщений
- **MessageRetryService**: Повторная отправка при ошибках
- **TelegramMessageService**: Работа с Telegram API
- **UpdateProcessor**: Обработка обновлений от Telegram
- **UnauthorizedMessageService**: Обработка неавторизованных сообщений

#### 7. Dispatching (service.dispatcher)
- **CommandDispatcher**: Диспетчер команд
- **CallbackQueryDispatcher**: Диспетчер callback запросов
- **CallbackQueryService**: Сервис обработки callback

#### 8. Other Services
- **service.formatting**: MessageFormatter
- **service.search**: SearchService
- **service.statistics**: StatisticsService
- **service.trash**: TrashService
- **service.user**: UserService
- **service.notification**: NotificationService

#### 9. Planner (service.planner) - переименование из myevents
- **PlannerFormattingService**: Форматирование планировщика
- **PlannerNavigationService**: Навигация в планировщике
- **PlannerQueryService**: Запросы планировщика

#### 10. Command Handlers (handler.command)
- **CommandHandler**: Интерфейс обработчика команд
- **AddEventCommandHandler**: Добавление события
- **FilterCommandHandler**: Фильтрация
- **HelpCommandHandler**: Помощь
- **PlannerCommandHandler**: Планировщик (было MyEventsCommandHandler)
- **SearchCommandHandler**: Поиск
- **StartCommandHandler**: Старт
- **StatsCommandHandler**: Статистика
- **TodayCommandHandler**: События сегодня
- **TrashCommandHandler**: Корзина
- **UpcomingEventsCommandHandler**: Предстоящие события
- **WeekCommandHandler**: События недели

#### 11. Callback Handlers (handler.callback)
- **ReminderCallbackHandler**: Перемещается из корня handler

## Data Models

Рефакторинг не затрагивает модели данных. Все entity, DTO и другие модели остаются без изменений.

## Correctness Properties

*Свойство корректности - это характеристика или поведение, которое должно выполняться во всех валидных выполнениях системы. Свойства служат мостом между человеко-читаемыми спецификациями и машинно-проверяемыми гарантиями корректности.*


### Prework Analysis Summary

После анализа acceptance criteria выявлены следующие testable properties:
- Обновление импортов и ссылок при перемещении/переименовании классов (общее свойство)
- Обновление тестовых классов при изменениях (общее свойство)
- Сохранение Spring аннотаций после рефакторинга (общее свойство)

Большинство критериев являются примерами проверки конкретной структуры пакетов, которые будут проверены вручную после рефакторинга.

### Property 1: Обновление импортов и ссылок

*For any* класс, который перемещается в новый пакет или переименовывается, все import statements и ссылки на этот класс во всех файлах проекта (включая Java файлы, конфигурационные файлы, тесты) должны быть автоматически обновлены, и проект должен компилироваться без ошибок.

**Validates: Requirements 4.1, 4.2**

### Property 2: Обновление тестовых классов

*For any* класс, который перемещается или переименовывается, все тестовые классы, которые используют этот класс (через импорты, аннотации, или прямые ссылки), должны быть обновлены с новыми путями и именами, и все тесты должны успешно компилироваться.

**Validates: Requirements 5.1**

### Property 3: Сохранение Spring аннотаций

*For any* класс с Spring аннотациями (@Service, @Component, @Repository, @Controller, @RestController и т.д.), после перемещения или переименования все эти аннотации должны сохраниться без изменений, и Spring контекст должен успешно загружаться.

**Validates: Requirements 6.3**

## Error Handling

Рефакторинг - это операция изменения структуры кода, которая должна выполняться с использованием безопасных инструментов IDE. Основные сценарии ошибок:

### 1. Ошибки компиляции после рефакторинга

**Причина**: Не все импорты были обновлены автоматически

**Обработка**:
- Использовать функцию IDE "Optimize Imports" для всего проекта
- Вручную проверить и исправить оставшиеся ошибки импортов
- Использовать поиск по проекту для нахождения старых ссылок

### 2. Падение тестов после рефакторинга

**Причина**: Тесты используют reflection или строковые ссылки на классы

**Обработка**:
- Найти все использования Class.forName() и подобных конструкций
- Обновить строковые имена классов вручную
- Проверить конфигурационные файлы (application.yml, XML конфиги)

### 3. Проблемы с Spring контекстом

**Причина**: Spring не может найти компоненты после перемещения

**Обработка**:
- Проверить @ComponentScan аннотации
- Убедиться что базовые пакеты для сканирования включают новые подпакеты
- Проверить явные bean definitions в конфигурации

### 4. Конфликты имен

**Причина**: В новом пакете уже существует класс с таким именем

**Обработка**:
- Проверить целевой пакет перед перемещением
- При необходимости переименовать класс перед перемещением
- Использовать fully qualified names при конфликтах

## Testing Strategy

### Подход к тестированию рефакторинга

Рефакторинг - это особый тип изменений, где мы не добавляем новую функциональность, а только реорганизуем существующий код. Поэтому стратегия тестирования отличается от обычной разработки.

### Unit Tests

**Не требуется писать новые unit tests**, так как:
- Функциональность классов не меняется
- Все существующие unit tests должны продолжать работать
- Новые тесты не добавят дополнительной проверки корректности

**Что нужно сделать с существующими тестами**:
- Обновить импорты в тестовых классах
- Убедиться что все тесты компилируются
- Запустить весь test suite для проверки сохранения функциональности

### Property-Based Tests

**Не требуется писать property-based tests**, так как:
- Свойства корректности (Properties 1-3) проверяются через компиляцию и запуск существующих тестов
- Property 1 (обновление импортов) проверяется успешной компиляцией проекта
- Property 2 (обновление тестов) проверяется успешной компиляцией тестов
- Property 3 (сохранение аннотаций) проверяется успешным запуском Spring контекста

### Integration Tests

**Критически важно**: Запустить все существующие integration tests после рефакторинга

**Что проверяют integration tests**:
- Spring контекст загружается корректно
- Все beans создаются и внедряются правильно
- Взаимодействие между компонентами работает
- Telegram bot функционирует как ожидается

### Manual Testing

**Обязательные ручные проверки**:

1. **Проверка структуры пакетов**:
   - Визуально проверить что все классы находятся в правильных пакетах
   - Убедиться что старые пакеты пусты (кроме keyboard, message, callback)
   - Проверить что myevents переименован в planner

2. **Проверка компиляции**:
   - Выполнить `mvn clean compile` для проверки компиляции основного кода
   - Выполнить `mvn clean test-compile` для проверки компиляции тестов
   - Убедиться в отсутствии warnings об устаревших импортах

3. **Проверка тестов**:
   - Запустить полный test suite: `mvn clean test`
   - Убедиться что все тесты проходят (100% success rate)
   - Проверить что количество тестов не изменилось

4. **Проверка Spring контекста**:
   - Запустить приложение локально
   - Убедиться что Spring контекст загружается без ошибок
   - Проверить логи на отсутствие warnings о missing beans

5. **Функциональное тестирование**:
   - Протестировать основные команды бота (/start, /help, /today)
   - Проверить создание события
   - Проверить создание напоминания
   - Проверить работу планировщика (/planner вместо /myevents)

### Критерии успешности рефакторинга

Рефакторинг считается успешным если:
- ✅ Проект компилируется без ошибок
- ✅ Все unit tests проходят (100%)
- ✅ Все integration tests проходят (100%)
- ✅ Spring контекст загружается без ошибок
- ✅ Приложение запускается и работает корректно
- ✅ Все классы находятся в правильных пакетах
- ✅ Переименование myevents → planner выполнено полностью

### Инструменты для тестирования

- **Maven**: `mvn clean test` для запуска всех тестов
- **IDE**: Встроенный test runner для быстрой проверки отдельных тестов
- **Spring Boot Test**: Для проверки загрузки контекста
- **Code Coverage**: Убедиться что coverage не уменьшился после рефакторинга
