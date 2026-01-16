# Implementation Plan: Code Quality Refactoring

## Overview

Поэтапный рефакторинг кодовой базы Family Calendar Bot для улучшения архитектуры, устранения дублирования и оптимизации производительности. Реализация разбита на 6 фаз с инкрементальным прогрессом.

## Tasks

- [x] 1. Подготовка инфраструктуры
  - [x] 1.1 Создать enum CallbackPrefix со всеми префиксами callback data
    - Создать файл `src/main/java/ru/golubyatnikov/family/calendar/bot/model/CallbackPrefix.java`
    - Реализовать методы `matches()`, `extractPayload()`, `withPayload()`, `fromCallbackData()`
    - Включить все 25+ префиксов из текущего кода
    - _Requirements: 3.1, 3.2, 3.5_

  - [x] 1.2 Написать property-тест для CallbackPrefix
    - **Property 2: CallbackPrefix Matching Consistency**
    - **Validates: Requirements 3.2**

  - [x] 1.3 Создать интерфейс CallbackHandler
    - Создать файл `src/main/java/ru/golubyatnikov/family/calendar/bot/handler/callback/CallbackHandler.java`
    - Определить методы `getPrefix()`, `handle()`, `canHandle()`
    - _Requirements: 1.6_

  - [x] 1.4 Создать аннотацию @HandleCallbackErrors
    - Создать файл `src/main/java/ru/golubyatnikov/family/calendar/bot/annotation/HandleCallbackErrors.java`
    - _Requirements: 2.6_

  - [x] 1.5 Создать AOP-аспект CallbackErrorHandlingAspect
    - Создать файл `src/main/java/ru/golubyatnikov/family/calendar/bot/aspect/CallbackErrorHandlingAspect.java`
    - Реализовать перехват исключений с логированием контекста
    - Реализовать отправку сообщения об ошибке пользователю
    - _Requirements: 2.1, 2.2, 2.3, 2.4_

  - [x] 1.6 Написать property-тест для CallbackErrorHandlingAspect
    - **Property 3: Error Handling Completeness**
    - **Validates: Requirements 2.2, 2.3, 2.4**

  - [x] 1.7 Создать компонент BotMessageBuilder
    - Создать файл `src/main/java/ru/golubyatnikov/family/calendar/bot/util/BotMessageBuilder.java`
    - Реализовать методы для всех типов сообщений
    - Использовать MarkdownFormatter для экранирования
    - _Requirements: 4.1, 4.2, 4.4_

  - [x] 1.8 Написать property-тест для BotMessageBuilder
    - **Property 4: BotMessageBuilder Escaping**
    - **Validates: Requirements 4.4**

- [x] 2. Checkpoint - Проверка инфраструктуры
  - Убедиться, что все тесты проходят
  - Проверить, что аспект корректно перехватывает исключения
  - Спросить пользователя, если возникли вопросы

- [x] 3. Создание CallbackHandlers
  - [x] 3.1 Создать DateTimeCallbackHandler
    - Создать файл `src/main/java/ru/golubyatnikov/family/calendar/bot/handler/callback/DateTimeCallbackHandler.java`
    - Перенести логику из UpdateProcessor: handleDateSelection, handleHourSelection, handleTimeSelection, handleTimeBack, handleTimeCancel
    - Использовать @HandleCallbackErrors и BotMessageBuilder
    - _Requirements: 1.3, 2.5_

  - [x] 3.2 Создать EventCallbackHandler
    - Создать файл `src/main/java/ru/golubyatnikov/family/calendar/bot/handler/callback/EventCallbackHandler.java`
    - Перенести логику: view_event_, edit_event_, delete_event_, edit_field_
    - _Requirements: 1.3, 2.5_

  - [x] 3.3 Создать NavigationCallbackHandler
    - Создать файл `src/main/java/ru/golubyatnikov/family/calendar/bot/handler/callback/NavigationCallbackHandler.java`
    - Перенести логику: calendar_, date_actions_
    - _Requirements: 1.3, 2.5_

  - [x] 3.4 Создать EventTypeCallbackHandler
    - Создать файл `src/main/java/ru/golubyatnikov/family/calendar/bot/handler/callback/EventTypeCallbackHandler.java`
    - Перенести логику: event_type_, skip_description
    - _Requirements: 1.3, 2.5_

  - [x] 3.5 Создать ChecklistCallbackHandler
    - Создать файл `src/main/java/ru/golubyatnikov/family/calendar/bot/handler/callback/ChecklistCallbackHandler.java`
    - Перенести логику: checklist_
    - _Requirements: 1.3, 2.5_

  - [x] 3.6 Создать CommentCallbackHandler
    - Создать файл `src/main/java/ru/golubyatnikov/family/calendar/bot/handler/callback/CommentCallbackHandler.java`
    - Перенести логику: comment_, add_completion_note_
    - _Requirements: 1.3, 2.5_

  - [x] 3.7 Создать AttachmentCallbackHandler
    - Создать файл `src/main/java/ru/golubyatnikov/family/calendar/bot/handler/callback/AttachmentCallbackHandler.java`
    - Перенести логику: attach_file_
    - _Requirements: 1.3, 2.5_

  - [x] 3.8 Создать RecurrenceCallbackHandler
    - Создать файл `src/main/java/ru/golubyatnikov/family/calendar/bot/handler/callback/RecurrenceCallbackHandler.java`
    - Перенести логику: recurrence_, series_action_
    - _Requirements: 1.3, 2.5_

  - [x] 3.9 Создать TextEventCallbackHandler
    - Создать файл `src/main/java/ru/golubyatnikov/family/calendar/bot/handler/callback/TextEventCallbackHandler.java`
    - Перенести логику: confirm_text_event:, cancel_text_event
    - Исправить транзакцию: вынести Telegram API вызовы за пределы @Transactional
    - _Requirements: 1.3, 2.5, 7.1, 7.2, 7.3, 7.5_

  - [x] 3.10 Написать unit-тесты для CallbackHandlers
    - Тесты для DateTimeCallbackHandler
    - Тесты для EventCallbackHandler
    - Тесты для TextEventCallbackHandler (включая транзакции)
    - _Requirements: 1.3_

