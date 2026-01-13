# План реализации - Семейный Календарь Бот

- [x] 1. Инициализация проекта и Docker инфраструктура




- [x] 1.1 Создать Maven проект с pom.xml


  - Настроить Spring Boot 3.5.3 как parent (декабрь 2025, проверено через Context7)
  - Настроить Java 21 LTS как целевую версию
  - Добавить properties: telegram.version=8.2.0, flyway.version=11.1.0, testcontainers.version=1.21.2
  - Добавить зависимости: spring-boot-starter-web, spring-boot-starter-data-jpa, spring-boot-starter-validation
  - Добавить PostgreSQL driver, Flyway 11.1.0 (core + database-postgresql)
  - Добавить telegrambots-spring-boot-starter 6.9.7.1 (Spring Boot интеграция)
  - Добавить зависимости для тестирования: spring-boot-starter-test, spring-boot-testcontainers, testcontainers 1.21.2
  - Настроить spring-boot-maven-plugin с исключением Lombok
  - _Requirements: 1.1_

- [x] 1.2 Создать структуру пакетов




  - Создать пакеты: controller, service, repository, model, config, exception, handler
  - Создать главный класс FamilyCalendarBotApplication с @SpringBootApplication
  - _Requirements: 1.2_

- [x] 1.3 Настроить Docker и Docker Compose





  - Создать Dockerfile для приложения (базовый образ eclipse-temurin:21-jre-alpine)
  - Создать docker-compose.yml с сервисами app и postgres:18.1-alpine
  - Настроить volumes для персистентности данных PostgreSQL
  - Настроить healthcheck для PostgreSQL
  - Настроить зависимости между контейнерами
  - _Requirements: 1.5, 10.1_

- [x] 1.4 Настроить Git и .gitignore





  - Создать .gitignore с исключениями для target/, .idea/, *.iml, .env
  - Исключить файлы с секретами и паролями
  - Инициализировать Git репозиторий
  - _Requirements: 2.3_

- [x] 1.5 Создать конфигурационные файлы





  - Создать application.yml с конфигурацией Spring Boot, JPA, Flyway
  - Создать application-dev.yml для локальной разработки
  - Создать application-prod.yml для production
  - Создать .env.example с примерами переменных окружения
  - Настроить logback-spring.xml для логирования
  - _Requirements: 1.4, 2.1, 2.4_

- [x] 2. Настройка базы данных и миграции




- [x] 2.1 Создать Flyway миграции для схемы БД







  - Создать V1__Initial_schema.sql с таблицами families, users, events
  - Добавить ENUM event_status со значениями 'draft' и 'active'
  - Добавить поле status в таблицу events с default значением 'active'
  - Добавить индексы для оптимизации запросов
  - Добавить индексы для работы с черновиками (idx_events_status, idx_events_user_status)
  - Добавить foreign keys и constraints
  - _Requirements: 11.1, 11.2, 11.3, 11.4, 11.6_

- [x] 2.2 Создать JPA Entity классы







  - Создать Family entity с полями id, name, members, created_at
  - Создать User entity с полями id, telegram_id, username, first_name, family, created_at
  - Создать Event entity с полями id, user, family, title, description, event_date, event_time, status, notified, created_at
  - Добавить ENUM EventStatus с значениями DRAFT и ACTIVE
  - Настроить relationships (@ManyToOne, @OneToMany)
  - Добавить @PrePersist для автоматического created_at
  - _Requirements: 11.2, 11.3, 11.4, 11.6_



- [x] 2.3 Создать Spring Data JPA репозитории







  - Создать FamilyRepository extends JpaRepository
  - Создать UserRepository с методом findByTelegramId
  - Создать EventRepository с методами findByFamilyIdAndEventDateBetween, findByUserIdOrderByEventDateAsc
  - Добавить методы для работы с черновиками: findByUserIdAndStatus, findAllByUserIdAndStatus
  - Добавить custom query для поиска событий для уведомлений
  - _Requirements: 11.1, 15.1, 15.2_


- [x] 3. Реализация конфигурации и Webhook




- [x] 3.1 Создать BotConfig класс





  - Аннотировать @Configuration и @ConfigurationProperties(prefix = "telegram.bot")
  - Добавить поля: token, username, webhookUrl
  - Добавить @NotBlank валидацию для обязательных полей
  - _Requirements: 2.1, 2.5_

- [x] 3.2 Создать Webhook регистрацию при старте




  - Создать @Component с @PostConstruct для регистрации webhook
  - Использовать SetWebhook API метод Telegram
  - Добавить логирование успешной/неуспешной регистрации
  - При ошибке регистрации - остановить приложение
  - _Requirements: 8.1, 8.5_

- [x] 3.3 Создать Webhook REST Controller




  - Аннотировать @RestController с @RequestMapping("/webhook")
  - Создать POST endpoint /{botToken} для приема Updates
  - Валидировать токен в URL
  - Возвращать HTTP 200 OK после приема
  - Добавить логирование входящих updates
  - _Requirements: 8.2, 8.4_

- [x] 3.4 Написать unit тест для валидации конфигурации





  - Проверить, что при отсутствии токена выбрасывается исключение
  - Проверить, что при отсутствии webhook URL выбрасывается исключение
  - _Requirements: 2.2, 2.5_

- [x] 4. Реализация User Service и авторизации




- [x] 4.1 Создать UserService




  - Создать метод findByTelegramId(Long telegramId)
  - Создать метод createUser(Long telegramId, String username, String firstName, Family family)
  - Создать метод isUserAuthorized(Long telegramId)
  - Добавить логирование операций с пользователями
  - _Requirements: 3.1, 3.2, 3.4_

- [x] 4.2 Написать unit тесты для UserService


  - Тест поиска пользователя по Telegram ID
  - Тест создания нового пользователя
  - Тест проверки авторизации
  - Использовать моки для UserRepository
  - _Requirements: 3.1, 3.2_



- [x] 5. Реализация Event Service



