# План реализации: Комплексный рефакторинг проекта

## Обзор

Данный план описывает поэтапную реализацию комплексного рефакторинга Spring Boot приложения семейного календаря. Рефакторинг разделен на 4 этапа: критические исправления, архитектурная реорганизация, оптимизация производительности и очистка технического долга.

## Задачи

### Этап 1: Критические исправления безопасности

- [x] 1. Исправить критические проблемы безопасности
  - Заменить System.exit() на graceful shutdown в WebhookRegistrar
  - Реализовать безопасную регистрацию webhook с secret token
  - Добавить валидацию secret token в контроллере
  - _Requirements: 2.1, 2.2, 2.5_

- [ ]* 1.1 Написать property тест для безопасности webhook
  - **Property 4: Безопасность webhook**
  - **Validates: Requirements 2.1**

- [ ]* 1.2 Написать property тест для graceful shutdown
  - **Property 5: Graceful shutdown**
  - **Validates: Requirements 2.2**

- [x] 2. Исправить N+1 проблемы в репозиториях
  - Добавить @EntityGraph во все методы репозиториев без него, если он требуется
  - Исправить ReminderRepository.findPendingReminders с @EntityGraph
  - Добавить @EntityGraph в методы других репозиториев с связанными сущностями
  - _Requirements: 3.1_

- [ ]* 2.1 Написать property тест для EntityGraph
  - **Property 9: EntityGraph для связанных сущностей**
  - **Validates: Requirements 3.1**

- [ ] 3. Исправить неправильные транзакции
  - Убрать @Transactional с уровня класса в следующих сервисах:
    - EventService (имеет методы чтения: getEventById, getEventByIdWithReminders, isToday, isTomorrow, getActiveEventsCount)
    - ReminderService (имеет методы чтения: getReminderById, getReminderWithEventById, getReminderWithEventAndUser, hasActiveReminders)
    - AttachmentService (имеет методы чтения: getAttachment, countEventAttachments)
    - ChecklistService (имеет метод чтения: isChecklistComplete)
    - ConversationService (имеет методы чтения: getActiveDraft, hasActiveDraft)
  - Добавить @Transactional(readOnly = true) для всех методов чтения
  - Добавить @Transactional для всех методов изменения данных
  - Оставить @Transactional на уровне класса для сервисов только с операциями записи:
    - EventHistoryService (только запись истории)
    - DraftCleanupService (только очистка черновиков)
  - Примечание: CommentService, ChecklistService и RecurrenceService будут удалены как неиспользуемые
  - _Requirements: 4.1, 4.2, 4.3_

- [ ]* 3.1 Написать property тест для правильных транзакций
  - **Property 10: Правильные транзакции**
  - **Validates: Requirements 3.2, 4.1, 4.2**

- [ ] 4. Checkpoint - Убедиться, что все критические тесты проходят
  - Убедиться, что все тесты проходят, спросить пользователя, если возникнут вопросы.

### Этап 2: Архитектурная реорганизация

- [ ] 5. Разделить EventService (2250 строк)
- [ ] 5.1 Создать EventQueryService для операций чтения
  - Перенести все методы только для чтения
  - Добавить кэширование для часто используемых запросов
  - _Requirements: 1.1, 3.4, 6.1_

- [ ] 5.2 Создать EventCommandService для операций записи
  - Перенести методы создания и обновления событий
  - Добавить публикацию доменных событий
  - _Requirements: 1.1, 1.2_

- [ ] 5.3 Создать EventDeletionService для удаления
  - Перенести логику удаления и восстановления
  - Реализовать событийную архитектуру вместо прямых вызовов
  - _Requirements: 1.1, 1.2_

- [ ] 5.4 Создать EventValidationService для валидации
  - Перенести всю логику валидации бизнес-правил
  - _Requirements: 1.1, 2.4_

- [ ] 5.5 Создать EventNotificationService для уведомлений
  - Перенести логику отправки уведомлений
  - _Requirements: 1.1_

- [ ] 5.6 Написать property тест для архитектурной целостности
  - **Property 1: Архитектурная целостность**
  - **Validates: Requirements 1.1, 11.2, 11.3**

- [ ] 6. Разделить KeyboardService (2293 строки)
- [ ] 6.1 Создать ReplyKeyboardService
  - Перенести создание обычных клавиатур
  - _Requirements: 1.1_

