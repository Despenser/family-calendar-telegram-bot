# Requirements Document

## Introduction

Данный документ описывает требования к рефакторингу кодовой базы Telegram-бота Family Calendar Bot. Цель рефакторинга — устранение архитектурных проблем, улучшение поддерживаемости, тестируемости и производительности кода без изменения функциональности приложения.

## Glossary

- **UpdateProcessor**: Центральный сервис обработки входящих обновлений от Telegram API
- **CallbackQuery**: Событие нажатия на inline-кнопку в Telegram
- **CallbackHandler**: Компонент, обрабатывающий определённый тип callback query
- **CallbackPrefix**: Префикс строки callback data для маршрутизации обработки
- **MessageBuilder**: Компонент для централизованного форматирования сообщений бота
- **EntityGraph**: JPA-аннотация для оптимизации загрузки связанных сущностей
- **AOP**: Aspect-Oriented Programming — аспектно-ориентированное программирование

## Requirements

### Requirement 1: Декомпозиция God-класса UpdateProcessor

**User Story:** Как разработчик, я хочу иметь модульную архитектуру обработки callback queries, чтобы код был легко тестируемым, расширяемым и поддерживаемым.

#### Acceptance Criteria

1. THE UpdateProcessor SHALL делегировать обработку callback queries специализированному CallbackQueryDispatcher
2. WHEN callback query поступает в систему, THE CallbackQueryDispatcher SHALL маршрутизировать его к соответствующему CallbackHandler на основе префикса callback data
3. THE System SHALL содержать отдельные CallbackHandler компоненты для каждой функциональной области:
   - DateTimeCallbackHandler для выбора даты и времени
   - EventCallbackHandler для операций с событиями (просмотр, редактирование, удаление)
   - NavigationCallbackHandler для навигации по календарю
   - EventTypeCallbackHandler для выбора типа события
   - ChecklistCallbackHandler для работы с чек-листами
   - CommentCallbackHandler для работы с комментариями
   - AttachmentCallbackHandler для работы с вложениями
   - RecurrenceCallbackHandler для настройки повторений
4. WHEN новый тип callback добавляется в систему, THE System SHALL позволять добавить его без модификации существующих классов (Open/Closed Principle)
5. THE UpdateProcessor SHALL иметь не более 300 строк кода после рефакторинга
6. THE каждый CallbackHandler SHALL реализовывать единый интерфейс CallbackHandler с методами getPrefix() и handle()

### Requirement 2: Централизованная обработка ошибок callback queries

**User Story:** Как разработчик, я хочу иметь единую точку обработки ошибок для callback queries, чтобы избежать дублирования кода и обеспечить консистентное поведение при ошибках.

#### Acceptance Criteria

1. THE System SHALL использовать AOP-аспект CallbackErrorHandlingAspect для перехвата исключений в методах обработки callback
2. WHEN исключение возникает при обработке callback query, THE System SHALL логировать ошибку с контекстом (callbackData, userId, chatId)
3. WHEN исключение возникает при обработке callback query, THE System SHALL отправлять пользователю информативное сообщение об ошибке
4. WHEN исключение возникает при обработке callback query, THE System SHALL отвечать на callback query с текстом ошибки
5. THE System SHALL НЕ содержать дублирующихся блоков try-catch для обработки ошибок callback в handler-классах
6. THE CallbackErrorHandlingAspect SHALL поддерживать аннотацию @HandleCallbackErrors для маркировки методов

### Requirement 3: Типизация callback data через enum

**User Story:** Как разработчик, я хочу использовать типизированные константы для callback prefixes, чтобы избежать опечаток и упростить рефакторинг.

#### Acceptance Criteria

1. THE System SHALL содержать enum CallbackPrefix со всеми используемыми префиксами callback data
2. THE CallbackPrefix enum SHALL содержать методы matches(String data) и extractPayload(String data)
3. WHEN callback data проверяется на соответствие префиксу, THE System SHALL использовать методы CallbackPrefix enum вместо строковых литералов
4. THE System SHALL НЕ содержать захардкоженных строковых литералов для callback prefixes в коде обработчиков
5. THE CallbackPrefix enum SHALL содержать все существующие префиксы: date_, calendar_, hour_, time_, edit_event_, delete_event_, view_event_, filter_, trash_, event_type_, edit_field_, setup_reminders_, toggle_reminder_, confirm_reminders_, view_reminders_, delete_reminder_, reminder_, recurrence_, series_action_, date_actions_, attach_file_, checklist_, comment_, add_completion_note_, confirm_text_event, cancel_text_event