- [x] 5.1 Создать EventService





  - Создать метод createEvent(userId, title, description, eventDateTime)
  - Создать метод getUpcomingEvents(familyId, days)
  - Создать метод getUserEvents(userId)
  - Создать метод updateEvent(eventId, userId, title, description, eventDateTime)
  - Создать метод deleteEvent(eventId, userId)
  - Добавить валидацию даты (не в прошлом)
  - Добавить проверку прав доступа (только создатель может редактировать/удалять)
  - _Requirements: 4.1, 4.2, 4.3, 5.1, 5.4, 7.1, 7.2, 7.3, 7.4, 7.5_

- [x] 5.2 Написать unit тесты для EventService


  - Тест создания события с валидными данными
  - Тест отклонения события с датой в прошлом
  - Тест получения предстоящих событий
  - Тест обновления события создателем
  - Тест отклонения обновления события не создателем
  - Тест удаления события создателем
  - Использовать моки для репозиториев
  - _Requirements: 4.2, 5.1, 7.5_



- [x] 6. Реализация Command Handler инфраструктуры




- [x] 6.1 Создать интерфейс CommandHandler





  - Определить методы: handle(Message, User), getCommand(), getDescription(), requiresAuth()
  - Добавить Javadoc комментарии
  - _Requirements: 1.2_

- [x] 6.2 Создать CommandDispatcher




  - Аннотировать @Service
  - Внедрить List<CommandHandler> через конструктор
  - Создать Map<String, CommandHandler> для быстрого поиска
  - Реализовать метод dispatch(Message) для маршрутизации
  - Добавить проверку авторизации перед вызовом handler
  - Добавить логирование маршрутизации
  - _Requirements: 1.2_

- [x] 6.3 Создать UpdateProcessor





  - Аннотировать @Service
  - Внедрить CommandDispatcher и UserService
  - Реализовать @Async метод processUpdate(Update)
  - Извлекать Message из Update
  - Проверять авторизацию пользователя
  - Делегировать обработку CommandDispatcher
  - _Requirements: 8.2_

- [x] 6.4 Написать unit тесты для CommandDispatcher


  - Тест регистрации обработчиков
  - Тест маршрутизации к правильному handler
  - Тест проверки авторизации
  - _Requirements: 1.2_

- [x] 7. Реализация базовых Command Handlers




- [x] 7.1 Реализовать StartCommandHandler





  - Аннотировать @Component
  - Проверить наличие пользователя в БД
  - Если пользователь найден - отправить приветствие с командами
  - Если не найден - отправить сообщение о необходимости регистрации
  - Добавить логирование
  - _Requirements: 3.1, 3.2, 3.3, 3.5_

- [x] 7.2 Реализовать HelpCommandHandler





  - Аннотировать @Component
  - Внедрить List<CommandHandler>
  - Сформировать список всех команд с описаниями
  - Использовать Markdown форматирование
  - _Requirements: 12.3_

- [x] 7.3 Написать unit тесты для базовых handlers





  - Тест StartCommandHandler для авторизованного пользователя
  - Тест StartCommandHandler для неавторизованного пользователя
  - Тест HelpCommandHandler на полноту списка команд
  - _Requirements: 3.1, 3.2_

- [x] 8. Реализация Event Command Handlers







- [x] 8.1 Реализовать AddEventCommandHandler с поддержкой inline-календаря и выбора времени







  - Аннотировать @Component
  - Внедрить ConversationService, KeyboardService, TelegramMessageService
  - При получении команды /add_event создать черновик через ConversationService
  - Отправить inline-календарь для выбора даты
  - Обработать callback query с выбранной датой, обновить черновик
  - Отправить inline-кнопки для выбора часа
  - Обработать callback query с выбранным часом, отправить кнопки для выбора минут
  - Обработать callback query с выбранным временем, обновить черновик
  - Запросить название события через текстовое сообщение
  - Обработать текстовое сообщение с названием, обновить черновик
  - Запросить описание события (с кнопкой "Пропустить")
  - Обработать описание или пропуск, завершить создание события
  - Отправлять подтверждение с деталями события
  - Обрабатывать отмену на любом шаге через callback "calendar_cancel" или "time_cancel"
  - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.6, 4.7, 4.8, 4.9, 15.1, 15.2, 15.3, 15.4, 15.5, 15.6_

- [x] 8.2 Реализовать UpcomingEventsCommandHandler




  - Аннотировать @Component
  - Вызывать EventService.getUpcomingEvents() для семьи пользователя
  - Форматировать список событий с Markdown
  - Если событий нет - отправить соответствующее сообщение
  - _Requirements: 5.1, 5.2, 5.3, 5.4, 5.5_

- [x] 8.3 Реализовать MyEventsCommandHandler




  - Аннотировать @Component
  - Вызывать EventService.getUserEvents()
  - Отправить список с inline кнопками для редактирования/удаления
  - Обработать callback queries для редактирования и удаления
  - _Requirements: 7.1, 7.2, 7.3, 7.4, 7.5_

- [ ] 8.4 Написать unit тесты для AddEventCommandHandler


  - Тест начала создания события (отправка календаря)
  - Тест обработки выбора даты
  - Тест обработки выбора времени
  - Тест обработки ввода названия события
  - Тест обработки ввода описания
  - Тест обработки пропуска описания
  - Тест отмены создания события
  - Тест валидации даты в прошлом
  - Использовать моки для ConversationService и KeyboardService
  - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5, 4.8, 15.1, 15.2, 15.3_


- [x] 9. Реализация Notification Service



- [x] 9.1 Создать NotificationService





  - Аннотировать @Service
  - Создать @Scheduled метод с fixedDelay = 300000 (5 минут)
  - Найти события, которые начнутся через 1 час
  - Отправить уведомления всем членам семьи
  - Отметить события как notified в БД
  - Добавить retry логику для отправки
  - Добавить логирование отправленных уведомлений
  - _Requirements: 6.1, 6.2, 6.3, 6.4, 6.5_

