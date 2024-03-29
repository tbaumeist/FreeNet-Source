/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package freenet.node;

import freenet.crypt.CryptFormatException;
import freenet.crypt.DSAPublicKey;
import freenet.io.comm.ByteCounter;
import freenet.io.comm.DMT;
import freenet.io.comm.DisconnectedException;
import freenet.io.comm.Message;
import freenet.io.comm.MessageFilter;
import freenet.io.comm.NotConnectedException;
import freenet.io.comm.PeerRestartedException;
import freenet.io.xfer.WaitedTooLongException;
import freenet.keys.NodeSSK;
import freenet.keys.SSKBlock;
import freenet.keys.SSKVerifyException;
import freenet.store.KeyCollisionException;
import freenet.support.Logger;
import freenet.support.OOMHandler;
import freenet.support.ShortBuffer;
import freenet.support.Logger.LogLevel;
import freenet.support.io.NativeThread;

/**
 * Handles an incoming SSK insert.
 * SSKs need their own insert/request classes, see comments in SSKInsertSender.
 */
public class SSKInsertHandler implements PrioRunnable, ByteCounter {

	private static boolean logMINOR;
	
    static final int PUBKEY_TIMEOUT = 10000;
    
    static final int DATA_INSERT_TIMEOUT = 30000;
    
    final Node node;
    final long uid;
    final PeerNode source;
    final NodeSSK key;
    final long startTime;
    private SSKBlock block;
    private DSAPublicKey pubKey;
    private short htl;
    private SSKInsertSender sender;
    private byte[] data;
    private byte[] headers;
    private boolean canCommit;
    final InsertTag tag;
    private final boolean canWriteDatastore;
	private final boolean forkOnCacheable;
	private final boolean preferInsert;
	private final boolean ignoreLowBackoff;
	private final boolean realTimeFlag;

	private boolean collided = false;
    
    SSKInsertHandler(NodeSSK key, byte[] data, byte[] headers, short htl, PeerNode source, long id, Node node, long startTime, InsertTag tag, boolean canWriteDatastore, boolean forkOnCacheable, boolean preferInsert, boolean ignoreLowBackoff, boolean realTimeFlag) {
        this.node = node;
        this.uid = id;
        this.source = source;
        this.startTime = startTime;
        this.key = key;
        this.htl = htl;
        this.data = data;
        this.headers = headers;
        this.tag = tag;
        this.canWriteDatastore = canWriteDatastore;
        if(htl <= 0) htl = 1;
        byte[] pubKeyHash = key.getPubKeyHash();
        pubKey = node.getPubKey.getKey(pubKeyHash, false, false, null);
        canCommit = false;
        logMINOR = Logger.shouldLog(LogLevel.MINOR, this);
        this.forkOnCacheable = forkOnCacheable;
        this.preferInsert = preferInsert;
        this.ignoreLowBackoff = ignoreLowBackoff;
        this.realTimeFlag = realTimeFlag;
    }
    
    @Override
	public String toString() {
        return super.toString()+" for "+uid;
    }
    
    public void run() {
	    freenet.support.Logger.OSThread.logPID(this);
        try {
        	realRun();
		} catch (OutOfMemoryError e) {
			OOMHandler.handleOOM(e);
        } catch (Throwable t) {
            Logger.error(this, "Caught "+t, t);
        } finally {
            if(logMINOR) Logger.minor(this, "Exiting InsertHandler.run() for "+uid);
            node.unlockUID(uid, true, true, false, false, false, realTimeFlag, tag);
        }
    }

