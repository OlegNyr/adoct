-- Создаёт отдельные БД для Confluence и Jira на общем PostgreSQL.
-- Confluence требует кодировку UTF8 и C-сортировку (требование Atlassian).

CREATE DATABASE confluence
    WITH OWNER = atlassian
    ENCODING = 'UTF8'
    LC_COLLATE = 'C'
    LC_CTYPE = 'C'
    TEMPLATE = template0;

CREATE DATABASE jira
    WITH OWNER = atlassian
    ENCODING = 'UTF8'
    LC_COLLATE = 'C'
    LC_CTYPE = 'C'
    TEMPLATE = template0;