- [x] 9.2 Создать TelegramMessageService





  - Аннотировать @Service
  - Реализовать метод sendMessage(telegramId, text)
  - Реализовать метод sendMessage(telegramId, text, replyMarkup) для inline кнопок
  - Добавить retry механизм с экспоненциальной задержкой
  - Обработать ошибки Telegram API
  - _Requirements: 6.4, 9.4_

- [x] 9.3 Написать unit тесты для NotificationService


  - Тест поиска событий для уведомлений
  - Тест отправки уведомлений членам семьи
  - Тест отметки события как notified
  - Использовать моки для репозиториев и TelegramMessageService


  - _Requirements: 6.1, 6.3_

- [x] 9.4 Создать ConversationService для управления состоянием диалога




  - Аннотировать @Service и @Transactional
  - Внедрить EventRepository и UserRepository
  - Реализовать метод startEventCreation() - создание черновика
  - Реализовать метод updateEventDate() - обновление даты в черновике
  - Реализовать метод updateEventTime() - обновление времени в черновике
  - Реализовать метод updateEventTitle() - обновление названия в черновике
  - Реализовать метод completeEventCreation() - завершение создания (статус ACTIVE)
  - Реализовать метод cancelEventCreation() - удаление черновика
  - Реализовать метод getActiveDraft() - получение активного черновика
  - Реализовать метод hasActiveDraft() - проверка наличия черновика
  - Реализовать метод getCurrentStep() - определение текущего шага диалога
  - Реализовать приватный метод cancelPendingDrafts() - удаление старых черновиков
  - Добавить ENUM ConversationStep с шагами диалога
  - _Requirements: 15.1, 15.2, 15.3, 15.4, 15.5, 15.6, 15.7_

- [x] 9.5 Написать unit тесты для ConversationService
  - Тест создания черновика события
  - Тест обновления даты в черновике
  - Тест обновления времени в черновике
  - Тест обновления названия в черновике
  - Тест завершения создания события
  - Тест отмены создания события
  - Тест определения текущего шага диалога
  - Тест удаления старых черновиков при создании нового
  - Использовать моки для репозиториев
  - _Requirements: 15.1, 15.2, 15.3, 15.4, 15.5, 15.6_



- [x] 10. Реализация обработки ошибок




- [x] 10.1 Создать пользовательские исключения



  - Создать UserNotFoundException
  - Создать EventNotFoundException
  - Создать UnauthorizedAccessException
  - Создать InvalidDateException
  - _Requirements: 9.1_

- [x] 10.2 Создать GlobalExceptionHandler





  - Аннотировать @RestControllerAdvice
  - Обработать UserNotFoundException
  - Обработать UnauthorizedAccessException
  - Обработать DataAccessException (ошибки БД)
  - Обработать TelegramApiException
  - Логировать все исключения с stack trace
  - Формировать дружественные сообщения для пользователей
  - _Requirements: 9.1, 9.2, 9.3, 9.4_

- [x] 10.3 Написать unit тесты для GlobalExceptionHandler


  - Тест обработки UserNotFoundException
  - Тест обработки UnauthorizedAccessException
  - Тест обработки DataAccessException
  - Тест формирования дружественных сообщений
  - _Requirements: 9.2, 9.3_

- [ ] 11. Docker и развертывание
- [x] 11.1 Протестировать Docker Compose локально


  - Выполнить docker-compose build
  - Выполнить docker-compose up
  - Проверить, что PostgreSQL запустился и готов
  - Проверить, что приложение подключилось к БД
  - Проверить, что миграции применились
  - Проверить, что webhook зарегистрирован
  - _Requirements: 10.1, 10.2, 10.3, 10.4_

- [x] 11.2 Создать скрипты для управления





  - Создать start.sh для запуска docker-compose
  - Создать stop.sh для остановки
  - Создать logs.sh для просмотра логов
  - Создать clean.sh для очистки volumes
  - _Requirements: 10.1_



- [x] 12. Документация




- [x] 12.1 Создать README.md






  - Добавить описание проекта и функционала
  - Добавить архитектурную диаграмму
  - Добавить ER-диаграмму базы данных
  - Добавить инструкции по локальному запуску с Docker Compose
  - Добавить инструкции по настройке ngrok для локальной разработки webhook
  - Добавить список всех команд бота с примерами
  - Добавить примеры переменных окружения
  - Добавить раздел о тестировании
  - Добавить информацию о структуре проекта
  - _Requirements: 12.1, 12.2, 12.3, 12.4, 12.5_


- [x] 12.2 Создать SETUP.md с инструкциями






  - Инструкции по созданию бота через @BotFather
  - Инструкции по настройке PostgreSQL
  - Инструкции по настройке webhook URL
  - Инструкции по добавлению пользователей в БД
  - Troubleshooting секция
  - _Requirements: 12.5_


- [x] 12.3 Добавить Javadoc комментарии





  - Документировать все публичные классы
  - Документировать все публичные методы
  - Добавить описания параметров и возвращаемых значений
  - Добавить примеры использования где необходимо
  - _Requirements: 12.3_

- [ ] 13. Финальная проверка
- [ ] 13.1 Запустить все тесты
  - Запустить все unit тесты
  - Проверить покрытие кода (цель > 70%)
  - Исправить failing тесты

- [ ] 13.2 Проверить Docker Compose
  - Выполнить полный цикл: build, up, test, down
  - Проверить персистентность данных после перезапуска
  - Проверить логи на наличие ошибок
  - _Requirements: 10.1, 10.4_


- [x] 13.3 Проверить документацию




  - Проверить README.md на полноту
  - Проверить SETUP.md на актуальность
  - Проверить Javadoc комментарии
  - Проверить примеры конфигурации
  - _Requirements: 12.1, 12.3_

