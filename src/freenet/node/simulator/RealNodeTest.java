/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package freenet.node.simulator;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;

import freenet.crypt.RandomSource;
import freenet.io.comm.PeerParseException;
import freenet.node.FSParseException;
import freenet.node.Location;
import freenet.node.Node;
import freenet.node.NodeInitException;
import freenet.node.NodeStats;
import freenet.node.OpennetDisabledException;
import freenet.node.OpennetManager.ConnectionType;
import freenet.node.OpennetPeerNode;
import freenet.node.PeerNode;
import freenet.support.LogThresholdCallback;
import freenet.support.Logger;
import freenet.support.Logger.LogLevel;
import freenet.testbed.INode;

/**
 * Optional base class for RealNode*Test.
 * Has some useful utilities.
 * @author toad
 * @author robert
 */
public class RealNodeTest {

	static final int EXIT_BASE = NodeInitException.EXIT_NODE_UPPER_LIMIT;
	protected static final int EXIT_CANNOT_DELETE_OLD_DATA = EXIT_BASE + 3;
	static final int EXIT_PING_TARGET_NOT_REACHED = EXIT_BASE + 4;
	static final int EXIT_INSERT_FAILED = EXIT_BASE + 5;
	static final int EXIT_REQUEST_FAILED = EXIT_BASE + 6;
	static final int EXIT_BAD_DATA = EXIT_BASE + 7;

        private static volatile boolean logMINOR;
	static {
		Logger.registerLogThresholdCallback(new LogThresholdCallback(){
			@Override
			public void shouldUpdate(){
				logMINOR = Logger.shouldLog(LogLevel.MINOR, this);
			}
		});
	}
	
	/* Because we start a whole bunch of nodes at once, we will get many "Not reusing
	 * tracker, so wiping old trackers" messages. This is normal, all the nodes start
	 * handshaking straight off, they all send JFK(1)s, and we get race conditions. */
	
	/*
	 Borrowed from mrogers simulation code (February 6, 2008)
	 --
	 FIXME: May not generate good networks. Presumably this is because the arrays are always scanned
	        [0..n], some nodes tend to have *much* higher connections than the degree (the first few),
	        starving the latter ones.
	 */
	protected static void makeKleinbergNetwork (INode[] nodes, boolean idealLocations, int degree, boolean forceNeighbourConnections, RandomSource random)
	{
		if(idealLocations) {
			// First set the locations up so we don't spend a long time swapping just to stabilise each network.
			double div = 1.0 / nodes.length;
			double loc = 0.0;
			
			DecimalFormat twoDForm = new DecimalFormat("0.00000");
			for (int i=0; i<nodes.length; i++) {
				nodes[i].setLocation(Double.valueOf(twoDForm.format(loc)));
				loc += div;
			}
		}
		if(forceNeighbourConnections) {
			for(int i=0;i<nodes.length;i++) {
				int next = (i+1) % nodes.length;
				connectOpen(nodes[i], nodes[next]);
			}
		}
		for (int i=0; i<nodes.length; i++) {
			INode a = nodes[i];
			
			// Normalise the probabilities
			double norm = 0.0;
			for (int j=0; j<nodes.length; j++) {
				INode b = nodes[j];
				if (a.getLocation() == b.getLocation()) continue;
				norm += 1.0 / distance (a, b);
			}
			// Create degree/2 outgoing connections
			for (int k=0; k<nodes.length; k++) {
				INode b = nodes[k];
				if (a.getLocation() == b.getLocation()) continue;
				double p = 1.0 / distance (a, b) / norm;
				for (int n = 0; n < degree / 2; n++) {
					if (random.nextFloat() < p && 
							a.countValidPeers() < degree &&
							b.countValidPeers() < degree){
						connectOpen(a, b);
						break;
					}
				}
			}
		}
	}
	
