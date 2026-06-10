package ru.gitverse.adoct.parser;

import lombok.Getter;

import java.io.PrintWriter;
import java.io.Writer;

public class PrintWriterReturn extends PrintWriter {
    @Getter
    boolean lastReturn;

    public PrintWriterReturn(Writer out) {
        super(out);
    }

    public PrintWriterReturn(Writer out, boolean autoFlush) {
        super(out, autoFlush);
    }

    public PrintWriter format(String format, Object... args) {
        super.format(format, args);
        lastReturn = false;
        return this;
    }

    public void print(char c) {
        super.print(c);
        lastReturn = false;

    }

    public void write(String s, int off, int len) {
        super.write(s, off, len);
        lastReturn = false;
    }

    @Override
    public void println() {
        lastReturn = true;
        super.println();
    }

    @Override
    public void println(boolean x) {
        lastReturn = true;
        super.println(x);
    }

    @Override
    public void println(char x) {
        lastReturn = true;
        super.println(x);
    }

    @Override
    public void println(int x) {
        lastReturn = true;
        super.println(x);
    }

    @Override
    public void println(long x) {
        lastReturn = true;
        super.println(x);
    }

    @Override
    public void println(float x) {
        lastReturn = true;
        super.println(x);
    }

    @Override
    public void println(double x) {
        lastReturn = true;
        super.println(x);
    }

    @Override
    public void println(char[] x) {
        lastReturn = true;
        super.println(x);
    }

    @Override
    public void println(String x) {
        lastReturn = true;
        super.println(x);
    }

    @Override
    public void println(Object x) {
        lastReturn = true;
        super.println(x);
    }

    public void setLastReturn(boolean b) {
        lastReturn = b;
    }

    public void printHeader(int number) {
        for (int i = 0; i < number + 1; i++) {
            this.print('=');
        }
    }
}