- [x] 13.4 Финальный review кода







  - Проверить соответствие Java best practices
  - Проверить обработку всех ошибок
  - Проверить логирование



  - Проверить безопасность (нет хардкодед секретов)
  - _Requirements: 2.3_

- [ ] 14. Реализация кнопок команд и inline-календаря
- [x] 14.1 Обновить KeyboardService с методами для календаря и выбора времени







  - Аннотировать @Service
  - Реализовать метод createAuthorizedUserKeyboard() с кнопками: "📅 Предстоящие события", "➕ Добавить событие", "📋 Мои события", "❓ Помощь"
  - Реализовать метод createUnauthorizedUserKeyboard() с кнопками: "🚀 Начать", "❓ Помощь"
  - Реализовать метод buttonTextToCommand() для преобразования текста кнопки в команду
  - Реализовать метод createCalendarKeyboard(year, month) - inline-календарь с навигацией
  - Реализовать метод createHourSelectionKeyboard() - выбор часа (0-23)


  - Реализовать метод createMinuteSelectionKeyboard(hour) - выбор минут (0, 15, 30, 45)
  - Настроить resize_keyboard=true и persistent=true для клавиатур
  - Блокировать даты в прошлом в календаре
  - Добавить кнопки навигации по месяцам в календаре
  - Добавить кнопки отмены для календаря и выбора времени
  - _Requirements: 13.1, 13.3, 13.4, 13.5, 4.1, 4.2, 4.7, 4.8_

- [x] 14.2 Обновить UpdateProcessor для поддержки кнопок и callback queries


  - Внедрить KeyboardService и ConversationService через конструктор
  - Добавить обработку callback queries для календаря (date_*, calendar_*)
  - Добавить обработку callback queries для выбора времени (hour_*, time_*)
  - Добавить обработку callback queries для отмены (calendar_cancel, time_cancel)
  - Добавить преобразование текста кнопки в команду перед обработкой
  - Добавить отправку соответствующей клавиатуры в ответе
  - Для авторизованных пользователей отправлять полную клавиатуру
  - Для неавторизованных пользователей отправлять ограниченную клавиатуру
  - Обрабатывать текстовые сообщения в контексте активного диалога
  - _Requirements: 13.2, 13.3, 13.4, 4.1, 4.2, 15.4_


- [x] 14.3 Обновить TelegramMessageService

  - Добавить перегруженный метод sendMessage с параметром ReplyKeyboardMarkup
  - Настроить отправку клавиатуры вместе с сообщением
  - Добавить логирование отправки клавиатур
  - _Requirements: 13.1_

- [ ] 14.4 Написать unit тесты для KeyboardService


  - Тест создания клавиатуры для авторизованного пользователя
  - Тест создания клавиатуры для неавторизованного пользователя
  - Тест преобразования текста кнопки в команду
  - Тест создания inline-календаря для текущего месяца
  - Тест создания inline-календаря с навигацией
  - Тест блокировки дат в прошлом
  - Тест создания клавиатуры выбора часа
  - Тест создания клавиатуры выбора минут
  - Проверить наличие всех необходимых кнопок
  - Проверить настройки resize_keyboard и persistent
  - _Requirements: 13.1, 13.3, 13.4, 4.1, 4.2, 4.7, 4.8_

- [ ] 14.5 Обновить тесты UpdateProcessor



  - Добавить тест обработки нажатия кнопки
  - Добавить тест обработки callback query для выбора даты
  - Добавить тест обработки callback query для выбора времени
  - Добавить тест обработки отмены диалога
  - Проверить, что текст кнопки преобразуется в команду
  - Проверить, что отправляется правильная клавиатура
  - Проверить обработку текстовых сообщений в контексте диалога
  - _Requirements: 13.2, 4.1, 4.2, 15.4_


- [ ] 15. Улучшение inline-календаря с визуальными индикаторами
- [x] 15.1 Обновить EventRepository для поддержки запросов событий по месяцу


  - Добавить метод findByFamilyIdAndEventDateBetweenAndStatus() для получения активных событий за месяц
  - Метод должен принимать familyId, startDate, endDate и status
  - Метод должен возвращать List<Event>
  - _Requirements: 16.4_

- [x] 15.2 Обновить KeyboardService.createCalendarKeyboard() для улучшенного отображения


  - Изменить сигнатуру метода: добавить параметр Long familyId
  - Получать события семьи за отображаемый месяц через EventRepository
  - Создать Map<LocalDate, Event> с первым событием (по времени) для каждой даты
  - Для дат в прошлом отображать пустую ячейку " " вместо точки
  - Для дат с событиями добавлять визуальный индикатор в формате "день📌инициал" (например, "5📌А")
  - Извлекать инициал из firstName создателя события (первая буква в верхнем регистре)
  - Блокировать кнопку "Предыдущий месяц" если предыдущий месяц в прошлом
  - Если предыдущий месяц в прошлом, отображать пустую кнопку "   " вместо "◀️ Пред"
  - Добавить логирование количества найденных событий для месяца
  - _Requirements: 16.1, 16.2, 16.3, 16.5, 16.6_

- [x] 15.3 Обновить все вызовы createCalendarKeyboard() для передачи familyId


  - Найти все места, где вызывается createCalendarKeyboard()
  - Обновить вызовы для передачи familyId пользователя
  - Убедиться, что familyId доступен в контексте вызова
  - _Requirements: 16.4_


- [x] 15.4 Написать unit тесты для улучшенного календаря

  - Тест отображения пустых ячеек для дат в прошлом
  - Тест блокировки кнопки "Предыдущий месяц" для прошлых месяцев
  - Тест визуального выделения дней с событиями (формат "день📌инициал")
  - Тест корректного извлечения инициала из firstName
  - Тест отображения инициала первого события при нескольких событиях в один день
  - Тест корректной загрузки событий за месяц
  - Тест календаря для месяца без событий
  - Тест календаря для месяца с несколькими событиями от разных пользователей
  - Использовать моки для EventRepository
  - _Requirements: 16.1, 16.2, 16.3, 16.4, 16.5, 16.6_

