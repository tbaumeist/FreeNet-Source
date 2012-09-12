package freenet.testbed.simulation;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class RoutePredictionThread extends Thread {
	private int insertCount;
	private String outFileName;
	private OpennetSimulator openSim;

	public RoutePredictionThread(OpennetSimulator sim, int insertCount,
			String outFileName) {
		this.openSim = sim;
		this.insertCount = insertCount;
		this.outFileName = outFileName;
	}

	@Override
	public void run() {
		/*
		 * Steps for each node in the topology for 1 to insertCount check if
		 * topology has changed (stop) Insert a unique word Save the origin
		 * node Save the unique word Save word's key Save nodes with word
		 * stored Save insert path
		 */
		try {
			PrintWriter b = new PrintWriter(new File(this.outFileName));
			// StringBuilder b = new StringBuilder();
			b.append(ExperimentRoutePredictionStats.toStringCSVHeader());
			b.append("\n");

			String originalTop = this.openSim.getTopology();

			int index = 0;
			int randomStart = (int)(Math.random() * 20000);
			String baseWord = "jabberwocky";
			for (int n = 0; n < this.openSim.getNodeCount(); n++) {
				for (int i = 0; i < insertCount; i++) {

					// check if topology has held (shouldn't change)
					if (!this.openSim.getTopology().equals(originalTop))
						throw new Exception(
								"Topology changed at experiment " + index);

					String word = baseWord + randomStart + index;

					ExperimentRoutePredictionStats.reset();
					ExperimentRoutePredictionStats.getInstance()
							.startInsert(index + "",
									this.openSim.getNodeCount() + "",
									this.openSim.getPeerCount() + "",
									this.openSim.getMaxHTL() + "",
									this.openSim.getOpennetPort(n) + "",
									word);

					// use the telnet interface to insert the word
					String result = this.sendSingleCommand(this.openSim
							.getTMCIPort(n), "PUT:" + word);
					if (result == null)
						continue;

					if (result == null)
						continue;

					// get word location
					Pattern pattern = java.util.regex.Pattern
							.compile("Double: [-+]?[0-9]*\\.[0-9]+([eE][-+]?[0-9]+)?");

					Matcher matcher = pattern.matcher(result);
					matcher.find();
					ExperimentRoutePredictionStats.getInstance()
							.setWordLocation(matcher.group().split(" ")[1]);

					if (index > 0)
						b.append("\n");
					b.append(ExperimentRoutePredictionStats.getInstance()
							.toString());
					b.flush();

					index++;
				}
			}
			b.close();
		} catch (Exception ex) {
			System.out.println( ex.getMessage() );
		}
	}

	private String sendSingleCommand(int port, String command) {
		String result = null;
		try {
			Socket socket = new Socket("localhost", port);
			PrintWriter out = new PrintWriter(socket.getOutputStream(),
					true);
			BufferedReader reader = new BufferedReader(
					new InputStreamReader(socket.getInputStream()));
			out.println(command);
			out.println("QUIT");
			String line = null;
			while ((line = reader.readLine()) != null)
				result += line + "\n";

			out.close();
			reader.close();
			socket.close();
		} catch (IOException e) {
			System.out.println(e.getMessage());
			return null;
		}
		return result;
	}
}
