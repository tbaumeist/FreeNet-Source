/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package freenet.node;

import java.util.ArrayList;
import java.util.HashSet;

import freenet.crypt.CryptFormatException;
import freenet.crypt.DSAPublicKey;
import freenet.io.comm.ByteCounter;
import freenet.io.comm.DMT;
import freenet.io.comm.DisconnectedException;
import freenet.io.comm.Message;
import freenet.io.comm.MessageFilter;
import freenet.io.comm.NotConnectedException;
import freenet.io.comm.NullAsyncMessageFilterCallback;
import freenet.io.comm.PeerContext;
import freenet.io.comm.PeerParseException;
import freenet.io.comm.ReferenceSignatureVerificationException;
import freenet.io.comm.RetrievalException;
import freenet.io.comm.SlowAsyncMessageFilterCallback;
import freenet.io.xfer.BlockReceiver;
import freenet.io.xfer.BlockReceiver.BlockReceiverCompletion;
import freenet.io.xfer.PartiallyReceivedBlock;
import freenet.keys.CHKBlock;
import freenet.keys.Key;
import freenet.keys.KeyVerifyException;
import freenet.keys.NodeCHK;
import freenet.keys.NodeSSK;
import freenet.keys.SSKBlock;
import freenet.keys.SSKVerifyException;
import freenet.node.FailureTable.BlockOffer;
import freenet.node.FailureTable.OfferList;
import freenet.node.OpennetManager.ConnectionType;
import freenet.node.PeerNode.OutputLoadTracker;
import freenet.node.PeerNode.RequestLikelyAcceptedState;
import freenet.node.PeerNode.SlotWaiter;
import freenet.store.KeyCollisionException;
import freenet.support.LogThresholdCallback;
import freenet.support.Logger;
import freenet.support.ShortBuffer;
import freenet.support.SimpleFieldSet;
import freenet.support.TimeUtil;
import freenet.support.Logger.LogLevel;
import freenet.support.io.NativeThread;
import freenet.support.math.MedianMeanRunningAverage;

/**
 * @author amphibian
 * 
 * Sends a request out onto the network, and deals with the 
 * consequences. Other half of the request functionality is provided
 * by RequestHandler.
 * 
 * Must put self onto node's list of senders on creation, and remove
 * self from it on destruction. Must put self onto node's list of
 * transferring senders when starts transferring, and remove from it
 * when finishes transferring.
 */
public final class RequestSender implements PrioRunnable, ByteCounter {

    // Constants
    static final int ACCEPTED_TIMEOUT = 10000;
    static final int GET_OFFER_TIMEOUT = 10000;
    static final int FETCH_TIMEOUT_BULK = 600*1000;
    static final int FETCH_TIMEOUT_REALTIME = 60*1000;
    final int fetchTimeout;
    /** Wait up to this long to get a path folding reply */
    static final int OPENNET_TIMEOUT = 120000;
    /** One in this many successful requests is randomly reinserted.
     * This is probably a good idea anyway but with the split store it's essential. */
    static final int RANDOM_REINSERT_INTERVAL = 200;
    
    // Basics
    final Key key;
    final double target;
    private short htl;
    private final short origHTL;
    final long uid;
    final RequestTag origTag;
    final Node node;
    /** The source of this request if any - purely so we can avoid routing to it */
    final PeerNode source;
    private PartiallyReceivedBlock prb;
    private DSAPublicKey pubKey;
    private byte[] headers;
    private byte[] sskData;
    private SSKBlock block;
    private boolean hasForwarded;
    private PeerNode transferringFrom;
    private boolean turtleMode;
    private boolean sentBackoffTurtle;
    /** Set when we start to think about going to turtle mode - not unset if we get cancelled instead. */
    private boolean tryTurtle;
    private final boolean canWriteClientCache;
    private final boolean canWriteDatastore;
    private final boolean isSSK;
    
    // State of the main loop which is more convenient to keep as private members
    private PeerNode next;
    private long timeSentRequest;
    private int rejectOverloads;
    private int gotMessages;
    private String lastMessage;
    private HashSet<PeerNode> nodesRoutedTo = new HashSet<PeerNode>();
    private long deadline;
    
    /** If true, only try to fetch the key from nodes which have offered it */
    private boolean tryOffersOnly;
    
	private ArrayList<Listener> listeners=new ArrayList<Listener>();
	
	private boolean mustUnlock;
    
    // Terminal status
    // Always set finished AFTER setting the reason flag

    private int status = -1;
    static final int NOT_FINISHED = -1;
    static final int SUCCESS = 0;
    static final int ROUTE_NOT_FOUND = 1;
    static final int DATA_NOT_FOUND = 3;
    static final int TRANSFER_FAILED = 4;
    static final int VERIFY_FAILURE = 5;
    static final int TIMED_OUT = 6;
    static final int GENERATED_REJECTED_OVERLOAD = 7;
    static final int INTERNAL_ERROR = 8;
    static final int RECENTLY_FAILED = 9;
    static final int GET_OFFER_VERIFY_FAILURE = 10;
    static final int GET_OFFER_TRANSFER_FAILED = 11;
    private PeerNode successFrom;
    private PeerNode lastNode;
    private final long startTime;
    final boolean realTimeFlag;
    
    static String getStatusString(int status) {
    	switch(status) {
    	case NOT_FINISHED:
    		return "NOT FINISHED";
    	case SUCCESS:
    		return "SUCCESS";
    	case ROUTE_NOT_FOUND:
    		return "ROUTE NOT FOUND";
    	case DATA_NOT_FOUND:
    		return "DATA NOT FOUND";
    	case TRANSFER_FAILED:
    		return "TRANSFER FAILED";
    	case GET_OFFER_TRANSFER_FAILED:
    		return "GET OFFER TRANSFER FAILED";
    	case VERIFY_FAILURE:
    		return "VERIFY FAILURE";
    	case GET_OFFER_VERIFY_FAILURE:
    		return "GET OFFER VERIFY FAILURE";
    	case TIMED_OUT:
    		return "TIMED OUT";
    	case GENERATED_REJECTED_OVERLOAD:
    		return "GENERATED REJECTED OVERLOAD";
    	case INTERNAL_ERROR:
    		return "INTERNAL ERROR";
    	case RECENTLY_FAILED:
    		return "RECENTLY FAILED";
    	default:
    		return "UNKNOWN STATUS CODE: "+status;
    	}
    }
    
    String getStatusString() {
    	return getStatusString(getStatus());
    }
    
    private static volatile boolean logMINOR;
    static {
	Logger.registerLogThresholdCallback(new LogThresholdCallback(){
		@Override
		public void shouldUpdate(){
			logMINOR = Logger.shouldLog(LogLevel.MINOR, this);
		}
	});
    }
    
    @Override
	public String toString() {
        return super.toString()+" for "+uid;
    }

    /**
     * RequestSender constructor.
     * @param key The key to request. Its public key should have been looked up
     * already; RequestSender will not look it up.
     * @param realTimeFlag If enabled,  
     */
    public RequestSender(Key key, DSAPublicKey pubKey, short htl, long uid, RequestTag tag, Node n,
            PeerNode source, boolean offersOnly, boolean canWriteClientCache, boolean canWriteDatastore, boolean realTimeFlag) {
    	if(key.getRoutingKey() == null) throw new NullPointerException();
    	startTime = System.currentTimeMillis();
    	this.realTimeFlag = realTimeFlag;
    	if(realTimeFlag)
    		fetchTimeout = FETCH_TIMEOUT_REALTIME;
    	else
    		fetchTimeout = FETCH_TIMEOUT_BULK;
        this.key = key;
        this.pubKey = pubKey;
        this.htl = htl;
        this.origHTL = htl;
        this.uid = uid;
        this.origTag = tag;
        this.node = n;
        this.source = source;
        this.tryOffersOnly = offersOnly;
        this.canWriteClientCache = canWriteClientCache;
        this.canWriteDatastore = canWriteDatastore;
        this.isSSK = key instanceof NodeSSK;
        assert(isSSK || key instanceof NodeCHK);
        target = key.toNormalizedDouble();
    }

    public void start() {
    	node.executor.execute(this, "RequestSender for UID "+uid+" on "+node.getDarknetPortNumber());
    }
    
    public void run() {
        try {
        	realRun();
        } catch (Throwable t) {
            Logger.error(this, "Caught "+t, t);
            finish(INTERNAL_ERROR, null, false);
        } finally {
        	if(status == NOT_FINISHED && !receivingAsync) {
        		Logger.error(this, "Not finished: "+this);
        		finish(INTERNAL_ERROR, null, false);
        	} else if(!receivingAsync) {
            	boolean mustUnlock;
            	synchronized(this) {
            		mustUnlock = this.mustUnlock;
            	}
            	if(mustUnlock)
            		node.unlockUID(uid, isSSK, false, false, false, false, realTimeFlag, origTag);
        	}
        	if(logMINOR) Logger.minor(this, "Leaving RequestSender.run() for "+uid);
        }
    }

