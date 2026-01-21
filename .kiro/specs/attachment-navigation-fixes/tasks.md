# Implementation Plan: Attachment Navigation Fixes

## Overview

План реализации исправлений проблем с навигацией и редактированием сообщений при работе с вложениями. Включает расширение ConversationState для хранения контекста шапки, модификацию обработчиков для сохранения шапки при возврате к событию, удаление дублирующего подтверждающего сообщения при загрузке файла и добавление кнопки "Назад к вложениям" при просмотре файла.

## Tasks

- [x] 1. Создать миграцию БД для расширения ConversationState
  - Создать Flyway миграцию для добавления полей `event_has_my_events_header` и `event_count_for_header`
  - Добавить комментарии к полям в БД
  - _Requirements: 4.1_

- [x] 2. Расширить entity ConversationState
  - [x] 2.1 Добавить поля для контекста шапки
    - Добавить поле `eventHasMyEventsHeader` (Boolean)
    - Добавить поле `eventCountForHeader` (Integer)
    - Добавить методы `hasEventHeaderContext()` и `clearEventHeaderContext()`
    - _Requirements: 4.1_
  
  - [ ]* 2.2 Написать property-тест для ConversationState
    - **Property 8: Сохранение контекста при отображении**
    - **Validates: Requirements 4.1**

- [x] 3. Расширить ConversationStateService
  - [x] 3.1 Реализовать методы для работы с контекстом шапки
    - Реализовать `saveEventHeaderContext(userId, hasMyEventsHeader, eventCount)`
    - Реализовать `getEventHeaderContext(userId)` с возвратом EventHeaderContext
    - Реализовать `clearEventHeaderContext(userId)`
    - Создать внутренний класс `EventHeaderContext`
    - Добавить обработку ошибок с логированием
    - _Requirements: 4.1, 5.4_
  
  - [ ]* 3.2 Написать unit-тесты для ConversationStateService
    - Тест сохранения контекста шапки
    - Тест получения контекста шапки
    - Тест получения null при отсутствии контекста
    - Тест очистки контекста шапки
    - _Requirements: 4.1_

- [x] 4. Модифицировать AttachmentCallbackHandler.handleBackToEvent
  - [x] 4.1 Добавить получение контекста шапки
    - Получать контекст через `conversationStateService.getEventHeaderContext(userId)`
    - Использовать `buildEventMessageWithHeader()` если контекст существует и `hasMyEventsHeader = true`
    - Использовать `buildEventMessage()` если контекста нет
    - Очищать attachment context через `clearAttachmentMessageContext()`
    - Добавить логирование на уровне DEBUG
    - _Requirements: 1.1, 1.2, 1.4_
  
  - [ ]* 4.2 Написать property-тест для handleBackToEvent
    - **Property 1: Сохранение шапки при возврате к событию**
    - **Validates: Requirements 1.1, 1.2**
  
  - [ ]* 4.3 Написать unit-тесты для handleBackToEvent
    - Тест включения шапки при наличии контекста
    - Тест отсутствия шапки при отсутствии контекста
    - Тест граничного случая с пустым контекстом
    - _Requirements: 1.1, 1.2, 1.4_

- [x] 5. Расширить KeyboardService
  - [x] 5.1 Реализовать createFileViewKeyboard
    - Создать метод `createFileViewKeyboard(Long eventId)`
    - Добавить кнопку "⬅️ Назад к вложениям" с callback `attach_file_list_{eventId}`
    - _Requirements: 3.1_
  
  - [ ]* 5.2 Написать unit-тесты для createFileViewKeyboard
    - Тест структуры клавиатуры
    - Тест корректности callback data
    - _Requirements: 3.1_

- [x] 6. Расширить TelegramMessageService
  - [x] 6.1 Реализовать sendFileWithKeyboard
    - Создать метод `sendFileWithKeyboard(chatId, fileId, fileType, caption, keyboard)`
    - Поддержать типы: photo, video, audio, document
    - Добавить обработку ошибок TelegramApiException
    - Добавить логирование
    - _Requirements: 3.1_
  
  - [ ]* 6.2 Написать unit-тесты для sendFileWithKeyboard
    - Тест отправки фото с клавиатурой
    - Тест отправки документа с клавиатурой
    - Тест отправки видео с клавиатурой
    - Тест отправки аудио с клавиатурой
    - _Requirements: 3.1_

- [x] 7. Модифицировать AttachmentCallbackHandler.handleViewFile
  - [x] 7.1 Добавить клавиатуру при отправке файла
    - Создавать клавиатуру через `keyboardService.createFileViewKeyboard(eventId)`
    - Использовать `messageService.sendFileWithKeyboard()` вместо `sendFile()`
    - _Requirements: 3.1_
  
  - [ ]* 7.2 Написать property-тест для handleViewFile
    - **Property 5: Наличие кнопки "Назад к вложениям"**
    - **Validates: Requirements 3.1**

