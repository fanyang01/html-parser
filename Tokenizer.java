package fanyang01;

import java.io.*;
import java.nio.IntBuffer;
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
    final InputStreamReader r;
    final int[] buf;
    int pos, end;

    static CharArrayWriter writer;
	private Queue<Token> queue = new LinkedList<Token>();

    public Tokenizer(InputStream in) {
        buf = new int[16];
        end =  -1;
        pos = 0;
        r = new InputStreamReader(in);
        writer = new CharArrayWriter();
    }

    public Token next() throws IOException {
		if(!queue.isEmpty()) return queue.remove();
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
                    put(ch);
                    return lexText();
            }
        }
    }

    private int get() throws IOException {
        if (pos <= end) {
            int ch = buf[pos++];
            if(pos == end+1) {
                end =  -1;
                pos = 0;
            }
            return ch;
        }
        return r.read();
    }

    private int peek() throws IOException {
        int c = get();
        buf[++end] = c;
        return c;
    }

    private void put(int c) {
        buf[++end] = c;
    }

    private Token lexText() throws IOException {
        writer.reset();
        LOOP:
        while (true) {
            int ch = peek();
            switch (ch) {
                case '<':
                    break LOOP;
                default:
                    writer.write(get());
            }
        }
        return new Token(TokenType.TEXT, writer.toString());
    }

    private Token lexStartBracket() throws IOException {
        writer.reset();
		int ch = get();

        if (ch == '/') return lexCloseTag();

        if (ch == '!') {
            skip();
            return new Token(TokenType.SKIP);
        }

        put(ch);
        String tagName = lexTagName();
        if (tagName.length() == 0)
            return new Token(TokenType.ERROR, "empty tagname");
		if(tagName.equals("script") || tagName.equals("style")) {
			lexToEndTag(tagName);
			lexToEndBracket();
			return new Token(TokenType.START_TAG, tagName);
		}

        Token token;
        String rest = lexToEndBracket();
        if (rest.endsWith("/")) {
            rest = rest.substring(0, rest.length() - 1);
            token = new Token(TokenType.SELF_CLOSING_TAG, tagName);
        } else {
            token = new Token(TokenType.START_TAG, tagName);
        }

        String[] attrs = rest.trim().split(" ");
        for(String kv: attrs) {
            if(kv.length() == 0) continue;
            if(kv.indexOf('=') == -1 || kv.endsWith("=")) {
                token.attr.put(kv, "true");
                continue;
            }
            String[] arr = kv.split("=", 2);
            String k = arr[0];
            String v = arr[1].substring(1, arr[1].length()-1);
            token.attr.put(k, v);
        }
        return token;
    }

	private void lexToEndTag(String tag) throws IOException {
		String endTag = "</" + tag + ">";
		StringBuilder s = new StringBuilder();
        int ch;
		do {
			ch = get();
			s.append(ch);
		} while(ch != '>' || s.length() < endTag.length() ||
				s.indexOf(endTag) < 0);
        queue.add(new Token(TokenType.TEXT,
                s.substring(0, s.length() - endTag.length())));
        queue.add(new Token(TokenType.END_TAG, tag));
	}

    private String lexTagName() throws IOException {
        writer.reset();
        int ch;
        while (isLetterDigit(ch = get()))
            writer.write(ch);
		put(ch);
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
}