	static final int MAX_HIGH_HTL_FAILURES = 5;
	
	synchronized void setMustUnlock() {
		mustUnlock = true;
	}
	
    private void realRun() {
	    freenet.support.Logger.OSThread.logPID(this);
        if(isSSK && (pubKey == null)) {
        	pubKey = ((NodeSSK)key).getPubKey();
        }
        
        // First ask any nodes that have offered the data
        
        final OfferList offers = node.failureTable.getOffers(key);
        
        if(offers != null) {
        	if(tryOffers(offers)) return;
        }
        
        if(tryOffersOnly) {
        	if(logMINOR) Logger.minor(this, "Tried all offers, not doing a regular request for key");
        	finish(DATA_NOT_FOUND, null, true); // FIXME need a different error code?
        	return;
        }
        
        next = null;
		routeAttempts=0;
		starting = true;
        // While in no-cache mode, we don't decrement HTL on a RejectedLoop or similar, but we only allow a limited number of such failures before RNFing.
		highHTLFailureCount = 0;
        routeRequests();
    }
    
    private int routeAttempts;
    private boolean starting;
    private int highHTLFailureCount;
    
    private void routeRequests() {
        
        peerLoop:
        while(true) {
            boolean canWriteStorePrev = node.canWriteDatastoreInsert(htl);
            if((!starting) && (!canWriteStorePrev)) {
            	// We always decrement on starting a sender.
            	// However, after that, if our HTL is above the no-cache threshold,
            	// we do not want to decrement the HTL for trivial rejections (e.g. RejectedLoop),
            	// because we would end up caching data too close to the originator.
            	// So allow 5 failures and then RNF.
            	if(highHTLFailureCount++ >= MAX_HIGH_HTL_FAILURES) {
            		if(logMINOR) Logger.minor(this, "Too many failures at non-cacheable HTL");
            		finish(ROUTE_NOT_FOUND, null, false);
            		return;
            	}
            	if(logMINOR) Logger.minor(this, "Allowing failure "+highHTLFailureCount+" htl is still "+htl);
            } else {
            	/*
            	 * If we haven't routed to any node yet, decrement according to the source.
            	 * If we have, decrement according to the node which just failed.
            	 * Because:
            	 * 1) If we always decrement according to source then we can be at max or min HTL
            	 * for a long time while we visit *every* peer node. This is BAD!
            	 * 2) The node which just failed can be seen as the requestor for our purposes.
            	 */
            	// Decrement at this point so we can DNF immediately on reaching HTL 0.
            	htl = node.decrementHTL((hasForwarded ? next : source), htl);
            	if(logMINOR) Logger.minor(this, "Decremented HTL to "+htl);
            }
            starting = false;

            if(logMINOR) Logger.minor(this, "htl="+htl);
            if(htl == 0) {
            	// This used to be RNF, I dunno why
				//???: finish(GENERATED_REJECTED_OVERLOAD, null);
                finish(DATA_NOT_FOUND, null, false);
                node.failureTable.onFinalFailure(key, null, htl, origHTL, FailureTable.REJECT_TIME, source);
                return;
            }

			routeAttempts++;
            
            // Route it
            next = node.peers.closerPeer(source, nodesRoutedTo, target, true, node.isAdvancedModeEnabled(), -1, null,
			        key, htl, 0, source == null);
            
            if(next == null) {
				if (logMINOR && rejectOverloads>0)
					Logger.minor(this, "no more peers, but overloads ("+rejectOverloads+"/"+routeAttempts+" overloaded)");
                // Backtrack
                finish(ROUTE_NOT_FOUND, null, false);
                node.failureTable.onFinalFailure(key, null, htl, origHTL, -1, source);
                return;
            }
            
            synchronized(this) {
            	lastNode = next;
            }
			
            if(logMINOR) Logger.minor(this, "Routing request to "+next);
            nodesRoutedTo.add(next);
            
            Message req = createDataRequest();
            
            // Not possible to get an accurate time for sending, guaranteed to be not later than the time of receipt.
            // Why? Because by the time the sent() callback gets called, it may already have been acked, under heavy load.
            // So take it from when we first started to try to send the request.
            // See comments below when handling FNPRecentlyFailed for why we need this.
            synchronized(this) {
            	timeSentRequest = System.currentTimeMillis();
            }
			
            origTag.addRoutedTo(next, false);
            
            try {
            	//This is the first contact to this node, it is more likely to timeout
				/*
				 * using sendSync could:
				 *   make ACCEPTED_TIMEOUT more accurate (as it is measured from the send-time),
				 *   use a lot of our time that we have to fulfill this request (simply waiting on the send queue, or longer if the node just went down),
				 * using sendAsync could:
				 *   make ACCEPTED_TIMEOUT much more likely,
				 *   leave many hanging-requests/unclaimedFIFO items,
				 *   potentially make overloaded peers MORE overloaded (we make a request and promptly forget about them).
				 * 
				 * Don't use sendAsync().
				 */
            	next.sendSync(req, this);
            } catch (NotConnectedException e) {
            	Logger.minor(this, "Not connected");
	        	origTag.removeRoutingTo(next);
            	continue;
            }
            
            synchronized(this) {
            	hasForwarded = true;
            }
            
loadWaiterLoop:
            while(true) {
            	
            	DO action = waitForAccepted();
            	// Here FINISHED means accepted, WAIT means try again (soft reject).
            	if(action == DO.WAIT) {
					//retriedForLoadManagement = true;
            		continue loadWaiterLoop;
            	} else if(action == DO.NEXT_PEER) {
            		continue peerLoop;
            	} else { // FINISHED => accepted
            		break;
            	}
            } // loadWaiterLoop
            
            if(logMINOR) Logger.minor(this, "Got Accepted");
            
            // Otherwise, must be Accepted
            
            gotMessages = 0;
            lastMessage = null;
            deadline = System.currentTimeMillis() + fetchTimeout;
            
            synchronized(this) {
            	receivingAsync = true;
            }
            WaitForAcceptedCallback cb = new WaitForAcceptedCallback(lastNode);
            cb.schedule();
            return;
        }
	}
    
    private synchronized int timeSinceSent() {
    	return (int) (System.currentTimeMillis() - timeSentRequest);
    }
    
    private class WaitForAcceptedCallback implements SlowAsyncMessageFilterCallback {
    	
    	// Needs to be a separate class so it can check whether the main loop has moved on to another peer.
    	// If it has
    	
    	private final PeerNode waitingFor;

		public WaitForAcceptedCallback(PeerNode source) {
			waitingFor = source;
		}

		public void onMatched(Message msg) {
			
        	DO action = handleMessage(msg);
        	
        	if(action == DO.FINISHED)
        		return;
        	else if(action == DO.NEXT_PEER) {
        		// Try another peer
        		routeRequests();
        	} else /*if(action == DO.WAIT)*/ {
        		// Try again.
        		schedule();
        	}
		}
		
		public void schedule() {
        	long now = System.currentTimeMillis();
        	int timeout = (int)(Math.min(Integer.MAX_VALUE, deadline - now));
        	if(timeout >= 0) {
        		MessageFilter mf = createMessageFilter(timeout);
        		try {
        			node.usm.addAsyncFilter(mf, this, RequestSender.this);
        		} catch (DisconnectedException e) {
        			onDisconnect(lastNode);
        		}
        	} else {
        		onTimeout();
        	}
		}

		public boolean shouldTimeout() {
			synchronized(RequestSender.this) {
				if(lastNode != waitingFor) return true;
				if(status != -1) return true;
			}
			return false;
		}

		public void onTimeout() {
			synchronized(RequestSender.this) {
				if(lastNode != waitingFor) {
					if(logMINOR) Logger.minor(this, "Timeout but has moved on: waiting for "+waitingFor+" but moved on to "+lastNode+" on "+uid);
					return;
				}
				if(status != -1) {
					if(logMINOR) Logger.minor(this, "Timed out but already set status to "+status);
					return;
				}
			}
			Logger.error(this, "Timed out after waiting "+fetchTimeout+" on "+uid+" from "+waitingFor+" ("+gotMessages+" messages; last="+lastMessage+") for "+uid);
    		// Fatal timeout
    		next.localRejectedOverload("FatalTimeout");
    		forwardRejectedOverload();
    		finish(TIMED_OUT, next, false);
    		node.failureTable.onFinalFailure(key, next, htl, origHTL, FailureTable.REJECT_TIME, source);
    		// Finished, can't try another node as timed out.
		}