- [ ] 6.2 Создать InlineKeyboardService
  - Перенести создание inline клавиатур
  - _Requirements: 1.1_

- [ ] 6.3 Создать KeyboardButtonFactory
  - Создать фабрику для создания кнопок
  - _Requirements: 1.1_

- [ ] 6.4 Создать KeyboardLayoutService
  - Перенести логику компоновки клавиатур
  - _Requirements: 1.1_

- [ ] 7. Разделить TelegramMessageService (2237 строк)
- [ ] 7.1 Создать MessageSender для базовой отправки
  - Перенести основную логику отправки сообщений
  - _Requirements: 1.1_

- [ ] 7.2 Создать MessageFormatter для форматирования
  - Перенести логику форматирования сообщений
  - _Requirements: 1.1_

- [ ] 7.3 Создать MessageRetryService для retry логики
  - Перенести retry механизм с exponential backoff
  - _Requirements: 1.1, 12.1_

- [ ] 7.4 Создать CallbackQueryService
  - Перенести обработку callback queries
  - _Requirements: 1.1_

- [ ] 8. Разделить ReminderService (1893 строки)
- [ ] 8.1 Создать ReminderCreationService
  - Перенести создание напоминаний
  - _Requirements: 1.1_

- [ ] 8.2 Создать ReminderSchedulingService
  - Перенести планирование отправки
  - _Requirements: 1.1_

- [ ] 8.3 Создать ReminderNotificationService
  - Перенести отправку уведомлений
  - _Requirements: 1.1_

- [ ] 8.4 Создать ReminderConfigurationService
  - Перенести настройку типов напоминаний
  - _Requirements: 1.1_

- [ ] 9. Checkpoint - Проверить архитектурную целостность
  - Убедиться, что все тесты проходят, спросить пользователя, если возникнут вопросы.

### Этап 3: Оптимизация производительности

- [ ] 10. Внедрить пагинацию
- [ ] 10.1 Добавить пагинацию в EventRepository
  - Заменить все методы List<Event> на Page<Event>
  - Добавить Pageable параметры
  - _Requirements: 3.3, 7.1, 7.2_

- [ ] 10.2 Обновить сервисы для поддержки пагинации
  - Обновить EventQueryService для работы с Page<T>
  - Добавить метаданные пагинации в ответы
  - _Requirements: 7.4, 7.5_

- [ ] 10.3 Написать property тест для пагинации
  - **Property 11: Пагинация вместо списков**
  - **Validates: Requirements 3.3, 7.1, 7.2, 7.4, 7.5**

- [ ] 11. Внедрить кэширование
- [ ] 11.1 Настроить Caffeine кэш
  - Создать CacheConfig с настройками TTL и размера
  - Настроить метрики кэша
  - _Requirements: 3.4, 6.4, 6.5_

- [ ] 11.2 Добавить кэширование в EventQueryService
  - Добавить @Cacheable для getUpcomingEvents
  - Добавить @Cacheable для getUserEvents
  - Добавить @CacheEvict для методов изменения
  - _Requirements: 6.1, 6.2, 6.3_

- [ ] 11.3 Написать property тест для кэширования
  - **Property 12: Кэширование часто используемых данных**
  - **Validates: Requirements 3.4, 6.1, 6.2, 6.3, 6.5**

- [ ] 12. Внедрить correlation ID и трейсинг
- [ ] 12.1 Создать CorrelationIdFilter
  - Генерировать уникальный correlation ID для каждого запроса
  - Добавлять correlation ID в MDC
  - _Requirements: 5.1, 5.2_

- [ ] 12.2 Обновить конфигурацию логирования
  - Добавить correlation ID в формат логов
  - Настроить структурированное логирование
  - _Requirements: 5.5_

- [ ] 12.3 Написать property тест для correlation ID
  - **Property 14: Correlation ID трейсинг**
  - **Validates: Requirements 5.1, 5.2**

- [ ] 13. Внедрить метрики и мониторинг
- [ ] 13.1 Настроить Prometheus метрики
  - Создать MonitoringConfig с MeterRegistry
  - Настроить экспорт метрик
  - _Requirements: 9.1_