	protected static void connectOpen(INode a, INode b) {
		try {
			a.addNewOpennetNode(b.exportOpennetPublicFieldSet(), ConnectionType.ANNOUNCE);
			b.addNewOpennetNode(a.exportOpennetPublicFieldSet(), ConnectionType.ANNOUNCE);
		} catch (FSParseException e) {
			Logger.error(RealNodeSecretPingTest.class, "cannot connect!!!!", e);
		} catch (PeerParseException e) {
			Logger.error(RealNodeSecretPingTest.class, "cannot connect #2!!!!", e);
		} catch (freenet.io.comm.ReferenceSignatureVerificationException e) {
			Logger.error(RealNodeSecretPingTest.class, "cannot connect #3!!!!", e);
		}
	}
	
	static void connect(Node a, Node b) {
		try {
			a.connect (b);
			b.connect (a);
		} catch (FSParseException e) {
			Logger.error(RealNodeSecretPingTest.class, "cannot connect!!!!", e);
		} catch (PeerParseException e) {
			Logger.error(RealNodeSecretPingTest.class, "cannot connect #2!!!!", e);
		} catch (freenet.io.comm.ReferenceSignatureVerificationException e) {
			Logger.error(RealNodeSecretPingTest.class, "cannot connect #3!!!!", e);
		}
	}
	
	static double distance(INode a, INode b) {
		double aL=a.getLocation();
		double bL=b.getLocation();
		return Location.distance(aL, bL);
	}
	
	static String getPortNumber(PeerNode p) {
		if (p == null || p.getPeer() == null)
			return "null";
		return Integer.toString(p.getPeer().getPort());
	}
	
	static String getPortNumber(Node n) {
		if (n == null)
			return "null";
		return Integer.toString(n.getDarknetPortNumber());
	}
	
	protected static boolean waitForAllConnectedOpen(INode[] nodes) throws InterruptedException, NodeInitException {
		long tStart = System.currentTimeMillis();
		
		int disconnectTime = 1 * nodes.length;
		
		while(true) {
			int countFullyConnected = 0;
			int countReallyConnected = 0;
			int totalPeers = 0;
			int totalConnections = 0;
			int totalBackedOff = 0;
			double totalPingTime = 0.0;
			double maxPingTime = 0.0;
			double minPingTime = Double.MAX_VALUE;
			
			for(int i=0;i<nodes.length;i++) {
				int countConnected = nodes[i].countConnectedOpennetPeers();
				int countTotal = nodes[i].countValidPeers();
				int countBackedOff = nodes[i].countBackedOffPeers();
				totalPeers += countTotal;
				totalConnections += countConnected;
				totalBackedOff += countBackedOff;
				double pingTime = nodes[i].getNodeAveragePingTime();
				totalPingTime += pingTime;
				if(pingTime > maxPingTime) maxPingTime = pingTime;
				if(pingTime < minPingTime) minPingTime = pingTime;
				if(countConnected == countTotal) {
					countFullyConnected++;
					if(countBackedOff == 0) countReallyConnected++;
				} else {
					if(logMINOR)
						Logger.minor(RealNodeTest.class, "Connection count for "+nodes[i]+" : "+countConnected);
				}
				if(countBackedOff > 0) {
					if(logMINOR)
						Logger.minor(RealNodeTest.class, "Backed off: "+nodes[i]+" : "+countBackedOff);
				}
			}
			double avgPingTime = totalPingTime / nodes.length;
			if(countFullyConnected == nodes.length && countReallyConnected == nodes.length && totalBackedOff == 0 &&
					minPingTime < NodeStats.DEFAULT_SUB_MAX_PING_TIME && maxPingTime < NodeStats.DEFAULT_SUB_MAX_PING_TIME && avgPingTime < NodeStats.DEFAULT_SUB_MAX_PING_TIME) {
				System.err.println("All nodes fully connected");
				Logger.normal(RealNodeTest.class, "All nodes fully connected");
				System.err.println();
				return true;
			} else {
				long tDelta = (System.currentTimeMillis() - tStart)/1000;	
				
				if(tDelta > disconnectTime && countFullyConnected != nodes.length){
					return false;
				}
				
				System.err.println("Waiting for nodes to be fully connected: "+countFullyConnected+" / "+nodes.length+" ("+totalConnections+" / "+totalPeers+" connections total) - backed off "+totalBackedOff+" ping min/avg/max "+(int)minPingTime+"/"+(int)avgPingTime+"/"+(int)maxPingTime+" at "+tDelta+'s');
				Logger.normal(RealNodeTest.class, "Waiting for nodes to be fully connected: "+countFullyConnected+" / "+nodes.length+" ("+totalConnections+" / "+totalPeers+" connections total) - backed off "+totalBackedOff+" ping min/avg/max "+(int)minPingTime+"/"+(int)avgPingTime+"/"+(int)maxPingTime+" at "+tDelta+'s');
				Thread.sleep(1000);
			}
		}
	}
	