		public void onDisconnect(PeerContext ctx) {
			Logger.normal(this, "Disconnected from "+next+" while waiting for data on "+uid);
			next.noLongerRoutingTo(origTag, false);
			// Try another peer.
			routeRequests();
		}

		public void onRestarted(PeerContext ctx) {
			onDisconnect(ctx);
		}

		public int getPriority() {
			return NativeThread.NORM_PRIORITY;
		}
    	
    };
    
	/** @return True if we successfully fetched an offered key or failed fatally. False
     * if we should proceed to a normal fetch. */
    private boolean tryOffers(final OfferList offers) {
    	
        while(true) {
        	// Fetches valid offers, then expired ones. Expired offers don't count towards failures,
        	// but they're still worth trying.
        	BlockOffer offer = offers.getFirstOffer();
        	if(offer == null) {
        		if(logMINOR) Logger.minor(this, "No more offers");
        		break;
        	}
        	PeerNode pn = offer.getPeerNode();
        	if(pn == null) {
        		offers.deleteLastOffer();
        		if(logMINOR) Logger.minor(this, "Null offer");
        		continue;
        	}
        	if(pn.getBootID() != offer.bootID) {
        		offers.deleteLastOffer();
        		if(logMINOR) Logger.minor(this, "Restarted node");
        		continue;
        	}
        	origTag.addRoutedTo(pn, true);
        	Message msg = DMT.createFNPGetOfferedKey(key, offer.authenticator, pubKey == null, uid);
        	msg.addSubMessage(DMT.createFNPRealTimeFlag(realTimeFlag));
        	try {
				pn.sendAsync(msg, null, this);
			} catch (NotConnectedException e2) {
				if(logMINOR)
					Logger.minor(this, "Disconnected: "+pn+" getting offer for "+key);
				offers.deleteLastOffer();
	        	origTag.removeFetchingOfferedKeyFrom(pn);
				continue;
			}
        	MessageFilter mfRO = MessageFilter.create().setSource(pn).setField(DMT.UID, uid).setTimeout(GET_OFFER_TIMEOUT).setType(DMT.FNPRejectedOverload);
        	MessageFilter mfGetInvalid = MessageFilter.create().setSource(pn).setField(DMT.UID, uid).setTimeout(GET_OFFER_TIMEOUT).setType(DMT.FNPGetOfferedKeyInvalid);
        	// Wait for a response.
        	if(!isSSK) {
        		// Headers first, then block transfer.
        		MessageFilter mfDF = MessageFilter.create().setSource(pn).setField(DMT.UID, uid).setTimeout(GET_OFFER_TIMEOUT).setType(DMT.FNPCHKDataFound);
        		Message reply;
				try {
					reply = node.usm.waitFor(mfDF.or(mfRO.or(mfGetInvalid)), this);
				} catch (DisconnectedException e2) {
					if(logMINOR)
						Logger.minor(this, "Disconnected: "+pn+" getting offer for "+key);
					offers.deleteLastOffer();
		        	origTag.removeFetchingOfferedKeyFrom(pn);
					continue;
				}
        		if(reply == null) {
        			// We gave it a chance, don't give it another.
        			offers.deleteLastOffer();
        			continue;
        		} else {
        			if(handleCHKOfferReply(reply, pn, offer, offers)) return true;
        		}
        	} else {
        		// Data, possibly followed by pubkey
        		MessageFilter mfAltDF = MessageFilter.create().setSource(pn).setField(DMT.UID, uid).setTimeout(GET_OFFER_TIMEOUT).setType(DMT.FNPSSKDataFoundHeaders);
        		Message reply;
				try {
					reply = node.usm.waitFor(mfRO.or(mfGetInvalid.or(mfAltDF)), this);
				} catch (DisconnectedException e) {
					if(logMINOR)
						Logger.minor(this, "Disconnected: "+pn+" getting offer for "+key);
					offers.deleteLastOffer();
					continue;
				}
        		if(reply == null) {
            		offers.deleteLastOffer();
        			continue;
        		} else {
        			if(handleSSKOfferReply(reply, pn, offer, offers)) return true;
        		}
        	}
        	// RejectedOverload is possible - but we need to include it in the statistics.
        	// We don't remove the offer in that case. Otherwise we do, even if it fails.
        	// FNPGetOfferedKeyInvalid is also possible.
        }
        return false;
    }

    private boolean handleSSKOfferReply(Message reply, PeerNode pn,
			BlockOffer offer, OfferList offers) {
    	if(reply.getSpec() == DMT.FNPRejectedOverload) {
			// Non-fatal, keep it.
			if(logMINOR)
				Logger.minor(this, "Node "+pn+" rejected FNPGetOfferedKey for "+key+" (expired="+offer.isExpired());
			offers.keepLastOffer();
			return false;
		} else if(reply.getSpec() == DMT.FNPGetOfferedKeyInvalid) {
			// Fatal, delete it.
			if(logMINOR)
				Logger.minor(this, "Node "+pn+" rejected FNPGetOfferedKey as invalid with reason "+reply.getShort(DMT.REASON));
			offers.deleteLastOffer();
			return false;
		} else if(reply.getSpec() == DMT.FNPSSKDataFoundHeaders) {
			headers = ((ShortBuffer) reply.getObject(DMT.BLOCK_HEADERS)).getData();
			// Wait for the data
			MessageFilter mfData = MessageFilter.create().setSource(pn).setField(DMT.UID, uid).setTimeout(GET_OFFER_TIMEOUT).setType(DMT.FNPSSKDataFoundData);
			Message dataMessage;
			try {
				dataMessage = node.usm.waitFor(mfData, this);
			} catch (DisconnectedException e) {
				if(logMINOR)
					Logger.minor(this, "Disconnected: "+pn+" getting data for offer for "+key);
				offers.deleteLastOffer();
	        	origTag.removeFetchingOfferedKeyFrom(pn);
	        	return false;
			}
			if(dataMessage == null) {
				Logger.error(this, "Got headers but not data from "+pn+" for offer for "+key);
				offers.deleteLastOffer();
				return false;
			}
			sskData = ((ShortBuffer) dataMessage.getObject(DMT.DATA)).getData();
			if(pubKey == null) {
				MessageFilter mfPK = MessageFilter.create().setSource(pn).setField(DMT.UID, uid).setTimeout(GET_OFFER_TIMEOUT).setType(DMT.FNPSSKPubKey);
				Message pk;
				try {
					pk = node.usm.waitFor(mfPK, this);
				} catch (DisconnectedException e) {
					if(logMINOR)
						Logger.minor(this, "Disconnected: "+pn+" getting pubkey for offer for "+key);
					offers.deleteLastOffer();
					return false;
				}
				if(pk == null) {
					Logger.error(this, "Got data but not pubkey from "+pn+" for offer for "+key);
					offers.deleteLastOffer();
					return false;
				}
				try {
					pubKey = DSAPublicKey.create(((ShortBuffer)pk.getObject(DMT.PUBKEY_AS_BYTES)).getData());
				} catch (CryptFormatException e) {
					Logger.error(this, "Bogus pubkey from "+pn+" for offer for "+key+" : "+e, e);
					offers.deleteLastOffer();
					return false;
				}
				
				try {
					((NodeSSK)key).setPubKey(pubKey);
				} catch (SSKVerifyException e) {
					Logger.error(this, "Bogus SSK data from "+pn+" for offer for "+key+" : "+e, e);
					offers.deleteLastOffer();
					return false;
				}
			}
			
			if(finishSSKFromGetOffer(pn)) {
				if(logMINOR) Logger.minor(this, "Successfully fetched SSK from offer from "+pn+" for "+key);
				return true;
			} else {
        		offers.deleteLastOffer();
        		return false;
			}
		} else 
			return false;
	}

