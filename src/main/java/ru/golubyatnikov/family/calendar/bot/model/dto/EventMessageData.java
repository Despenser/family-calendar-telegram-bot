package ru.golubyatnikov.family.calendar.bot.model.dto;

import org.telegram.telegrambots.meta.api.objects.replykeyboard.InlineKeyboardMarkup;

/**
 * DTO для хранения данных сообщения о событии.
 *
 * @param messageText Текст сообщения о событии с заголовком.
 * @param keyboard Клавиатура для сообщения о событии.
 *
 * @author Golubyatnikov Aleksey
 * @since 2026-02-26
 */
public record EventMessageData(String messageText, InlineKeyboardMarkup keyboard) { }