    private void realRun() {
        // Send Accepted
        Message accepted = DMT.createFNPSSKAccepted(uid, pubKey == null);
        
        try {
			source.sendAsync(accepted, null, this);
		} catch (NotConnectedException e1) {
			if(logMINOR) Logger.minor(this, "Lost connection to source");
			return;
		}
		
		if(headers == null) {
			try {
				MessageFilter mf = MessageFilter.create().setType(DMT.FNPSSKInsertRequestHeaders).setField(DMT.UID, uid).setSource(source).setTimeout(DATA_INSERT_TIMEOUT);
				Message msg = node.usm.waitFor(mf, this);
				if(msg == null) {
					Logger.normal(this, "Failed to receive FNPSSKInsertRequestHeaders for "+uid);
					return;
				}
				headers = ((ShortBuffer)msg.getObject(DMT.BLOCK_HEADERS)).getData();
			} catch (DisconnectedException e) {
				if(logMINOR) Logger.minor(this, "Lost connection to source on "+uid);
				return;
			}	
		}
		
		if(data == null) {
			try {
				MessageFilter mf = MessageFilter.create().setType(DMT.FNPSSKInsertRequestData).setField(DMT.UID, uid).setSource(source).setTimeout(DATA_INSERT_TIMEOUT);
				Message msg = node.usm.waitFor(mf, this);
				if(msg == null) {
					Logger.normal(this, "Failed to receive FNPSSKInsertRequestData for "+uid);
					return;
				}
				data = ((ShortBuffer)msg.getObject(DMT.DATA)).getData();
			} catch (DisconnectedException e) {
				if(logMINOR) Logger.minor(this, "Lost connection to source on "+uid);
				return;
			}	
			
		}
		
		if(pubKey == null) {
			// Wait for pub key
			if(logMINOR) Logger.minor(this, "Waiting for pubkey on "+uid);
			
			MessageFilter mfPK = MessageFilter.create().setType(DMT.FNPSSKPubKey).setField(DMT.UID, uid).setSource(source).setTimeout(PUBKEY_TIMEOUT);
			
			try {
				Message pk = node.usm.waitFor(mfPK, this);
				if(pk == null) {
					Logger.normal(this, "Failed to receive FNPSSKPubKey for "+uid);
					return;
				}
				byte[] pubkeyAsBytes = ((ShortBuffer)pk.getObject(DMT.PUBKEY_AS_BYTES)).getData();
				try {
					pubKey = DSAPublicKey.create(pubkeyAsBytes);
					if(logMINOR) Logger.minor(this, "Got pubkey on "+uid+" : "+pubKey);
					Message confirm = DMT.createFNPSSKPubKeyAccepted(uid);
					try {
						source.sendAsync(confirm, null, this);
					} catch (NotConnectedException e) {
						if(logMINOR) Logger.minor(this, "Lost connection to source on "+uid);
						return;
					}
				} catch (CryptFormatException e) {
					Logger.error(this, "Invalid pubkey from "+source+" on "+uid);
					Message msg = DMT.createFNPDataInsertRejected(uid, DMT.DATA_INSERT_REJECTED_SSK_ERROR);
					try {
						source.sendSync(msg, this);
					} catch (NotConnectedException ee) {
						// Ignore
					}
					return;
				}
			} catch (DisconnectedException e) {
				if(logMINOR) Logger.minor(this, "Lost connection to source on "+uid);
				return;
			}
		}
		
		try {
			key.setPubKey(pubKey);
			block = new SSKBlock(data, headers, key, false);
		} catch (SSKVerifyException e1) {
			Logger.error(this, "Invalid SSK from "+source, e1);
			Message msg = DMT.createFNPDataInsertRejected(uid, DMT.DATA_INSERT_REJECTED_SSK_ERROR);
			try {
				source.sendSync(msg, this);
			} catch (NotConnectedException e) {
				// Ignore
			}
			return;
		}
		
		SSKBlock storedBlock = node.fetch(key, false, false, false, canWriteDatastore, false, null);
		
		if((storedBlock != null) && !storedBlock.equals(block)) {
			try {
				RequestHandler.sendSSK(storedBlock.getRawHeaders(), storedBlock.getRawData(), false, pubKey, source, uid, this);
			} catch (NotConnectedException e1) {
				if(logMINOR) Logger.minor(this, "Lost connection to source on "+uid);
				return;
			} catch (WaitedTooLongException e1) {
				Logger.error(this, "Took too long to send ssk datareply to "+uid+" (because of throttling)");
				return;
			} catch (PeerRestartedException e) {
				if(logMINOR) Logger.minor(this, "Source restarted on "+uid);
				return;
			}
			block = storedBlock;
		}
		
		if(logMINOR) Logger.minor(this, "Got block for "+key+" for "+uid);
		
        if(htl > 0)
            sender = node.makeInsertSender(block, htl, uid, tag, source, false, false, canWriteDatastore, forkOnCacheable, preferInsert, ignoreLowBackoff, realTimeFlag);
        
        boolean receivedRejectedOverload = false;
        
        while(true) {
            synchronized(sender) {
                try {
                	if(sender.getStatus() == SSKInsertSender.NOT_FINISHED)
                		sender.wait(5000);
                } catch (InterruptedException e) {
                	// Ignore
                }
            }

            if((!receivedRejectedOverload) && sender.receivedRejectedOverload()) {
            	receivedRejectedOverload = true;
            	// Forward it
            	Message m = DMT.createFNPRejectedOverload(uid, false);
            	try {
					source.sendSync(m, this);
				} catch (NotConnectedException e) {
					if(logMINOR) Logger.minor(this, "Lost connection to source");
					return;
				}
            }
            
            if(sender.hasRecentlyCollided()) {
            	// Forward collision
            	data = sender.getData();
            	headers = sender.getHeaders();
            	collided = true;
        		try {
					block = new SSKBlock(data, headers, key, true);
				} catch (SSKVerifyException e1) {
					// Is verified elsewhere...
					throw new Error("Impossible: " + e1, e1);
				}
				try {
					RequestHandler.sendSSK(headers, data, false, pubKey, source, uid, this);
				} catch (NotConnectedException e1) {
					if(logMINOR) Logger.minor(this, "Lost connection to source on "+uid);
					return;
				} catch (WaitedTooLongException e1) {
					Logger.error(this, "Took too long to send ssk datareply to "+uid+" because of bwlimiting");
					return;
				} catch (PeerRestartedException e) {
					Logger.error(this, "Peer restarted on "+uid);
					return;
				}
            }
            
            int status = sender.getStatus();
            
            if(status == SSKInsertSender.NOT_FINISHED) {
                continue;
            }
            
            // Local RejectedOverload's (fatal).
            // Internal error counts as overload. It'd only create a timeout otherwise, which is the same thing anyway.
            // We *really* need a good way to deal with nodes that constantly R_O!
            if((status == SSKInsertSender.TIMED_OUT) ||
            		(status == SSKInsertSender.GENERATED_REJECTED_OVERLOAD) ||
            		(status == SSKInsertSender.INTERNAL_ERROR)) {
                Message msg = DMT.createFNPRejectedOverload(uid, true);
                try {
					source.sendSync(msg, this);
				} catch (NotConnectedException e) {
					if(logMINOR) Logger.minor(this, "Lost connection to source");
					return;
				}
                // Might as well store it anyway.
                if((status == SSKInsertSender.TIMED_OUT) ||
                		(status == SSKInsertSender.GENERATED_REJECTED_OVERLOAD))
                	canCommit = true;
                finish(status);
                return;
            }
            
            if((status == SSKInsertSender.ROUTE_NOT_FOUND) || (status == SSKInsertSender.ROUTE_REALLY_NOT_FOUND)) {
                Message msg = DMT.createFNPRouteNotFound(uid, sender.getHTL());
                try {
					source.sendSync(msg, this);
				} catch (NotConnectedException e) {
					if(logMINOR) Logger.minor(this, "Lost connection to source");
					return;
				}
                canCommit = true;
                finish(status);
                return;
            }
            
            if(status == SSKInsertSender.SUCCESS) {
            	Message msg = DMT.createFNPInsertReply(uid);
            	try {
					source.sendSync(msg, this);
				} catch (NotConnectedException e) {
					if(logMINOR) Logger.minor(this, "Lost connection to source");
					return;
				}
                canCommit = true;
                finish(status);
                return;
            }
            
            // Otherwise...?
            Logger.error(this, "Unknown status code: "+sender.getStatusString());
            Message msg = DMT.createFNPRejectedOverload(uid, true);
            try {
				source.sendSync(msg, this);
			} catch (NotConnectedException e) {
				// Ignore
			}
            finish(status);
            return;
        }
    }