- [x] 15.5 Checkpoint - Проверка улучшенного календаря



  - Убедиться, что все тесты проходят
  - Проверить визуальное отображение календаря в Telegram
  - Проверить, что прошлые даты отображаются как пустые ячейки
  - Проверить, что навигация в прошлое заблокирована
  - Проверить, что дни с событиями выделены в формате "день📌инициал"
  - Проверить корректность отображения инициалов создателей
  - Спросить пользователя, если возникают вопросы


- [ ] 16. Расширение базы данных для новых функций
- [x] 16.1 Создать миграцию V3__Add_extended_features.sql
  - Добавить поля в таблицу events: end_time, is_personal, series_id, completion_note, deleted_at, completed_at
  - Обновить CHECK constraint для статусов: добавить значения 'COMPLETED' и 'DELETED'
  - Создать индексы: idx_events_series_id, idx_events_is_personal, idx_events_deleted_at, idx_events_user_status_deleted, idx_events_completed
  - Добавить constraint для валидации временного интервала
  - _Requirements: 26.2, 32.1, 25.3, 19.2_

- [x] 16.2 Создать миграцию V4__Add_attachments_table.sql
  - Создать таблицу attachments с полями: id, event_id, file_id, file_name, file_type, file_size, uploaded_at
  - Добавить foreign key на events с ON DELETE CASCADE
  - Создать индексы: idx_attachments_event_id, idx_attachments_event_uploaded
  - Добавить constraints для валидации данных
  - _Requirements: 20.2, 20.3_

- [x] 16.3 Создать миграцию V5__Add_comments_table.sql
  - Создать таблицу comments с полями: id, event_id, user_id, text, created_at
  - Добавить foreign keys на events и users с ON DELETE CASCADE
  - Создать индексы: idx_comments_event_id, idx_comments_event_created, idx_comments_created_at
  - Добавить constraint для валидации непустого текста
  - _Requirements: 21.3_

- [x] 16.4 Создать миграцию V6__Add_checklist_items_table.sql
  - Создать таблицу checklist_items с полями: id, event_id, text, completed, position, completed_at, completed_by
  - Добавить foreign keys на events и users
  - Создать индексы: idx_checklist_event_id, idx_checklist_event_position, idx_checklist_event_completed
  - Добавить constraint для валидации логики выполнения
  - _Requirements: 22.3_

- [x] 16.5 Создать миграцию V7__Add_recurrence_rules_table.sql
  - Создать ENUM frequency_type со значениями 'daily', 'weekly', 'monthly'
  - Создать таблицу recurrence_rules с полями: id, series_id, frequency, interval, days_of_week, end_date, occurrences, exceptions
  - Создать индекс idx_recurrence_series_id
  - Добавить constraint для валидации условий окончания повторений
  - _Requirements: 27.6_

- [x] 16.6 Создать миграцию V8__Add_event_history_table.sql
  - Создать ENUM action_type со значениями 'created', 'updated', 'deleted', 'restored'
  - Создать таблицу event_history с полями: id, event_id, user_id, action_type, field_name, old_value, new_value, changed_at
  - Создать индексы: idx_event_history_event_id, idx_event_history_event_changed, idx_event_history_changed_at, idx_event_history_action_changed
  - Добавить constraint для валидации логики полей
  - _Requirements: 29.1, 29.2_

- [x] 16.7 Создать миграцию V9__Add_reminders_table.sql
  - Создать ENUM reminder_type со значениями 'morning_of_day', 'evening_before', 'one_hour_before', 'ten_minutes_before', 'custom'
  - Создать таблицу reminders с полями: id, event_id, reminder_type, custom_minutes, reminder_time, sent, sent_at
  - Создать индексы: idx_reminders_event_id, idx_reminders_time_sent, idx_reminders_event_time
  - Добавить constraints для валидации логики custom напоминаний и отправки
  - _Requirements: 23.3, 23.6_

- [x] 17. Создание новых Entity классов
- [x] 17.1 Создать Attachment entity
  - Добавить поля: id, event, fileId, fileName, fileType, fileSize, uploadedAt
  - Настроить @ManyToOne связь с Event
  - Добавить @PrePersist для uploadedAt
  - _Requirements: 20.3_

- [x] 17.2 Создать Comment entity
  - Добавить поля: id, event, user, text, createdAt
  - Настроить @ManyToOne связи с Event и User
  - Добавить @PrePersist для createdAt
  - _Requirements: 21.3_

- [x] 17.3 Создать ChecklistItem entity
  - Добавить поля: id, event, text, completed, position, completedAt, completedBy
  - Настроить @ManyToOne связи с Event и User
  - _Requirements: 22.3_

- [x] 17.4 Создать RecurrenceRule entity
  - Добавить поля: id, seriesId, frequency, interval, daysOfWeek, endDate, occurrences, exceptions
  - Создать ENUM Frequency с DAILY, WEEKLY, MONTHLY
  - _Requirements: 27.6_

- [x] 17.5 Создать EventHistory entity
  - Добавить поля: id, eventId, user, actionType, fieldName, oldValue, newValue, changedAt
  - Создать ENUM ActionType с CREATED, UPDATED, DELETED, RESTORED
  - Добавить @PrePersist для changedAt
  - _Requirements: 29.1, 29.2_

- [x] 17.6 Создать Reminder entity
  - Добавить поля: id, event, reminderType, customMinutes, reminderTime, sent, sentAt
  - Создать ENUM ReminderType с всеми типами напоминаний
  - Настроить @ManyToOne связь с Event
  - _Requirements: 23.3_

- [x] 17.7 Обновить Event entity
  - Добавить поля: endTime, isPersonal, seriesId, completionNote, deletedAt, completedAt
  - Добавить значения COMPLETED и DELETED в EventStatus enum
  - Добавить @OneToMany связи: attachments, comments, checklistItems, reminders
  - _Requirements: 26.2, 32.1, 25.3, 19.2_

