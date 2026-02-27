package ru.golubyatnikov.family.calendar.bot.util;

/**
 * Централизованное хранилище всех эмодзи, используемых в приложении.
 * Единый источник истины для всех эмодзи.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-24
 */
public final class EmojiConstants {

    private EmojiConstants() {
        throw new UnsupportedOperationException("Это утилитный класс и не может быть инстанцирован");
    }

    /**
     * Эмодзи для команд и навигации.
     */
    public static final class Commands {
        private Commands() {
            throw new UnsupportedOperationException("Это утилитный класс и не может быть инстанцирован");
        }

        public static final String START = "🚀";
        public static final String HELP = "📚";
        public static final String CALENDAR = "🗓️";
        public static final String ADD_EVENT = "➕";
        public static final String MY_EVENTS = "📋";
        public static final String TODAY = "📍";
        public static final String WEEK = "📅";
        public static final String MONTH = "📆";
        public static final String SEARCH = "🔍";
        public static final String FILTER = "🫧";
        public static final String STATS = "📊";
        public static final String TRASH = "🗑️";
        public static final String BACK = "🔙";
    }

    /**
     * Эмодзи для статусов и результатов операций.
     */
    public static final class Status {
        private Status() {
            throw new UnsupportedOperationException("Это утилитный класс и не может быть инстанцирован");
        }

        public static final String SUCCESS = "✅";
        public static final String ERROR = "❌";
        public static final String WARNING = "⚠️";
        public static final String INFO = "ℹ️";
        public static final String COMPLETED = "✅";
        public static final String CANCELLED = "❌";
    }

    /**
     * Эмодзи для информации о событии.
     */
    public static final class Event {
        private Event() {
            throw new UnsupportedOperationException("Это утилитный класс и не может быть инстанцирован");
        }

        public static final String TITLE = "📌";
        public static final String DATE = "📅";
        public static final String TIME = "🕐";
        public static final String DESCRIPTION = "📝";
        public static final String NOTE = "📄";
        public static final String CREATION = "📋";
    }

    /**
     * Эмодзи для типов событий и пользователей.
     */
    public static final class EventType {
        private EventType() {
            throw new UnsupportedOperationException("Это утилитный класс и не может быть инстанцирован");
        }

        public static final String FAMILY = "👨‍👩‍👧‍👦";
        public static final String PERSONAL = "👤";
        public static final String CREATOR = "👤";
    }

    /**
     * Эмодзи для действий с UI.
     */
    public static final class Actions {
        private Actions() {
            throw new UnsupportedOperationException("Это утилитный класс и не может быть инстанцирован");
        }

        public static final String EDIT = "✏️";
        public static final String DELETE = "🗑️";
        public static final String RESTORE = "♻️";
        public static final String ATTACHMENT = "📎";
        public static final String COMPLETE = "✅";
        public static final String SKIP = "⏭️";
        public static final String CANCEL = "✖️";
        public static final String REPEAT = "🔄";
    }

    /**
     * Эмодзи для напоминаний.
     */
    public static final class Reminders {
        private Reminders() {
            throw new UnsupportedOperationException("Это утилитный класс и не может быть инстанцирован");
        }

        public static final String ENABLED = "🔔";
        public static final String DISABLED = "🔕";
    }

    /**
     * Эмодзи для временных меток и периодов.
     */
    public static final class Time {
        private Time() {
            throw new UnsupportedOperationException("Это утилитный класс и не может быть инстанцирован");
        }

        public static final String CLOCK = "⏰";
        public static final String UPCOMING = "🔜";
        public static final String PIN = "📍";
    }

    /**
     * Прочие эмодзи.
     */
    public static final class Misc {
        private Misc() {
            throw new UnsupportedOperationException("Это утилитный класс и не может быть инстанцирован");
        }

        public static final String LOCK = "🔒";
        public static final String SEPARATOR = "─────────────────────";
        public static final String ALL_EVENTS = "📋";
        public static final String WAVE = "👋";
        public static final String AI = "🤖";
        public static final String NUMBER_1 = "1️⃣";
        public static final String NUMBER_2 = "2️⃣";
        public static final String NUMBER_3 = "3️⃣";
        public static final String NUMBER_4 = "4️⃣";
        public static final String ARROW_RIGHT = "➡️";
        public static final String ARROW_LEFT = "⬅️";
        public static final String EYE = "👀";
        public static final String CHART = "📊";
        public static final String LIGHTNING = "⚡";
        public static final String FIRE = "🔥";
        public static final String MOON = "🌙";
        public static final String PARTY = "🎉";
        public static final String BULLET = "•";
    }

    /**
     * Эмодзи для типов файлов.
     */
    public static final class FileTypes {
        private FileTypes() {
            throw new UnsupportedOperationException("Это утилитный класс и не может быть инстанцирован");
        }

        public static final String PHOTO = "🖼️";
        public static final String VIDEO = "🎥";
        public static final String AUDIO = "🎵";
        public static final String DOCUMENT = "📄";
    }
}
