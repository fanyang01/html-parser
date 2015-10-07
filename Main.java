package crawler;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Scanner;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

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

    static void extractLinks(Node x, Set<String> links) throws Exception {
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
        String filename = "urls.txt";
        Scanner sc = null;


        if (args.length == 1) {
            filename = args[0];
        }

        try {
            sc = new Scanner(new File(filename));
        } catch (FileNotFoundException e) {
            System.err.println(e);
            System.exit(1);
        }

        while (sc.hasNextLine()) {
            StringBuilder s = new StringBuilder();
            String line = sc.nextLine().trim();
            if(line.startsWith("#"))
                continue;
            String[] ss = line.split("\\s+", 3);
            if (ss.length != 3 || !ss[2].startsWith("\"") || !ss[2].endsWith("\"")) {
                System.err.printf("bad syntex: '%s'\n", line);
                continue;
            }
            String url = ss[0];
            String charset = ss[1];
            if(charset.equals("*"))
                charset = "UTF-8";
            String selector = ss[2].substring(1, ss[2].length()-1);
            Selector slt = new Selector(selector);

            System.out.printf("------------------------------------------------\n");
            System.out.printf("FETCH %s\n", url);
            try {
                Node root = p.parse(getReader(url), charset);
                slt.select(root, s);

                System.out.printf("TEXT CONTENT for selector '%s':\n", selector);
                System.out.printf("------------------------------------------------\n");
                System.out.println(StringUtils.unescapeHtml3(s.toString().trim()));
            } catch (Exception e) {
                e.printStackTrace();
                continue;
            }
        }

    }
}

class Selector {
    Pattern pattern;
    String id, className;

    public Selector(String selector) {
        int i;
        id = className = "";
        if((i = selector.indexOf("#")) >= 0) {
            id = selector.substring(i+1);
            selector = selector.substring(0, i);
        }
        if((i = selector.indexOf(".")) >= 0) {
            className = selector.substring(i+1);
            selector = selector.substring(0, i);
        } else if((i = id.indexOf(".")) >= 0) {
            className = id.substring(i+1);
            id = id.substring(0, i);
        }
        String[] ss = selector.split("\\s+");
        StringBuilder sbd = new StringBuilder();
        for (String s : ss) {
            if (s.length() != 0) {
                sbd.append("(\\w+>)*");
                sbd.append(s);
            }
        }
        // sbd.append("(\\w+>)*");
        pattern = Pattern.compile(sbd.toString());
    }

    public void select(Node x, StringBuilder text) throws Exception {
        selectPath(x, "", text);
    }

    public static void extractText(Node x, StringBuilder text) throws Exception {
        switch (x.type) {
            case TEXT:
                String s = x.value.trim();
                if (s.length() != 0) {
                    text.append(s);
                    text.append("\n");
                }
                break;
            case ERROR:
                throw new Exception(x.value);
        }
        for (x = x.child; x != null; x = x.siblings) {
            if (x.type == NodeType.ELEMENT && (x.value.equals("script") || x.value.equals("style")))
                continue;
            extractText(x, text);
        }
    }

    private void selectPath(Node x, String path, StringBuilder text) throws Exception {
        switch (x.type) {
            case DOCUMENT:
            case ELEMENT:
                if (!path.equals(""))
                    path = path + ">";
                path = path + x.value;
                Matcher m = pattern.matcher(path);
                if (!m.matches())
                    break;
                if(id.length() > 0)
                    if(!x.attr.containsKey("id") || !x.attr.get("id").equals(id))
                        break;
                if(className.length() > 0)
                    if(!x.attr.containsKey("calss")|| !x.attr.get("class").contains(className))
                        break;
                extractText(x, text);
                return;
        }
        for (x = x.child; x != null; x = x.siblings) {
            selectPath(x, path, text);
        }
    }
}
