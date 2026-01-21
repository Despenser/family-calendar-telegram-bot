# План реализации: Завершение функционала вложений к событиям

## Обзор

Данный план описывает пошаговую реализацию завершения функционала работы с вложениями к событиям в Telegram-боте семейного календаря. Реализация будет выполнена на Java с использованием Spring Boot и Telegram Bot API.

Частичная реализация уже существует (модель, сервис, репозиторий), необходимо добавить пользовательский интерфейс и интеграцию с основным потоком работы бота.

## Задачи

- [x] 1. Расширить ConversationStateService для поддержки состояния ожидания файла
  - Добавить Map для хранения состояния ожидания файла
  - Реализовать методы setAwaitingFile(), isAwaitingFile(), getAwaitingFileContext(), clearAwaitingFile()
  - Создать внутренний класс AwaitingFileContext с полями eventId, chatId, messageId
  - _Requirements: 2.1, 8.1, 8.2, 8.3_

- [ ]* 1.1 Написать unit-тесты для ConversationStateService
  - **Property 4: Установка состояния ожидания файла**
  - **Validates: Requirements 2.1, 8.1**
  - **Property 14: Очистка состояния после обработки**
  - **Validates: Requirements 2.7, 8.2**
  - **Property 15: Очистка состояния при отмене**
  - **Validates: Requirements 8.3**

- [x] 2. Расширить KeyboardService для создания клавиатур вложений
  - [x] 2.1 Добавить метод createAttachmentsListKeyboard() для создания списка вложений
    - Принимать параметры: eventId, List<Attachment>, isCreator
    - Создавать кнопки для каждого вложения с callback data "attach_file_view_{eventId}_{attachmentId}"
    - Добавлять кнопку "➕ Добавить файл" только для создателя события
    - Добавлять кнопку "🔙 Назад к событию" для возврата
    - _Requirements: 4.5, 10.4_

  - [x] 2.2 Добавить метод createDeleteAttachmentConfirmationKeyboard()
    - Принимать параметры: eventId, attachmentId
    - Создавать кнопки "✅ Да, удалить" и "❌ Отмена"
    - Использовать callback data "attach_file_confirm_delete_{eventId}_{attachmentId}" и "attach_file_cancel_delete_{eventId}"
    - _Requirements: 6.1, 10.4_

  - [x] 2.3 Модифицировать createEventActionsKeyboard() для добавления кнопки вложений
    - Добавить кнопку "📎 Вложения" или "📎 Вложения (N)" в зависимости от количества вложений
    - Использовать callback data "attach_file_list_{eventId}"
    - _Requirements: 1.1, 1.3_

- [ ]* 2.4 Написать unit-тесты для KeyboardService
  - **Property 1: Кнопка вложений в карточке события**
  - **Validates: Requirements 1.1**
  - **Property 3: Отображение количества вложений**
  - **Validates: Requirements 1.3**
  - **Property 11: Видимость кнопок для создателя**
  - **Validates: Requirements 4.5, 6.5**
  - **Property 21: Использование inline-клавиатур**
  - **Validates: Requirements 10.4**

- [x] 3. Расширить AttachmentService для получения одного вложения
  - Добавить метод getAttachment(Long attachmentId)
  - Выбрасывать AttachmentNotFoundException если вложение не найдено
  - Добавить логирование
  - _Requirements: 5.1_

- [ ]* 3.1 Написать unit-тест для AttachmentService.getAttachment()
  - Тест успешного получения вложения
  - Тест выброса AttachmentNotFoundException для несуществующего ID

- [x] 4. Расширить TelegramMessageService для отправки файлов
  - [x] 4.1 Добавить метод sendFile() для отправки файлов по file_id
    - Принимать параметры: chatId, fileId, fileType, caption
    - Использовать switch по fileType для выбора метода отправки (sendDocument, sendPhoto, sendVideo, sendAudio)
    - Обрабатывать TelegramApiException
    - Добавить логирование
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5_

- [ ]* 4.2 Написать unit-тесты для TelegramMessageService.sendFile()
  - **Property 7: Отправка файлов по типу**
  - **Validates: Requirements 5.1, 5.2, 5.3, 5.4, 5.5**
  - Тест отправки документа
  - Тест отправки фотографии
  - Тест отправки видео
  - Тест отправки аудио
  - Тест обработки TelegramApiException

- [x] 5. Checkpoint - Убедиться что все базовые компоненты работают
  - Убедиться что все тесты проходят, спросить пользователя если возникли вопросы.

