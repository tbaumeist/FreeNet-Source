package freenet.testbed.simulation;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import freenet.crypt.DummyRandomSource;
import freenet.crypt.RandomSource;
import freenet.keys.ClientCHK;
import freenet.node.NodeStarter;
import freenet.node.OpennetPeerNode;
import freenet.node.PeerNode;
import freenet.node.simulator.RealNodeTest;
import freenet.support.Executor;
import freenet.support.PooledExecutor;
import freenet.support.Logger.LogLevel;
import freenet.support.io.FileUtil;
import freenet.testbed.DummyNode;
import freenet.testbed.INode;
import freenet.testbed.IOpennetPeerNode;

public class OpennetSimulator extends RealNodeTest {
	private int nodeCount, peerCount, basePort;
	private short maxHTL;
	private INode[] nodes;
	private int networkState;
	private File storageDirectory;

	private boolean needRestart = false;
	private boolean nodesStarted = false;

	private int[] portsFreenet;
	private int[] portsOpennet;
	private int[] portsTMCI;
	private int portOffset = 0;

	private final boolean START_WITH_IDEAL_LOCATIONS = true;
	private final boolean FORCE_NEIGHBOUR_CONNECTIONS = true;
	private final boolean ENABLE_SWAPPING = false;
	private final boolean ENABLE_SWAP_QUEUEING = false;
	private final boolean ENABLE_FOAF = true;
	private final boolean ENABLE_ANNOUNCEMENT = false;

	public OpennetSimulator(int nodeCount, int peerCount, short maxHTL,
			int startPort, File storageDir) {
		this.nodeCount = nodeCount;
		this.peerCount = peerCount;
		this.maxHTL = maxHTL;
		this.basePort = startPort;
		this.nodes = new INode[this.nodeCount];
		this.storageDirectory = storageDir;
		this.portsFreenet = new int[this.nodeCount];
		this.portsOpennet = new int[this.nodeCount];
		this.portsTMCI = new int[this.nodeCount];
	}

	public int getNetworkState() {
		return this.networkState;
	}

	public void startNetwork(int networkState) throws Exception {
		this.genTopology(networkState, false);

		this.nodesStarted = true;

		for (int i = 0; i < this.nodeCount; i++) {
			System.out.println("Starting node " + i);
			this.nodes[i].start(false);
		}

		while (!waitForAllConnectedOpen(this.nodes)) {
			// failed to get all nodes to connect
			// reset trouble makers
			System.out.println("Forcing reconnects...");
			forceReconnect(this.nodes);
		}
	}

	public void genTopology(int networkState, boolean topologyOnly)
			throws Exception {
		if (this.needRestart)
			throw new Exception(
					"Must restart program before running a new topology.");
		this.needRestart = true;
		String dir = this.storageDirectory.getAbsolutePath();
		File wd = this.storageDirectory;
		if (!FileUtil.removeAll(wd)) {
			throw new Exception("Error removing old data folder.");
		}
		wd.mkdir();
		// NOTE: globalTestInit returns in ignored random source
		RandomSource r = NodeStarter.globalTestInit(dir, false, LogLevel.ERROR,
				"", true);

		// Make the network reproducible so we can easily compare different
		// routing options by specifying a seed.
		if (networkState > 0)
			this.networkState = networkState;
		else
			this.networkState = Math.abs(r.nextInt());
		DummyRandomSource random = new DummyRandomSource(this.networkState);

		Executor executor = new PooledExecutor();
		for (int i = 0; i < this.nodeCount; i++) {
			System.out.println("Creating node " + i);
			if (topologyOnly) {
				this.nodes[i] = new DummyNode(getPort(i));
			} else {
				this.nodes[i] = NodeStarter.createTestNode(getPort(i),
						getOpennetPort(i), dir, true, this.maxHTL,
						this.peerCount, 0 /* no dropped packets */, random,
						executor, 500 * this.nodeCount, 65536 * 100, true,
						ENABLE_SWAPPING, false, false, false,
						ENABLE_SWAP_QUEUEING, true, 0, ENABLE_FOAF,
						ENABLE_ANNOUNCEMENT, true, false, null, getTMCIPort(i));
			}
		}

		// Now link them up
		makeKleinbergNetwork(this.nodes, START_WITH_IDEAL_LOCATIONS,
				this.peerCount, FORCE_NEIGHBOUR_CONNECTIONS, random);

		finishFillingNetwork(this.nodes, this.peerCount, random);
	}

	public String getTopology() {
		if (!this.nodesStarted)
			return getTopologyOffline();
		StringBuilder b = new StringBuilder();
		for (INode n : this.nodes) {
			b.append(n.writeTMCIPeerFile(false, true));
		}
		if (b.length() > 0)
			b.deleteCharAt(b.length() - 1);
		return b.toString();
	}

	public INode getNodeByPort(int port) {
		for (INode n : this.nodes) {
			if (n.getOpennetFNPPort() == port)
				return n;
		}
		return null;
	}

	public String getNodeIDs() {
		StringBuilder b = new StringBuilder();
		for (int i = 0; i < this.nodeCount; i++) {
			b.append(this.getOpennetPort(i));
			b.append(":");
			b.append(this.getTMCIPort(i));
			b.append(":");
			b.append(this.getPort(i));
			b.append(":\n");
		}
		if (b.length() > 0)
			b.deleteCharAt(b.length() - 1);
		return b.toString();
	}

	public String getStoredDataInfo() {
		StringBuilder b = new StringBuilder();
		for (INode n : this.nodes) {
			b.append(n.getOpennetFNPPort());
			b.append(" ");
			b.append(n.writeChkDatastoreFileA());
			b.append("\n");
		}

		if (b.length() > 0)
			b.deleteCharAt(b.length() - 1);
		return b.toString();
	}

