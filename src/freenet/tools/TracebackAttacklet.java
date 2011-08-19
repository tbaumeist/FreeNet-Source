package freenet.tools;

import freenet.io.comm.ByteCounter;
import freenet.io.comm.DMT;
import freenet.io.comm.Message;
import freenet.io.comm.MessageFilter;
import freenet.io.comm.NotConnectedException;
import freenet.keys.NodeCHK;
import freenet.node.*;
import freenet.support.Logger;

public class TracebackAttacklet {
	
	private Node node;
	
	public TracebackAttacklet(Node n)
	{
		node = n;
	}
	
	public void attack(long uid, StringBuilder status)
	{
		boolean isSSK = false;
		ByteCounter ctr = isSSK ? node.nodeStats.sskRequestCtr : node.nodeStats.chkRequestCtr;
		
		// dummy routing key
		byte[] routingKey = new byte[32];
		byte cryptoAlgorithm = 0;
		NodeCHK nodeChk = new NodeCHK(routingKey, cryptoAlgorithm);
		
		// us first
		String ip = "127.0.0.1";
		if(	node.ipDetector != null && node.ipDetector.lastIPAddress.length != 0)
			ip = node.ipDetector.lastIPAddress[0].getAddress().toString().replace("/","");
		ip += ":"+node.getOpennetFNPPort();
		
		status.append("Result:"+ ip +":");
		if(node.recentlyCompleted(uid))
			status.append("true\n");
		else
			status.append("false\n");
		
		// try peers
		Message m = DMT.createFNPCHKDataRequest(uid, (short)0, nodeChk);
		OpennetPeerNode[] neighbors = node.peers.getOpennetPeers();
		for( OpennetPeerNode n : neighbors)
		{
			try {
				
				n.sendSync(m, ctr);
				
				status.append("Result:"+ n.getPeer()+":");
				MessageFilter mfAccepted = MessageFilter.create().setSource(n).setField(DMT.UID, uid).setTimeout(5000).setType(DMT.FNPRejectedLoop);
				Message msg = node.usm.waitFor(mfAccepted, null);
				if (msg==null || (msg.getSpec() != DMT.FNPRejectedLoop)) {
					status.append("false\n");
					continue;
				}
				status.append("true\n");
			} catch (Exception e) {
			}
		}
		status.append("Attack_Complete\n");
	}
}