	/** @return True if we successfully received the offer or failed fatally. False if we
     * should try the next offer and/or normal fetches. */
	private boolean handleCHKOfferReply(Message reply, PeerNode pn, final BlockOffer offer, final OfferList offers) {
		if(reply.getSpec() == DMT.FNPRejectedOverload) {
			// Non-fatal, keep it.
			if(logMINOR)
				Logger.minor(this, "Node "+pn+" rejected FNPGetOfferedKey for "+key+" (expired="+offer.isExpired());
			offers.keepLastOffer();
        	origTag.removeFetchingOfferedKeyFrom(pn);
        	return false;
		} else if(reply.getSpec() == DMT.FNPGetOfferedKeyInvalid) {
			// Fatal, delete it.
			if(logMINOR)
				Logger.minor(this, "Node "+pn+" rejected FNPGetOfferedKey as invalid with reason "+reply.getShort(DMT.REASON));
			offers.deleteLastOffer();
        	origTag.removeFetchingOfferedKeyFrom(pn);
        	return false;
		} else if(reply.getSpec() == DMT.FNPCHKDataFound) {
			headers = ((ShortBuffer)reply.getObject(DMT.BLOCK_HEADERS)).getData();
			// Receive the data
			
        	// FIXME: Validate headers
        	
        	node.addTransferringSender((NodeCHK)key, this);
        	
        	try {
        		
        		prb = new PartiallyReceivedBlock(Node.PACKETS_IN_BLOCK, Node.PACKET_SIZE);
        		
        		synchronized(this) {
        			notifyAll();
        		}
        		fireCHKTransferBegins();
				
        		BlockReceiver br = new BlockReceiver(node.usm, pn, uid, prb, this, node.getTicker(), true);
        		
       			if(logMINOR) Logger.minor(this, "Receiving data");
       			final PeerNode p = pn;
       			receivingAsync = true;
       			br.receive(new BlockReceiverCompletion() {
       				
					public void blockReceived(byte[] data) {
        				synchronized(RequestSender.this) {
        					transferringFrom = null;
        				}
        				node.removeTransferringSender((NodeCHK)key, RequestSender.this);
                		try {
	                		// Received data
	               			p.transferSuccess();
	                		if(logMINOR) Logger.minor(this, "Received data");
                			verifyAndCommit(data);
	                		finish(SUCCESS, p, true);
	                		node.nodeStats.successfulBlockReceive();
                		} catch (KeyVerifyException e1) {
                			Logger.normal(this, "Got data but verify failed: "+e1, e1);
                			finish(GET_OFFER_VERIFY_FAILURE, p, true);
                       		offers.deleteLastOffer();
                		} catch (Throwable t) {
                			Logger.error(this, "Failed on "+this, t);
                			finish(INTERNAL_ERROR, p, true);
                		}
					}

					public void blockReceiveFailed(
							RetrievalException e) {
        				synchronized(RequestSender.this) {
        					transferringFrom = null;
        				}
        				node.removeTransferringSender((NodeCHK)key, RequestSender.this);
						try {
							if (e.getReason()==RetrievalException.SENDER_DISCONNECTED)
								Logger.normal(this, "Transfer failed (disconnect): "+e, e);
							else
								// A certain number of these are normal, it's better to track them through statistics than call attention to them in the logs.
								Logger.normal(this, "Transfer for offer failed ("+e.getReason()+"/"+RetrievalException.getErrString(e.getReason())+"): "+e+" from "+p, e);
							finish(GET_OFFER_TRANSFER_FAILED, p, true);
							// Backoff here anyway - the node really ought to have it!
							p.transferFailed("RequestSenderGetOfferedTransferFailed");
							offers.deleteLastOffer();
							node.nodeStats.failedBlockReceive(false, false, false);
                		} catch (Throwable t) {
                			Logger.error(this, "Failed on "+this, t);
                			finish(INTERNAL_ERROR, p, true);
                		}
					}
        				
        		});
        		return true;
        	} finally {
        		node.removeTransferringSender((NodeCHK)key, this);
        	}
		} else return false;
	}

	/** Here FINISHED means accepted, WAIT means try again (soft reject). */
    private DO waitForAccepted() {
    	while(true) {
    		
    		Message msg;
    		/**
    		 * What are we waiting for?
    		 * FNPAccepted - continue
    		 * FNPRejectedLoop - go to another node
    		 * FNPRejectedOverload - propagate back to source, go to another node if local
    		 */
    		
    		MessageFilter mfAccepted = MessageFilter.create().setSource(next).setField(DMT.UID, uid).setTimeout(ACCEPTED_TIMEOUT).setType(DMT.FNPAccepted);
    		MessageFilter mfRejectedLoop = MessageFilter.create().setSource(next).setField(DMT.UID, uid).setTimeout(ACCEPTED_TIMEOUT).setType(DMT.FNPRejectedLoop);
    		MessageFilter mfRejectedOverload = MessageFilter.create().setSource(next).setField(DMT.UID, uid).setTimeout(ACCEPTED_TIMEOUT).setType(DMT.FNPRejectedOverload);
    		
    		// mfRejectedOverload must be the last thing in the or
    		// So its or pointer remains null
    		// Otherwise we need to recreate it below
    		MessageFilter mf = mfAccepted.or(mfRejectedLoop.or(mfRejectedOverload));
    		
    		try {
    			msg = node.usm.waitFor(mf, this);
    			if(logMINOR) Logger.minor(this, "first part got "+msg);
    		} catch (DisconnectedException e) {
    			Logger.normal(this, "Disconnected from "+next+" while waiting for Accepted on "+uid);
    			next.noLongerRoutingTo(origTag, false);
    			return DO.NEXT_PEER;
    		}
    		
    		if(msg == null) {
    			if(logMINOR) Logger.minor(this, "Timeout waiting for Accepted");
    			// Timeout waiting for Accepted
    			next.localRejectedOverload("AcceptedTimeout");
    			forwardRejectedOverload();
    			node.failureTable.onFailed(key, next, htl, timeSinceSent());
    			// Try next node
    			// It could still be running. So the timeout is fatal to the node.
    			Logger.error(this, "Timeout awaiting Accepted/Rejected "+this+" to "+next);
    			//next.fatalTimeout();
    			return DO.NEXT_PEER;
    		}
    		
    		if(msg.getSpec() == DMT.FNPRejectedLoop) {
    			if(logMINOR) Logger.minor(this, "Rejected loop");
    			next.successNotOverload();
    			node.failureTable.onFailed(key, next, htl, timeSinceSent());
    			// Find another node to route to
    			next.noLongerRoutingTo(origTag, false);
    			return DO.NEXT_PEER;
    		}
    		
    		if(msg.getSpec() == DMT.FNPRejectedOverload) {
    			if(logMINOR) Logger.minor(this, "Rejected: overload");
    			// Non-fatal - probably still have time left
    			forwardRejectedOverload();
    			if (msg.getBoolean(DMT.IS_LOCAL)) {
    				
    				if(logMINOR) Logger.minor(this, "Is local");
    				
    				// FIXME new load management introduces soft rejects and waiting.
//    				if(msg.getSubMessage(DMT.FNPRejectIsSoft) != null) {
//    					if(logMINOR) Logger.minor(this, "Soft rejection, waiting to resend");
//    					nodesRoutedTo.remove(next);
//    					origTag.removeRoutingTo(next);
//    					return DO.WAIT;
//    				} else {
    					next.localRejectedOverload("ForwardRejectedOverload");
    					node.failureTable.onFailed(key, next, htl, timeSinceSent());
    					if(logMINOR) Logger.minor(this, "Local RejectedOverload, moving on to next peer");
    					// Give up on this one, try another
    					next.noLongerRoutingTo(origTag, false);
    					return DO.NEXT_PEER;
//    				}
    			}
    			//Could be a previous rejection, the timeout to incur another ACCEPTED_TIMEOUT is minimal...
    			continue;
    		}
    		
    		if(msg.getSpec() != DMT.FNPAccepted) {
    			Logger.error(this, "Unrecognized message: "+msg);
    			return DO.NEXT_PEER;
    		}
    		
    		return DO.FINISHED;
    		
    	}
	}

	private MessageFilter createMessageFilter(int timeout) {
		MessageFilter mfDNF = MessageFilter.create().setSource(next).setField(DMT.UID, uid).setTimeout(timeout).setType(DMT.FNPDataNotFound);
		MessageFilter mfRF = MessageFilter.create().setSource(next).setField(DMT.UID, uid).setTimeout(timeout).setType(DMT.FNPRecentlyFailed);
		MessageFilter mfRouteNotFound = MessageFilter.create().setSource(next).setField(DMT.UID, uid).setTimeout(timeout).setType(DMT.FNPRouteNotFound);
		MessageFilter mfRejectedOverload = MessageFilter.create().setSource(next).setField(DMT.UID, uid).setTimeout(timeout).setType(DMT.FNPRejectedOverload);
		
		MessageFilter mf = mfDNF.or(mfRF.or(mfRouteNotFound.or(mfRejectedOverload)));
		if(!isSSK) {
			MessageFilter mfRealDFCHK = MessageFilter.create().setSource(next).setField(DMT.UID, uid).setTimeout(timeout).setType(DMT.FNPCHKDataFound);
			mf = mfRealDFCHK.or(mf);
		} else {
			MessageFilter mfPubKey = MessageFilter.create().setSource(next).setField(DMT.UID, uid).setTimeout(timeout).setType(DMT.FNPSSKPubKey);
			MessageFilter mfDFSSKHeaders = MessageFilter.create().setSource(next).setField(DMT.UID, uid).setTimeout(timeout).setType(DMT.FNPSSKDataFoundHeaders);
			MessageFilter mfDFSSKData = MessageFilter.create().setSource(next).setField(DMT.UID, uid).setTimeout(timeout).setType(DMT.FNPSSKDataFoundData);
			mf = mfPubKey.or(mfDFSSKHeaders.or(mfDFSSKData.or(mf)));
		}
		return mf;
	}