- [ ] 13.2 Добавить бизнес-метрики
  - Добавить счетчики для событий и ошибок
  - Добавить таймеры для операций БД
  - _Requirements: 9.2, 9.3_

- [ ] 13.3 Написать property тест для метрик
  - **Property 17: Метрики бизнес-событий**
  - **Validates: Requirements 9.2, 9.3**

- [ ] 14. Checkpoint - Проверить производительность
  - Убедиться, что все тесты проходят, спросить пользователя, если возникнут вопросы.

### Этап 4: Очистка технического долга

- [ ] 15. Разделить оставшиеся God Services
- [ ] 15.1 Разделить UpdateProcessor (1620 строк)
  - Создать MessageUpdateProcessor, CallbackUpdateProcessor
  - Создать AttachmentUpdateProcessor, ConversationUpdateProcessor
  - _Requirements: 1.1_

- [ ] 15.2 Разделить AttachmentCallbackHandler (1341 строка)
  - Создать AttachmentViewHandler, AttachmentUploadHandler
  - Создать AttachmentDeleteHandler, AttachmentNavigationHandler
  - _Requirements: 1.1_

- [ ] 15.3 Разделить EventCallbackHandler (1223 строки)
  - Создать EventViewHandler, EventEditHandler
  - Создать EventDeleteHandler, EventCompletionHandler
  - _Requirements: 1.1_

- [ ] 15.4 Разделить MyEventsCommandHandler (1032 строки)
  - Создать MyEventsQueryService, MyEventsFormattingService
  - Создать MyEventsNavigationService, MyEventsHeaderService
  - _Requirements: 1.1_

- [ ] 16. Очистить мертвый код
- [x] 16.1 Удалить неиспользуемые сервисы и связанные файлы
  - Удалить ContextualHintsService (не используется)
  - Удалить CommentService + Comment entity + CommentRepository
  - Удалить ChecklistService + ChecklistItem entity + ChecklistItemRepository
  - Удалить RecurrenceService + RecurrenceRule entity + RecurrenceRuleRepository
  - Удалить TODO комментарии в ChecklistCallbackHandler и RecurrenceCallbackHandler
  - _Requirements: 14.1, 14.6_

- [ ] 16.2 Реализовать или удалить TODO/FIXME комментарии
  - Обработать оставшиеся файлы с TODO/FIXME (после удаления мертвых сервисов)
  - Либо реализовать функциональность, либо удалить комментарии
  - _Requirements: 14.1_

- [ ] 16.3 Заменить System.out на логирование
  - Исправить MessageToneValidator.java и MarkdownFormatter.java
  - Заменить все System.out.println на log.debug/info
  - _Requirements: 14.2_

- [ ] 16.4 Заменить wildcard импорты на конкретные
  - Исправить все файлы с import static ...MarkdownFormatter.*;
  - Использовать только необходимые импорты
  - _Requirements: 14.4_

- [ ] 16.5 Написать property тест для очистки мертвого кода
  - **Property 26: Очистка мертвого кода**
  - **Validates: Requirements 14.1, 14.2, 14.3, 14.4, 14.5, 14.6**

- [ ] 17. Вынести критические параметры в конфигурацию
- [ ] 17.1 Обновить ApplicationProperties record
  - Добавить SchedulerProperties с параметрами планировщиков
  - Добавить TelegramApiProperties с baseUrl
  - Добавить валидацию для всех параметров
  - _Requirements: 10.3, 10.6, 10.7, 10.8_

- [ ] 17.2 Обновить application.yml
  - Добавить app.scheduler.event-completion-fixed-delay-ms: 600000
  - Добавить app.scheduler.reminder-check-fixed-rate-ms: 60000
  - Добавить app.scheduler.notification-check-fixed-delay-ms: 300000
  - Добавить app.telegram-api.base-url: https://api.telegram.org
  - Добавить комментарии для каждого параметра
  - _Requirements: 10.6, 10.7, 10.8_

- [ ] 17.3 Обновить EventCompletionScheduler
  - Заменить @Scheduled(fixedDelay = 600000) на fixedDelayString = "${app.scheduler.event-completion-fixed-delay-ms}"
  - _Requirements: 10.7_

