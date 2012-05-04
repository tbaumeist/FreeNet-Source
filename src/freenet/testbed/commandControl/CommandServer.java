package freenet.testbed.commandControl;

import java.net.ServerSocket;
import java.net.Socket;

import freenet.testbed.ISimulator;

public class CommandServer {
	private ServerSocket serverSocket = null;
	private ISimulator simulator;

	public CommandServer(int port, ISimulator sim) throws Exception {
		this.serverSocket = new ServerSocket(port);
		this.simulator = sim;
	}

	public void start() throws Exception {
		listen();
	}

	private void listen() throws Exception {
		while (true) {
			Socket s = serverSocket.accept();
			if (s == null)
				continue; // timeout

			CommandInterpreter interp = new CommandInterpreter(s,
					this.simulator);
			interp.start();
		}
	}
}
