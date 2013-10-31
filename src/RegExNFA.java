import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PushbackReader;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.Stack;
import java.util.regex.Pattern;

/**
 * Regular expression matcher.  Handles pathological regular expressions 
 * better than Java JDK.<br><br>
 * 
 * Pattern: a?a?a?a?a?a?a?a?a?a?a?a?a?a?a?a?aaaaaaaaaaaaaaaa<br>
 * Input: aaaaaaaaaaaaaaaa<br><br>
 * 
 * This is because in the worst case, the Java JDK pattern matcher has 
 * to traverse 2^n (n=16 in this case) different paths prior to 
 * reaching the accept state.  Whereas in my version, the worst
 * case is n^2.<br><br>
 * 
 * Accepts the following input characters:<br>
 * <ol>
 *    <li> '('
 *    <li> ')'
 *    <li> 'a'..'Z', 'A'..'Z'
 *    <li> '&nbsp;'
 *    <li> '+'
 *    <li> '?'
 *    <li> '*'
 * </ol>   
 * Parses the string using recursive-descent parser.  Matches string using
 * <a href="http://swtch.com/~rsc/regexp/regexp1.html">Thompson NFA
 * </a> technique.
 * 
 * 
 * @author Chuck Lan
 *
 */
public class RegExNFA {
	Node _start;
	Node _end;
	RegExNFA _currNFA;
	RegExNFA prevNFA;
	Stack<Node> _stack;
	Set<Node> _foundSet;
	Set<Node> _nodes;
	Set<Node> _visited;
	Lexer _lexer;
	State _state;

	class Lexer {
		protected PushbackReader _reader;
		public int _laBuffer;
		public int _pos = 0;
	}
	
	class State {
		int state = 0;
	}
	
	public RegExNFA() {
		_stack = new Stack<Node>();
		_foundSet = new HashSet<Node>();
		_nodes = new HashSet<Node>();
		_visited = new HashSet<Node>();
		_lexer = new Lexer();
		_state = new State();
	}

	public RegExNFA createStartNode() {
		RegExNFA retVal = new RegExNFA();
		Node startNode = new Node();
		startNode.setState(_state.state++);
		retVal.setStart(startNode);
		RegExNFA currNFA = new RegExNFA();
		currNFA.setStart(startNode);
		currNFA.setEnd(startNode);
		retVal.setCurrNFA(currNFA);
		retVal._lexer = this._lexer;
		return retVal;
	}

	public Node getEnd() {
		return _end;
	}

	public Node getStart() {
		return _start;
	}

	public void setEnd(Node end) {
		_end = end;

	}

	public void setStart(Node start) {
		_start = start;
	}

	public RegExNFA getCurrNFA() {
		return _currNFA;
	}

	public void setCurrNFA(RegExNFA graph) {
		_currNFA = graph;
	}

	public static final int EPS = -1;
	

	public static RegExNFA compile(String expr) throws IOException {
		RegExNFA nfa = new RegExNFA();
		nfa._lexer._reader = new PushbackReader(new StringReader(expr));
		return nfa.compile();
	}

	public RegExNFA modifier() {
		int la = la();
		char ch = (char) la;
		switch (la) {
		case '*': 
			clearBuffer();
			createEdge(EPS, getPrevNFA().getEnd(), getCurrNFA().getEnd());
			createEdge(EPS, getCurrNFA().getEnd(), getPrevNFA().getEnd());
		    break;
		case '+': 
			clearBuffer();
			createEdge(EPS, getCurrNFA().getEnd(), getPrevNFA().getEnd());
		    break;
		case '?': 
			clearBuffer();
			createEdge(EPS, getPrevNFA().getEnd(), getCurrNFA().getEnd());
		    break;
		default:
			unreadBuffer();
		}
		return getCurrNFA();
	}
	
	public RegExNFA expr() {
		while (true) {
			int la = la();
			char ch = (char) la;
			boolean identifiedTokenChild = false;
			if ((isAlpha(la) || isSpace(la) || la == '.') && !identifiedTokenChild) {
				clearBuffer();
				RegExNFA alphaNFA = createStartNode();
				Node alphaNode = createNode(la, getCurrNFA().getEnd());
				alphaNFA.setStart(alphaNode);
				alphaNFA.setEnd(alphaNode);
				setPrevNFA(getCurrNFA());
				setCurrNFA(alphaNFA);
				modifier();
				identifiedTokenChild = true;
			}
			if (la == '(' && !identifiedTokenChild) {
				unreadBuffer();
				RegExNFA groupNFA = group();
				createEdge(EPS, getCurrNFA().getEnd(), groupNFA.getStart());
				setPrevNFA(getCurrNFA());
				setCurrNFA(groupNFA);
				modifier();
				identifiedTokenChild = true;
			} 
			if (!identifiedTokenChild) {
				unreadBuffer();
				setEnd(getCurrNFA().getEnd());
				return this;
			}
		}
	}