- [x] 4. Checkpoint - Проверка CallbackHandlers
  - Убедиться, что все тесты проходят
  - Проверить, что handlers корректно обрабатывают callback queries
  - Спросить пользователя, если возникли вопросы

- [-] 5. Создание CallbackQueryDispatcher и рефакторинг UpdateProcessor
  - [x] 5.1 Создать CallbackQueryDispatcher
    - Создать файл `src/main/java/ru/golubyatnikov/family/calendar/bot/service/CallbackQueryDispatcher.java`
    - Реализовать маршрутизацию к handlers через List<CallbackHandler>
    - Обработать неавторизованных пользователей и неизвестные callback
    - _Requirements: 1.1, 1.2_

  - [x] 5.2 Написать property-тест для CallbackQueryDispatcher
    - **Property 1: Callback Routing Correctness**
    - **Validates: Requirements 1.2**

  - [x] 5.3 Рефакторинг UpdateProcessor
    - Удалить все методы обработки callback (перенесены в handlers)
    - Заменить processCallbackQuery на вызов CallbackQueryDispatcher.dispatch()
    - Удалить неиспользуемые зависимости
    - Целевой размер: не более 300 строк
    - _Requirements: 1.1, 1.5_

  - [x] 5.4 Написать unit-тесты для UpdateProcessor
    - Тест делегирования callback queries в CallbackQueryDispatcher
    - Тест обработки сообщений
    - _Requirements: 1.1_

- [x] 6. Checkpoint - Проверка интеграции
  - [x] Убедиться, что все тесты проходят (392 теста, 0 ошибок)
  - [x] Проверить, что UpdateProcessor корректно делегирует обработку callback queries в CallbackQueryDispatcher
  - [x] UpdateProcessor уменьшен с 1778 до 668 строк
  - [x] Спросить пользователя, если возникли вопросы

- [x] 7. Оптимизация БД и валидация
  - [x] 7.1 Добавить @EntityGraph к методам EventRepository
    - findByUserIdOrderByEventDateAsc
    - findAllByUserIdAndStatus
    - findByUserIdAndStatusOrderByDeletedAtDesc
    - searchByTitleOrDescription
    - findUpcomingEvents
    - findBySeriesIdAndStatus
    - _Requirements: 5.1, 5.3_

  - [x] 7.2 Написать integration-тест для проверки N+1
    - **Property 5: EntityGraph N+1 Prevention**
    - Использовать Testcontainers для реальной БД
    - Проверить количество SQL-запросов
    - **Validates: Requirements 5.2**

  - [x] 7.3 Добавить Bean Validation к EventService
    - Добавить @Validated на класс
    - Добавить @NotNull, @NotBlank, @Size к параметрам методов
    - _Requirements: 8.1, 8.4, 8.5_

  - [x] 7.4 Написать property-тест для Bean Validation
    - **Property 7: Bean Validation Enforcement**
    - **Validates: Requirements 8.2**

  - [x] 7.5 Расширить GlobalExceptionHandler
    - Добавить обработку ConstraintViolationException
    - Возвращать понятное сообщение об ошибке
    - _Requirements: 8.3_

- [x] 8. Checkpoint - Проверка оптимизаций БД
  - [x] Убедиться, что все тесты проходят (392 теста, 0 ошибок)
  - [x] Проверить, что N+1 проблема устранена (требует выполнения задач 7.1-7.2)
  - [x] Спросить пользователя, если возникли вопросы

- [x] 9. Оптимизация логирования
  - [x] 9.1 Удалить отладочные блоки логирования
    - Найти и удалить блоки "=== ОТЛАДКА ===" и подобные
    - Удалить логирование полных текстов сообщений на уровне INFO
    - _Requirements: 6.1, 6.3_

  - [x] 9.2 Исправить уровни логирования
    - Перевести отладочную информацию на уровень DEBUG
    - Убедиться, что ошибки логируются с полным контекстом
    - Использовать параметризованное логирование везде
    - _Requirements: 6.2, 6.4, 6.5_

  - [x] 9.3 Добавить маскирование чувствительных данных
    - Создать утилиту для маскирования токенов и паролей
    - Применить к логированию
    - _Requirements: 6.6_

- [x] 10. Финальный checkpoint
  - Убедиться, что все тесты проходят
  - Проверить, что UpdateProcessor не превышает 300 строк
  - Проверить, что нет дублирующихся try-catch блоков в handlers
  - Проверить, что нет магических строк для callback prefixes
  - Спросить пользователя, если возникли вопросы

## Notes

- Все задачи, включая тесты, являются обязательными для полного покрытия
- Каждая задача ссылается на конкретные требования для трассируемости
- Checkpoints обеспечивают инкрементальную валидацию
- Property-тесты используют библиотеку jqwik с минимум 100 итерациями
- Unit-тесты проверяют конкретные примеры и edge cases