    /**
     * If canCommit, and we have received all the data, and it
     * verifies, then commit it.
     */
    private void finish(int code) {
    	if(logMINOR) Logger.minor(this, "Finishing");
    	
    	if(canCommit) {
    		commit();
    	}
    	
        if(code != SSKInsertSender.TIMED_OUT && code != SSKInsertSender.GENERATED_REJECTED_OVERLOAD &&
        		code != SSKInsertSender.INTERNAL_ERROR && code != SSKInsertSender.ROUTE_REALLY_NOT_FOUND) {
        	int totalSent = getTotalSentBytes();
        	int totalReceived = getTotalReceivedBytes();
        	if(sender != null) {
        		totalSent += sender.getTotalSentBytes();
        		totalReceived += sender.getTotalReceivedBytes();
        	}
        	if(logMINOR) Logger.minor(this, "Remote SSK insert cost "+totalSent+ '/' +totalReceived+" bytes ("+code+ ')');
        	node.nodeStats.remoteSskInsertBytesSentAverage.report(totalSent);
        	node.nodeStats.remoteSskInsertBytesReceivedAverage.report(totalReceived);
        	if(code == SSKInsertSender.SUCCESS) {
        		// Can report both sides
        		node.nodeStats.successfulSskInsertBytesSentAverage.report(totalSent);
        		node.nodeStats.successfulSskInsertBytesReceivedAverage.report(totalReceived);
        	}
        }

    }

    private void commit() {
		try {
			node.store(block, node.shouldStoreDeep(key, source, sender == null ? new PeerNode[0] : sender.getRoutedTo()), collided, false, canWriteDatastore, false);
		} catch (KeyCollisionException e) {
			Logger.normal(this, "Collision on "+this);
		}
	}

	private final Object totalBytesSync = new Object();
    private int totalBytesSent;
    private int totalBytesReceived;
    
	public void sentBytes(int x) {
		synchronized(totalBytesSync) {
			totalBytesSent += x;
		}
		node.nodeStats.insertSentBytes(true, x);
	}

	public void receivedBytes(int x) {
		synchronized(totalBytesSync) {
			totalBytesReceived += x;
		}
		node.nodeStats.insertReceivedBytes(true, x);
	}
	
	public int getTotalSentBytes() {
		return totalBytesSent;
	}
	
	public int getTotalReceivedBytes() {
		return totalBytesReceived;
	}

	public void sentPayload(int x) {
		node.sentPayload(x);
		node.nodeStats.insertSentBytes(true, -x);
	}

	public int getPriority() {
		return NativeThread.HIGH_PRIORITY;
	}
    
}