- [ ] 17.4 Обновить ReminderScheduler
  - Заменить @Scheduled(fixedRate = 60000) на fixedRateString = "${app.scheduler.reminder-check-fixed-rate-ms}"
  - _Requirements: 10.7_

- [ ] 17.5 Обновить NotificationService
  - Заменить @Scheduled(fixedDelay = 300000) на fixedDelayString = "${app.scheduler.notification-check-fixed-delay-ms}"
  - _Requirements: 10.7_

- [ ] 17.6 Обновить WebhookRegistrar
  - Внедрить ApplicationProperties через конструктор
  - Заменить хардкоженный URL "https://api.telegram.org" на appProperties.telegramApi().baseUrl()
  - _Requirements: 10.8_

- [ ] 17.7 Написать property тест для критических параметров в конфигурации
  - **Property 21.1: Критические параметры в конфигурации**
  - **Property 21.2: URL внешних API в конфигурации**
  - **Validates: Requirements 10.6, 10.7, 10.8_

- [ ] 18. Добавить константы вместо магических чисел
- [ ] 18.1 Создать ApplicationConstants класс
  - Вынести оставшиеся магические числа в константы (не связанные с конфигурацией)
  - Организовать константы по категориям
  - _Requirements: 10.2, 11.1_

- [ ] 18.2 Заменить магические числа в коде
  - Заменить все числовые литералы на именованные константы
  - _Requirements: 10.2, 11.1_

- [ ] 18.3 Написать property тест для отсутствия магических чисел
  - **Property 19: Отсутствие магических чисел**
  - **Validates: Requirements 10.2, 10.6, 11.1**

- [ ] 19. Добавить типизированную конфигурацию для остальных параметров
- [ ] 19.1 Расширить ApplicationProperties record
  - Добавить остальные типизированные конфигурационные классы (если нужно)
  - Добавить валидацию для всех параметров
  - _Requirements: 10.3_

- [ ] 19.2 Обновить сервисы для использования типизированной конфигурации
  - Заменить оставшиеся @Value на @ConfigurationProperties где возможно
  - _Requirements: 10.3_

- [ ] 19.3 Написать property тест для типизированной конфигурации
  - **Property 20: Типизированная конфигурация**
  - **Validates: Requirements 10.3**

- [ ] 20. Оптимизировать JavaDoc документацию
- [ ] 20.1 Оптимизировать JavaDoc для всех публичных классов
  - Убрать избыточные @version теги
  - Сократить описания до 1-2 предложений
  - Убедиться что есть @author и @since
  - _Requirements: 11.5, 12.1, 12.2, 12.3_

- [ ] 20.2 Оптимизировать JavaDoc для всех публичных методов
  - Сократить описания параметров до сути
  - Убрать дублирование информации из сигнатуры
  - Убедиться что есть @param, @return, @throws где необходимо
  - _Requirements: 11.5, 12.2, 12.3, 12.4_

- [ ] 20.3 Написать property тест для JavaDoc
  - **Property 21: Лаконичная JavaDoc документация**
  - **Validates: Requirements 11.5, 12.1, 12.2, 12.3, 12.4, 12.5**

- [ ] 21. Добавить устойчивость к ошибкам
- [ ] 21.1 Внедрить Circuit Breaker для внешних сервисов
  - Добавить Resilience4j зависимость
  - Обернуть вызовы Telegram API в circuit breaker
  - _Requirements: 13.2_

- [ ] 21.2 Добавить retry механизм
  - Настроить retry с exponential backoff
  - Добавить fallback методы
  - _Requirements: 13.1_

- [ ] 21.3 Написать property тест для устойчивости
  - **Property 22: Retry для сетевых операций**
  - **Property 23: Circuit breaker для внешних сервисов**
  - **Validates: Requirements 13.1, 13.2**

- [ ] 22. Финальный checkpoint - Убедиться, что все тесты проходят
  - Убедиться, что все тесты проходят, спросить пользователя, если возникнут вопросы.

## Примечания

- Все задачи являются обязательными для комплексного подхода к рефакторингу
- Каждая задача ссылается на конкретные требования для отслеживаемости
- Checkpoint задачи обеспечивают инкрементальную валидацию
- Property тесты валидируют универсальные свойства корректности
- Unit тесты валидируют конкретные примеры и граничные случаи
- Рефакторинг выполняется поэтапно для минимизации рисков