	protected static void waitForAllConnected(Node[] nodes) throws InterruptedException {
		long tStart = System.currentTimeMillis();
		while(true) {
			int countFullyConnected = 0;
			int countReallyConnected = 0;
			int totalPeers = 0;
			int totalConnections = 0;
			int totalPartialConnections = 0;
			int totalCompatibleConnections = 0;
			int totalBackedOff = 0;
			double totalPingTime = 0.0;
			double maxPingTime = 0.0;
			double minPingTime = Double.MAX_VALUE;
			for(int i=0;i<nodes.length;i++) {
				int countConnected = nodes[i].peers.countConnectedDarknetPeers();
				int countAlmostConnected = nodes[i].peers.countAlmostConnectedDarknetPeers();
				int countTotal = nodes[i].peers.countValidPeers();
				int countBackedOff = nodes[i].peers.countBackedOffPeers();
				int countCompatible = nodes[i].peers.countCompatibleDarknetPeers();

				totalPeers += countTotal;
				totalConnections += countConnected;
				totalPartialConnections += countAlmostConnected;
				totalCompatibleConnections += countCompatible;
				totalBackedOff += countBackedOff;
				double pingTime = nodes[i].nodeStats.getNodeAveragePingTime();
				totalPingTime += pingTime;
				if(pingTime > maxPingTime) maxPingTime = pingTime;
				if(pingTime < minPingTime) minPingTime = pingTime;
				if(countConnected == countTotal) {
					countFullyConnected++;
					if(countBackedOff == 0) countReallyConnected++;
				} else {
					if(logMINOR)
						Logger.minor(RealNodeTest.class, "Connection count for "+nodes[i]+" : "+countConnected+" partial N/A");
				}
				if(countBackedOff > 0) {
					if(logMINOR)
						Logger.minor(RealNodeTest.class, "Backed off: "+nodes[i]+" : "+countBackedOff);
				}
			}
			double avgPingTime = totalPingTime / nodes.length;
			if(countFullyConnected == nodes.length && countReallyConnected == nodes.length && totalBackedOff == 0 &&
					minPingTime < NodeStats.DEFAULT_SUB_MAX_PING_TIME && maxPingTime < NodeStats.DEFAULT_SUB_MAX_PING_TIME && avgPingTime < NodeStats.DEFAULT_SUB_MAX_PING_TIME) {
				System.err.println("All nodes fully connected");
				Logger.normal(RealNodeTest.class, "All nodes fully connected");
				System.err.println();
				return;
			} else {
				long tDelta = (System.currentTimeMillis() - tStart)/1000;
				System.err.println("Waiting for nodes to be fully connected: "+countFullyConnected+" / "+nodes.length+" ("+totalConnections+" / "+totalPeers+" connections total partial N/A compatible N/A) - backed off "+totalBackedOff+" ping min/avg/max "+(int)minPingTime+"/"+(int)avgPingTime+"/"+(int)maxPingTime+" at "+tDelta+'s');
				Logger.normal(RealNodeTest.class, "Waiting for nodes to be fully connected: "+countFullyConnected+" / "+nodes.length+" ("+totalConnections+" / "+totalPeers+" connections total partial N/A compatible N/A) - backed off "+totalBackedOff+" ping min/avg/max "+(int)minPingTime+"/"+(int)avgPingTime+"/"+(int)maxPingTime+" at "+tDelta+'s');
				Thread.sleep(1000);
			}
		}
	}

}
