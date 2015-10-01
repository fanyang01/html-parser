package fanyang01;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Stack;

enum NodeType {ERROR, DOCUMENT, ELEMENT, TEXT};

class Node {
    Node child, siblings;
    NodeType type;
    String value;
    Map<String, String> attr;

    public Node(NodeType type) {
        this.type = type;
        child = siblings = null;
        value = "";
        attr = new HashMap<String, String>();
    }
}

enum Direction {CHILD, SIBLING}
public class Parser {

    public Node parse(InputStream in) throws IOException {
        Tokenizer t = new Tokenizer(in);
        Token token;
        Node root = new Node(NodeType.DOCUMENT);
        Node x = root;
        Direction direction = Direction.CHILD;
        Stack<Node> stack = new Stack<Node>();

        LOOP:
        do {
            token = t.next();
            System.err.printf("TOKEN=%s value='%s'\n", token.type, token.value);
            switch (token.type) {
                case EOF:
                    break LOOP;
                case ERROR:
                    Node node = new Node(NodeType.ERROR);
                    node.value = token.value;

                    switch (direction) {
                        case CHILD: x.child = node; break;
                        case SIBLING: x.siblings = node; break;
                    }
                    break LOOP;
                case SKIP:
                    continue LOOP;
                case START_TAG:
                    node = new Node(NodeType.ELEMENT);
                    node.value = token.value;
                    node.attr = token.attr;

                    switch (direction) {
                        case CHILD: x.child = node; break;
                        case SIBLING: x.siblings = node; break;
                    }
                    x = node;
                    stack.push(node);
                    direction = Direction.CHILD;
                    break;
                case END_TAG:
                    x = stack.pop();
                    direction = Direction.SIBLING;
                    break;
                case SELF_CLOSING_TAG:
                    node = new Node(NodeType.ELEMENT);
                    node.value = token.value;
                    node.attr = token.attr;

                    switch (direction) {
                        case CHILD: x.child = node; break;
                        case SIBLING: x.siblings = node; break;
                    }
                    x = node;
                    direction = Direction.SIBLING;
                    break;
                case TEXT:
                    node = new Node(NodeType.TEXT);
                    node.value = token.value;
                    switch (direction) {
                        case CHILD: x.child = node; break;
                        case SIBLING: x.siblings = node; break;
                    }
                    x = node;
                    direction = Direction.SIBLING;
                    break;
            }
        } while (true);
        return root;
    }
}
