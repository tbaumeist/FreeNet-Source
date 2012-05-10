package freenet.testbed.simulation;

import java.io.File;
import java.io.IOException;
import java.net.DatagramSocket;
import java.net.ServerSocket;
import java.util.ArrayList;
import java.util.List;

import freenet.crypt.DummyRandomSource;
import freenet.crypt.RandomSource;
import freenet.node.Node;
import freenet.node.NodeStarter;
import freenet.node.PeerNode;
import freenet.node.simulator.RealNodeRoutingTest;
import freenet.node.simulator.RealNodeTest;
import freenet.support.Executor;
import freenet.support.Logger;
import freenet.support.PooledExecutor;
import freenet.support.Logger.LogLevel;
import freenet.support.io.FileUtil;

public class OpennetSimulator extends RealNodeTest {
	private int nodeCount, peerCount, basePort;
	private short maxHTL;
	private Node[] nodes;
	private int networkState;
	private File storageDirectory;
	
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
		this.nodes = new Node[this.nodeCount];
		this.storageDirectory = storageDir;
		this.portsFreenet = new int[this.nodeCount];
		this.portsOpennet = new int[this.nodeCount];
		this.portsTMCI = new int[this.nodeCount];
	}

	public int getNetworkState() {
		return this.networkState;
	}

	public void startNetwork(int networkState) throws Exception {
		String dir = this.storageDirectory.getAbsolutePath();
		File wd = this.storageDirectory;
		if (!FileUtil.removeAll(wd)) {
			throw new Exception("Error removing old data folder.");
		}
		wd.mkdir();
		// NOTE: globalTestInit returns in ignored random source
		RandomSource r = NodeStarter.globalTestInit(
				dir, false, LogLevel.ERROR, "",
				true);

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
			this.nodes[i] = NodeStarter.createTestNode(getPort(i),
					getOpennetPort(i), dir, true, this.maxHTL, this.peerCount,
					0 /* no dropped packets */, random, executor,
					500 * this.nodeCount, 65536 * 100, true, ENABLE_SWAPPING, false,
					false, false, ENABLE_SWAP_QUEUEING, true, 0, ENABLE_FOAF,
					ENABLE_ANNOUNCEMENT, true, false, null, getTMCIPort(i));
		}

		// Now link them up
		makeKleinbergNetwork(this.nodes, START_WITH_IDEAL_LOCATIONS,
				this.peerCount, FORCE_NEIGHBOUR_CONNECTIONS, random);

		finishFillingNetwork(this.nodes, this.peerCount, random);

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

	public String getTopology() {
		StringBuilder b = new StringBuilder();
		for (Node n : this.nodes) {
			b.append(n.writeTMCIPeerFile(false, true));
		}
		if(b.length() > 0)
			b.deleteCharAt(b.length() - 1);
		return b.toString();
	}

	public Node getNodeByPort(int port) {
		for (Node n : this.nodes) {
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
		if(b.length() > 0)
			b.deleteCharAt(b.length()-1);
		return b.toString();
	}
	
	public String getStoredDataInfo() {
		StringBuilder b = new StringBuilder();
		for(Node n : this.nodes){
			b.append(n.getOpennetFNPPort());
			b.append(" ");
			b.append(n.writeChkDatastoreFileA());
			b.append("\n");
		}
		
		if(b.length() > 0)
			b.deleteCharAt(b.length()-1);
		return b.toString();
	}

	private int getPort(int i) {
		if(this.portsFreenet[i] == 0)
			this.portsFreenet[i] = getNextOpenPort();
		return this.portsFreenet[i];
	}

	private int getOpennetPort(int i) {
		if(this.portsOpennet[i] == 0)
			this.portsOpennet[i] = getNextOpenPort();
		return this.portsOpennet[i];
	}

	private int getTMCIPort(int i) {
		if(this.portsTMCI[i] == 0)
			this.portsTMCI[i] = getNextOpenPort();
		return this.portsTMCI[i];
	}

	private void forceReconnect(Node[] nodes) {

		// just force all peers to reconnect
		// hack to get around a bug in Freenet
		// all nodes get connected, but not put in connect node array
		for (Node n : nodes) {
			if (n.peers.countValidPeers() > n.peers
					.countConnectedOpennetPeers()) {
				for (PeerNode p : n.peers.getOpennetPeers()) {
					p.forceDisconnect(true);
				}
			}
		}
	}

	private void finishFillingNetwork(Node[] nodes, int degree,
			RandomSource random) {
		// assuming the network is already seeded, now fill the holes with
		// random peers

		List<Node> openStill = new ArrayList<Node>();
		for (Node n : nodes) {
			if (n.peers.countValidPeers() < degree)
				openStill.add(n);
		}

		int maxIterations = openStill.size() * 3;
		while (openStill.size() > 1 && maxIterations-- >= 0) {
			Node fill = openStill.get(0);
			openStill.remove(0);

			int peerIndex = random.nextInt(openStill.size());
			Node peer = openStill.get(peerIndex);
			openStill.remove(peerIndex);

			connectOpen(fill, peer);

			if (fill.peers.countValidPeers() < degree)
				openStill.add(fill);
			if (peer.peers.countValidPeers() < degree)
				openStill.add(peer);
		}
	}
	
	private int getNextOpenPort(){
		int current = this.basePort + this.portOffset;
		while( ! isPortOpen(current)){
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

}
