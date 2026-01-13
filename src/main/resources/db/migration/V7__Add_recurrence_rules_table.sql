-- ============================================================================
-- Миграция V7: Создание таблицы правил повторения
-- ============================================================================
-- Описание: Создание ENUM frequency_type и таблицы recurrence_rules для
--           хранения правил повторения событий (ежедневно, еженедельно, ежемесячно)
-- Требования: 27.6
-- ============================================================================

-- ----------------------------------------------------------------------------
-- ENUM frequency_type: Типы частоты повторения
-- ----------------------------------------------------------------------------
CREATE TYPE frequency_type AS ENUM ('daily', 'weekly', 'monthly');

COMMENT ON TYPE frequency_type IS 
    'Тип частоты повторения события: daily (ежедневно), weekly (еженедельно), monthly (ежемесячно)';

-- ----------------------------------------------------------------------------
-- Таблица recurrence_rules: Хранит правила повторения событий
-- ----------------------------------------------------------------------------
CREATE TABLE recurrence_rules (
    id BIGSERIAL PRIMARY KEY,
    series_id VARCHAR(255) NOT NULL UNIQUE,
    frequency frequency_type NOT NULL,
    interval INTEGER NOT NULL DEFAULT 1,
    days_of_week VARCHAR(50),
    end_date DATE,
    occurrences INTEGER,
    exceptions TEXT,
    
    -- Constraints
    CONSTRAINT recurrence_rules_series_id_not_empty CHECK (LENGTH(TRIM(series_id)) > 0),
    CONSTRAINT recurrence_rules_interval_positive CHECK (interval > 0),
    CONSTRAINT recurrence_rules_occurrences_positive CHECK (occurrences IS NULL OR occurrences > 0),
    CONSTRAINT recurrence_rules_end_condition CHECK (
        end_date IS NOT NULL OR occurrences IS NOT NULL
    )
);

-- Комментарии для таблицы recurrence_rules
COMMENT ON TABLE recurrence_rules IS 
    'Правила повторения для серий событий с настройками частоты и ограничений';

COMMENT ON COLUMN recurrence_rules.id IS 
    'Уникальный идентификатор правила повторения';

COMMENT ON COLUMN recurrence_rules.series_id IS 
    'UUID серии событий (связь с events.series_id)';

COMMENT ON COLUMN recurrence_rules.frequency IS 
    'Частота повторения: daily, weekly, monthly';

COMMENT ON COLUMN recurrence_rules.interval IS 
    'Интервал повторения: каждые N дней/недель/месяцев (по умолчанию 1)';

COMMENT ON COLUMN recurrence_rules.days_of_week IS 
    'Дни недели для еженедельного повторения в формате "1,3,5" (1=Пн, 7=Вс)';

COMMENT ON COLUMN recurrence_rules.end_date IS 
    'Дата окончания повторений (опционально, если не указано occurrences)';

COMMENT ON COLUMN recurrence_rules.occurrences IS 
    'Количество повторений (опционально, если не указано end_date)';

COMMENT ON COLUMN recurrence_rules.exceptions IS 
    'Исключенные даты в формате "2025-01-15,2025-02-20" (даты, когда событие не создается)';

-- ----------------------------------------------------------------------------
-- Индексы для таблицы recurrence_rules
-- ----------------------------------------------------------------------------

-- Уникальный индекс для series_id (уже создан через UNIQUE constraint)
-- Дополнительный индекс для быстрого поиска правила по series_id
CREATE INDEX idx_recurrence_series_id ON recurrence_rules(series_id);

COMMENT ON INDEX idx_recurrence_series_id IS 
    'Индекс для быстрого поиска правила повторения по идентификатору серии';

-- ============================================================================
-- Конец миграции V7
-- ============================================================================