- [x] 6. Реализовать AttachmentCallbackHandler
  - [x] 6.1 Реализовать метод handleAttachmentList() для отображения списка вложений
    - Получать список вложений через AttachmentService.getEventAttachments()
    - Форматировать сообщение с информацией о каждом вложении (имя, тип, размер, дата)
    - Использовать эмодзи для типов файлов (📄, 🖼️, 🎥, 🎵)
    - Форматировать размер файла в КБ/МБ
    - Форматировать дату в формате "dd.MM.yyyy HH:mm"
    - Отображать "У этого события пока нет вложений" если список пуст
    - Создавать клавиатуру через KeyboardService.createAttachmentsListKeyboard()
    - Проверять права доступа для отображения кнопки "Добавить файл"
    - _Requirements: 1.2, 4.1, 4.2, 4.3, 4.4, 4.5, 10.1, 10.2, 10.3, 10.5_

  - [x] 6.2 Реализовать метод handleAddFile() для начала добавления файла
    - Проверять что пользователь является создателем события через AuthorizationService
    - Устанавливать состояние ожидания файла через ConversationStateService.setAwaitingFile()
    - Отправлять сообщение "📎 Отправьте файл для прикрепления к событию\n\n_Максимальный размер: 20 МБ_"
    - Обрабатывать UnauthorizedAccessException
    - _Requirements: 2.1, 7.1, 8.1_

  - [x] 6.3 Реализовать метод handleViewFile() для просмотра файла
    - Получать вложение через AttachmentService.getAttachment()
    - Отправлять файл через TelegramMessageService.sendFile()
    - Формировать caption с именем файла
    - Обрабатывать AttachmentNotFoundException и TelegramApiException
    - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5, 5.6, 9.1, 9.2_

  - [x] 6.4 Реализовать метод handleDeleteFile() для запроса подтверждения удаления
    - Проверять что пользователь является создателем события
    - Создавать клавиатуру подтверждения через KeyboardService.createDeleteAttachmentConfirmationKeyboard()
    - Редактировать сообщение с запросом подтверждения
    - Обрабатывать UnauthorizedAccessException
    - _Requirements: 6.1, 7.2_

  - [x] 6.5 Реализовать метод handleConfirmDelete() для удаления вложения
    - Удалять вложение через AttachmentService.deleteAttachment()
    - Обновлять список вложений через handleAttachmentList()
    - Отправлять callback answer с подтверждением
    - Обрабатывать AttachmentNotFoundException и UnauthorizedAccessException
    - _Requirements: 6.2, 6.3, 6.4_

  - [x] 6.6 Реализовать метод handleCancelDelete() для отмены удаления
    - Возвращать к списку вложений через handleAttachmentList()
    - Отправлять callback answer "Удаление отменено"

  - [x] 6.7 Реализовать метод handleBackToEvent() для возврата к карточке события
    - Получать событие через EventService
    - Формировать сообщение о событии через BotMessageBuilder
    - Создавать клавиатуру через KeyboardService.createEventActionsKeyboard()
    - Редактировать сообщение

- [ ]* 6.8 Написать unit-тесты для AttachmentCallbackHandler
  - **Property 8: Подтверждение удаления**
  - **Validates: Requirements 6.1**
  - **Property 9: Удаление вложения из БД**
  - **Validates: Requirements 6.2**
  - **Property 10: Обновление списка после удаления**
  - **Validates: Requirements 6.3, 6.4**
  - **Property 12: Проверка прав доступа**
  - **Validates: Requirements 7.1, 7.2, 7.3**
  - **Property 13: Доступ к просмотру для всех**
  - **Validates: Requirements 7.4**
  - **Property 16: Обработка ошибок Telegram API**
  - **Validates: Requirements 9.1, 9.2, 9.3**
  - **Property 20: Использование эмодзи для типов файлов**
  - **Validates: Requirements 10.1**
  - **Property 22: Использование Markdown-форматирования**
  - **Validates: Requirements 10.5**
  - **Property 26: Отображение списка или сообщения об отсутствии**
  - **Validates: Requirements 1.2, 4.4**
  - **Property 27: Обработка ошибки отправки файла**
  - **Validates: Requirements 5.6**

