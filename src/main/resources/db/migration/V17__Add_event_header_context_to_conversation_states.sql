-- Добавление полей для хранения контекста шапки события
-- Используется для сохранения информации о шапке "Мои события" при навигации между экранами

ALTER TABLE conversation_states
ADD COLUMN event_has_my_events_header BOOLEAN DEFAULT NULL,
ADD COLUMN event_count_for_header INTEGER DEFAULT NULL;

-- Комментарии к новым полям
COMMENT ON COLUMN conversation_states.event_has_my_events_header IS 'Флаг наличия шапки "Мои события" у отображаемого события';
COMMENT ON COLUMN conversation_states.event_count_for_header IS 'Количество событий пользователя для формирования шапки "Мои события"';
