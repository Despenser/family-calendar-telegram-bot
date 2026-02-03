# Requirements Document

## Introduction

Данный документ описывает требования к рефакторингу структуры пакетов `service` и `handler` в проекте семейного календаря-бота на Telegram. Цель рефакторинга - улучшить организацию кода путем группировки классов по функциональным областям и переименования некоторых компонентов для более точного отражения их назначения.

## Glossary

- **System**: Система семейного календаря-бота на Telegram
- **Service_Package**: Пакет `ru.golubyatnikov.family.calendar.bot.service`
- **Handler_Package**: Пакет `ru.golubyatnikov.family.calendar.bot.handler`
- **Functional_Area**: Логически связанная группа классов, выполняющих схожие функции
- **Refactoring**: Изменение структуры кода без изменения его функциональности
- **Import_Statement**: Объявление импорта класса в Java файле
- **Test_Suite**: Набор автоматических тестов проекта

## Requirements

### Requirement 1: Реорганизация пакета service

**User Story:** Как разработчик, я хочу иметь логически организованную структуру пакета service, чтобы легко находить нужные классы и понимать архитектуру системы.

#### Acceptance Criteria

1. THE System SHALL создать подпакет `service.event` и переместить туда все сервисы работы с событиями (EventService, EventCommandService, EventQueryService, EventDeletionService, EventValidationService, EventHistoryService, EventNotificationService)
2. THE System SHALL создать подпакет `service.reminder` и переместить туда все сервисы напоминаний (ReminderService, ReminderConfigurationService, ReminderCreationService, ReminderNotificationService, ReminderSchedulingService)
3. THE System SHALL создать подпакет `service.attachment` и переместить туда сервисы вложений (AttachmentService, AttachmentMessageContext)
4. THE System SHALL создать подпакет `service.authorization` и переместить туда сервисы авторизации (AuthorizationService, AuthorizationMetricsService, WebhookSecurityService)
5. THE System SHALL создать подпакет `service.conversation` и переместить туда сервисы диалогов (ConversationService, ConversationStateService, DraftCleanupService)
6. THE System SHALL создать подпакет `service.telegram` и переместить туда сервисы работы с Telegram API (MessageSender, MessageRetryService, TelegramMessageService, UpdateProcessor, UnauthorizedMessageService)
7. THE System SHALL создать подпакет `service.dispatcher` и переместить туда диспетчеры (CommandDispatcher, CallbackQueryDispatcher, CallbackQueryService)
8. THE System SHALL создать подпакет `service.formatting` и переместить туда MessageFormatter
9. THE System SHALL создать подпакет `service.search` и переместить туда SearchService
10. THE System SHALL создать подпакет `service.statistics` и переместить туда StatisticsService
11. THE System SHALL создать подпакет `service.trash` и переместить туда TrashService
12. THE System SHALL создать подпакет `service.user` и переместить туда UserService
13. THE System SHALL создать подпакет `service.notification` и переместить туда NotificationService
14. THE System SHALL сохранить существующий подпакет `service.keyboard` без изменений
15. THE System SHALL сохранить существующий подпакет `service.message` без изменений

### Requirement 2: Переименование пакета myevents в planner

**User Story:** Как разработчик, я хочу использовать более точное название "planner" вместо "myevents", чтобы лучше отражать назначение компонента как планировщика событий.

#### Acceptance Criteria

1. THE System SHALL переименовать подпакет `service.myevents` в `service.planner`
2. THE System SHALL переименовать класс MyEventsFormattingService в PlannerFormattingService
3. THE System SHALL переименовать класс MyEventsNavigationService в PlannerNavigationService
4. THE System SHALL переименовать класс MyEventsQueryService в PlannerQueryService
5. THE System SHALL переименовать класс MyEventsCommandHandler в PlannerCommandHandler

### Requirement 3: Реорганизация пакета handler

**User Story:** Как разработчик, я хочу иметь четкое разделение command и callback handlers, чтобы быстро находить нужные обработчики.

#### Acceptance Criteria

1. THE System SHALL создать подпакет `handler.command` и переместить туда все command handlers (CommandHandler интерфейс, AddEventCommandHandler, FilterCommandHandler, HelpCommandHandler, PlannerCommandHandler, SearchCommandHandler, StartCommandHandler, StatsCommandHandler, TodayCommandHandler, TrashCommandHandler, UpcomingEventsCommandHandler, WeekCommandHandler)
2. THE System SHALL переместить ReminderCallbackHandler из корня `handler` в существующий подпакет `handler.callback`
3. THE System SHALL сохранить существующий подпакет `handler.callback` со всеми его классами

### Requirement 4: Обновление импортов

**User Story:** Как разработчик, я хочу чтобы все импорты в проекте были автоматически обновлены после рефакторинга, чтобы код компилировался без ошибок.

#### Acceptance Criteria

1. WHEN класс перемещается в новый пакет, THEN THE System SHALL обновить все import statements этого класса во всех файлах проекта
2. WHEN класс переименовывается, THEN THE System SHALL обновить все ссылки на этот класс во всех файлах проекта
3. WHEN рефакторинг завершен, THEN THE System SHALL обеспечить отсутствие ошибок компиляции связанных с импортами

### Requirement 5: Обновление тестов

**User Story:** Как разработчик, я хочу чтобы все тесты продолжали работать после рефакторинга, чтобы убедиться в сохранении функциональности.

#### Acceptance Criteria

1. WHEN класс перемещается или переименовывается, THEN THE System SHALL обновить все тестовые классы, использующие этот класс
2. WHEN рефакторинг завершен, THEN THE System SHALL обеспечить успешное прохождение всех тестов
3. THE System SHALL сохранить все существующие тесты без изменения их логики

### Requirement 6: Сохранение функциональности

**User Story:** Как разработчик, я хочу чтобы рефакторинг не изменял поведение системы, чтобы избежать регрессии функциональности.

#### Acceptance Criteria

1. THE System SHALL выполнять только операции перемещения и переименования классов
2. THE System SHALL сохранить всю бизнес-логику классов без изменений
3. THE System SHALL сохранить все аннотации Spring (Component, Service, Repository и т.д.)
4. THE System SHALL сохранить все зависимости между классами
5. WHEN рефакторинг завершен, THEN THE System SHALL обеспечить идентичное поведение приложения

### Requirement 7: Использование безопасного рефакторинга IDE

**User Story:** Как разработчик, я хочу использовать встроенные возможности IDE для рефакторинга, чтобы минимизировать риск ошибок.

#### Acceptance Criteria

1. THE System SHALL использовать операцию "Refactor → Move" для перемещения классов
2. THE System SHALL использовать операцию "Refactor → Rename" для переименования классов
3. THE System SHALL позволить IDE автоматически обновлять все ссылки на перемещенные и переименованные классы
