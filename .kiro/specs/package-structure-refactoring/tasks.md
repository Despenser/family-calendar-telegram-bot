# Implementation Plan: Package Structure Refactoring

## Overview

Данный план описывает пошаговый рефакторинг структуры пакетов `service` и `handler` в проекте семейного календаря-бота. Рефакторинг будет выполняться с использованием безопасных операций IDE (Refactor → Move, Refactor → Rename) для автоматического обновления всех импортов и ссылок.

Ключевые принципы:
- Использовать встроенные возможности IDE для безопасного рефакторинга
- Выполнять изменения инкрементально с проверками на каждом этапе
- Не изменять функциональность классов
- Проверять компиляцию после каждой группы изменений

## Tasks

- [ ] 1. Подготовка к рефакторингу
  - Убедиться что все изменения закоммичены (clean working directory)
  - Создать новую ветку для рефакторинга
  - Запустить все тесты для установления baseline
  - _Requirements: 5.2, 6.5_

- [x] 2. Реорганизация пакета service - Event Management
  - [x] 2.1 Создать подпакет service.event и переместить сервисы событий
    - Создать пакет `ru.golubyatnikov.family.calendar.bot.service.event`
    - Переместить EventService, EventCommandService, EventQueryService
    - Переместить EventDeletionService, EventValidationService
    - Переместить EventHistoryService, EventNotificationService
    - Использовать Refactor → Move для автоматического обновления импортов
    - _Requirements: 1.1, 4.1_

- [x] 3. Реорганизация пакета service - Reminder Management
  - [x] 3.1 Создать подпакет service.reminder и переместить сервисы напоминаний
    - Создать пакет `ru.golubyatnikov.family.calendar.bot.service.reminder`
    - Переместить ReminderService, ReminderConfigurationService
    - Переместить ReminderCreationService, ReminderNotificationService, ReminderSchedulingService
    - Использовать Refactor → Move для автоматического обновления импортов
    - _Requirements: 1.2, 4.1_

- [x] 4. Реорганизация пакета service - Supporting Services (Attachment, Authorization, Conversation)
  - [x] 4.1 Создать подпакет service.attachment и переместить сервисы вложений
    - Создать пакет `ru.golubyatnikov.family.calendar.bot.service.attachment`
    - Переместить AttachmentService, AttachmentMessageContext
    - _Requirements: 1.3, 4.1_
  
  - [x] 4.2 Создать подпакет service.authorization и переместить сервисы авторизации
    - Создать пакет `ru.golubyatnikov.family.calendar.bot.service.authorization`
    - Переместить AuthorizationService, AuthorizationMetricsService, WebhookSecurityService
    - _Requirements: 1.4, 4.1_
  
  - [x] 4.3 Создать подпакет service.conversation и переместить сервисы диалогов
    - Создать пакет `ru.golubyatnikov.family.calendar.bot.service.conversation`
    - Переместить ConversationService, ConversationStateService, DraftCleanupService
    - _Requirements: 1.5, 4.1_

- [x] 5. Реорганизация пакета service - Telegram Integration
  - [x] 5.1 Создать подпакет service.telegram и переместить сервисы Telegram API
    - Создать пакет `ru.golubyatnikov.family.calendar.bot.service.telegram`
    - Переместить MessageSender, MessageRetryService, TelegramMessageService
    - Переместить UpdateProcessor, UnauthorizedMessageService
    - _Requirements: 1.6, 4.1_

- [x] 6. Реорганизация пакета service - Dispatchers and Other Services
  - [x] 6.1 Создать подпакет service.dispatcher и переместить диспетчеры
    - Создать пакет `ru.golubyatnikov.family.calendar.bot.service.dispatcher`
    - Переместить CommandDispatcher, CallbackQueryDispatcher, CallbackQueryService
    - _Requirements: 1.7, 4.1_
  
  - [x] 6.2 Создать подпакеты для остальных сервисов
    - Создать пакет `ru.golubyatnikov.family.calendar.bot.service.formatting` и переместить MessageFormatter
    - Создать пакет `ru.golubyatnikov.family.calendar.bot.service.search` и переместить SearchService
    - Создать пакет `ru.golubyatnikov.family.calendar.bot.service.statistics` и переместить StatisticsService
    - Создать пакет `ru.golubyatnikov.family.calendar.bot.service.trash` и переместить TrashService
    - Создать пакет `ru.golubyatnikov.family.calendar.bot.service.user` и переместить UserService
    - Создать пакет `ru.golubyatnikov.family.calendar.bot.service.notification` и переместить NotificationService
    - _Requirements: 1.8, 1.9, 1.10, 1.11, 1.12, 1.13, 4.1_