	private String getTopologyOffline() {
		StringBuilder b = new StringBuilder();
		for (INode n : this.nodes) {
			for (IOpennetPeerNode p : n.getOpennetPeers()) {
				INode n2 = this.getNodeByPort(p.getPort());
				if (n2 == null)
					continue;
				b.append("\"");
				b.append(n.getLocation());
				b.append("\t");
				b.append(n.getOpennetFNPPort());
				b.append("\"\t->\t\"");
				b.append(n2.getLocation());
				b.append("\t");
				b.append(n2.getOpennetFNPPort());
				b.append("\"\n");
			}
		}
		if (b.length() > 0)
			b.deleteCharAt(b.length() - 1);
		return b.toString();
	}

	private int getPort(int i) {
		if (this.portsFreenet[i] == 0)
			this.portsFreenet[i] = getNextOpenPort();
		return this.portsFreenet[i];
	}

	private int getOpennetPort(int i) {
		if (this.portsOpennet[i] == 0)
			this.portsOpennet[i] = getNextOpenPort();
		return this.portsOpennet[i];
	}

	private int getTMCIPort(int i) {
		if (this.portsTMCI[i] == 0)
			this.portsTMCI[i] = getNextOpenPort();
		return this.portsTMCI[i];
	}

	private void forceReconnect(INode[] nodes) {

		// just force all peers to reconnect
		// hack to get around a bug in Freenet
		// all nodes get connected, but not put in connect node array
		for (INode n : nodes) {
			if (n.countValidPeers() > n.countConnectedOpennetPeers()) {
				for (IOpennetPeerNode p : n.getOpennetPeers()) {
					p.forceDisconnect(true);
				}
			}
		}
	}

	private void finishFillingNetwork(INode[] nodes, int degree,
			RandomSource random) {
		// assuming the network is already seeded, now fill the holes with
		// random peers

		List<INode> openStill = new ArrayList<INode>();
		for (INode n : nodes) {
			if (n.countValidPeers() < degree)
				openStill.add(n);
		}

		int maxIterations = openStill.size() * 3;
		while (openStill.size() > 1 && maxIterations-- >= 0) {
			INode fill = openStill.get(0);
			openStill.remove(0);

			int peerIndex = random.nextInt(openStill.size());
			INode peer = openStill.get(peerIndex);
			openStill.remove(peerIndex);

			connectOpen(fill, peer);

			if (fill.countValidPeers() < degree)
				openStill.add(fill);
			if (peer.countValidPeers() < degree)
				openStill.add(peer);
		}
	}

	private int getNextOpenPort() {
		int current = this.basePort + this.portOffset;
		while (!isPortOpen(current)) {
			current++;
		}
		this.portOffset = (current - this.basePort) + 1;
		return current;
	}

	private boolean isPortOpen(int port) {
		ServerSocket ss = null;
		DatagramSocket ds = null;
		try {
			ss = new ServerSocket(port);
			ss.setReuseAddress(true);
			ds = new DatagramSocket(port);
			ds.setReuseAddress(true);
			return true;
		} catch (IOException e) {
		} finally {
			if (ds != null) {
				ds.close();
			}

			if (ss != null) {
				try {
					ss.close();
				} catch (IOException e) {
					/* should not be thrown */
				}
			}
		}

		return false;
	}

	public String experimentRoutePrecition(int insertCount) throws Exception {
		/*
		 * Steps for each node in the topology for 1 to insertCount check if
		 * topology has changed (stop) Insert a unique word Save the
		 * origin node Save the unique word Save word's key Save nodes with word
		 * stored Save insert path
		 */
		StringBuilder b = new StringBuilder();
		b.append(ExperimentRoutePredictionStats.toStringCSVHeader());
		b.append("\n");
		
		String originalTop = this.getTopology();

		int index = 0;
		String baseWord = "jabberwocky";
		for (int n = 0; n < this.nodeCount; n++) {
			for (int i = 0; i < insertCount; i++) {
				
				// check if topology has held (shouldn't change)
				if(!this.getTopology().equals(originalTop))
					return "Topology changed at experiment "+index;
				
				String word = baseWord + index;

				ExperimentRoutePredictionStats.reset();
				ExperimentRoutePredictionStats.getInstance().startInsert(
						index + "", this.nodeCount + "", this.peerCount + "",
						this.maxHTL + "", this.getOpennetPort(n) + "", word);

				// use the telnet interface to insert the word
				String result = this.sendSingleCommand(this.getTMCIPort(n),
						"PUT:" + word);
				if (result == null)
					continue;

				if (result == null)
					continue;
				
				// get word location
				Pattern pattern = java.util.regex.Pattern
				.compile("Double: [-+]?[0-9]*\\.[0-9]+([eE][-+]?[0-9]+)?");

				Matcher matcher = pattern.matcher(result);
				matcher.find();
				ExperimentRoutePredictionStats.getInstance().setWordLocation(matcher.group().split(" ")[1]);

				if (index > 0)
					b.append("\n");
				b.append(ExperimentRoutePredictionStats.getInstance()
						.toString());

				index++;
			}
		}

		return b.toString();
	}

	private String sendSingleCommand(int port, String command) {
		String result = null;
		try {
			Socket socket = new Socket("localhost", port);
			PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
			BufferedReader reader = new BufferedReader(new InputStreamReader(
					socket.getInputStream()));
			out.println(command);
			out.println("QUIT");
			String line = null;
			while ((line = reader.readLine()) != null)
				result += line + "\n";

			out.close();
			reader.close();
			socket.close();
		} catch (IOException e) {
			System.out.println(e.getMessage());
			return null;
		}
		return result;
	}
}