- [x] 8. Реализовать AttachmentCallbackHandler.handleBackToAttachments
  - [x] 8.1 Создать новый метод handleBackToAttachments
    - Получать событие и список вложений
    - Формировать сообщение со списком вложений (аналогично handleAttachmentList)
    - Использовать `editOrSendMessage()` для редактирования
    - Добавить обработку в switch-case метода handle() для action "list"
    - _Requirements: 3.2, 3.3, 3.4_
  
  - [ ]* 8.2 Написать property-тесты для handleBackToAttachments
    - **Property 6: Полнота списка вложений при возврате**
    - **Validates: Requirements 3.2**
  
  - [ ]* 8.3 Написать unit-тесты для handleBackToAttachments
    - Тест отображения всех вложений
    - Тест редактирования существующего сообщения
    - Тест fallback на новое сообщение
    - _Requirements: 3.2, 3.3, 3.4_

- [x] 9. Модифицировать UpdateProcessor.handleAttachmentFileUpload
  - [x] 9.1 Убрать отдельное подтверждающее сообщение
    - Удалить строку `messageService.sendMessage(chatId, confirmationMessage)`
    - Оставить только вызов `editOrSendMessage()` для обновления списка вложений
    - Убедиться что список вложений формируется корректно
    - _Requirements: 2.1, 2.2, 2.5_
  
  - [ ]* 9.2 Написать property-тесты для handleAttachmentFileUpload
    - **Property 3: Отсутствие дублирующих сообщений при загрузке файла**
    - **Property 4: Актуальность списка вложений после загрузки**
    - **Validates: Requirements 2.2, 2.5**
  
  - [ ]* 9.3 Написать unit-тесты для handleAttachmentFileUpload
    - Тест отсутствия вызова sendMessage с подтверждением
    - Тест редактирования существующего сообщения
    - Тест наличия нового файла в списке вложений
    - _Requirements: 2.1, 2.2, 2.5_

- [-] 10. Checkpoint - Проверка базовой функциональности
  - Убедиться что все unit-тесты проходят
  - Проверить что миграция БД применяется корректно
  - Убедиться что код компилируется без ошибок

- [x] 11. Добавить сохранение контекста шапки в обработчиках событий
  - [x] 11.1 Модифицировать обработчик "Мои события"
    - Найти обработчик команды /my_events или callback "my_events"
    - Добавить вызов `conversationStateService.saveEventHeaderContext(userId, true, events.size())`
    - Добавить при отправке первого события из списка
    - _Requirements: 4.1_
  
  - [ ]* 11.2 Написать unit-тест для сохранения контекста
    - Тест сохранения контекста при открытии первого события
    - _Requirements: 4.1_

- [ ] 12. Добавить property-тесты для механизма editOrSendMessage
  - [ ]* 12.1 Написать property-тест для приоритета редактирования
    - **Property 2: Механизм editOrSendMessage**
    - **Property 10: Приоритет редактирования над отправкой**
    - **Validates: Requirements 2.1, 2.3, 3.3, 3.4, 4.3, 4.4**
  
  - [ ]* 12.2 Написать property-тест для сохранения messageId
    - **Property 7: Сохранение messageId после операций**
    - **Validates: Requirements 2.4, 5.3**
  
  - [ ]* 12.3 Написать property-тест для fallback механизма
    - **Property 11: Fallback при ошибках редактирования**
    - **Property 13: Целостность данных при fallback**
    - **Validates: Requirements 5.1, 5.2, 5.5**

- [ ] 13. Добавить property-тест для логирования ошибок
  - [ ]* 13.1 Написать property-тест для логирования
    - **Property 12: Логирование ошибок редактирования**
    - **Validates: Requirements 5.4**

- [ ] 14. Написать integration-тесты
  - [ ]* 14.1 Написать integration-тест для полного flow
    - Тест: открытие события → переход к вложениям → загрузка файла → возврат к событию
    - Проверка сохранения и восстановления контекста через БД
    - Использовать @SpringBootTest с Testcontainers
    - _Requirements: 1.1, 2.1, 3.2, 4.1, 4.2_
  
  - [ ]* 14.2 Написать integration-тест для fallback механизма
    - Тест fallback при ошибках редактирования
    - Проверка сохранения нового messageId
    - _Requirements: 5.1, 5.2, 5.3_

- [x] 15. Final checkpoint - Полная проверка
  - Убедиться что все тесты (unit, property, integration) проходят
  - Проверить покрытие кода тестами (минимум 80%)
  - Проверить что все требования покрыты тестами
  - Провести ручное тестирование основных сценариев

## Notes

- Задачи, отмеченные `*`, являются опциональными (тесты) и могут быть пропущены для быстрого MVP
- Каждая задача ссылается на конкретные требования для трассируемости
- Property-тесты должны выполняться минимум 100 итераций
- Используется библиотека jqwik для property-based тестирования
- Checkpoints обеспечивают инкрементальную валидацию
- Integration-тесты используют Testcontainers для PostgreSQL
