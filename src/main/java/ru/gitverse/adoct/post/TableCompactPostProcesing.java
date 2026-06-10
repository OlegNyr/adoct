package ru.gitverse.adoct.post;

import ru.gitverse.adoct.PostProcesing;

import java.util.ArrayList;
import java.util.List;

public class TableCompactPostProcesing implements PostProcesing {

    private static final String SEP = System.lineSeparator();

    @Override
    public String execute(String res) {
        List<String> lines = res.lines().toList();
        List<String> linesRes = new ArrayList<>();
        int i = 0;
        while (i < lines.size()) {
            String ln = getLn(lines, i);
            if (ln.equals("|")) {
                int j = i + 1;
                String next = null;
                while ((next = getLn(lines, j)) != null) {
                    if (next.isEmpty()) {
                        j++;
                        continue;
                    }
                    if (next.equals("|")) {
                        j++;
                        ln = ln + next;
                        continue;
                    }
                    if (next.startsWith("|===")
                        || next.startsWith("=")
                        || next.startsWith("-")
                        || next.startsWith("*")
                        || next.startsWith("[")
                        || next.startsWith("(")
                    ) {
                        break;
                    }

                    if (next.indexOf("|") > 0) {
                        break;
                    }
                    //все остальное добавим
                    ln = ln + next;
                    j++;
                    break;
                }
                if (next == null) {
                    linesRes.add(ln);
                    break;
                }
                i = j;
            } else {
                i++;
            }
            linesRes.add(ln);
        }


        return String.join(SEP, linesRes);
    }

    private static String getLn(List<String> lines, int i) {
        if (i > lines.size()) {
            return null;
        }
        return lines.get(i);
    }
}