	public void throwSyntaxException() throws RuntimeException {
		throw new RuntimeException("Syntax error. Character position = " + (_lexer._pos - 1));
	}
	
	public RegExNFA mainExpr() {
		RegExNFA exprNFA = createStartNode().multGroup();
		int la = la();
		if (la != -1 && la != Character.MAX_VALUE ) {
			throwSyntaxException();
		}
		createEdge(EPS, getCurrNFA().getEnd(), exprNFA.getStart());
		setPrevNFA(getCurrNFA());
		setCurrNFA(exprNFA);
		return exprNFA;
	}
	
	public RegExNFA group() {		
		if (la() != '(')
			throwSyntaxException();
		RegExNFA exprNFA = createStartNode().multGroup();
		char la = (char)la();
		if (la != ')') {
			throwSyntaxException();	
		}
		clearBuffer();
		return exprNFA;
	}

	public RegExNFA multGroup() {		
		RegExNFA parentNFA = createStartNode().expr();
		if (la() == '|') {
			unreadBuffer();
			RegExNFA multExprTailNFA = createStartNode().multGroupTail();
			createEdge(EPS, parentNFA.getStart(), multExprTailNFA.getStart());
			createEdge(EPS, multExprTailNFA.getEnd(), parentNFA.getEnd());
		} else {
			unreadBuffer();
		}
		return parentNFA;
	}

	public RegExNFA multGroupTail() {
		RegExNFA parentNFA = createStartNode();
		Node endNode = new Node();
		endNode.setState(_state.state++);
		parentNFA.setEnd(endNode);
		do {
			int la = la();
			if (la == '|') {
				RegExNFA multExprTailNFA = createStartNode().expr();
				createEdge(EPS, parentNFA.getStart(), multExprTailNFA
						.getStart());
				createEdge(EPS, multExprTailNFA.getEnd(), parentNFA.getEnd());
				continue;
			} else {
				unreadBuffer();
				break;
			}
		} while (true);
		return parentNFA;
	}

	public RegExNFA compile() throws IOException {
		RegExNFA graph = createStartNode();
		graph.mainExpr();
		Node endNode = new Node();
		createEdge(RegExNFA.EPS, graph.getCurrNFA().getEnd(), endNode);
		endNode.setState(_state.state++);
		graph.setEnd(endNode);
		simplifyGraph(graph.getStart(), endNode);
		return graph;
	}

	public Node createNode(int tChar, Node srcNode) {
		Node node = new Node();
		node.setState(_state.state++);
		OutEdge edge = new OutEdge();
		edge.setDest(node);
		edge.setTChar((char) tChar);
		srcNode.addEdge(edge);
		return node;
	}

	public void createEdge(int tChar, Node start, Node end) {
		OutEdge epsEdge = new OutEdge();
		epsEdge.setTChar(EPS);
		epsEdge.setDest(end);
		start.addEdge(epsEdge);
	}