	private DO handleMessage(Message msg) {
		//For debugging purposes, remember the number of responses AFTER the insert, and the last message type we received.
		gotMessages++;
		lastMessage=msg.getSpec().getName();
    	
    	if(msg.getSpec() == DMT.FNPDataNotFound) {
    		handleDataNotFound(msg);
    		return DO.FINISHED;
    	}
    	
    	if(msg.getSpec() == DMT.FNPRecentlyFailed) {
    		handleRecentlyFailed(msg);
    		return DO.FINISHED;
    	}
    	
    	if(msg.getSpec() == DMT.FNPRouteNotFound) {
    		handleRouteNotFound(msg);
    		return DO.NEXT_PEER;
    	}
    	
    	if(msg.getSpec() == DMT.FNPRejectedOverload) {
    		if(handleRejectedOverload(msg)) return DO.WAIT;
    		else return DO.NEXT_PEER;
    	}

    	if((!isSSK) && msg.getSpec() == DMT.FNPCHKDataFound) {
    		handleCHKDataFound(msg);
    		return DO.FINISHED;
    	}
    	
    	if(isSSK && msg.getSpec() == DMT.FNPSSKPubKey) {
    		
    		if(!handleSSKPubKey(msg)) return DO.NEXT_PEER;
			if(sskData != null && headers != null) {
				finishSSK(next);
				return DO.FINISHED;
			}
			return DO.WAIT;
    	}
    	            	
    	if(isSSK && msg.getSpec() == DMT.FNPSSKDataFoundData) {
    		
    		if(logMINOR) Logger.minor(this, "Got data on "+uid);
    		
        	sskData = ((ShortBuffer)msg.getObject(DMT.DATA)).getData();
        	
        	if(pubKey != null && headers != null) {
        		finishSSK(next);
        		return DO.FINISHED;
        	}
        	return DO.WAIT;

    	}
    	
    	if(isSSK && msg.getSpec() == DMT.FNPSSKDataFoundHeaders) {
    		
    		if(logMINOR) Logger.minor(this, "Got headers on "+uid);
    		
        	headers = ((ShortBuffer)msg.getObject(DMT.BLOCK_HEADERS)).getData();
    		
        	if(pubKey != null && sskData != null) {
        		finishSSK(next);
        		return DO.FINISHED;
        	}
        	return DO.WAIT;

    	}
    	
   		Logger.error(this, "Unexpected message: "+msg);
   		node.failureTable.onFailed(key, next, htl, timeSinceSent());
   		return DO.NEXT_PEER;
    	
	}
    
	protected void makeTurtle() {
		synchronized(this) {
			if(tryTurtle) return;
			tryTurtle = true;
		}
		node.makeTurtle(RequestSender.this);
	}

	private static enum DO {
    	FINISHED,
    	WAIT,
    	NEXT_PEER
    }

    /** @return True unless the pubkey is broken and we should try another node */
    private boolean handleSSKPubKey(Message msg) {
		if(logMINOR) Logger.minor(this, "Got pubkey on "+uid);
		byte[] pubkeyAsBytes = ((ShortBuffer)msg.getObject(DMT.PUBKEY_AS_BYTES)).getData();
		try {
			if(pubKey == null)
				pubKey = DSAPublicKey.create(pubkeyAsBytes);
			((NodeSSK)key).setPubKey(pubKey);
			return true;
		} catch (SSKVerifyException e) {
			pubKey = null;
			Logger.error(this, "Invalid pubkey from "+source+" on "+uid+" ("+e.getMessage()+ ')', e);
    		node.failureTable.onFailed(key, next, htl, timeSinceSent());
			return false; // try next node
		} catch (CryptFormatException e) {
			Logger.error(this, "Invalid pubkey from "+source+" on "+uid+" ("+e+ ')');
    		node.failureTable.onFailed(key, next, htl, timeSinceSent());
			return false; // try next node
		}
	}

	private void handleCHKDataFound(Message msg) {
    	// Found data
    	
    	// First get headers
    	
    	headers = ((ShortBuffer)msg.getObject(DMT.BLOCK_HEADERS)).getData();
    	
    	// FIXME: Validate headers
    	
    	node.addTransferringSender((NodeCHK)key, this);
    	
    	prb = new PartiallyReceivedBlock(Node.PACKETS_IN_BLOCK, Node.PACKET_SIZE);
    	
    	synchronized(this) {
    		notifyAll();
    	}
    	fireCHKTransferBegins();
    	
    	final long tStart = System.currentTimeMillis();
    	final BlockReceiver br = new BlockReceiver(node.usm, next, uid, prb, this, node.getTicker(), true);
    	
    	if(logMINOR) Logger.minor(this, "Receiving data");
    	final PeerNode from = next;
    	synchronized(this) {
    		transferringFrom = next;
    	}
    	node.getTicker().queueTimedJob(new Runnable() {
    		
    		public void run() {
    			synchronized(RequestSender.this) {
    				if(transferringFrom != from) return;
    			}
    			makeTurtle();
    		}
    		
    	}, 60*1000);
    	final PeerNode sentTo = next;
			receivingAsync = true;
    	br.receive(new BlockReceiverCompletion() {
    		
    		public void blockReceived(byte[] data) {
    			try {
    				long tEnd = System.currentTimeMillis();
    				transferTime = tEnd - tStart;
    				boolean turtle;
    				boolean turtleBackedOff;
    				synchronized(RequestSender.this) {
    					turtle = turtleMode;
    					turtleBackedOff = sentBackoffTurtle;
    					sentBackoffTurtle = true;
    					transferringFrom = null;
    				}
    				node.removeTransferringSender((NodeCHK)key, RequestSender.this);
    				if(!turtle)
    					sentTo.transferSuccess();
    				else {
    					Logger.normal(this, "TURTLE SUCCEEDED: "+key+" for "+this+" in "+TimeUtil.formatTime(transferTime, 2, true));
    					if(!turtleBackedOff)
    						sentTo.transferFailed("TurtledTransfer");
    					node.nodeStats.turtleSucceeded();
    				}
    				sentTo.successNotOverload();
    				if(turtle) {
    					sentTo.unregisterTurtleTransfer(RequestSender.this);
    					node.unregisterTurtleTransfer(RequestSender.this);
    				}
    				node.nodeStats.successfulBlockReceive();
    				if(logMINOR) Logger.minor(this, "Received data");
    				// Received data
    				try {
    					verifyAndCommit(data);
    				} catch (KeyVerifyException e1) {
    					Logger.normal(this, "Got data but verify failed: "+e1, e1);
    					finish(VERIFY_FAILURE, sentTo, false);
    					node.failureTable.onFinalFailure(key, sentTo, htl, origHTL, FailureTable.REJECT_TIME, source);
    					return;
    				}
    				finish(SUCCESS, sentTo, false);
    			} catch (Throwable t) {
        			Logger.error(this, "Failed on "+this, t);
        			finish(INTERNAL_ERROR, sentTo, true);
    			}
    		}
    		
    		public void blockReceiveFailed(
    				RetrievalException e) {
    			try {
    				boolean turtle;
    				synchronized(RequestSender.this) {
    					turtle = turtleMode;
    					transferringFrom = null;
    				}
    				node.removeTransferringSender((NodeCHK)key, RequestSender.this);
    				if(turtle) {
    					if(e.getReason() != RetrievalException.GONE_TO_TURTLE_MODE) {
    						Logger.normal(this, "TURTLE FAILED: "+key+" for "+this+" : "+e);
    						node.nodeStats.turtleFailed();
    					} else {
    						if(logMINOR) Logger.minor(this, "Upstream turtled for "+this+" from "+sentTo);
    					}
    					sentTo.unregisterTurtleTransfer(RequestSender.this);
    					node.unregisterTurtleTransfer(RequestSender.this);
    				}
    				if (e.getReason()==RetrievalException.SENDER_DISCONNECTED)
    					Logger.normal(this, "Transfer failed (disconnect): "+e, e);
    				else
    					// A certain number of these are normal, it's better to track them through statistics than call attention to them in the logs.
    					Logger.normal(this, "Transfer failed ("+e.getReason()+"/"+RetrievalException.getErrString(e.getReason())+"): "+e+" from "+sentTo, e);
    				// We do an ordinary backoff in all cases.
    				// This includes the case where we decide not to turtle the request. This is reasonable as if it had completely quickly we wouldn't have needed to make that choice.
    				sentTo.localRejectedOverload("TransferFailedRequest"+e.getReason());
    				finish(TRANSFER_FAILED, sentTo, false);
    				node.failureTable.onFinalFailure(key, sentTo, htl, origHTL, FailureTable.REJECT_TIME, source);
    				int reason = e.getReason();
    				boolean timeout = (!br.senderAborted()) &&
    				(reason == RetrievalException.SENDER_DIED || reason == RetrievalException.RECEIVER_DIED || reason == RetrievalException.TIMED_OUT
    						|| reason == RetrievalException.UNABLE_TO_SEND_BLOCK_WITHIN_TIMEOUT);
    				// But we only do a transfer backoff (which is separate, and starts at a higher threshold) if we timed out i.e. not if upstream turtled.
    				if(timeout) {
    					// Looks like a timeout. Backoff, even if it's a turtle.
    					if(logMINOR) Logger.minor(this, "Timeout transferring data : "+e, e);
    					sentTo.transferFailed(e.getErrString());
    				} else {
    					// Quick failure (in that we didn't have to timeout). Don't backoff.
    					// Treat as a DNF.
    					// If it was turtled, and then failed, still treat it as a DNF.
    					node.failureTable.onFinalFailure(key, sentTo, htl, origHTL, FailureTable.REJECT_TIME, source);
    				}
    				node.nodeStats.failedBlockReceive(true, timeout, reason == RetrievalException.GONE_TO_TURTLE_MODE);
    			} catch (Throwable t) {
        			Logger.error(this, "Failed on "+this, t);
        			finish(INTERNAL_ERROR, sentTo, true);
    			}
    		}
    		
    	});
	}

