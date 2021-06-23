package org.reploop.topology.parser;

import org.springframework.stereotype.Component;

import java.text.CharacterIterator;
import java.text.StringCharacterIterator;

import static org.reploop.topology.Constants.*;

@Component
public class ProcessParser {
    private static final String CB = "-Dcatalina.base";
    private static final String BIN = "bin";
    private static final String BOOTSTRAP = "org.apache.catalina.startup.Bootstrap";

    public RawProcess parse(String host, String line) {
        CharacterIterator it = new StringCharacterIterator(line);
        char prev = 0;
        int start = 0;
        int i = 0;
        RawProcess process = new RawProcess();
        for (char c = it.first(); c != CharacterIterator.DONE; c = it.next()) {
            boolean before = Character.isWhitespace(prev);
            boolean after = Character.isWhitespace(c);
            if (before && after) {
                continue;
            } else if (before) {
                start = it.getIndex();
                // This is the last one, it's CMD column as a whole.
                if (i == 7) {
                    break;
                }
            } else if (after) {
                String val = line.substring(start, it.getIndex());
                switch (i) {
                    case 0:
                        process.user = val;
                        break;
                    case 1:
                        process.pid = Integer.valueOf(val);
                        break;
                    case 2:
                        process.ppid = Integer.valueOf(val);
                        break;
                    case 7:
                        process.command = line.substring(start);
                        break;
                    default:
                        break;
                }
                i++;
            }
            prev = c;
        }
        // The last column as cmd
        process.command = shortCmd(line.substring(start));
        if (null != host) {
            process.host = host;
        }
        return process;
    }

    private String parentHome(String path) {
        String[] elements = path.split(SLASH);
        int j = elements.length - 1;
        if (j > 0) {
            if (BIN.equals(elements[j])) {
                j--;
            }
            return elements[j];
        }
        return null;
    }

    private String shortCmd(String cmd) {
        String projectName = null;
        String[] elements = cmd.split(WS);
        if (cmd.contains(JAVA)) {
            String prev = null;
            boolean javaCmd = false;
            int i = 0;
            for (; i < elements.length; i++) {
                String ele = elements[i];
                if (0 == i && ele.endsWith(JAVA)) {
                    javaCmd = true;
                    continue;
                }
                if (javaCmd) {
                    if (ele.startsWith(CB)) {
                        projectName = parentHome(ele.substring(CB.length() + 1));
                    }
                    boolean option = isOption(prev, ele);
                    if (!option && null != prev && prev.startsWith(OOM_CMD)) {
                        int j = i + 1;
                        for (; j < elements.length; j++) {
                            String next = elements[j];
                            if (next.equals(PROCESS_PARAM) || next.equals(JAR) || next.startsWith("-X") || next.startsWith("-D")) {
                                break;
                            }
                        }
                        if (j != elements.length) {
                            i = j;
                            option = true;
                        }
                    }
                    if (!option) {
                        break;
                    }
                }
                prev = ele;
            }
            if (javaCmd) {
                if (i < elements.length) {
                    String s = trimAfter(elements[i], COLON, COMMA, SEMICOLON);
                    if (s.equals(BOOTSTRAP) && null != projectName) {
                        return projectName;
                    }
                    return s;
                }
            }
        } else if (cmd.contains(PYTHON)) {
            int i = 0;
            boolean pythonCmd = false;
            for (; i < elements.length; i++) {
                String ele = elements[i];
                if (ele.contains(PYTHON)) {
                    pythonCmd = true;
                    continue;
                }
                if (pythonCmd) {
                    if (ele.startsWith(HYPHEN)) {
                        continue;
                    }
                    return ele;
                }
            }
        }
        int i = 0;
        // MySQL
        String exe = elements[i++];
        if (exe.equals("/bin/sh") || exe.equals("/bin/bash") || exe.equals("sh")) {
            for (; i < elements.length; i++) {
                String c = elements[i];
                if (!c.startsWith(HYPHEN)) {
                    exe = c;
                    break;
                }
            }
        }
        return trimAfter(exe, COLON, COMMA, SEMICOLON);
    }

    private boolean isOption(String prev, String ele) {
        return ele.startsWith(HYPHEN)
                || ele.startsWith(PERCENT_SIGN)
                || CP.equals(prev)
                || CLASSPATH.equals(prev);
    }

    private String trimAfter(String val, String... seps) {
        for (String sep : seps) {
            val = trimAfter(val, sep);
        }
        return val;
    }

    private String trimAfter(String val, String sep) {
        int idx = val.indexOf(sep);
        if (idx > 0) {
            return val.substring(0, idx);
        }
        return val;
    }
}