- [x] 18. Создание новых Repository интерфейсов
- [x] 18.1 Создать AttachmentRepository
  - Extends JpaRepository<Attachment, Long>
  - Добавить метод findByEventIdOrderByUploadedAtAsc
  - _Requirements: 20.4_

- [x] 18.2 Создать CommentRepository
  - Extends JpaRepository<Comment, Long>
  - Добавить метод findByEventIdOrderByCreatedAtAsc
  - _Requirements: 21.4_

- [x] 18.3 Создать ChecklistItemRepository
  - Extends JpaRepository<ChecklistItem, Long>
  - Добавить метод findByEventIdOrderByPositionAsc
  - _Requirements: 22.4_

- [x] 18.4 Создать RecurrenceRuleRepository
  - Extends JpaRepository<RecurrenceRule, Long>
  - Добавить метод findBySeriesId
  - _Requirements: 27.6_

- [x] 18.5 Создать EventHistoryRepository
  - Extends JpaRepository<EventHistory, Long>
  - Добавить метод findByEventIdOrderByChangedAtDesc
  - _Requirements: 29.4_

- [x] 18.6 Создать ReminderRepository
  - Extends JpaRepository<Reminder, Long>
  - Добавить метод findBySentFalseAndReminderTimeBetween
  - _Requirements: 23.4_

- [x] 18.7 Обновить EventRepository
  - Добавить метод findByUserIdAndStatusOrderByDeletedAtDesc для корзины
  - Добавить метод findByStatusAndDeletedAtBefore для очистки корзины
  - Добавить метод findExpiredActiveEvents для автозавершения
  - Добавить метод searchByTitleOrDescription для поиска
  - Добавить методы для фильтрации по типу события (семейные/персональные)
  - Добавить метод findBySeriesIdAndStatus для повторяющихся событий
  - Добавить методы count для статистики
  - _Requirements: 19.4, 28.3, 28.5, 27.7, 31.3_

- [x] 19. Реализация AttachmentService
- [x] 19.1 Создать AttachmentService
  - Реализовать метод saveAttachment с проверкой размера файла (макс 20 МБ)
  - Реализовать метод getEventAttachments
  - Реализовать метод deleteAttachment с проверкой прав доступа
  - Добавить логирование операций
  - Создать исключения: FileSizeExceededException, AttachmentNotFoundException
  - _Requirements: 20.2, 20.3, 20.4, 20.6_

- [ ]* 19.2 Написать unit тесты для AttachmentService
  - Тест сохранения вложения с валидным размером
  - Тест отклонения файла > 20 МБ
  - Тест получения вложений события
  - Тест удаления вложения создателем
  - Тест отклонения удаления чужого вложения
  - _Requirements: 20.2, 20.6_

- [ ] 20. Реализация CommentService
- [x] 20.1 Создать CommentService



  - Реализовать метод addComment
  - Реализовать метод getEventComments
  - Реализовать notifyFamilyAboutComment для семейных событий
  - Добавить логирование
  - _Requirements: 21.2, 21.3, 21.4, 21.5_

- [ ]* 20.2 Написать unit тесты для CommentService
  - Тест добавления комментария
  - Тест получения комментариев события
  - Тест отправки уведомлений семье
  - Тест отсутствия уведомлений для персональных событий
  - _Requirements: 21.3, 21.5_

- [ ] 21. Реализация ChecklistService
- [x] 21.1 Создать ChecklistService


  - Реализовать метод createChecklist
  - Реализовать метод toggleItemCompletion
  - Реализовать метод getEventChecklist
  - Реализовать метод isChecklistComplete
  - _Requirements: 22.2, 22.3, 22.4, 22.5, 22.6_

- [ ]* 21.2 Написать unit тесты для ChecklistService
  - Тест создания чек-листа
  - Тест отметки пункта выполненным
  - Тест проверки завершенности чек-листа
  - Тест получения чек-листа события
  - _Requirements: 22.3, 22.5, 22.6_

- [ ] 22. Реализация RecurrenceService
- [x] 22.1 Создать RecurrenceService


  - Реализовать метод createRecurringEvent
  - Реализовать метод updateSeries
  - Реализовать метод deleteSeries
  - Реализовать приватные методы: shouldCreateOccurrence, getNextOccurrenceDate, isExcludedDate
  - Добавить логику обработки дней недели
  - _Requirements: 27.1, 27.2, 27.3, 27.4, 27.5, 27.6, 27.7, 27.8, 27.9_

- [ ]* 22.2 Написать unit тесты для RecurrenceService
  - Тест создания ежедневного повторения
  - Тест создания еженедельного повторения
  - Тест создания месячного повторения
  - Тест ограничения по дате окончания
  - Тест ограничения по количеству повторений
  - Тест обработки исключений дат
  - Тест обновления всей серии
  - Тест удаления всей серии
  - _Requirements: 27.2, 27.5, 27.7, 27.8, 27.9_

- [x] 23. Реализация TrashService


- [x] 23.1 Создать TrashService
  - Реализовать метод getUserTrash
  - Реализовать метод restoreEvent
  - Реализовать метод permanentlyDelete
  - Реализовать @Scheduled метод cleanupOldTrash (каждый день в 2:00)
  - _Requirements: 19.1, 19.2, 19.4, 19.5, 19.6_

- [ ]* 23.2 Написать unit тесты для TrashService
  - Тест получения удаленных событий
  - Тест восстановления события
  - Тест окончательного удаления
  - Тест автоматической очистки старых событий
  - _Requirements: 19.4, 19.5, 19.6_


- [ ] 24. Реализация EventHistoryService
- [x] 24.1 Создать EventHistoryService

  - Реализовать метод recordChange
  - Реализовать метод getEventHistory
  - Добавить логирование
  - _Requirements: 29.1, 29.2, 29.4_