	/** @return True to continue waiting for this node, false to move on to another. */
	private boolean handleRejectedOverload(Message msg) {
		
		// Non-fatal - probably still have time left
		forwardRejectedOverload();
		rejectOverloads++;
		if (msg.getBoolean(DMT.IS_LOCAL)) {
			//NB: IS_LOCAL means it's terminal. not(IS_LOCAL) implies that the rejection message was forwarded from a downstream node.
			//"Local" from our peers perspective, this has nothing to do with local requests (source==null)
    		node.failureTable.onFailed(key, next, htl, timeSinceSent());
			next.localRejectedOverload("ForwardRejectedOverload2");
			// Node in trouble suddenly??
			Logger.normal(this, "Local RejectedOverload after Accepted, moving on to next peer");
			// Give up on this one, try another
			origTag.removeRoutingTo(next);
			return false;
		}
		//so long as the node does not send a (IS_LOCAL) message. Interestingly messages can often timeout having only received this message.
		return true;
	}

	private void handleRouteNotFound(Message msg) {
		// Backtrack within available hops
		short newHtl = msg.getShort(DMT.HTL);
		if(newHtl < htl) htl = newHtl;
		next.successNotOverload();
		node.failureTable.onFailed(key, next, htl, timeSinceSent());
		origTag.removeRoutingTo(next);
	}

	private void handleDataNotFound(Message msg) {
		next.successNotOverload();
		finish(DATA_NOT_FOUND, next, false);
		node.failureTable.onFinalFailure(key, next, htl, origHTL, FailureTable.REJECT_TIME, source);
	}

	private void handleRecentlyFailed(Message msg) {
		next.successNotOverload();
		/*
		 * Must set a correct recentlyFailedTimeLeft before calling this finish(), because it will be
		 * passed to the handler.
		 * 
		 * It is *VITAL* that the TIME_LEFT we pass on is not larger than it should be.
		 * It is somewhat less important that it is not too much smaller than it should be.
		 * 
		 * Why? Because:
		 * 1) We have to use FNPRecentlyFailed to create failure table entries. Because otherwise,
		 * the failure table is of little value: A request is routed through a node, which gets a DNF,
		 * and adds a failure table entry. Other requests then go through that node via other paths.
		 * They are rejected with FNPRecentlyFailed - not with DataNotFound. If this does not create
		 * failure table entries, more requests will be pointlessly routed through that chain.
		 * 
		 * 2) If we use a fixed timeout on receiving FNPRecentlyFailed, they can be self-seeding. 
		 * What this means is A sends a request to B, which DNFs. This creates a failure table entry 
		 * which lasts for 10 minutes. 5 minutes later, A sends another request to B, which is killed
		 * with FNPRecentlyFailed because of the failure table entry. B's failure table lasts for 
		 * another 5 minutes, but A's lasts for the full 10 minutes i.e. until 5 minutes after B's. 
		 * After B's failure table entry has expired, but before A's expires, B sends a request to A. 
		 * A replies with FNPRecentlyFailed. Repeat ad infinitum: A reinforces B's blocks, and B 
		 * reinforces A's blocks!
		 * 
		 * 3) This can still happen even if we check where the request is coming from. A loop could 
		 * very easily form: A - B - C - A. A requests from B, DNFs (assume the request comes in from 
		 * outside, there are more nodes. C requests from A, sets up a block. B's block expires, C's 
		 * is still active. A requests from B which requests from C ... and it goes round again.
		 * 
		 * 4) It is exactly the same if we specify a timeout, unless the timeout can be guaranteed to 
		 * not increase the expiry time.
		 */
		
		// First take the original TIME_LEFT. This will start at 10 minutes if we get rejected in
		// the same millisecond as the failure table block was added.
		int timeLeft = msg.getInt(DMT.TIME_LEFT);
		int origTimeLeft = timeLeft;
		
		if(timeLeft <= 0) {
			Logger.error(this, "Impossible: timeLeft="+timeLeft);
			origTimeLeft = 0;
			timeLeft=1000; // arbitrary default...
		}
		
		// This is in theory relative to when the request was received by the node. Lets make it relative
		// to a known event before that: the time when we sent the request.
		
		long timeSinceSent = Math.max(0, timeSinceSent());
		timeLeft -= timeSinceSent;
		
		// Subtract 1% for good measure / to compensate for dodgy clocks
		timeLeft -= origTimeLeft / 100;
		
		//Store the timeleft so that the requestHandler can get at it.
		recentlyFailedTimeLeft = timeLeft;
		
			// Kill the request, regardless of whether there is timeout left.
		// If there is, we will avoid sending requests for the specified period.
		// FIXME we need to create the FT entry.
			finish(RECENTLY_FAILED, next, false);
			node.failureTable.onFinalFailure(key, next, htl, origHTL, timeLeft, source);
	}

	/**
     * Finish fetching an SSK. We must have received the data, the headers and the pubkey by this point.
     * @param next The node we received the data from.
     */
	private void finishSSK(PeerNode next) {
    	try {
			block = new SSKBlock(sskData, headers, (NodeSSK)key, false);
			node.storeShallow(block, canWriteClientCache, canWriteDatastore, false);
			if(node.random.nextInt(RANDOM_REINSERT_INTERVAL) == 0)
				node.queueRandomReinsert(block);
			finish(SUCCESS, next, false);
		} catch (SSKVerifyException e) {
			Logger.error(this, "Failed to verify: "+e+" from "+next, e);
			finish(VERIFY_FAILURE, next, false);
			return;
		} catch (KeyCollisionException e) {
			Logger.normal(this, "Collision on "+this);
			finish(SUCCESS, next, false);
		}
	}

    /**
     * Finish fetching an SSK. We must have received the data, the headers and the pubkey by this point.
     * @param next The node we received the data from.
     * @return True if the request has completed. False if we need to look elsewhere.
     */
	private boolean finishSSKFromGetOffer(PeerNode next) {
    	try {
			block = new SSKBlock(sskData, headers, (NodeSSK)key, false);
			node.storeShallow(block, canWriteClientCache, canWriteDatastore, tryOffersOnly);
			if(node.random.nextInt(RANDOM_REINSERT_INTERVAL) == 0)
				node.queueRandomReinsert(block);
			finish(SUCCESS, next, true);
			return true;
		} catch (SSKVerifyException e) {
			Logger.error(this, "Failed to verify (from get offer): "+e+" from "+next, e);
			return false;
		} catch (KeyCollisionException e) {
			Logger.normal(this, "Collision (from get offer) on "+this);
			finish(SUCCESS, next, true);
			return false;
		}
	}

