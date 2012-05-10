/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package freenet.node;

import freenet.io.comm.AsyncMessageCallback;
import freenet.io.comm.ByteCounter;
import freenet.io.comm.DMT;
import freenet.io.comm.Message;
import freenet.support.Logger;
import freenet.testbed.Simulator;

/** A queued byte[], maybe including a Message, and a callback, which may be null.
 * Note that we always create the byte[] on construction, as almost everywhere
 * which uses a MessageItem needs to know its length immediately. */
public class MessageItem {

	final Message msg;
	final byte[] buf;
	final AsyncMessageCallback[] cb;
	final long submitted;
	/** If true, the buffer may contain several messages, and is formatted
	 * for sending as a single packet.
	 */
	final boolean formatted;
	final ByteCounter ctrCallback;
	private final short priority;
	private long cachedID;
	private boolean hasCachedID;

	public MessageItem(Message msg2, AsyncMessageCallback[] cb2, ByteCounter ctr, PeerNode pn, Node n) {
		this.msg = msg2;
		this.cb = cb2;
		formatted = false;
		this.ctrCallback = ctr;
		this.submitted = System.currentTimeMillis();
		priority = msg2.getSpec().getPriority();
		buf = msg.encodeToPacket(pn, n.getLocation(), pn.getLocation());
		
		if(msg.getSpec() == DMT.FNPVoid)
			return;
		
		StringBuilder b = new StringBuilder();
		b.append(pn.getPeer().getPort());
		b.append(",");
		if(msg.isSet(DMT.UID))
			b.append(msg.getLong(DMT.UID));
		else
			b.append("NO_UID");
		b.append(",");
		b.append(msg.getSpec().getName());
		
		Simulator.writeProtocolTrace(n.getOpennetFNPPort(), b.toString());
	}

	public MessageItem(byte[] data, AsyncMessageCallback[] cb2, boolean formatted, ByteCounter ctr, short priority) {
		this.cb = cb2;
		this.msg = null;
		this.buf = data;
		this.formatted = formatted;
		if(formatted && buf == null)
			throw new NullPointerException();
		this.ctrCallback = ctr;
		this.submitted = System.currentTimeMillis();
		this.priority = priority;
	}

	/**
	 * Return the data contents of this MessageItem.
	 */
	public byte[] getData() {
		return buf;
	}

	public int getLength() {
		return buf.length;
	}

	/**
	 * @param length The actual number of bytes sent to send this message, including our share of the packet overheads,
	 * *and including alreadyReportedBytes*, which is only used when deciding how many bytes to report to the throttle.
	 */
	public void onSent(int length) {
		//NB: The fact that the bytes are counted before callback notifications is important for load management.
		if(ctrCallback != null) {
			try {
				ctrCallback.sentBytes(length);
			} catch (Throwable t) {
				Logger.error(this, "Caught "+t+" reporting "+length+" sent bytes on "+this, t);
			}
		}
		if(cb != null) {
			for(int i=0;i<cb.length;i++) {
				try {
					cb[i].sent();
				} catch (Throwable t) {
					Logger.error(this, "Caught "+t+" calling sent() on "+cb[i]+" for "+this, t);
				}
			}
		}
	}

	public short getPriority() {
		return priority;
	}

	@Override
	public String toString() {
		return super.toString()+":formatted="+formatted+",msg="+msg;
	}

	public void onDisconnect() {
		if(cb != null) {
			for(int i=0;i<cb.length;i++) {
				try {
					cb[i].disconnected();
				} catch (Throwable t) {
					Logger.error(this, "Caught "+t+" calling sent() on "+cb[i]+" for "+this, t);
				}
			}
		}
	}

	public void onFailed() {
		if(cb != null) {
			for(int i=0;i<cb.length;i++) {
				try {
					cb[i].fatalError();
				} catch (Throwable t) {
					Logger.error(this, "Caught "+t+" calling sent() on "+cb[i]+" for "+this, t);
				}
			}
		}
	}

	public synchronized long getID() {
		if(hasCachedID) return cachedID;
		cachedID = generateID();
		hasCachedID = true;
		return cachedID;
	}
	
	private long generateID() {
		if(msg == null) return -1;
		Object o = msg.getObject(DMT.UID);
		if(o == null || !(o instanceof Long)) {
			return -1;
		} else {
			return (Long)o;
		}
	}
}
