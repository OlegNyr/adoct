package ru.gitverse.adoct.mcp;

/**
 * Именованный шаблон задачи — свободный текст. Сервер не парсит {@code body}: его читает LLM и сама
 * формирует вызов {@code jira_create_issue}. Конфигурируется в настройках, отдаётся {@code jira_list_templates}.
 *
 * @param name имя шаблона (например Story, Bug)
 * @param body произвольный текст шаблона (поля, чек-листы, Definition of Done и т.п.)
 */
public record Template(String name, String body) {
}
