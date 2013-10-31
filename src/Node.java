import java.util.ArrayList;
import java.util.List;


public class Node {
	
	
	protected List<OutEdge> _edges;
	protected int _state;
	
	public Node() {
		_edges = new ArrayList<OutEdge>();
	}

	public List<OutEdge> getEdges() {
		return _edges;
	}
	
	public void addEdge(OutEdge edge) {
		_edges.add(edge);
		
	}
	
	public int getState() {
		return _state;
	}

	public void setState(int state) {	
		_state = state;
	}

	public void setEdges(List<OutEdge> edges) {
		_edges = edges;
	}
	
	public boolean equals(Object o) {
		return this == o;
	}
	
	public boolean hasEpsilonEdges() {
		for (OutEdge edge : _edges) {
			if (edge.getTChar() == -1) {
				return true;
			}
		}
		return false;
	}
}
