package ru.gitverse.adoct.parser.macros;

import ru.gitverse.adoct.parser.PrintWriterReturn;
import ru.gitverse.adoct.parser.doc.ParseDispatcher;

import java.util.Set;

public abstract class AbstractParseMacros implements ParseMacros {
    protected ParseDispatcher dispatcher;
    protected PrintWriterReturn printer;
    private final Set<String> names;

    public AbstractParseMacros(String... names) {
        this.names = Set.of(names);
    }

    @Override
    public Set<String> getMacrosName() {
        return names;
    }

    @Override
    public void setPrinter(PrintWriterReturn printer) {
        this.printer = printer;
    }

    @Override
    public void setDispatcher(ParseDispatcher dispatcher) {
        this.dispatcher = dispatcher;
    }
}
