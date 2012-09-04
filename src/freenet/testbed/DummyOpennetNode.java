package freenet.testbed;

public class DummyOpennetNode implements IOpennetPeerNode {

	private int peerPort = 0;
	
	public DummyOpennetNode(int peerPort){
		this.peerPort = peerPort;
	}
	
	public void forceDisconnect(boolean purge) {
		// Do nothing
	}

	public int getPort() {
		return this.peerPort;
	}
	
	@Override
	public boolean equals(Object obj){
		if (obj == null)
			return false;
		if (this == obj)
			return true;

		if (!(obj instanceof DummyOpennetNode))
			return false;
		DummyOpennetNode node = (DummyOpennetNode) obj;
		return this.getPort() == node.getPort();
	}

}