- [ ] 7. Checkpoint - Проверка компиляции после реорганизации service
  - Выполнить `mvn clean compile` для проверки компиляции
  - Убедиться в отсутствии ошибок импортов
  - Проверить что Spring аннотации сохранились на всех классах
  - Если есть ошибки - исправить их перед продолжением
  - _Requirements: 4.3, 6.3_

- [x] 8. Переименование myevents в planner
  - [x] 8.1 Переименовать пакет service.myevents в service.planner
    - Использовать Refactor → Rename для переименования пакета
    - Убедиться что все импорты обновились автоматически
    - _Requirements: 2.1, 4.2_
  
  - [x] 8.2 Переименовать классы в пакете planner
    - Переименовать MyEventsFormattingService → PlannerFormattingService
    - Переименовать MyEventsNavigationService → PlannerNavigationService
    - Переименовать MyEventsQueryService → PlannerQueryService
    - Использовать Refactor → Rename для каждого класса
    - _Requirements: 2.2, 2.3, 2.4, 4.2_

- [x] 9. Реорганизация пакета handler - Command Handlers
  - [x] 9.1 Создать подпакет handler.command и переместить command handlers
    - Создать пакет `ru.golubyatnikov.family.calendar.bot.handler.command`
    - Переместить интерфейс CommandHandler
    - Переместить AddEventCommandHandler, FilterCommandHandler, HelpCommandHandler
    - Переместить SearchCommandHandler, StartCommandHandler, StatsCommandHandler
    - Переместить TodayCommandHandler, TrashCommandHandler, UpcomingEventsCommandHandler, WeekCommandHandler
    - Использовать Refactor → Move для автоматического обновления импортов
    - _Requirements: 3.1, 4.1_

- [x] 10. Переименование MyEventsCommandHandler в PlannerCommandHandler
  - [x] 10.1 Переименовать MyEventsCommandHandler в PlannerCommandHandler
    - Использовать Refactor → Rename для переименования класса
    - Переместить PlannerCommandHandler в пакет handler.command
    - Убедиться что все ссылки обновились
    - _Requirements: 2.5, 3.1, 4.2_

- [x] 11. Реорганизация пакета handler - Callback Handlers
  - [x] 11.1 Переместить ReminderCallbackHandler в handler.callback
    - Переместить ReminderCallbackHandler из корня handler в подпакет handler.callback
    - Использовать Refactor → Move
    - _Requirements: 3.2, 4.1_

