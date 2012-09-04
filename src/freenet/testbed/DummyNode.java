package freenet.testbed;

import java.util.ArrayList;
import java.util.List;

import freenet.io.comm.PeerParseException;
import freenet.io.comm.ReferenceSignatureVerificationException;
import freenet.node.FSParseException;
import freenet.node.NodeInitException;
import freenet.node.OpennetPeerNode;
import freenet.node.OpennetManager.ConnectionType;
import freenet.support.SimpleFieldSet;

public class DummyNode implements INode {

	private double location = 0;
	private int portNumber = 0;
	private List<IOpennetPeerNode> peerNodes = new ArrayList<IOpennetPeerNode>();
	
	private final String LOCATION = "location";
	private final String PORT = "port";
	
	public DummyNode(int port){
		this.portNumber = port;
	}
	
	public OpennetPeerNode addNewOpennetNode(SimpleFieldSet fs,
			ConnectionType connectionType) throws FSParseException,
			PeerParseException, ReferenceSignatureVerificationException {

		int peerPort = fs.getInt(PORT);
		DummyOpennetNode dummy = new DummyOpennetNode(peerPort);
		if(!this.peerNodes.contains(dummy))
			this.peerNodes.add(dummy);
		
		return null;
	}

	public int countBackedOffPeers() {
		return 0;
	}

	public int countConnectedOpennetPeers() {
		return this.peerNodes.size();
	}

	public int countValidPeers() {
		return this.peerNodes.size();
	}

	public SimpleFieldSet exportOpennetPublicFieldSet() {
		SimpleFieldSet set = new SimpleFieldSet(true);
		set.put(LOCATION, this.getLocation());
		set.put(PORT, this.getOpennetFNPPort());
		return set;
	}

	public double getLocation() {
		return this.location;
	}

	public double getNodeAveragePingTime() {
		return 0;
	}

	public int getOpennetFNPPort() {
		return this.portNumber;
	}

	public IOpennetPeerNode[] getOpennetPeers() {
		IOpennetPeerNode[] peers = new IOpennetPeerNode[this.peerNodes.size()];
		return this.peerNodes.toArray(peers);
	}

	public void setLocation(double loc) {
		this.location = loc;
	}

	public void start(boolean noSwaps) throws NodeInitException {
		// Do nothing
	}

	public String writeChkDatastoreFileA() {
		return null;
	}

	public String writeTMCIPeerFile(boolean allPeers, boolean simulation) {
		return null;
	}

}
