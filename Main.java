package fanyang01;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;

public class Main {
    static InputStream getReader(String url) throws IOException {
        URL u = new URL(url);
        return u.openConnection().getInputStream();
    }

    static void extractText(Node x, StringBuilder s) throws Exception {
        switch(x.type) {
            case TEXT:
                s.append(x.value);
                break;
            case ERROR:
                throw new Exception(x.value);
        }
        for(x = x.child; x != null; x = x.siblings) {
            s.append(" ");
            extractText(x, s);
        }
    }

    public static void main(String[] args) {
		Parser p = new Parser();
		for(String arg: args) {
            System.out.printf("FETCH %s:\n", arg);
            StringBuilder s = new StringBuilder();
            try {
                Node root = p.parse(getReader(arg));
                extractText(root, s);
            } catch(Exception e) {
                e.printStackTrace();
				continue;
            }
            System.out.println(s.toString());
        }
    }
}
