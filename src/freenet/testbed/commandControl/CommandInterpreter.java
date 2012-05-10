package freenet.testbed.commandControl;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.net.*;

import freenet.testbed.ISimulator;

public class CommandInterpreter extends Thread {

	private Socket socket;
	private Command[] commands;
	private PrintWriter output;
	private ISimulator simulator;
	private boolean closeConnection = false;
	private boolean exitProgram = false;

	private final String STATUS_SUCCESS = "STATUS:SUCCESS";
	private final String STATUS_FAILED = "STATUS:FAILED";

	public CommandInterpreter(Socket s, ISimulator sim) throws Exception {
		this.socket = s;
		this.output = new PrintWriter(this.socket.getOutputStream(), true);
		this.simulator = sim;
		commands = new Command[] {
				new Command("help", 0, "List all available commands.") {
					public boolean action() {
						return printCommands();
					}
				},
				new Command("topology", 0,
						"Get the topology of the active network.") {
					public boolean action() {
						writeOutput(simulator.getTopology());
						return true;
					}
				},
				new Command("status", 0,
						"Get the status of the current simulation."),
				new Command("start", 3,
						"[node count] [peer count] [max HTL] Start the simulation environment.") {
					public boolean action() throws Exception {
						writeOutput("Starting ...");
						long startTime = System.currentTimeMillis();

						Boolean stat = simulator.startSimulator(getParameterInt(0),
								getParameterInt(1), (short) getParameterInt(2));
						
						long totalTime = System.currentTimeMillis() - startTime;
						writeOutput("Start up time seconds:"+ (totalTime/1000));
						return stat;
					}
				},
				new Command("networkstate", 0,
						"Get the network state of the current simulation.") {
					public boolean action() throws Exception {
						writeOutput("STATE:" + simulator.getNetworkState());
						return true;
					}
				},
				new Command(
						"restore",
						4,
						"[node count] [peer count] [max HTL] [Network State] Start the simulation environment.") {
					public boolean action() throws Exception {
						writeOutput("Restoring ...");
						return simulator.restoreSimulator(getParameterInt(0),
								getParameterInt(1), (short) getParameterInt(2),
								getParameterInt(3));
					}
				},
				new Command("listnodes", 0,
						"List all of the node ids.(Opennet Port:TMCI Port:Darknet Port:).") {
					public boolean action() throws Exception {
						writeOutput(simulator.getNodeIDs());
						return true;
					}
				},
				new Command("liststoreddata", 0,
						"List all of the data stored in the nodes.") {
					public boolean action() throws Exception {
						writeOutput(simulator.getStoredDataInfo());
						return true;
					}
				},
				new Command("close", 0,
						"Close the connection to the simulation environment.") {
					public boolean action() throws Exception {
						closeConnection = true;
						return true;
					}
				},
				new Command("quit", 0,
						"Close the connection to the simulation environment.") {
					public boolean action() throws Exception {
						closeConnection = true;
						return true;
					}
				},
				new Command("shutdown", 0,
						"Shutdown the simulation environment.") {
					public boolean action() throws Exception {
						exitProgram = true;
						String pName = ManagementFactory.getRuntimeMXBean()
								.getName().split("@")[0];
						writeOutput("PID:" + pName);
						return true;
					}
				} };
	}

	private boolean printCommands() {
		for (Command c : this.commands)
			writeCommand(c.toString());
		return true;
	}

	@Override
	public void start() {
		try {
			// System.out.println("Connection open");
			BufferedReader input = new BufferedReader(new InputStreamReader(
					this.socket.getInputStream()));

			printCommands();
			writeCommand("SIM>");

			String command;
			while (!this.exitProgram && !this.closeConnection
					&& (command = input.readLine()) != null) {
				processCommand(command);
				writeCommand("SIM>");
				this.output.flush();
			}
			this.socket.close();

			if (this.exitProgram)
				this.simulator.exit();
		} catch (Exception ex) {
			System.out.println("Error communicating on command interface "
					+ ex.getMessage());
		}
	}

	private void processCommand(String command) {
		try {
			for (Command c : this.commands) {
				if (c.isCommand(command)) {
					if (c.action())
						this.writeCommand(STATUS_SUCCESS);
					else
						this.writeCommand(STATUS_FAILED);
					System.out.println("Processed command: "
							+ c.getCommandName());
					return;
				}
			}

			this.writeOutput("Command not found");
			this.writeCommand(STATUS_FAILED);
		} catch (Exception ex) {
			System.out.println("Error processing command " + command + " : "
					+ ex.getMessage());
			this.writeCommand(STATUS_FAILED);
		}
	}

	private void writeOutput(String s) {
		this.output.println("\t" + s.replace("\n", "\n\t"));
	}

	private void writeCommand(String s) {
		this.output.println(s);
	}

}
