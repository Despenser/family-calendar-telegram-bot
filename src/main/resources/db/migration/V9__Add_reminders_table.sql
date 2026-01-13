-- ============================================================================
-- Миграция V9: Создание таблицы напоминаний
-- ============================================================================
-- Описание: Создание ENUM reminder_type и таблицы reminders для
--           хранения гибких настроек напоминаний о событиях
-- Требования: 23.3, 23.6
-- ============================================================================

-- ----------------------------------------------------------------------------
-- ENUM reminder_type: Типы напоминаний
-- ----------------------------------------------------------------------------
CREATE TYPE reminder_type AS ENUM (
    'morning_of_day',
    'evening_before',
    'one_hour_before',
    'ten_minutes_before',
    'custom'
);

COMMENT ON TYPE reminder_type IS 
    'Тип напоминания: morning_of_day (утром в день события), evening_before (вечером накануне), one_hour_before (за 1 час), ten_minutes_before (за 10 минут), custom (свое время)';

-- ----------------------------------------------------------------------------
-- Таблица reminders: Хранит настройки напоминаний для событий
-- ----------------------------------------------------------------------------
CREATE TABLE reminders (
    id BIGSERIAL PRIMARY KEY,
    event_id BIGINT NOT NULL,
    reminder_type reminder_type NOT NULL,
    custom_minutes INTEGER,
    reminder_time TIMESTAMP NOT NULL,
    sent BOOLEAN NOT NULL DEFAULT FALSE,
    sent_at TIMESTAMP,
    
    -- Constraints
    CONSTRAINT reminders_event_fk FOREIGN KEY (event_id) 
        REFERENCES events(id) ON DELETE CASCADE,
    CONSTRAINT reminders_custom_minutes_positive CHECK (
        custom_minutes IS NULL OR custom_minutes > 0
    ),
    CONSTRAINT reminders_custom_logic CHECK (
        (reminder_type = 'custom' AND custom_minutes IS NOT NULL) OR
        (reminder_type != 'custom' AND custom_minutes IS NULL)
    ),
    CONSTRAINT reminders_sent_logic CHECK (
        (sent = FALSE AND sent_at IS NULL) OR
        (sent = TRUE AND sent_at IS NOT NULL)
    )
);

-- Комментарии для таблицы reminders
COMMENT ON TABLE reminders IS 
    'Напоминания о событиях с гибкими настройками времени отправки';

COMMENT ON COLUMN reminders.id IS 
    'Уникальный идентификатор напоминания';

COMMENT ON COLUMN reminders.event_id IS 
    'Ссылка на событие, для которого настроено напоминание';

COMMENT ON COLUMN reminders.reminder_type IS 
    'Тип напоминания: morning_of_day, evening_before, one_hour_before, ten_minutes_before, custom';

COMMENT ON COLUMN reminders.custom_minutes IS 
    'Количество минут до события для custom типа (обязательно для custom, NULL для остальных)';

COMMENT ON COLUMN reminders.reminder_time IS 
    'Рассчитанное время отправки напоминания';

COMMENT ON COLUMN reminders.sent IS 
    'Флаг отправки напоминания: true - отправлено, false - ожидает отправки';

COMMENT ON COLUMN reminders.sent_at IS 
    'Дата и время фактической отправки напоминания';

-- ----------------------------------------------------------------------------
-- Индексы для таблицы reminders
-- ----------------------------------------------------------------------------

-- Индекс для быстрого получения всех напоминаний события
CREATE INDEX idx_reminders_event_id ON reminders(event_id);

COMMENT ON INDEX idx_reminders_event_id IS 
    'Индекс для быстрого получения всех напоминаний конкретного события';

-- Составной индекс для поиска напоминаний, готовых к отправке
CREATE INDEX idx_reminders_time_sent ON reminders(reminder_time, sent) 
    WHERE sent = FALSE;

COMMENT ON INDEX idx_reminders_time_sent IS 
    'Индекс для быстрого поиска неотправленных напоминаний по времени (используется планировщиком)';

-- Составной индекс для получения напоминаний события с сортировкой по времени
CREATE INDEX idx_reminders_event_time ON reminders(event_id, reminder_time);

COMMENT ON INDEX idx_reminders_event_time IS 
    'Индекс для получения напоминаний события с сортировкой по времени отправки';

-- ============================================================================
-- Конец миграции V9
-- ============================================================================
