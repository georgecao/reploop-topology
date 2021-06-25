package org.reploop.topology.aliyun;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.CharacterIterator;
import java.text.StringCharacterIterator;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

public interface CsvFile<L extends Line> {
    char QUOTE = '"';
    char COMMA = ',';

    L newInstance();

    void collect(Integer idx, String element, Map<Integer, BiConsumer<L, String>> handlers);

    default List<L> loadCsvFile(String file) throws IOException {
        List<L> data = new ArrayList<>();
        List<String> lines = Files.readAllLines(Paths.get(file), StandardCharsets.UTF_8);
        Map<Integer, BiConsumer<L, String>> handlers = new HashMap<>();
        int headerLine = -1;
        for (int l = 0; l < lines.size(); l++) {
            String line = lines.get(l);
            L nl = null;
            List<String> elements = split(line);
            for (int i = 0; i < elements.size(); i++) {
                String val = elements.get(i).trim();
                // Try to define the Header line if we find the consumer handler for at least one column.
                if (headerLine == -1 && !handlers.isEmpty()) {
                    headerLine = l;
                }
                // Collect header if we know the header line, or we just try util we find the header line
                if (headerLine == -1 || headerLine == l) {
                    collect(i, val, handlers);
                    continue;
                }
                BiConsumer<L, String> consumer = handlers.get(i);
                // We should define header first, otherwise we just ignore data lines if the header line is not the first line.
                if (null != consumer) {
                    // data line, create new line instance
                    if (null == nl) {
                        nl = newInstance();
                    }
                    consumer.accept(nl, val);
                }
            }
            if (null != nl) {
                data.add(nl);
            }
        }
        return data;
    }

    /**
     * Split the line by {@link #COMMA}, support double-quoted string value.
     */
    default List<String> split(String line) {
        List<String> elements = new ArrayList<>();
        StringCharacterIterator it = new StringCharacterIterator(line);
        char expect = COMMA;
        int startIdx = it.getIndex();
        for (char c = it.first(); c != CharacterIterator.DONE; c = it.next()) {
            if (c == QUOTE && expect != QUOTE) {
                expect = QUOTE;
                continue;
            }
            if (c == expect && expect == QUOTE) {
                expect = COMMA;
                continue;
            }
            if (c == expect) {
                String e;
                if (startIdx == it.getIndex()) {
                    e = "";
                } else {
                    e = line.substring(startIdx, it.getIndex()).trim();
                    if (e.charAt(0) == QUOTE) {
                        e = e.substring(1, e.length() - 1);
                    }
                }
                elements.add(e);
                startIdx = it.getIndex() + 1;
            }
        }
        if (startIdx < line.length()) {
            elements.add(line.substring(startIdx).trim());
        }
        return elements;
    }
}
