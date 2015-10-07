package crawler;

import java.io.*;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.Map;
import java.util.Queue;

enum TokenType {ERROR, EOF, SKIP, START_TAG, END_TAG, SELF_CLOSING_TAG, TEXT};

class Token {
    TokenType type;
    String value;
    Map<String, String> attr;

    public Token(TokenType type) {
        this(type, "");
    }

    public Token(TokenType type, String s) {
        this.type = type;
        value = s;
        attr = new HashMap<String, String>();
    }
}

public class Tokenizer {
    private BufferedReader r;
    private static Map<String, Object> selfclosingTags;
    private static String[] tags = {
            "area", "base", "br", "col", "command",
            "embed", "hr", "img", "input", "keygen",
            "link", "meta", "param", "source", "trace", "wbr",
    };
    final int[] buf;
    int pos;

    static CharArrayWriter writer;
    private Queue<Token> queue = new LinkedList<Token>();

    public Tokenizer(InputStream in) throws UnsupportedEncodingException {
        this(in, "UTF-8");
    }
    public Tokenizer(InputStream in, String charset) throws UnsupportedEncodingException {
        buf = new int[256];
        pos = 0; // next free slot
        r = new BufferedReader(new InputStreamReader(in, charset));
        writer = new CharArrayWriter();
        selfclosingTags = new HashMap<String, Object>();
        for (String tag : tags)
            selfclosingTags.put(tag, null);
    }

    public Token next() throws IOException {
        if (!queue.isEmpty()) return queue.remove();
        LOOP:
        while (true) {
            int ch = get();
            switch (ch) {
                case -1:
                    return new Token(TokenType.EOF);
                case ' ':
                case '\n':
                case '\t':
                case '\r':
                    continue LOOP;
                case '<':
                    return lexStartBracket();
                default:
                    unget(ch);
                    return lexText();
            }
        }
    }

    private int get() throws IOException {
        if (pos > 0) {
            return buf[--pos];
        }
        return r.read();
    }

    private int peek() throws IOException {
        int c = get();
        unget(c);
        return c;
    }

    private void unget(int c) {
        buf[pos++] = c;
    }

    private Token lexText() throws IOException {
        writer.reset();
        LOOP:
        while (true) {
            int ch = get();
            switch (ch) {
                case '<':
                    // a naive implementation to determine whether it's a tag
                    int i;
                    int[] buffer = new int[16];
                    boolean isTag = false;
                    buffer[0] = ch;
                    for (i = 1; i < buffer.length; i++) {
                        buffer[i] = ch = get();
                        if (i != 1) {
                            if (ch == ' ' || ch == '>') {
                                isTag = true;
                                break;
                            } else if (!isLetterDigit(ch))
                                break;
                        } else if (ch != '/' && !isLetter(ch)) {
                            break;
                        }
                    }
                    if (isTag) {
                        for (int j = i; j >= 0; j--) unget(buffer[j]);
                        break LOOP;
                    }
                    for (int j = 0; j < i; j++)
                        writer.write(buffer[j]);
                    if (i != buffer.length)
                        writer.write(buffer[i]);
                    break;
                default:
                    writer.write(ch);
            }
        }
        return new Token(TokenType.TEXT, writer.toString());
    }

    private Token lexStartBracket() throws IOException {
        int ch = get();

        if (ch == '/') return lexCloseTag();

        if (ch == '!') {
            skip();
            return new Token(TokenType.SKIP);
        }

        unget(ch);
        String tagName = lexTagName();
        Token token;
        if (tagName.length() == 0) {
            return new Token(TokenType.ERROR, "empty tagname");
        }
        if (selfclosingTags.containsKey(tagName)) {
            token = new Token(TokenType.SELF_CLOSING_TAG, tagName);
        } else {
            token = new Token(TokenType.START_TAG, tagName);
        }
        lexAttr(token);
        if (tagName.equals("script") || tagName.equals("style")) {
            lexToEndTag(tagName);
        }
        return token;
    }

    private void lexAttr(Token token) throws IOException {
        int ch;
        while(true) {
            StringBuilder key = new StringBuilder();
            StringBuilder value = new StringBuilder();
            ch = get();
            if(ch == '>') {
                break;
            } else if(isLetter(ch)) {
                key.append((char)ch);
                while(isLetterDigit(ch = get()) || ch == '-')
                    key.append((char)ch);
                if(Character.isSpaceChar(ch) || ch == '>') {
                    unget(ch);
                    token.attr.put(key.toString(), "true");
                    continue;
                } else if(ch == '=') {
                    int quote = get();
                    if(quote != '\'' && quote != '"') { // assume non-quoted value is valid
                        value.append(quote);
                        while (isLetterDigit(ch = get()))
                            value.append((char)ch);
                        unget(ch);
                    } else {
                        while ((ch = get()) != quote)
                            value.append((char) ch);
                    }
                    token.attr.put(key.toString(), value.toString());
                    continue;
                } else {
                    token.type = TokenType.ERROR;
                    token.value = "bad key/value syntax: " + (char)ch;
                    return;
                }
            } else if(Character.isSpaceChar(ch)) {
                continue;
            } else if(ch == '/' && peek() == '>') {
                token.type = TokenType.SELF_CLOSING_TAG;
                continue;
            } else {
                token.type = TokenType.ERROR;
                token.value = "bad tag syntax: " + (char)ch;
                return;
            }
        }
    }

    private void lexToEndTag(String tag) throws IOException {
        String endTag = "</" + tag + ">";
        StringBuilder s = new StringBuilder();
        int ch;
        do {
            ch = get();
            s.append((char) ch);
        } while (ch != '>' || s.length() < endTag.length() ||
                s.indexOf(endTag, s.length() - endTag.length()) < 0);
        String text = s.substring(0, s.length() - endTag.length());
        if (text.length() != 0)
            queue.add(new Token(TokenType.TEXT, text));
        queue.add(new Token(TokenType.END_TAG, tag));
    }

    private String lexTagName() throws IOException {
        writer.reset();
        int ch;
        while (isLetterDigit(ch = get()))
            writer.write(ch);
        unget(ch);
        return writer.toString();
    }

    private String lexToEndBracket() throws IOException {
        writer.reset();
        int ch;
        while ((ch = get()) != '>')
            writer.write(ch);
        return writer.toString();
    }

    // skip <!-- Comment --> and <!DOCTYPE html>
    private void skip() throws IOException {
        get();
        switch (get()) {
            case '-': // <!-- comment -->
                LOOP:
                do {
                    String s = lexToEndBracket();
                    if (s.endsWith("--")) break LOOP;
                } while (true);
                break;
            default: // may be <!DOCTYPE html>
                lexToEndBracket();
        }
    }

    private Token lexCloseTag() throws IOException {
        String s = lexTagName();
        int ch;
        if ((ch = get()) == '>') {
            return new Token(TokenType.END_TAG, s);
        }
        return new Token(TokenType.ERROR, "bad end tag");
    }

    private boolean isLetterDigit(int ch) {
        return (ch >= '0' && ch < '9') || (ch >= 'A' && ch <= 'Z') ||
                (ch >= 'a' && ch <= 'z');
    }

    private boolean isLetter(int ch) {
        return ((ch >= 'A' && ch <= 'Z') || (ch >= 'a' && ch <= 'z'));
    }
}