- [x] 12. Реорганизация пакета handler.callback - Создание подпакетов
  - [x] 12.1 Создать подпакет handler.callback.datetime и переместить handler'ы даты/времени
    - Создать пакет `ru.golubyatnikov.family.calendar.bot.handler.callback.datetime`
    - Переместить DateTimeCallbackHandler
    - Переместить NavigationCallbackHandler (работа с календарем)
    - Использовать Refactor → Move для автоматического обновления импортов
    - _Requirements: 3.2, 4.1_
  
  - [x] 12.2 Создать подпакет handler.callback.reminder и переместить handler'ы напоминаний
    - Создать пакет `ru.golubyatnikov.family.calendar.bot.handler.callback.reminder`
    - Переместить ReminderCallbackHandler
    - Переместить ReminderCallbackHandlerImpl
    - Использовать Refactor → Move для автоматического обновления импортов
    - _Requirements: 3.2, 4.1_
  
  - [x] 12.3 Создать подпакет handler.callback.filter и переместить handler фильтрации
    - Создать пакет `ru.golubyatnikov.family.calendar.bot.handler.callback.filter`
    - Переместить FilterCallbackHandler
    - Использовать Refactor → Move для автоматического обновления импортов
    - _Requirements: 3.2, 4.1_
  
  - [x] 12.4 Создать подпакет handler.callback.textevent и переместить handler создания из текста
    - Создать пакет `ru.golubyatnikov.family.calendar.bot.handler.callback.textevent`
    - Переместить TextEventCallbackHandler
    - Использовать Refactor → Move для автоматического обновления импортов
    - _Requirements: 3.2, 4.1_
  
  - [x] 12.5 Создать подпакет handler.callback.eventtype и переместить handler типа события
    - Создать пакет `ru.golubyatnikov.family.calendar.bot.handler.callback.eventtype`
    - Переместить EventTypeCallbackHandler
    - Использовать Refactor → Move для автоматического обновления импортов
    - _Requirements: 3.2, 4.1_
  
  - [x] 12.6 Создать подпакет handler.callback.recurrence и переместить handler повторений
    - Создать пакет `ru.golubyatnikov.family.calendar.bot.handler.callback.recurrence`
    - Переместить RecurrenceCallbackHandler
    - Использовать Refactor → Move для автоматического обновления импортов
    - _Requirements: 3.2, 4.1_
  
  - [x] 12.7 Создать подпакет handler.callback.checklist и переместить handler чек-листов
    - Создать пакет `ru.golubyatnikov.family.calendar.bot.handler.callback.checklist`
    - Переместить ChecklistCallbackHandler
    - Использовать Refactor → Move для автоматического обновления импортов
    - _Requirements: 3.2, 4.1_
  
  - [x] 12.8 Создать подпакет handler.callback.trash и переместить handler корзины
    - Создать пакет `ru.golubyatnikov.family.calendar.bot.handler.callback.trash`
    - Переместить TrashCallbackHandler
    - Использовать Refactor → Move для автоматического обновления импортов
    - _Requirements: 3.2, 4.1_

- [ ] 13. Checkpoint - Проверка компиляции после реорганизации handler
  - Выполнить `mvn clean compile` для проверки компиляции
  - Убедиться в отсутствии ошибок импортов
  - Проверить что все handler классы находятся в правильных пакетах
  - _Requirements: 4.3_

- [ ] 14. Обновление тестов
  - [ ] 14.1 Проверить и обновить импорты в тестовых классах
    - Выполнить `mvn clean test-compile` для компиляции тестов
    - Если есть ошибки импортов - использовать "Optimize Imports" в IDE
    - Проверить что все тестовые классы компилируются
    - _Requirements: 5.1, 4.1, 4.2_

- [ ] 15. Checkpoint - Запуск всех тестов
  - Выполнить `mvn clean test` для запуска всего test suite
  - Убедиться что все тесты проходят (100% success rate)
  - Проверить что количество тестов не изменилось
  - Если тесты падают - исправить проблемы перед продолжением
  - _Requirements: 5.2, 6.5_

- [ ] 16. Проверка Spring контекста
  - [ ] 16.1 Запустить приложение и проверить загрузку Spring контекста
    - Запустить приложение локально
    - Проверить логи на отсутствие ошибок при загрузке контекста
    - Убедиться что все beans создаются корректно
    - Проверить что нет warnings о missing beans
    - _Requirements: 6.3, 6.5_

- [ ] 17. Финальная проверка структуры пакетов
  - [ ] 17.1 Визуально проверить структуру пакетов
    - Проверить что все классы находятся в правильных подпакетах согласно дизайну
    - Убедиться что старые пакеты пусты (кроме keyboard, message)
    - Проверить что myevents полностью переименован в planner
    - Проверить что все callback handler'ы находятся в соответствующих подпакетах
    - _Requirements: 1.1-1.15, 2.1-2.5, 3.1-3.3_

- [x] 18. Checkpoint - Финальная проверка
  - Выполнить полную компиляцию: `mvn clean compile`
  - Запустить все тесты: `mvn clean test`
  - Запустить приложение и протестировать основные команды
  - Убедиться что все работает идентично до рефакторинга
  - _Requirements: 4.3, 5.2, 6.5_

## Notes

- Все задачи выполняются с использованием встроенных возможностей IDE (IntelliJ IDEA)
- Refactor → Move автоматически обновляет все импорты
- Refactor → Rename автоматически обновляет все ссылки
- Checkpoint задачи критически важны для проверки корректности рефакторинга
- Тесты запускаются только на checkpoint задачах согласно стратегии выполнения тестов
- После каждого checkpoint необходимо исправить все найденные проблемы перед продолжением
- Рефакторинг не изменяет функциональность - только структуру пакетов
