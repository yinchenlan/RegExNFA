
public class OutEdge {
	
	protected Node _dest;
	protected int _tChar;
	protected char _ch;
	
	public Node getDest() {
		return _dest;
	}

	public int getTChar() {
		return _tChar;
	}

	public void setDest(Node dest) {
		_dest = dest;
	}

	public void setTChar(int c) {
		_tChar = c;

		_ch = (char) c;

	}
	public boolean equals(Object o) {
		return this == o;
	}
	public String toString() {
		return String.valueOf((char) _tChar);
	}
}
