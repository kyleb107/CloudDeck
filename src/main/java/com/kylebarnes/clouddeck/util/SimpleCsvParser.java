package com.kylebarnes.clouddeck.util;

import java.util.ArrayList;
import java.util.List;

public final class SimpleCsvParser {

    private SimpleCsvParser() {
    }

    public static List<String> parseLine(String line) {
        List<String> columns = new ArrayList<>();
        if (line == null) {
            return columns;
        }

        StringBuilder current = new StringBuilder();
        boolean inQuotes = false;

        for (int index = 0; index < line.length(); index++) {
            char currentChar = line.charAt(index);

            if (currentChar == '"') {
                if (inQuotes && index + 1 < line.length() && line.charAt(index + 1) == '"') {
                    current.append('"');
                    index++;
                } else {
                    inQuotes = !inQuotes;
                }
            } else if (currentChar == ',' && !inQuotes) {
                columns.add(current.toString());
                current.setLength(0);
            } else {
                current.append(currentChar);
            }
        }

        columns.add(current.toString());
        return columns;
    }
}
