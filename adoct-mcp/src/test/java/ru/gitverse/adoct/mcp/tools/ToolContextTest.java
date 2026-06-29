package ru.gitverse.adoct.mcp.tools;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Юнит-тест извлечения ключа пространства/проекта {@link ToolContext#spaceKeyOf}/{@link ToolContext#projectKeyOf}. */
public class ToolContextTest {

    @Test
    public void spaceKeyOf_extractsFromDisplayUrl() {
        assertEquals("PLCHAT",
                ToolContext.spaceKeyOf("https://confluence.otpbank.ru/display/PLCHAT/Glossary"));
    }

    @Test
    public void spaceKeyOf_extractsFromSpacesUrl() {
        assertEquals("PLCHAT",
                ToolContext.spaceKeyOf("https://confluence.otpbank.ru/spaces/PLCHAT/pages/123"));
    }

    @Test
    public void spaceKeyOf_extractsFromQueryParam() {
        assertEquals("PLCHAT",
                ToolContext.spaceKeyOf("https://confluence.otpbank.ru/pages/viewpage.action?spaceKey=PLCHAT&title=X"));
    }

    @Test
    public void spaceKeyOf_passesThroughBareKey() {
        assertEquals("PLCHAT", ToolContext.spaceKeyOf("  PLCHAT  "));
    }

    @Test
    public void spaceKeyOf_emptyForNull() {
        assertEquals("", ToolContext.spaceKeyOf(null));
    }

    @Test
    public void projectKeyOf_extractsFromBrowseUrl() {
        assertEquals("ABC", ToolContext.projectKeyOf("https://jira.example.com/browse/ABC-123"));
    }

    @Test
    public void projectKeyOf_extractsFromProjectsUrl() {
        assertEquals("ABC", ToolContext.projectKeyOf("https://jira.example.com/projects/ABC/issues"));
    }

    @Test
    public void projectKeyOf_extractsFromQueryParam() {
        assertEquals("ABC",
                ToolContext.projectKeyOf("https://jira.example.com/secure/CreateIssue.jspa?projectKey=ABC&x=1"));
    }

    @Test
    public void projectKeyOf_stripsIssueSuffixFromBareKey() {
        assertEquals("ABC", ToolContext.projectKeyOf("ABC-42"));
    }

    @Test
    public void projectKeyOf_passesThroughBareKey() {
        assertEquals("ABC", ToolContext.projectKeyOf("  ABC  "));
        assertEquals("", ToolContext.projectKeyOf(null));
    }
}
