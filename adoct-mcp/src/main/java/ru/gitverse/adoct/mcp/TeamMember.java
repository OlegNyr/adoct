package ru.gitverse.adoct.mcp;

/**
 * Участник команды (ростер) — для привязки задач к людям. Конфигурируется в настройках плагина,
 * отдаётся тулом {@code jira_list_team}.
 *
 * @param username    логин в Jira (для назначения)
 * @param displayName отображаемое имя
 * @param role        роль в команде (например Backend, QA, Lead)
 */
public record TeamMember(String username, String displayName, String role) {
}
