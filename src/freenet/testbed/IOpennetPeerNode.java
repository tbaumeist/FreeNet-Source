package freenet.testbed;

public interface IOpennetPeerNode {
	void forceDisconnect(boolean purge);
	
	int getPort();
}