	public int la() {
		try {
			_lexer._laBuffer = _lexer._reader.read();
			_lexer._pos++;
			return _lexer._laBuffer;

		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public void unreadBuffer() {
		try {
			_lexer._reader.unread(_lexer._laBuffer);
			clearBuffer();
			_lexer._pos--;
		} catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	public  void clearBuffer() {
		_lexer._laBuffer = 0;
	}

	public boolean isAlpha(int c) {
		return (c >= 'a' && c <= 'z') || (c >= 'A' && c <= 'Z');
	}
	
	public boolean isSpace(int c) {
		return c == ' ';
	}
	
	public boolean match(String input) throws IOException {
		Node start = this.getStart();
		_stack.clear();
		_foundSet.clear();
		_stack.push(start);
		if (input.length() > 0) {
			for (int idx = 0; idx < input.length(); idx++) {
				while (!_stack.isEmpty()) {
					Node popedNode = _stack.pop();
					Set<Node> nodes = getNodesWithTChar(input.charAt(idx),
							popedNode);
					for (Node node : nodes) {
						if (!_foundSet.contains(node)) {
							_foundSet.add(node);
						}
					}
				}
				if ((idx == input.length() - 1) && (_foundSet.size() > 0)
						&& findNode(this.getEnd(), _foundSet) != null) {
					return true;
				}
				_stack.addAll(_foundSet);
				_foundSet.clear();
			}
		} else {
			return findNode(this.getEnd(), Collections.singleton(this
					.getStart())) != null;
		}

		return false;
	}

	public boolean match(String pattern, String input) throws IOException {
		RegExNFA nfa = compile(pattern);
		return nfa.match(input);
	}

	public Node findNode(Node node, Set<Node> nodes) {
		Set<Node> visited = new HashSet<Node>();
		for (Node curNode : nodes) {
			findNodeThroughEPSEdges(node, curNode, visited);
		}
		if (visited.contains(node)) {
			return node;
		} else {
			return null;
		}
	}

	public void findNodeThroughEPSEdges(Node findNode, Node node,
			Set<Node> visited) {
		if (visited.contains(findNode)) {
			return;
		}
		List<OutEdge> edges = node.getEdges();
		for (OutEdge edge : edges) {
			if (edge.getTChar() != EPS)
				continue;
			Node destNode = edge.getDest();
			if (visited.contains(destNode))
				continue;
			visited.add(destNode);
			findNodeThroughEPSEdges(findNode, destNode, visited);
		}
		return;
	}

	public void getNodesWithTChar(char ch, Node node,
			Set<Node> visited, Set<Node> retVal) {

		List<OutEdge> edges = node.getEdges();
		for (OutEdge edge : edges) {
			Node destNode = edge.getDest();
			if (visited.contains(destNode))
				continue;
			if (edge.getTChar() == ch || edge.getTChar() == '.') {
				if (!retVal.contains(destNode)) {
					retVal.add(destNode);
				}
				continue;
			}
			if (edge.getTChar() == EPS) {
				visited.add(destNode);
				getNodesWithTChar(ch, edge.getDest(), visited, retVal);
				visited.remove(destNode);
			}
		}
	}

	public Set<Node> getNodesWithTChar(char ch, Node node) {
		_nodes.clear();
		_visited.clear();
		getNodesWithTChar(ch, node, _visited, _nodes);
		return _nodes;
	}

	public RegExNFA getPrevNFA() {
		return prevNFA;
	}

	public void setPrevNFA(RegExNFA prevNFA) {
		this.prevNFA = prevNFA;
	}

	
	/*
	 * Traverse the graph, removing epsilon edges and 
	 * thus shortening paths.
	 */
	public static int simplifyGraph(Node startNode, Node endNode, Set<Node> visited) {
		int cnt = 0;
		if (!visited.contains(startNode)) {
			final Set<OutEdge> origEdges = new HashSet<OutEdge>(startNode.getEdges());
			Set<OutEdge> edges = new HashSet<OutEdge>(startNode.getEdges());		
			for (OutEdge edge : origEdges) {
				if (edge.getTChar() == EPS) {
					final Node destNode = edge.getDest();
					if (destNode == endNode) {
						continue;
					}
					edges.remove(edge);
					edges.addAll(destNode.getEdges());	
					cnt++;
				}
			}
			startNode.setEdges(new ArrayList<OutEdge>(edges));
			visited.add(startNode);
			for (OutEdge edge : edges) {
				if (edge.getTChar() == EPS) {
					continue;
				}
				final Node destNode = edge.getDest();
				cnt += simplifyGraph(destNode, endNode, visited);
			}
		} 
		return cnt;		
	}
	
	public static void simplifyGraph(Node startNode, Node endNode) {
		Set<Node> visited = new HashSet<Node>();
		int cnt = 0;
		int curCnt = 0;
		while(cnt != (curCnt = simplifyGraph(startNode, endNode, visited))) {
			visited = new HashSet<Node>();
			cnt = curCnt;
		}		
	}
	
	public static void main(String[] args) {
		try {
			System.out.println("*** Welcome to regular expression benchmarking ***");
			System.out.println("Here is a pattern and input for you to try:");
			System.out.println("Pattern : a?a?a?a?a?a?a?a?a?a?a?a?a?a?a?a?aaaaaaaaaaaaaaaa");
			System.out.println("Input   : aaaaaaaaaaaaaaaa");
			while(true) {
				BufferedReader in = new BufferedReader(new InputStreamReader(System.in));
				System.out.println("\nPlease enter the regular expression that you want to benchmark:");
				String pattern = in.readLine();
				Pattern p = Pattern.compile(pattern);
				System.out.println("Please enter the sting that you want to match:");
				String pString = in.readLine();
				RegExNFA nfa = RegExNFA.compile(pattern);
				long startTime = new Date().getTime();
				boolean myRegExVal = nfa.match(pString);		
				long endTime = new Date().getTime();
				System.out.println("MyRegExNFA evaluates to " + myRegExVal + ".  Time to complete = " + (endTime - startTime) + " ms");
				long startTime2 = new Date().getTime();
				boolean javaRegExVal = p.matcher(pString).matches();
				long endTime2 = new Date().getTime();
				System.out.println("\nJava JDK implementation evaluates to " + javaRegExVal + ".  Time to complete = " + (endTime2 - startTime2) + " ms");				
			}			
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}