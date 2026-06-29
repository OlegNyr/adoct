package ru.gitverse.adoct.mcp;

/**
 * Конфигурация типа задачи: шаблон оформления и диаграмма состояний — оба привязаны к типу задачи
 * (Story, Bug, Task…). Сервер не парсит {@code body}: его читает LLM и сама формирует вызов
 * {@code jira_create_issue}. Конфигурируется в настройках; {@code jira_list_templates} отдаёт
 * шаблоны, {@code jira_get_workflow} — диаграммы состояний.
 *
 * @param issueType тип задачи Jira (например Story, Bug)
 * @param body      многострочный текст шаблона (поля, чек-листы, Definition of Done и т.п.)
 * @param workflow  диаграмма состояний/переходов для этого типа (PlantUML state); может быть пустой
 */
public record Template(String issueType, String body, String workflow) {

    /** Шаблон без диаграммы состояний. */
    public Template(String issueType, String body) {
        this(issueType, body, "");
    }
}