	private Message createDataRequest() {
		Message req;
    	if(!isSSK)
    		req = DMT.createFNPCHKDataRequest(uid, htl, (NodeCHK)key);
    	else// if(key instanceof NodeSSK)
    		req = DMT.createFNPSSKDataRequest(uid, htl, (NodeSSK)key, pubKey == null);
    	req.addSubMessage(DMT.createFNPRealTimeFlag(realTimeFlag));
    	return req;
	}

	private void verifyAndCommit(byte[] data) throws KeyVerifyException {
    	if(!isSSK) {
    		CHKBlock block = new CHKBlock(data, headers, (NodeCHK)key);
    		// Cache only in the cache, not the store. The reason for this is that
    		// requests don't go to the full distance, and therefore pollute the 
    		// store; simulations it is best to only include data from requests
    		// which go all the way i.e. inserts.
    		node.storeShallow(block, canWriteClientCache, canWriteDatastore, tryOffersOnly);
			if(node.random.nextInt(RANDOM_REINSERT_INTERVAL) == 0)
				node.queueRandomReinsert(block);
    	} else /*if (key instanceof NodeSSK)*/ {
    		try {
				node.storeShallow(new SSKBlock(data, headers, (NodeSSK)key, false), canWriteClientCache, canWriteDatastore, tryOffersOnly);
			} catch (KeyCollisionException e) {
				Logger.normal(this, "Collision on "+this);
			}
    	}
	}

	private volatile boolean hasForwardedRejectedOverload;
    
    /** Forward RejectedOverload to the request originator */
    private void forwardRejectedOverload() {
		synchronized (this) {
			if(hasForwardedRejectedOverload) return;
			hasForwardedRejectedOverload = true;
			notifyAll();
		}
		fireReceivedRejectOverload();
	}
    
    public PartiallyReceivedBlock getPRB() {
        return prb;
    }

    public boolean transferStarted() {
        return prb != null;
    }

    // these are bit-masks
    static final short WAIT_REJECTED_OVERLOAD = 1;
    static final short WAIT_TRANSFERRING_DATA = 2;
    static final short WAIT_FINISHED = 4;
    
    static final short WAIT_ALL = 
    	WAIT_REJECTED_OVERLOAD | WAIT_TRANSFERRING_DATA | WAIT_FINISHED;
    
    /**
     * Wait until either the transfer has started, we receive a 
     * RejectedOverload, or we get a terminal status code.
     * Must not return until we are finished - cannot timeout, because the caller will unlock
     * the UID!
     * @param mask Bitmask indicating what NOT to wait for i.e. the situation when this function
     * exited last time (see WAIT_ constants above). Bits can also be set true even though they
     * were not valid, to indicate that the caller doesn't care about that bit.
     * If zero, function will throw an IllegalArgumentException.
     * @return Bitmask indicating present situation. Can be fed back to this function,
     * if nonzero.
     */
    public synchronized short waitUntilStatusChange(short mask) {
    	if(mask == WAIT_ALL) throw new IllegalArgumentException("Cannot ignore all!");
    	while(true) {
    	long now = System.currentTimeMillis();
    	long deadline = now + 300 * 1000;
        while(true) {
        	short current = mask; // If any bits are set already, we ignore those states.
        	
       		if(hasForwardedRejectedOverload)
       			current |= WAIT_REJECTED_OVERLOAD;
        	
       		if(prb != null)
       			current |= WAIT_TRANSFERRING_DATA;
        	
        	if(status != NOT_FINISHED || sentAbortDownstreamTransfers)
        		current |= WAIT_FINISHED;
        	
        	if(current != mask) return current;
			
            try {
            	if(now >= deadline) {
            		Logger.error(this, "Waited more than 5 minutes for status change on " + this + " current = " + current + " and there was no change.");
            		break;
            	}
            	
            	if(logMINOR) Logger.minor(this, "Waiting for status change on "+this+" current is "+current+" status is "+status);
                wait(deadline - now);
                now = System.currentTimeMillis(); // Is used in the next iteration so needed even without the logging
                
                if(now >= deadline) {
                    Logger.error(this, "Waited more than 5 minutes for status change on " + this + " current = " + current + ", maybe nobody called notify()");
                    // Normally we would break; here, but we give the function a change to succeed
                    // in the next iteration and break in the above if(now >= deadline) if it
                    // did not succeed. This makes the function work if notify() is not called.
                }
            } catch (InterruptedException e) {
                // Ignore
            }
        }
    	}
    }
    
	private static MedianMeanRunningAverage avgTimeTaken = new MedianMeanRunningAverage();
	
	private static MedianMeanRunningAverage avgTimeTakenTurtle = new MedianMeanRunningAverage();
	
	private static MedianMeanRunningAverage avgTimeTakenTransfer = new MedianMeanRunningAverage();
	
	private long transferTime;
	
    private void finish(int code, PeerNode next, boolean fromOfferedKey) {
    	if(logMINOR) Logger.minor(this, "finish("+code+") on "+uid);
        
    	boolean turtle;
    	
        synchronized(this) {
        	if(status != NOT_FINISHED) {
        		if(logMINOR) Logger.minor(this, "Status already set to "+status+" - returning on "+this+" would be setting "+code+" from "+next);
        		return;
        	}
            status = code;
            notifyAll();
            turtle = turtleMode;
            if(status == SUCCESS)
            	successFrom = next;
        }
		
        if(status == SUCCESS) {
        	if((!isSSK) && transferTime > 0 && logMINOR) {
        		long timeTaken = System.currentTimeMillis() - startTime;
        		synchronized(avgTimeTaken) {
        			if(turtle)
        				avgTimeTakenTurtle.report(timeTaken);
        			else {
        				avgTimeTaken.report(timeTaken);
            			avgTimeTakenTransfer.report(transferTime);
        			}
        			if(turtle) {
        				if(logMINOR) Logger.minor(this, "Successful CHK turtle request took "+timeTaken+" average "+avgTimeTakenTurtle);
        			} else {
        				if(logMINOR) Logger.minor(this, "Successful CHK request took "+timeTaken+" average "+avgTimeTaken);
            			if(logMINOR) Logger.minor(this, "Successful CHK request transfer "+transferTime+" average "+avgTimeTakenTransfer);
            			if(logMINOR) Logger.minor(this, "Search phase: median "+(avgTimeTaken.currentValue() - avgTimeTakenTransfer.currentValue())+"ms, mean "+(avgTimeTaken.meanValue() - avgTimeTakenTransfer.meanValue())+"ms");
        			}
        		}
        	}
        	if(next != null) {
        		next.onSuccess(false, isSSK);
        	}
        	// FIXME should this be called when fromOfferedKey??
       		node.nodeStats.requestCompleted(true, source != null, isSSK);
        	
			//NOTE: because of the requesthandler implementation, this will block and wait
			//      for downstream transfers on a CHK. The opennet stuff introduces
			//      a delay of it's own if we don't get the expected message.
			fireRequestSenderFinished(code);
			
			if(!fromOfferedKey) {
				if((!isSSK) && next != null && 
						(next.isOpennet() || node.passOpennetRefsThroughDarknet()) ) {
					finishOpennet(next);
				} else
					finishOpennetNull(next);
			}
        } else {
        	node.nodeStats.requestCompleted(false, source != null, isSSK);
			fireRequestSenderFinished(code);
		}
        
		synchronized(this) {
			opennetFinished = true;
			notifyAll();
		}
		
    	if(sentAbortDownstreamTransfers) {
    		// We took on responsibility for unlocking.
    		if(logMINOR) Logger.minor(this, "Unlocking after turtle");
    		node.unlockUID(uid, key instanceof NodeSSK, false, false, false, source == null, realTimeFlag, origTag);
    	}
        
    }

    /** Wait for the opennet completion message and discard it */
    private void finishOpennetNull(PeerNode next) {
    	MessageFilter mf = MessageFilter.create().setSource(next).setField(DMT.UID, uid).setTimeout(OPENNET_TIMEOUT).setType(DMT.FNPOpennetCompletedAck);
    	
    	try {
			node.usm.addAsyncFilter(mf, new NullAsyncMessageFilterCallback(), this);
		} catch (DisconnectedException e) {
			// Fine by me.
		}
		
		// FIXME support new format path folding
	}

