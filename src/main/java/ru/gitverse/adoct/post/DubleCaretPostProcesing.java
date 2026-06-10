package ru.gitverse.adoct.post;

import org.apache.commons.lang3.StringUtils;
import ru.gitverse.adoct.PostProcesing;

public class DubleCaretPostProcesing implements PostProcesing {

    private static final String SEP = System.lineSeparator();

    @Override
    public String execute(String res) {

        while (res.indexOf(SEP + SEP + SEP) > 0) {
            res = StringUtils.replaceEachRepeatedly(res, new String[]{SEP + SEP + SEP}, new String[]{SEP + SEP});
        }
        return res;
    }
}