### Requirement 4: Выделение MessageBuilder для форматирования сообщений

**User Story:** Как разработчик, я хочу иметь централизованный компонент для форматирования сообщений бота, чтобы обеспечить консистентный стиль и упростить изменение формата.

#### Acceptance Criteria

1. THE System SHALL содержать компонент BotMessageBuilder для формирования типовых сообщений бота
2. THE BotMessageBuilder SHALL предоставлять методы для формирования:
   - Сообщений об успешном создании события
   - Сообщений об ошибках
   - Сообщений справки
   - Сообщений с предпросмотром события
   - Сообщений о выборе даты/времени
3. WHEN handler формирует сообщение для пользователя, THE handler SHALL использовать BotMessageBuilder вместо inline-форматирования
4. THE BotMessageBuilder SHALL использовать MarkdownFormatter для экранирования специальных символов
5. THE BotMessageBuilder SHALL поддерживать локализацию сообщений через конфигурацию

### Requirement 5: Оптимизация запросов к БД через EntityGraph

**User Story:** Как разработчик, я хочу избежать N+1 проблемы при загрузке событий, чтобы обеспечить высокую производительность приложения.

#### Acceptance Criteria

1. THE EventRepository SHALL использовать @EntityGraph для всех методов, возвращающих события с доступом к связанным сущностям user или family
2. WHEN метод репозитория возвращает List<Event>, THE метод SHALL загружать связанные сущности в одном запросе через @EntityGraph
3. THE следующие методы SHALL быть дополнены @EntityGraph:
   - findByUserIdOrderByEventDateAsc
   - findAllByUserIdAndStatus
   - findByUserIdAndStatusOrderByDeletedAtDesc
   - searchByTitleOrDescription
   - findUpcomingEvents
   - findBySeriesIdAndStatus
4. IF метод не требует доступа к связанным сущностям, THEN @EntityGraph НЕ SHALL применяться

### Requirement 6: Оптимизация логирования

**User Story:** Как разработчик, я хочу иметь оптимизированное логирование, чтобы не влиять на производительность в production и не раскрывать чувствительные данные.

#### Acceptance Criteria

1. THE System SHALL НЕ логировать полные тексты сообщений на уровне INFO
2. WHEN отладочная информация логируется, THE System SHALL использовать уровень DEBUG
3. THE System SHALL НЕ содержать блоков отладочного логирования вида "=== ОТЛАДКА ===" в production коде
4. WHEN логируется ошибка, THE System SHALL включать: тип ошибки, сообщение, контекст (userId, chatId, command/callbackData)
5. THE System SHALL использовать параметризованное логирование вместо конкатенации строк
6. WHEN логируется чувствительная информация (токены, пароли), THE System SHALL маскировать её

### Requirement 7: Исправление транзакций с внешними вызовами

**User Story:** Как разработчик, я хочу корректно управлять транзакциями, чтобы избежать блокировки соединений БД при вызовах внешних API.

#### Acceptance Criteria

1. THE методы с @Transactional SHALL НЕ содержать вызовов внешних API (Telegram API) внутри транзакции
2. WHEN метод требует и транзакцию, и вызов внешнего API, THE System SHALL разделять их на отдельные методы
3. THE вызовы Telegram API SHALL выполняться после коммита транзакции
4. IF ошибка происходит при вызове Telegram API после коммита, THE System SHALL логировать ошибку без отката транзакции
5. THE метод handleConfirmTextEvent SHALL быть рефакторен для соблюдения этого требования

### Requirement 8: Добавление Bean Validation

**User Story:** Как разработчик, я хочу валидировать входные данные на уровне контроллера, чтобы отсекать невалидные запросы как можно раньше.

#### Acceptance Criteria

1. THE входные параметры методов сервисов SHALL быть аннотированы валидационными аннотациями (@NotNull, @NotBlank, @Size, @Future)
2. WHEN невалидные данные передаются в сервис, THE System SHALL выбрасывать ConstraintViolationException
3. THE GlobalExceptionHandler SHALL обрабатывать ConstraintViolationException и возвращать понятное сообщение об ошибке
4. THE валидация SHALL применяться к параметрам: title события, description события, eventDateTime
5. THE System SHALL использовать @Validated на уровне сервисов для активации валидации параметров методов
