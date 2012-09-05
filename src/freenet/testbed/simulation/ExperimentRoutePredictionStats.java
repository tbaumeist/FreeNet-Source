package freenet.testbed.simulation;

import java.util.ArrayList;
import java.util.List;

public class ExperimentRoutePredictionStats {
	private static ExperimentRoutePredictionStats experStats = null;

	private String nodeCount = "";
	private String peerCount = "";
	private String htl = "";
	private String expIndex = "";
	private String originNode = "";
	private String insertWord = "";
	private List<List<String>> nodesStoringWord = new ArrayList<List<String>>();
	private List<List<String>> insertPath = new ArrayList<List<String>>();

	private ExperimentRoutePredictionStats() {
	}

	public static ExperimentRoutePredictionStats getInstance() {
		return ExperimentRoutePredictionStats.experStats;
	}

	public static void reset() {
		ExperimentRoutePredictionStats.experStats = new ExperimentRoutePredictionStats();
	}

	public void newPath() {
		this.nodesStoringWord.add(new ArrayList<String>());
		this.insertPath.add(new ArrayList<String>());
	}

	public void startInsert(String index, String nodeCount, String peerCount,
			String htl, String node, String word) {
		this.expIndex = index;
		this.nodeCount = nodeCount;
		this.peerCount = peerCount;
		this.htl = htl;
		this.originNode = node;
		this.insertWord = word;
	}

	public void routedInsert(String node) {
		this.insertPath.get(this.insertPath.size() - 1).add(node);
	}

	public void storedInsert(String node) {
		this.nodesStoringWord.get(this.nodesStoringWord.size() - 1).add(node);
	}

	public String toString() {
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < this.insertPath.size(); i++) {
			b.append(this.expIndex);
			b.append(",");
			b.append(this.nodeCount);
			b.append(",");
			b.append(this.peerCount);
			b.append(",");
			b.append(this.htl);
			b.append(",");
			b.append(this.originNode);
			b.append(",");
			b.append(this.insertWord);
			b.append(",");
			
			for (String s : this.nodesStoringWord.get(i))
				b.append(s + "|");
			b.append(",");
			
			b.append(this.originNode);
			b.append("|");
			for (String s : this.insertPath.get(i))
				b.append(s + "|");
			
			b.append("\n");
		}
		b.replace(b.length()-1, b.length(), "");
		return b.toString();
	}

	public static String toStringCSVHeader() {
		StringBuilder b = new StringBuilder();
		b.append("Experiment Index,");
		b.append("Node Count,");
		b.append("Peer Count,");
		b.append("HTL,");
		b.append("Origin Node,");
		b.append("Word,");
		b.append("Nodes Storing,");
		b.append("Insert Path");
		return b.toString();
	}
}
