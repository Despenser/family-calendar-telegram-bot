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
  - Добавить индексы для оптимизации запросов
  - Добавить foreign keys и constraints
  - _Requirements: 11.1, 11.2, 11.3, 11.4_

- [x] 2.2 Создать JPA Entity классы





  - Создать Family entity с полями id, name, members, created_at
  - Создать User entity с полями id, telegram_id, username, first_name, family, created_at
  - Создать Event entity с полями id, user, family, title, description, event_date, event_time, notified, created_at
  - Настроить relationships (@ManyToOne, @OneToMany)
  - Добавить @PrePersist для автоматического created_at
  - _Requirements: 11.2, 11.3, 11.4_

- [x] 2.3 Создать Spring Data JPA репозитории





  - Создать FamilyRepository extends JpaRepository
  - Создать UserRepository с методом findByTelegramId
  - Создать EventRepository с методами findByFamilyIdAndEventDateBetween, findByUserIdOrderByEventDateAsc
  - Добавить custom query для поиска событий для уведомлений
  - _Requirements: 11.1_


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




- [x] 8.1 Реализовать AddEventCommandHandler





  - Аннотировать @Component
  - Реализовать диалог для ввода: даты, времени, описания
  - Использовать conversation state (можно через Map в памяти или БД)
  - Валидировать формат даты и времени
  - Вызывать EventService.createEvent()
  - Отправлять подтверждение с деталями события
  - _Requirements: 4.1, 4.2, 4.3, 4.4, 4.5_

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

- [x] 8.4 Написать unit тесты для Event handlers


  - Тест AddEventCommandHandler с валидными данными
  - Тест AddEventCommandHandler с датой в прошлом
  - Тест UpcomingEventsCommandHandler с событиями
  - Тест UpcomingEventsCommandHandler без событий
  - Тест MyEventsCommandHandler
  - _Requirements: 4.2, 5.1, 7.1_


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
- [ ] 11.1 Протестировать Docker Compose локально
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