- [ ]* 24.2 Написать unit тесты для EventHistoryService
  - Тест записи изменения
  - Тест получения истории события
  - Тест сохранения всех деталей изменения
  - _Requirements: 29.1, 29.2_

- [ ] 25. Реализация ReminderService
- [x] 25.1 Создать ReminderService


  - Реализовать метод createReminders
  - Реализовать метод createCustomReminder
  - Реализовать @Scheduled метод sendReminders (каждую минуту)
  - Реализовать приватные методы: calculateReminderTime, formatReminderMessage
  - Добавить логику отправки только создателю для персональных событий
  - _Requirements: 23.1, 23.2, 23.3, 23.4, 23.5, 23.6, 26.6_

- [ ]* 25.2 Написать unit тесты для ReminderService
  - Тест создания стандартных напоминаний
  - Тест создания кастомного напоминания
  - Тест расчета времени напоминания для каждого типа
  - Тест отправки напоминаний
  - Тест отправки только создателю для персональных событий
  - _Requirements: 23.3, 23.4, 26.6_

- [ ] 26. Реализация SearchService и StatisticsService
- [x] 26.1 Создать SearchService


  - Реализовать метод searchEvents
  - Реализовать метод filterEvents с ENUM EventFilter
  - Добавить логирование
  - _Requirements: 28.3, 28.4, 28.5_

- [x] 26.2 Создать StatisticsService


  - Реализовать метод getMonthlyStatistics
  - Создать класс EventStatistics с полями статистики
  - _Requirements: 31.3, 31.4_

- [ ]* 26.3 Написать unit тесты для SearchService и StatisticsService
  - Тест поиска событий по тексту
  - Тест фильтрации по типу события
  - Тест подсчета статистики за месяц
  - _Requirements: 28.3, 28.5, 31.3_

- [x] 27. Реализация EventCompletionScheduler



- [x] 27.1 Создать EventCompletionScheduler
  - Аннотировать @Component
  - Реализовать @Scheduled метод completeExpiredEvents (каждые 10 минут)
  - Реализовать sendCompletionNotification
  - Реализовать createCompletionKeyboard с inline-кнопкой "Добавить заметку"
  - _Requirements: 25.1, 25.2, 25.5_

- [ ]* 27.2 Написать unit тесты для EventCompletionScheduler
  - Тест автоматического завершения событий
  - Тест отправки уведомления о завершении
  - _Requirements: 25.1, 25.2_

- [ ] 28. Обновление EventService
- [x] 28.1 Обновить EventService для новых функций
  - Добавить поддержку is_personal при создании события
  - Добавить поддержку end_time для интервалов
  - Обновить deleteEvent для перемещения в корзину (статус DELETED)
  - Добавить интеграцию с EventHistoryService для записи изменений
  - Добавить метод addCompletionNote
  - _Requirements: 26.2, 32.1, 19.1, 18.5, 25.3_

- [ ]* 28.2 Обновить тесты EventService
  - Тест создания персонального события
  - Тест создания события с интервалом
  - Тест перемещения события в корзину
  - Тест добавления заметки к завершенному событию
  - _Requirements: 26.2, 32.1, 19.1, 25.3_

- [ ] 29. Создание новых Command Handlers
- [x] 29.1 Создать TodayCommandHandler
  - Реализовать отображение событий на текущий день
  - Использовать Markdown форматирование
  - _Requirements: 28.1_

- [x] 29.2 Создать WeekCommandHandler
  - Реализовать отображение событий на неделю с группировкой по дням
  - Использовать Markdown форматирование
  - _Requirements: 28.2_

- [x] 29.3 Создать SearchCommandHandler
  - Запросить текст для поиска
  - Вызвать SearchService.searchEvents
  - Отобразить результаты
  - _Requirements: 28.3, 28.4_

- [x] 29.4 Создать FilterCommandHandler
  - Показать inline-меню фильтров
  - Обработать callback queries для фильтрации
  - Вызвать SearchService.filterEvents
  - _Requirements: 28.5_

- [x] 29.5 Создать TrashCommandHandler
  - Вызвать TrashService.getUserTrash
  - Показать список с inline-кнопками "Восстановить" и "Удалить навсегда"
  - Обработать callback queries
  - _Requirements: 19.4_

- [x] 29.6 Создать StatsCommandHandler
  - Вызвать StatisticsService.getMonthlyStatistics
  - Отформатировать и отобразить статистику
  - _Requirements: 31.3_

- [ ]* 29.7 Написать unit тесты для новых handlers
  - Тест TodayCommandHandler
  - Тест WeekCommandHandler
  - Тест SearchCommandHandler
  - Тест FilterCommandHandler
  - Тест TrashCommandHandler
  - Тест StatsCommandHandler
  - _Requirements: 28.1, 28.2, 28.3, 28.5, 19.4, 31.3_

- [ ] 30. Обновление KeyboardService
- [x] 30.1 Добавить методы для новых inline-клавиатур
  - Реализовать createEventTypeSelectionKeyboard (Семейное/Персональное)
  - Реализовать createEditEventMenuKeyboard (Изменить дату/время/название/описание)
  - Реализовать createReminderSettingsKeyboard (типы напоминаний)
  - Реализовать createRecurrenceMenuKeyboard (настройки повторения)
  - Реализовать createSeriesActionKeyboard (Изменить только это/всю серию)
  - Реализовать createDateActionsKeyboard (Посмотреть события/Создать новое)
  - Реализовать createAttachmentKeyboard (Прикрепить файл)
  - Реализовать createChecklistKeyboard (Добавить чек-лист)
  - Реализовать createCommentKeyboard (Добавить комментарий)
  - _Requirements: 26.1, 18.1, 23.2, 27.2, 27.7, 17.3, 20.1, 22.1, 21.1_

- [x] 30.2 Обновить createCalendarKeyboard для подсветки текущей даты
  - Добавить эмодзи "📍" для текущей даты
  - Добавить счетчик событий к дате (например, "5📌А(3)")
  - _Requirements: 31.1, 31.5_

