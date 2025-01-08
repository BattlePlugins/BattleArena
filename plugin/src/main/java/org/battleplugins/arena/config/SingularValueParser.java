package org.battleplugins.arena.config;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;

public final class SingularValueParser {

    public static ArgumentBuffer parseNamed(String contents, BraceStyle style, char separator) throws ParseException {
        return parse(contents, style, separator, true);
    }

    public static ArgumentBuffer parseUnnamed(String contents, BraceStyle style, char separator) throws ParseException {
        return parse(contents, style, separator, false);
    }

    private static ArgumentBuffer parse(String contents, BraceStyle style, char separator, boolean named) throws ParseException {
        ArgumentBuffer buffer = new ArgumentBuffer();
        if (named) {
            String[] split = contents.split(String.valueOf(new char[] { '\\', style.open }), 2);
            String dataType = split[0];

            buffer.push("root", dataType);

            if (split.length == 1) {
                // No more data to parse
                return buffer;
            }

            return parseNamed(buffer, split[1], style, separator);
        } else {
            return parseUnnamed(buffer, contents, style, separator);
        }
    }

    private static ArgumentBuffer parseNamed(ArgumentBuffer buffer, String contents, BraceStyle style, char separator) throws ParseException {
        contents = contents.substring(0, contents.lastIndexOf(style.close));

        for (String argument : parseArguments(contents, style, separator)) {
            String[] optionSplit = argument.split("=", 2);
            if (optionSplit.length != 2) {
                throw new ParseException("Invalid argument length! Expected arguments in the form of <key>=<value>, but got " + argument)
                        .cause(ParseException.Cause.INVALID_VALUE)
                        .context("Provided argument", argument)
                        .context("Contents", contents)
                        .userError();
            }

            buffer.push(optionSplit[0], optionSplit[1]);
        }

        return buffer;
    }

    private static ArgumentBuffer parseUnnamed(ArgumentBuffer buffer, String contents, BraceStyle style, char separator) throws ParseException {
        contents = contents.substring(1);
        contents = contents.substring(0, contents.length() - 1);

        int index = 0;
        for (String argument : parseArguments(contents, style, separator)) {
            buffer.push(Integer.toString(index++), argument);
        }

        return buffer;
    }

    private static List<String> parseArguments(String contents, BraceStyle style, char separator) {
        List<String> arguments = new ArrayList<>();
        StringBuilder builder = new StringBuilder();
        int depth = 0;
        for (char c : contents.toCharArray()) {
            if (c == style.open) {
                depth++;
            } else if (c == style.close) {
                depth--;
            }

            if (depth == 0 && c == separator) {
                arguments.add(builder.toString());
                builder = new StringBuilder();
            } else {
                builder.append(c);
            }
        }

        arguments.add(builder.toString());
        return arguments;
    }

    public static final class ArgumentBuffer {
        private final Queue<Argument> values;

        public ArgumentBuffer() {
            this(new ArrayDeque<>());
        }

        public ArgumentBuffer(Queue<Argument> values) {
            this.values = values;
        }

        public void push(String key, String value) {
            this.values.add(new Argument(key, value));
        }

        public boolean hasNext() {
            return !this.values.isEmpty();
        }

        public Argument pop() {
            return this.values.poll();
        }
    }

    public record Argument(String key, String value) {
    }

    public enum BraceStyle {
        CURLY('{', '}'),
        SQUARE('[', ']'),
        ROUND('(', ')');

        private final char open;
        private final char close;

        BraceStyle(char open, char close) {
            this.open = open;
            this.close = close;
        }
    }
}
