package freenet.testbed;

import java.io.File;
import java.io.PrintWriter;

import freenet.testbed.commandControl.CommandServer;
import freenet.testbed.simulation.OpennetSimulator;

public class Simulator implements ISimulator {

	private int basePort;
	private File storageDirectory;
	private OpennetSimulator openSim = null;
	private static PrintWriter protocolWriter = null;

	private final int NEW_TOPOLOGY = -1;

	/**
	 * @param args
	 */
	public static void main(String[] args) {
		try {
			int port = 4600;
			if (args.length > 0)
				port = Integer.parseInt(args[0]);

			File storageDir = new File("simulation_data");
			if (args.length > 1)
				storageDir = new File(args[1]);

			File protocolTrace = new File("protocol.trace");
			if (args.length > 2)
				protocolTrace = new File(args[2]);

			new Simulator(port, storageDir, protocolTrace);
		} catch (Exception ex) {
			System.out.println("Error: " + ex.getMessage());
			System.exit(1);
		}
	}

	private Simulator(int port, File storageDir, File protocolTrace)
			throws Exception {
		this.basePort = port;
		this.storageDirectory = storageDir;
		Simulator.protocolWriter = new PrintWriter(protocolTrace);

		System.out.println("Starting simulator...");

		CommandServer command = new CommandServer(port, this);

		System.out.println("Listening on port " + port);
		System.out.println("Storing runtime data in "
				+ storageDir.getAbsolutePath());

		command.start();

		System.out.println("Closing simulator");
	}

	public static synchronized void writeProtocolTrace(int current,
			String message) {
		if (Simulator.protocolWriter != null) {
			Simulator.protocolWriter.println(current + " : " + message);
			Simulator.protocolWriter.flush();
		}
		System.out.println(current + "," + message);
	}

	public boolean startSimulator(int nodeCount, int peerCount, short maxHTL)
			throws Exception {
		if (this.openSim != null)
			return false;
		this.openSim = new OpennetSimulator(nodeCount, peerCount, maxHTL,
				this.basePort + 1, this.storageDirectory);
		this.openSim.startNetwork(NEW_TOPOLOGY);
		return true;
	}

	public boolean genTopologyOnly(int nodeCount, int peerCount, short maxHTL)
			throws Exception {
		if (this.openSim != null)
			return false;
		this.openSim = new OpennetSimulator(nodeCount, peerCount, maxHTL,
				this.basePort + 1, this.storageDirectory);
		this.openSim.genTopology(NEW_TOPOLOGY, true);
		return true;
	}

	public int getNetworkState() {
		return this.openSim.getNetworkState();
	}

	public boolean restoreSimulator(int nodeCount, int peerCount, short maxHTL,
			int networkState) throws Exception {
		if (this.openSim != null)
			return false;
		this.openSim = new OpennetSimulator(nodeCount, peerCount, maxHTL,
				this.basePort + 1, this.storageDirectory);
		this.openSim.startNetwork(networkState);
		return true;
	}

	public String getTopology() {
		return openSim.getTopology();
	}

	public String getNodeIDs() {
		return openSim.getNodeIDs();
	}

	public void exit() {
		writeProtocolTrace(0, "Exiting simulator!!");
		System.exit(0);
	}

	public String getStoredDataInfo() {
		return this.openSim.getStoredDataInfo();
	}

	public boolean experimentRoutePrecition(int insertCount, String outFileName)
			throws Exception {
		if (this.openSim == null)
			throw new Exception( "START or RESTORE an network before running experiment.");
		return this.openSim.experimentRoutePrecition(insertCount, outFileName);
	}

	public String experimentRoutePrecitionDone() throws Exception  {
		if (this.openSim == null)
			return "START or RESTORE an network before running experiment.";
		return this.openSim.experimentRoutePrecitionDone();
	}

}