- [x] 7. Модифицировать UpdateProcessor для обработки загрузки файлов
  - [x] 7.1 Модифицировать метод handleFileMessage() для проверки состояния ожидания файла
    - Добавить проверку conversationStateService.isAwaitingFile() перед существующей логикой черновиков
    - Если пользователь ожидает файл для вложения, вызывать handleAttachmentFileUpload()
    - Сохранить существующую логику для черновиков как fallback
    - _Requirements: 8.2_

  - [x] 7.2 Реализовать метод handleAttachmentFileUpload() для обработки загрузки файла
    - Получать контекст ожидания файла через ConversationStateService.getAwaitingFileContext()
    - Извлекать информацию о файле из message (fileId, fileName, fileType, fileSize)
    - Обрабатывать разные типы файлов: document, photo, video, audio
    - Для photo выбирать самое большое разрешение
    - Сохранять вложение через AttachmentService.saveAttachment()
    - Отправлять подтверждающее сообщение с информацией о файле
    - Обновлять список вложений через редактирование сообщения (используя messageId из контекста)
    - Очищать состояние ожидания файла через ConversationStateService.clearAwaitingFile()
    - Обрабатывать FileSizeExceededException, EventNotFoundException, UnauthorizedAccessException
    - Очищать состояние при любой ошибке
    - _Requirements: 2.2, 2.3, 2.4, 2.5, 2.6, 2.7, 3.1, 3.2, 3.3, 8.2, 9.4_

  - [x] 7.3 Добавить обработку текстовых сообщений в режиме ожидания файла
    - В методе processUpdate() добавить проверку isAwaitingFile() перед другими проверками состояния
    - Если пользователь в режиме ожидания файла отправляет текст, отправлять подсказку
    - Подсказка: "📎 Пожалуйста, отправьте файл (документ, фото, видео или аудио)\n\n_Для отмены нажмите кнопку 'Отмена' в списке вложений_"
    - _Requirements: 8.4_

- [ ]* 7.4 Написать unit-тесты для UpdateProcessor
  - **Property 2: Сохранение файлов всех типов**
  - **Validates: Requirements 2.2, 2.3, 2.4, 2.5**
  - **Property 14: Очистка состояния после обработки**
  - **Validates: Requirements 2.7, 8.2**
  - **Property 17: Очистка состояния при ошибке**
  - **Validates: Requirements 9.4**
  - **Property 23: Подтверждение после сохранения**
  - **Validates: Requirements 2.6**
  - **Property 24: Сообщение об ошибке размера файла**
  - **Validates: Requirements 3.2**
  - **Property 25: Подсказка при текстовом сообщении**
  - **Validates: Requirements 8.4**
  - Тест обработки document
  - Тест обработки photo
  - Тест обработки video
  - Тест обработки audio
  - Тест обработки файла превышающего лимит
  - Тест обработки текстового сообщения в режиме ожидания файла
  - Тест очистки состояния после успешной загрузки
  - Тест очистки состояния при ошибке

- [x] 8. Checkpoint - Убедиться что вся функциональность работает корректно
  - Убедиться что все тесты проходят, спросить пользователя если возникли вопросы.

- [ ]* 9. Написать integration-тесты для полного потока работы с вложениями
  - **Property 5: Отображение информации о вложении**
  - **Validates: Requirements 4.2, 4.3**
  - **Property 6: Сортировка вложений по дате**
  - **Validates: Requirements 4.1**
  - **Property 18: Форматирование размера файла**
  - **Validates: Requirements 10.2**
  - **Property 19: Форматирование даты загрузки**
  - **Validates: Requirements 10.3**
  - Тест полного потока: открытие списка → добавление файла → просмотр → удаление
  - Тест добавления нескольких файлов разных типов
  - Тест попытки удаления вложения не создателем события
  - Тест попытки добавления файла не создателем события
  - Тест отображения пустого списка вложений
  - Тест отображения списка с несколькими вложениями
  - Тест сортировки вложений по дате загрузки
  - Тест форматирования размера файла (КБ и МБ)
  - Тест форматирования даты загрузки

- [x] 10. Финальный checkpoint - Проверка всей функциональности
  - Убедиться что все тесты проходят
  - Проверить что все требования покрыты
  - Спросить пользователя если возникли вопросы

## Примечания

- Задачи, помеченные `*`, являются опциональными и могут быть пропущены для более быстрого MVP
- Каждая задача ссылается на конкретные требования для обеспечения прослеживаемости
- Checkpoints обеспечивают инкрементальную валидацию
- Property-тесты валидируют универсальные свойства корректности
- Unit-тесты валидируют конкретные примеры и граничные случаи
- Integration-тесты проверяют взаимодействие компонентов