- [ ]* 30.3 Написать unit тесты для новых методов KeyboardService
  - Тест создания клавиатуры выбора типа события
  - Тест создания меню редактирования
  - Тест создания меню напоминаний
  - Тест создания меню повторений
  - Тест подсветки текущей даты
  - _Requirements: 26.1, 18.1, 23.2, 27.2, 31.1_

- [x] 31. Обновление CallbackQueryHandler
- [x] 31.1 Расширить UpdateProcessor для новых callback queries
  - Добавить обработку event_type_ (выбор типа события)
  - Добавить обработку edit_field_ (редактирование полей)
  - Добавить обработку reminder_ (настройка напоминаний)
  - Добавить обработку recurrence_ (настройка повторений)
  - Добавить обработку series_action_ (действия с серией)
  - Добавить обработку date_actions_ (действия с датой)
  - Добавить обработку attach_file_ (вложения)
  - Добавить обработку checklist_ (чек-листы)
  - Добавить обработку comment_ (комментарии)
  - Добавить обработку trash_ (корзина)
  - Добавить обработку add_completion_note_ (заметка к завершенному)
  - Добавить интеграцию FilterCommandHandler, TrashCommandHandler, SearchCommandHandler
  - _Requirements: 26.1, 18.1, 23.2, 27.2, 27.7, 17.3, 20.1, 22.1, 21.1, 19.4, 25.2_

- [ ]* 31.2 Написать unit тесты для обновленного CallbackQueryHandler
  - Тест обработки выбора типа события
  - Тест обработки редактирования полей
  - Тест обработки настройки напоминаний
  - Тест обработки настройки повторений
  - Тест обработки действий с серией
  - _Requirements: 26.1, 18.1, 23.2, 27.2, 27.7_

- [ ] 32. Реализация быстрого создания из текста
- [x] 32.1 Обновить UpdateProcessor для распознавания текстовых сообщений


  - Добавить метод parseEventFromText
  - Реализовать регулярные выражения для парсинга формата "Событие: [название] Дата: [дата] Время: [время]"
  - Показать inline-кнопку подтверждения с предпросмотром
  - _Requirements: 30.1, 30.2, 30.3_

- [x] 32.2 Создать TextEventParser утилиту


  - Реализовать методы парсинга даты и времени
  - Добавить валидацию распознанных параметров
  - _Requirements: 30.1_

- [ ]* 32.3 Написать unit тесты для парсинга текста
  - Тест распознавания корректного формата
  - Тест обработки некорректного формата
  - Тест парсинга различных форматов дат
  - _Requirements: 30.1, 30.4_

- [ ] 33. Реализация контекстных подсказок
- [x] 33.1 Создать ContextualHintsService


  - Реализовать метод analyzeEventTitle
  - Добавить словари ключевых слов для разных типов событий
  - Реализовать метод getSuggestedActions
  - _Requirements: 24.1, 24.2, 24.3, 24.4_

- [ ]* 33.2 Написать unit тесты для ContextualHintsService
  - Тест распознавания "день рождения" -> список подарков
  - Тест распознавания "поездка" -> прикрепить билеты
  - Тест распознавания "встреча" -> повестка дня
  - _Requirements: 24.1, 24.2, 24.3_

- [x] 34. Реализация еженедельной сводки
- [x] 34.1 Создать WeeklySummaryScheduler
  - Аннотировать @Component
  - Реализовать @Scheduled метод sendWeeklySummary (каждое воскресенье в 20:00)
  - Получить события на следующую неделю для каждой семьи
  - Отформатировать и отправить сводку всем членам семьи
  - _Requirements: 28.6_

- [ ]* 34.2 Написать unit тесты для WeeklySummaryScheduler
  - Тест формирования еженедельной сводки
  - Тест отправки сводки всем членам семьи
  - _Requirements: 28.6_

- [ ] 35. Обновление UpdateProcessor для обработки файлов
- [x] 35.1 Добавить обработку файлов, документов и изображений


  - Проверить наличие активного черновика или контекста редактирования
  - Извлечь file_id, имя файла, тип и размер
  - Вызвать AttachmentService.saveAttachment
  - Отправить подтверждение
  - _Requirements: 20.2_

- [ ]* 35.2 Написать unit тесты для обработки файлов
  - Тест сохранения документа
  - Тест сохранения изображения
  - Тест отклонения файла > 20 МБ
  - _Requirements: 20.2, 20.6_

- [ ]* 36. Финальная интеграция и тестирование
- [ ]* 36.1 Интеграционные тесты с Testcontainers
  - Тест полного цикла создания персонального события
  - Тест полного цикла создания повторяющегося события
  - Тест добавления вложения к событию
  - Тест добавления комментария и уведомления семьи
  - Тест создания и выполнения чек-листа
  - Тест настройки и отправки напоминаний
  - Тест удаления в корзину и восстановления
  - Тест автоматического завершения события
  - Тест поиска и фильтрации событий
  - Тест истории изменений

- [x] 36.2 Обновить документацию


  - Обновить README.md с описанием новых функций
  - Добавить описание новых команд (/today, /week, /search, /filter, /trash, /stats)
  - Добавить примеры использования новых функций
  - Обновить архитектурную диаграмму с новыми сервисами и handlers
  - Обновить ER-диаграмму с новыми таблицами
  - Добавить описание всех новых таблиц БД
  - Документировать расширенные функции (персональные события, вложения, комментарии, чек-листы, повторения, корзина)
  - _Requirements: 12.1, 12.3_

- [x] 36.3 Checkpoint - Финальная проверка





  - Запустить все unit и integration тесты
  - Проверить покрытие кода (цель > 70%)
  - Выполнить полный цикл: docker-compose up, тестирование всех функций, docker-compose down
  - Проверить логи на наличие ошибок
  - Проверить работу всех scheduled tasks
  - Убедиться в корректной работе всех inline-клавиатур
  - Спросить пользователя, если возникают вопросы
