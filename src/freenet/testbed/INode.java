package freenet.testbed;

import freenet.io.comm.PeerParseException;
import freenet.io.comm.ReferenceSignatureVerificationException;
import freenet.node.FSParseException;
import freenet.node.NodeInitException;
import freenet.node.OpennetPeerNode;
import freenet.node.OpennetManager.ConnectionType;
import freenet.support.SimpleFieldSet;

public interface INode {
	void start(boolean noSwaps) throws NodeInitException;
	double getLocation();
	int getOpennetFNPPort();
	void setLocation(double loc);
	OpennetPeerNode addNewOpennetNode(SimpleFieldSet fs, ConnectionType connectionType) throws FSParseException, PeerParseException, ReferenceSignatureVerificationException;
	SimpleFieldSet exportOpennetPublicFieldSet();
	String writeTMCIPeerFile(boolean allPeers, boolean simulation);
	String writeChkDatastoreFileA();
	
	IOpennetPeerNode[] getOpennetPeers();
	int countValidPeers();
	int countConnectedOpennetPeers();
	int countBackedOffPeers();
	double getNodeAveragePingTime();
}
