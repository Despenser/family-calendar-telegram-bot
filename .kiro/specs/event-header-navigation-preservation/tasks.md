# Implementation Plan: Event Header Navigation Preservation

## Overview

План реализации исправления бага с потерей шапки события при навигации между событием и его вложениями. Основная задача - модифицировать метод `AttachmentCallbackHandler.handleBackToEvent()` для использования существующего механизма сохранения контекста шапки через `ConversationStateService`.

## Tasks

- [x] 1. Модифицировать AttachmentCallbackHandler.handleBackToEvent()
  - [x] 1.1 Добавить получение контекста шапки
    - Добавить вызов `conversationStateService.getEventHeaderContext(user.getId())`
    - Сохранить результат в переменную `headerContext`
    - Добавить логирование на уровне DEBUG
    - _Requirements: 2.1, 2.2, 3.1_
  
  - [x] 1.2 Добавить условную логику выбора метода формирования сообщения
    - Проверить `if (headerContext != null && headerContext.isHasMyEventsHeader())`
    - При true: вызвать `buildEventMessageWithHeader(event, headerContext.getEventCount())`
    - При false или null: вызвать `buildEventMessage(event)`
    - Добавить логирование выбранного пути на уровне DEBUG
    - _Requirements: 2.1, 2.2, 2.3_
  
  - [x] 1.3 Добавить обработку ошибок
    - Обернуть логику получения контекста в try-catch
    - При исключении: залогировать ERROR и использовать `buildEventMessage()`
    - Убедиться что пользовательский flow не прерывается
    - _Requirements: 3.2, 4.4_
  
  - [ ]* 1.4 Написать unit-тесты для handleBackToEvent
    - Тест с существующим контекстом (hasMyEventsHeader=true)
    - Тест с отсутствующим контекстом (null)
    - Тест с контекстом hasMyEventsHeader=false
    - Тест обработки исключений
    - Тест очистки attachment context
    - _Requirements: 2.1, 2.2, 2.3, 2.4, 3.2, 7.1, 7.2, 7.3_
  
  - [ ]* 1.5 Написать property-тест для восстановления шапки
    - **Property 2: Восстановление шапки при наличии контекста**
    - **Validates: Requirements 2.1, 2.2**
    - Генерировать случайные события и контексты
    - Проверять что buildEventMessageWithHeader вызывается с правильным eventCount
    - Минимум 100 итераций

- [x] 2. Добавить логирование операций
  - [x] 2.1 Добавить DEBUG логи для нормальных операций
    - Лог при входе в метод с eventId и userId
    - Лог при нахождении контекста с параметрами
    - Лог при отсутствии контекста
    - Лог после очистки attachment context
    - _Requirements: 4.1, 4.2, 4.3_
  
  - [x] 2.2 Добавить ERROR логи для исключений
    - Лог при ошибке получения контекста с полным stack trace
    - Лог при других критических ошибках
    - _Requirements: 4.4_

- [x] 3. Checkpoint - Проверка базовой функциональности
  - Убедиться что код компилируется без ошибок
  - Запустить существующие тесты для проверки отсутствия регрессий
  - Проверить что метод handleBackToEvent корректно обрабатывает все случаи

- [ ]* 4. Написать property-тесты для ConversationStateService
  - [ ]* 4.1 Написать property-тест для round-trip контекста
    - **Property 1: Round-trip сохранения контекста шапки**
    - **Validates: Requirements 1.2, 1.3**
    - Генерировать случайные userId, hasMyEventsHeader, eventCount
    - Сохранять через saveEventHeaderContext
    - Получать через getEventHeaderContext
    - Проверять идентичность данных
    - Минимум 100 итераций
  
  - [ ]* 4.2 Написать property-тест для сохранения messageId
    - **Property 3: Сохранение messageId при операциях с вложениями**
    - **Validates: Requirements 6.3**
    - Генерировать случайные userId, eventId, chatId, messageId
    - Сохранять через saveAttachmentMessageId
    - Проверять корректность сохраненного messageId
    - Минимум 100 итераций

- [ ]* 5. Написать unit-тесты для edge cases
  - [ ]* 5.1 Тест edge case: отображение без шапки при null контексте
    - Мокировать getEventHeaderContext для возврата null
    - Проверить вызов buildEventMessage
    - Проверить отсутствие вызова buildEventMessageWithHeader
    - _Requirements: 2.3, 3.1, 3.3_
  
  - [ ]* 5.2 Тест edge case: обработка исключений
    - Мокировать getEventHeaderContext для выброса исключения
    - Проверить логирование ERROR
    - Проверить вызов buildEventMessage
    - Проверить что flow продолжается
    - _Requirements: 3.2_
  
  - [ ]* 5.3 Тест edge case: контекст с hasMyEventsHeader=false
    - Мокировать getEventHeaderContext для возврата контекста с false
    - Проверить вызов buildEventMessage
    - _Requirements: 2.3_

- [ ]* 6. Написать integration-тесты
  - [ ]* 6.1 Integration-тест полного flow
    - Использовать @SpringBootTest
    - Использовать Testcontainers для PostgreSQL
    - Симулировать: открытие "Мои события" → переход к вложениям → возврат
    - Проверить сохранение контекста в реальной БД
    - Проверить восстановление шапки
    - _Requirements: 1.2, 1.3, 2.1, 2.2, 7.4_
  
  - [ ]* 6.2 Integration-тест без контекста
    - Симулировать: прямое открытие события → переход к вложениям → возврат
    - Проверить отсутствие шапки
    - _Requirements: 3.1, 3.3, 7.4_

- [ ] 7. Final checkpoint - Полная проверка
  - Убедиться что все unit-тесты проходят
  - Убедиться что все property-тесты проходят (если реализованы)
  - Убедиться что все integration-тесты проходят (если реализованы)
  - Проверить покрытие кода тестами (цель: минимум 80%)
  - Провести ручное тестирование основных сценариев

## Notes

- Задачи, отмеченные `*`, являются опциональными (тесты) и могут быть пропущены для быстрого MVP
- Каждая задача ссылается на конкретные требования для трассируемости
- Property-тесты должны выполняться минимум 100 итераций
- Используется библиотека jqwik для property-based тестирования в Java
- Checkpoints обеспечивают инкрементальную валидацию
- Integration-тесты используют Testcontainers для PostgreSQL
- Основная задача - модификация одного метода в AttachmentCallbackHandler
- Все необходимые методы и поля уже существуют в ConversationStateService и ConversationState
- Миграции БД не требуются
