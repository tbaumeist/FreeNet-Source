package freenet.testbed;

public interface ISimulator {
	public boolean startSimulator(int nodeCount, int peerCount, short maxHTL)
			throws Exception;

	public boolean genTopologyOnly(int nodeCount, int peerCount, short maxHTL) throws Exception;

	public String experimentRoutePrecition(int insertCount) throws Exception;

	public int getNetworkState();

	public boolean restoreSimulator(int nodeCount, int peerCount, short maxHTL,
			int networkState) throws Exception;

	public String getTopology();

	public String getNodeIDs();

	public String getStoredDataInfo();

	public void exit();
}