	/**
     * Do path folding, maybe.
     * Wait for either a CompletedAck or a ConnectDestination.
     * If the former, exit.
     * If we want a connection, reply with a ConnectReply, otherwise send a ConnectRejected and exit.
     * Add the peer.
     */
    private void finishOpennet(PeerNode next) {
    	
    	OpennetManager om;
    	
    	try {
    		om = node.getOpennet();
    		
    		if(om == null) return; // Nothing to do
    		
        	byte[] noderef = om.waitForOpennetNoderef(false, next, uid, this);
        	
        	if(noderef == null) return;
        	
        	SimpleFieldSet ref = om.validateNoderef(noderef, 0, noderef.length, next, false);
        	
        	if(ref == null) return;
        	
			if(node.addNewOpennetNode(ref, ConnectionType.PATH_FOLDING) == null) {
				// If we don't want it let somebody else have it
				synchronized(this) {
					opennetNoderef = noderef;
					// RequestHandler will send a noderef back up, eventually
				}
				return;
			} else {
				// opennetNoderef = null i.e. we want the noderef so we won't pass it further down.
				Logger.normal(this, "Added opennet noderef in "+this+" from "+next);
			}
			
	    	// We want the node: send our reference
    		om.sendOpennetRef(true, uid, next, om.crypto.myCompressedFullRef(), this);

		} catch (FSParseException e) {
			Logger.error(this, "Could not parse opennet noderef for "+this+" from "+next, e);
			return;
		} catch (PeerParseException e) {
			Logger.error(this, "Could not parse opennet noderef for "+this+" from "+next, e);
			return;
		} catch (ReferenceSignatureVerificationException e) {
			Logger.error(this, "Bad signature on opennet noderef for "+this+" from "+next+" : "+e, e);
			return;
		} catch (NotConnectedException e) {
			// Hmmm... let the LRU deal with it
			if(logMINOR)
				Logger.minor(this, "Not connected sending ConnectReply on "+this+" to "+next);
    	} finally {
    		synchronized(this) {
    			opennetFinished = true;
    			notifyAll();
    		}
    	}
	}

    // Opennet stuff
    
    /** Have we finished all opennet-related activities? */
    private boolean opennetFinished;
    
    /** Opennet noderef from next node */
    private byte[] opennetNoderef;
    
    public byte[] waitForOpennetNoderef() {
    	synchronized(this) {
    		while(true) {
    			if(opennetFinished) {
    				// Only one RequestHandler may take the noderef
    				byte[] ref = opennetNoderef;
    				opennetNoderef = null;
    				return ref;
    			}
    			try {
					wait(OPENNET_TIMEOUT);
				} catch (InterruptedException e) {
					// Ignore
					continue;
				}
				return null;
    		}
    	}
    }

    public PeerNode successFrom() {
    	return successFrom;
    }
    
    public synchronized PeerNode routedLast() {
    	return lastNode;
    }
    
	public byte[] getHeaders() {
        return headers;
    }

    public int getStatus() {
        return status;
    }

    public short getHTL() {
        return htl;
    }
    
    final byte[] getSSKData() {
    	return sskData;
    }
    
    public SSKBlock getSSKBlock() {
    	return block;
    }

	private volatile Object totalBytesSync = new Object();
	private int totalBytesSent;
	
	public void sentBytes(int x) {
		synchronized(totalBytesSync) {
			totalBytesSent += x;
		}
		node.nodeStats.requestSentBytes(isSSK, x);
	}
	
	public int getTotalSentBytes() {
		synchronized(totalBytesSync) {
			return totalBytesSent;
		}
	}
	
	private int totalBytesReceived;
	
	public void receivedBytes(int x) {
		synchronized(totalBytesSync) {
			totalBytesReceived += x;
		}
		node.nodeStats.requestReceivedBytes(isSSK, x);
	}
	
	public int getTotalReceivedBytes() {
		synchronized(totalBytesSync) {
			return totalBytesReceived;
		}
	}
	
	synchronized boolean hasForwarded() {
		return hasForwarded;
	}

	public void sentPayload(int x) {
		node.sentPayload(x);
		node.nodeStats.requestSentBytes(isSSK, -x);
	}
	
	private int recentlyFailedTimeLeft;

	synchronized int getRecentlyFailedTimeLeft() {
		return recentlyFailedTimeLeft;
	}
	
	public boolean isLocalRequestSearch() {
		return (source==null);
	}
	
	/** All these methods should return quickly! */
	interface Listener {
		/** Should return quickly, allocate a thread if it needs to block etc */
		void onReceivedRejectOverload();
		/** Should return quickly, allocate a thread if it needs to block etc */
		void onCHKTransferBegins();
		/** Should return quickly, allocate a thread if it needs to block etc */
		void onRequestSenderFinished(int status, long grabbedUID);
		/** Abort downstream transfers (not necessarily upstream ones, so not via the PRB).
		 * Should return quickly, allocate a thread if it needs to block etc. */
		void onAbortDownstreamTransfers(int reason, String desc);
	}
	
	public void addListener(Listener l) {
		// Only call here if we've already called for the other listeners.
		// Therefore the callbacks will only be called once.
		boolean reject=false;
		boolean transfer=false;
		boolean sentFinished;
		boolean sentTransferCancel = false;
		int status;
		synchronized (this) {
			synchronized (listeners) {
				sentTransferCancel = sentAbortDownstreamTransfers;
				if(!sentTransferCancel)
					listeners.add(l);
				reject = sentReceivedRejectOverload;
				transfer = sentCHKTransferBegins;
				sentFinished = sentRequestSenderFinished;
			}
			reject=reject && hasForwardedRejectedOverload;
			transfer=transfer && transferStarted();
			status=this.status;
		}
		if (reject)
			l.onReceivedRejectOverload();
		if (transfer)
			l.onCHKTransferBegins();
		if(sentTransferCancel)
			l.onAbortDownstreamTransfers(abortDownstreamTransfersReason, abortDownstreamTransfersDesc);
		if (status!=NOT_FINISHED && sentFinished)
			l.onRequestSenderFinished(status, -1);
	}
	
	private boolean sentReceivedRejectOverload;
	
	private void fireReceivedRejectOverload() {
		synchronized (listeners) {
			if(sentReceivedRejectOverload) return;
			sentReceivedRejectOverload = true;
			for (Listener l : listeners) {
				try {
					l.onReceivedRejectOverload();
				} catch (Throwable t) {
					Logger.error(this, "Caught: "+t, t);
				}
			}
		}
	}
	
	private boolean sentCHKTransferBegins;
	
	private void fireCHKTransferBegins() {
		synchronized (listeners) {
			sentCHKTransferBegins = true;
			for (Listener l : listeners) {
				try {
					l.onCHKTransferBegins();
				} catch (Throwable t) {
					Logger.error(this, "Caught: "+t, t);
				}
			}
		}
	}
	
	private boolean sentRequestSenderFinished;
	
	private void fireRequestSenderFinished(int status) {
		synchronized (listeners) {
			sentRequestSenderFinished = true;
			for (Listener l : listeners) {
				try {
					l.onRequestSenderFinished(status, -1);
				} catch (Throwable t) {
					Logger.error(this, "Caught: "+t, t);
				}
			}
		}
	}

	private boolean sentAbortDownstreamTransfers;
	private int abortDownstreamTransfersReason;
	private String abortDownstreamTransfersDesc;
	private boolean receivingAsync;
	
	private void sendAbortDownstreamTransfers(int reason, String desc) {
		synchronized (listeners) {
			abortDownstreamTransfersReason = reason;
			abortDownstreamTransfersDesc = desc;
			sentAbortDownstreamTransfers = true;
			for (Listener l : listeners) {
				try {
					l.onAbortDownstreamTransfers(reason, desc);
					l.onRequestSenderFinished(TRANSFER_FAILED, uid);
				} catch (Throwable t) {
					Logger.error(this, "Caught: "+t, t);
				}
			}
			listeners.clear();
		}
		synchronized(this) {
			notifyAll();
		}
	}
	
	public int getPriority() {
		return NativeThread.HIGH_PRIORITY;
	}

	public void setTurtle() {
		synchronized(this) {
			this.turtleMode = true;
		}
		sendAbortDownstreamTransfers(RetrievalException.GONE_TO_TURTLE_MODE, "Turtling");
		node.getTicker().queueTimedJob(new Runnable() {

			public void run() {
				PeerNode from;
				synchronized(RequestSender.this) {
					if(sentBackoffTurtle) return;
					sentBackoffTurtle = true;
					from = transferringFrom;
					if(from == null) return;
				}
				from.transferFailed("TurtledTransfer");
			}
			
		}, 30*1000);
	}

	public PeerNode transferringFrom() {
		return transferringFrom;
	}

	public void killTurtle(String description) {
		prb.abort(RetrievalException.TURTLE_KILLED, description);
		node.failureTable.onFinalFailure(key, transferringFrom(), htl, origHTL, FailureTable.REJECT_TIME, source);
	}

	public synchronized boolean abortedDownstreamTransfers() {
		return sentAbortDownstreamTransfers;
	}

	public synchronized boolean mustUnlock() {
		return mustUnlock;
	}
	
	public long fetchTimeout() {
		return fetchTimeout;
	}

}
