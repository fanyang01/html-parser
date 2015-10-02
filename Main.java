package crawler;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;

public class Main {
    static InputStream getReader(String url) throws IOException {
        HttpURLConnection.setFollowRedirects(true);
        URL u = new URL(url);
        HttpURLConnection conn = (HttpURLConnection) u.openConnection();
        // conn.setInstanceFollowRedirects(true);
        conn.connect();
        for (int i = 0; i < 5; i++) {
            int status = conn.getResponseCode();
            if (status == HttpURLConnection.HTTP_MOVED_TEMP
                    || status == HttpURLConnection.HTTP_MOVED_PERM
                    || status == HttpURLConnection.HTTP_SEE_OTHER) {
                url = conn.getHeaderField("Location");
                u = new URL(url);
                conn = (HttpURLConnection) u.openConnection();
                conn.connect();
            } else {
                break;
            }
        }

        return conn.getInputStream();
    }

    static void extractText(Node x, StringBuilder s) throws Exception {
        switch (x.type) {
            case TEXT:
                s.append(x.value);
                s.append(" | ");
                break;
            case ERROR:
                throw new Exception(x.value);
        }
        for (x = x.child; x != null; x = x.siblings) {
            if (x.type == NodeType.ELEMENT && (x.value.equals("script") || x.value.equals("style")))
                continue;
            extractText(x, s);
        }
    }

    static void extractLinks(Node x, List<String> links) throws Exception {
        switch (x.type) {
            case ELEMENT:
                String href = x.attr.get("href");
                if (href != null)
                    links.add(href);
                break;
        }
        for (x = x.child; x != null; x = x.siblings) {
            extractLinks(x, links);
        }
    }

    public static void main(String[] args) {
        Parser p = new Parser();
        for (String arg : args) {
            System.out.printf("-----------------------\n", arg);
            System.out.printf("FETCH %s\n", arg);
            StringBuilder s = new StringBuilder();
            List<String> links = new LinkedList<String>();
            try {
                Node root = p.parse(getReader(arg));
                extractText(root, s);
                extractLinks(root, links);
            } catch (Exception e) {
                e.printStackTrace();
                continue;
            }
            System.out.println("TEXT CONTENT");
            System.out.println(s.toString());
            System.out.println("LINKS");
            while (!links.isEmpty()) {
                System.out.println(links.remove(0));
            }
        }
    }
}
