package freenet.testbed;

import java.io.File;

import freenet.testbed.commandControl.CommandServer;
import freenet.testbed.simulation.OpennetSimulator;

public class Simulator implements ISimulator {

	private int basePort;
	private File storageDirectory;
	private OpennetSimulator openSim = null;

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

			new Simulator(port, storageDir);
		} catch (Exception ex) {
			System.out.println("Error: " + ex.getMessage());
			System.exit(1);
		}
	}

	private Simulator(int port, File storageDir) throws Exception {
		this.basePort = port;
		this.storageDirectory = storageDir;

		System.out.println("Starting simulator...");

		CommandServer command = new CommandServer(port, this);

		System.out.println("Listening on port " + port);
		System.out.println("Storing runtime data in " + storageDir.getAbsolutePath());

		command.start();

		System.out.println("Closing simulator");
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
		System.out.println("Exiting simulator!!");
		System.exit(0);
	}

}
