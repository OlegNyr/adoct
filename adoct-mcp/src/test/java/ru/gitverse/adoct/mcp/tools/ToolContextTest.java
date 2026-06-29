package ru.gitverse.adoct.mcp.tools;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

/** Юнит-тест извлечения ключа пространства {@link ToolContext#spaceKeyOf}. */
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
}
