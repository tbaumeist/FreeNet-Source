/*
 * Dijjer - A Peer to Peer HTTP Cache
 * Copyright (C) 2004,2005 Change.Tv, Inc
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 59 Temple Place, Suite 330, Boston, MA  02111-1307  USA
 */

package freenet.io.comm;

import java.util.LinkedList;

import freenet.crypt.DSAPublicKey;
import freenet.keys.Key;
import freenet.keys.NodeCHK;
import freenet.keys.NodeSSK;
import freenet.node.NodeStats.PeerLoadStats;
import freenet.support.BitArray;
import freenet.support.Buffer;
import freenet.support.Fields;
import freenet.support.Logger;
import freenet.support.ShortBuffer;

/**
 * @author ian
 *
 * To change the template for this generated type comment go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
public class DMT {

	public static final String UID = "uid";
	public static final String SEND_TIME = "sendTime";
	public static final String EXTERNAL_ADDRESS = "externalAddress";
	public static final String BUILD = "build";
	public static final String FIRST_GOOD_BUILD = "firstGoodBuild";
	public static final String JOINER = "joiner";
	public static final String REASON = "reason";
	public static final String DESCRIPTION = "description";
	public static final String TTL = "ttl";
	public static final String PEERS = "peers";
	public static final String URL = "url";
	public static final String FORWARDERS = "forwarders";
	public static final String FILE_LENGTH = "fileLength";
	public static final String LAST_MODIFIED = "lastModified";
	public static final String CHUNK_NO = "chunkNo";
	public static final String DATA_SOURCE = "dataSource";
	public static final String CACHED = "cached";
	public static final String PACKET_NO = "packetNo";
	public static final String DATA = "data";
	public static final String IS_HASH = "isHash";
	public static final String HASH = "hash";
	public static final String SENT = "sent";
	public static final String MISSING = "missing";
	public static final String KEY = "key";
	public static final String CHK_HEADER = "chkHeader";
	public static final String FREENET_URI = "freenetURI";
	public static final String FREENET_ROUTING_KEY = "freenetRoutingKey";
	public static final String TEST_CHK_HEADERS = "testCHKHeaders";
	public static final String HTL = "hopsToLive";
	public static final String SUCCESS = "success";
	public static final String FNP_SOURCE_PEERNODE = "sourcePeerNode";
	public static final String PING_SEQNO = "pingSequenceNumber";
	public static final String LOCATION = "location";
	public static final String NEAREST_LOCATION = "nearestLocation";
	public static final String BEST_LOCATION = "bestLocation";
	public static final String TARGET_LOCATION = "targetLocation";
	public static final String TYPE = "type";
	public static final String PAYLOAD = "payload";
	public static final String COUNTER = "counter";
	public static final String UNIQUE_COUNTER = "uniqueCounter";
	public static final String LINEAR_COUNTER = "linearCounter";
	public static final String RETURN_LOCATION = "returnLocation";
	public static final String BLOCK_HEADERS = "blockHeaders";
	public static final String DATA_INSERT_REJECTED_REASON = "dataInsertRejectedReason";
	public static final String STREAM_SEQNO = "streamSequenceNumber";
	public static final String IS_LOCAL = "isLocal";
	public static final String ANY_TIMED_OUT = "anyTimedOut";
	public static final String PUBKEY_HASH = "pubkeyHash";
	public static final String NEED_PUB_KEY = "needPubKey";
	public static final String PUBKEY_AS_BYTES = "pubkeyAsBytes";
	public static final String SOURCE_NODENAME = "sourceNodename";
	public static final String TARGET_NODENAME = "targetNodename";
	public static final String NODE_TO_NODE_MESSAGE_TYPE = "nodeToNodeMessageType";
	public static final String NODE_TO_NODE_MESSAGE_TEXT = "nodeToNodeMessageText";
	public static final String NODE_TO_NODE_MESSAGE_DATA = "nodeToNodeMessageData";
	public static final String NODE_UIDS = "nodeUIDs";
	public static final String MY_UID = "myUID";
	public static final String PEER_LOCATIONS = "peerLocations";
	public static final String PEER_UIDS = "peerUIDs";
	public static final String BEST_LOCATIONS_NOT_VISITED = "bestLocationsNotVisited";
	public static final String MAIN_JAR_KEY = "mainJarKey";
	public static final String EXTRA_JAR_KEY = "extraJarKey";
	public static final String REVOCATION_KEY = "revocationKey";
	public static final String HAVE_REVOCATION_KEY = "haveRevocationKey";
	public static final String MAIN_JAR_VERSION = "mainJarVersion";
	public static final String EXTRA_JAR_VERSION = "extJarVersion";
	public static final String REVOCATION_KEY_TIME_LAST_TRIED = "revocationKeyTimeLastTried";
	public static final String REVOCATION_KEY_DNF_COUNT = "revocationKeyDNFCount";
	public static final String REVOCATION_KEY_FILE_LENGTH = "revocationKeyFileLength";
	public static final String MAIN_JAR_FILE_LENGTH = "mainJarFileLength";
	public static final String EXTRA_JAR_FILE_LENGTH = "extraJarFileLength";
	public static final String PING_TIME = "pingTime";
	public static final String BWLIMIT_DELAY_TIME = "bwlimitDelayTime";
	public static final String TIME = "time";
	public static final String FORK_COUNT = "forkCount";
	public static final String TIME_LEFT = "timeLeft";
	public static final String PREV_UID = "prevUID";
	public static final String OPENNET_NODEREF = "opennetNoderef";
	public static final String REMOVE = "remove";
	public static final String PURGE = "purge";
	public static final String TRANSFER_UID = "transferUID";
	public static final String NODEREF_LENGTH = "noderefLength";
	public static final String PADDED_LENGTH = "paddedLength";
	public static final String TIME_DELTAS = "timeDeltas";
	public static final String HASHES = "hashes";
	public static final String REJECT_CODE = "rejectCode";
	public static final String ROUTING_ENABLED = "routingEnabled";
	public static final String OFFER_AUTHENTICATOR = "offerAuthenticator";
	public static final String DAWN_HTL = "dawnHtl";
	public static final String SECRET = "secret";
	public static final String NODE_IDENTITY = "nodeIdentity";
	public static final String UPTIME_PERCENT_48H = "uptimePercent48H";
	public static final String ENABLE_INSERT_FORK_WHEN_CACHEABLE = "enableInsertForkWhenCacheable";
	public static final String PREFER_INSERT = "preferInsert";
	public static final String IGNORE_LOW_BACKOFF = "ignoreLowBackoff";
	
	/** Very urgent */
	public static final short PRIORITY_NOW=0;
	/** Short timeout, or urgent for other reasons - Accepted, RejectedLoop etc. */
	public static final short PRIORITY_HIGH=1; // 
	/** Unspecified e.g. test-only messages. */
	public static final short PRIORITY_UNSPECIFIED=2;
	/** Long timeout (e.g. DataFound), or moderately urgent. */
	public static final short PRIORITY_LOW=3; // long timeout, or moderately urgent
	/** Bulk data transfer for realtime requests. Not strictly inferior to 
	 * PRIORITY_BULK_DATA: we will not allow PRIORITY_REALTIME_DATA to starve 
	 * PRIORITY_BULK_DATA. */
	public static final short PRIORITY_REALTIME_DATA=4;
	/**
	 * Bulk data transfer, bottom of the heap, high level limiting must ensure there is time to send it by 
	 * not accepting an infeasible number of requests; starvation will cause bwlimitDelayTime to go high and 
	 * requests to be rejected. That's the ultimate limiter if even output bandwidth liability fails.
	 */
	public static final short PRIORITY_BULK_DATA=5;
	
	public static final short NUM_PRIORITIES = 6;
	
	// Assimilation

	// New data transmission messages
	public static final MessageType packetTransmit = new MessageType("packetTransmit", PRIORITY_BULK_DATA) {{
		addField(UID, Long.class);
		addField(PACKET_NO, Integer.class);
		addField(SENT, BitArray.class);
		addField(DATA, Buffer.class);
	}};
	
	public static final Message createPacketTransmit(long uid, int packetNo, BitArray sent, Buffer data, boolean realTime) {
		Message msg = new Message(packetTransmit);
		msg.set(UID, uid);
		msg.set(PACKET_NO, packetNo);
		msg.set(SENT, sent);
		msg.set(DATA, data);
		if(realTime)
			msg.boostPriority();
		return msg;
	}
	
	public static int packetTransmitSize(int size, int _packets) {
		return size + 8 /* uid */ + 4 /* packet# */ + 
			BitArray.serializedLength(_packets) + 4 /* Message header */;
	}
	
	public static int bulkPacketTransmitSize(int size) {
		return size + 8 /* uid */ + 4 /* packet# */ + 4 /* Message header */;
	}
	
	//This is of priority BULK_DATA to cut down on suprious resend requests, it will be queued after the packets it represents
	public static final MessageType allSent = new MessageType("allSent", PRIORITY_BULK_DATA) {{
		addField(UID, Long.class);
	}};
	
	public static final Message createAllSent(long uid) {
		Message msg = new Message(allSent);
		msg.set(UID, uid);
		return msg;
	}

	public static final MessageType allReceived = new MessageType("allReceived", PRIORITY_LOW) {{
		addField(UID, Long.class);
	}};
	public static final Message createAllReceived(long uid) {
		Message msg = new Message(allReceived);
		msg.set(UID, uid);
		return msg;
	}
	
	public static final MessageType sendAborted = new MessageType("sendAborted", PRIORITY_LOW) {{
		addField(UID, Long.class);
		addField(DESCRIPTION, String.class);
		addField(REASON, Integer.class);
	}};

	public static final Message createSendAborted(long uid, int reason, String description) {
		Message msg = new Message(sendAborted);
		msg.set(UID, uid);
		msg.set(REASON, reason);
		msg.set(DESCRIPTION, description);
		return msg;
	}

	public static final MessageType FNPBulkPacketSend = new MessageType("FNPBulkPacketSend", PRIORITY_BULK_DATA) {{
		addField(UID, Long.class);
		addField(PACKET_NO, Integer.class);
		addField(DATA, ShortBuffer.class);
	}};
	
	public static final Message createFNPBulkPacketSend(long uid, int packetNo, ShortBuffer data) {
		Message msg = new Message(FNPBulkPacketSend);
		msg.set(UID, uid);
		msg.set(PACKET_NO, packetNo);
		msg.set(DATA, data);
		return msg;
	}
	
	public static final Message createFNPBulkPacketSend(long uid, int packetNo, byte[] data) {
		return createFNPBulkPacketSend(uid, packetNo, new ShortBuffer(data));
	}
	
	public static final MessageType FNPBulkSendAborted = new MessageType("FNPBulkSendAborted", PRIORITY_LOW) {{
		addField(UID, Long.class);
	}};
	
	public static final Message createFNPBulkSendAborted(long uid) {
		Message msg = new Message(FNPBulkSendAborted);
		msg.set(UID, uid);
		return msg;
	}
	
	public static final MessageType FNPBulkReceiveAborted = new MessageType("FNPBulkReceiveAborted", PRIORITY_LOW) {{
		addField(UID, Long.class);
	}};
	
	public static final Message createFNPBulkReceiveAborted(long uid) {
		Message msg = new Message(FNPBulkReceiveAborted);
		msg.set(UID, uid);
		return msg;
	}
	
	public static final MessageType FNPBulkReceivedAll = new MessageType("FNPBulkReceivedAll", PRIORITY_LOW) {{
		addField(UID, Long.class);
	}};
	
	public static final Message createFNPBulkReceivedAll(long uid) {
		Message msg = new Message(FNPBulkReceivedAll);
		msg.set(UID, uid);
		return msg;
	}
	
	public static final MessageType testTransferSend = new MessageType("testTransferSend", PRIORITY_UNSPECIFIED) {{
		addField(UID, Long.class);
	}};
	
	public static final Message createTestTransferSend(long uid) {
		Message msg = new Message(testTransferSend);
		msg.set(UID, uid);
		return msg;
	}

	public static final MessageType testTransferSendAck = new MessageType("testTransferSendAck", PRIORITY_UNSPECIFIED) {{
		addField(UID, Long.class);
	}};
	
	public static final Message createTestTransferSendAck(long uid) {
		Message msg = new Message(testTransferSendAck);
		msg.set(UID, uid);
		return msg;
	}
	
	public static final MessageType testSendCHK = new MessageType("testSendCHK", PRIORITY_UNSPECIFIED) {{
		addField(UID, Long.class);
		addField(FREENET_URI, String.class);
		addField(CHK_HEADER, Buffer.class);
	}};
	
	public static final Message createTestSendCHK(long uid, String uri, Buffer header) {
		Message msg = new Message(testSendCHK);
		msg.set(UID, uid);
		msg.set(FREENET_URI, uri);
		msg.set(CHK_HEADER, header);
		return msg;
	}

	public static final MessageType testRequest = new MessageType("testRequest", PRIORITY_UNSPECIFIED) {{
		addField(UID, Long.class);
		addField(FREENET_ROUTING_KEY, Key.class);
		addField(HTL, Integer.class);
	}};
	
	public static final Message createTestRequest(Key Key, long id, int htl) {
		Message msg = new Message(testRequest);
		msg.set(UID, id);
		msg.set(FREENET_ROUTING_KEY, Key);
		msg.set(HTL, htl);
		return msg;
	}

	public static final MessageType testDataNotFound = new MessageType("testDataNotFound", PRIORITY_UNSPECIFIED) {{
		addField(UID, Long.class);
	}};
	
	public static final Message createTestDataNotFound(long uid) {
		Message msg = new Message(testDataNotFound);
		msg.set(UID, uid);
		return msg;
	}
	
	public static final MessageType testDataReply = new MessageType("testDataReply", PRIORITY_UNSPECIFIED) {{
		addField(UID, Long.class);
		addField(TEST_CHK_HEADERS, Buffer.class);
	}};
	
	public static final Message createTestDataReply(long uid, byte[] headers) {
		Message msg = new Message(testDataReply);
		msg.set(UID, uid);
		msg.set(TEST_CHK_HEADERS, new Buffer(headers));
		return msg;
	}
	
	public static final MessageType testSendCHKAck = new MessageType("testSendCHKAck", PRIORITY_UNSPECIFIED) {{
		addField(UID, Long.class);
		addField(FREENET_URI, String.class);
	}};
	public static final Message createTestSendCHKAck(long uid, String key) {
		Message msg = new Message(testSendCHKAck);
		msg.set(UID, uid);
		msg.set(FREENET_URI, key);
		return msg;
	}
	
	public static final MessageType testDataReplyAck = new MessageType("testDataReplyAck", PRIORITY_UNSPECIFIED) {{
		addField(UID, Long.class);
	}};
	
	public static final Message createTestDataReplyAck(long id) {
		Message msg = new Message(testDataReplyAck);
		msg.set(UID, id);
		return msg;
	}

	public static final MessageType testDataNotFoundAck = new MessageType("testDataNotFoundAck", PRIORITY_UNSPECIFIED) {{
		addField(UID, Long.class);
	}};
	public static final Message createTestDataNotFoundAck(long id) {
		Message msg = new Message(testDataNotFoundAck);
		msg.set(UID, id);
		return msg;
	}
	
	// Internal only messages
	
	public static final MessageType testReceiveCompleted = new MessageType("testReceiveCompleted", PRIORITY_UNSPECIFIED, true) {{
		addField(UID, Long.class);
		addField(SUCCESS, Boolean.class);
		addField(REASON, String.class);
	}};
	
	public static final Message createTestReceiveCompleted(long id, boolean success, String reason) {
		Message msg = new Message(testReceiveCompleted);
		msg.set(UID, id);
		msg.set(SUCCESS, success);
		msg.set(REASON, reason);
		return msg;
	}
	
	public static final MessageType testSendCompleted = new MessageType("testSendCompleted", PRIORITY_UNSPECIFIED, true) {{
		addField(UID, Long.class);
		addField(SUCCESS, Boolean.class);
		addField(REASON, String.class);
	}};

	public static final Message createTestSendCompleted(long id, boolean success, String reason) {
		Message msg = new Message(testSendCompleted);
		msg.set(UID, id);
		msg.set(SUCCESS, success);
		msg.set(REASON, reason);
		return msg;
	}

	// Node-To-Node Message (generic)
	public static final MessageType nodeToNodeMessage = new MessageType("nodeToNodeMessage", PRIORITY_LOW, false) {{
		addField(NODE_TO_NODE_MESSAGE_TYPE, Integer.class);
		addField(NODE_TO_NODE_MESSAGE_DATA, ShortBuffer.class);
	}};

	public static final Message createNodeToNodeMessage(int type, byte[] data) {
		Message msg = new Message(nodeToNodeMessage);
		msg.set(NODE_TO_NODE_MESSAGE_TYPE, type);
		msg.set(NODE_TO_NODE_MESSAGE_DATA, new ShortBuffer(data));
		return msg;
	}

	// FNP messages
	public static final MessageType FNPCHKDataRequest = new MessageType("FNPCHKDataRequest", PRIORITY_HIGH) {{
		addField(UID, Long.class);
		addField(HTL, Short.class);
		addField(NEAREST_LOCATION, Double.class);
		addField(FREENET_ROUTING_KEY, NodeCHK.class);
	}};
	
	public static final Message createFNPCHKDataRequest(long id, short htl, NodeCHK key) {
		Message msg = new Message(FNPCHKDataRequest);
		msg.set(UID, id);
		msg.set(HTL, htl);
		msg.set(FREENET_ROUTING_KEY, key);
		msg.set(NEAREST_LOCATION, 0.0);
		return msg;
	}
	
	public static final MessageType FNPSSKDataRequest = new MessageType("FNPSSKDataRequest", PRIORITY_HIGH) {{
		addField(UID, Long.class);
		addField(HTL, Short.class);
		addField(NEAREST_LOCATION, Double.class);
		addField(FREENET_ROUTING_KEY, NodeSSK.class);
		addField(NEED_PUB_KEY, Boolean.class);
	}};
	
	public static final Message createFNPSSKDataRequest(long id, short htl, NodeSSK key, boolean needPubKey) {
		Message msg = new Message(FNPSSKDataRequest);
		msg.set(UID, id);
		msg.set(HTL, htl);
		msg.set(FREENET_ROUTING_KEY, key);
		msg.set(NEAREST_LOCATION, 0.0);
		msg.set(NEED_PUB_KEY, needPubKey);
		return msg;
	}
	
	// Hit our tail, try a different node.
	public static final MessageType FNPRejectedLoop = new MessageType("FNPRejectLoop", PRIORITY_HIGH) {{
		addField(UID, Long.class);
	}};
	
	public static final Message createFNPRejectedLoop(long id) {
		Message msg = new Message(FNPRejectedLoop);
		msg.set(UID, id);
		return msg;
	}
	
	// Too many requests for present capacity. Fail, propagate back
	// to source, and reduce send rate.
	public static final MessageType FNPRejectedOverload = new MessageType("FNPRejectOverload", PRIORITY_HIGH) {{
		addField(UID, Long.class);
		addField(IS_LOCAL, Boolean.class);
	}};
	
	public static final Message createFNPRejectedOverload(long id, boolean isLocal) {
		Message msg = new Message(FNPRejectedOverload);
		msg.set(UID, id);
		msg.set(IS_LOCAL, isLocal);
		return msg;
	}
	
	public static final MessageType FNPAccepted = new MessageType("FNPAccepted", PRIORITY_HIGH) {{
		addField(UID, Long.class);
	}};
	
	public static final Message createFNPAccepted(long id) {
		Message msg = new Message(FNPAccepted);
		msg.set(UID, id);
		return msg;
	}
	
	public static final MessageType FNPDataNotFound = new MessageType("FNPDataNotFound", PRIORITY_LOW) {{
		addField(UID, Long.class);
	}};
	
	public static final Message createFNPDataNotFound(long id) {
		Message msg = new Message(FNPDataNotFound);
		msg.set(UID, id);
		return msg;
	}
	
	public static final MessageType FNPRecentlyFailed = new MessageType("FNPRecentlyFailed", PRIORITY_HIGH) {{
		addField(UID, Long.class);
		addField(TIME_LEFT, Integer.class);
	}};
	
	public static final Message createFNPRecentlyFailed(long id, int timeLeft) {
		Message msg = new Message(FNPRecentlyFailed);
		msg.set(UID, id);
		msg.set(TIME_LEFT, timeLeft);
		return msg;
	}
	
	public static final MessageType FNPCHKDataFound = new MessageType("FNPCHKDataFound", PRIORITY_LOW) {{
		addField(UID, Long.class);
		addField(BLOCK_HEADERS, ShortBuffer.class);
	}};
	
	public static final Message createFNPCHKDataFound(long id, byte[] buf) {
		Message msg = new Message(FNPCHKDataFound);
		msg.set(UID, id);
		msg.set(BLOCK_HEADERS, new ShortBuffer(buf));
		return msg;
	}
	
	public static final MessageType FNPRouteNotFound = new MessageType("FNPRouteNotFound", PRIORITY_LOW) {{
		addField(UID, Long.class);
		addField(HTL, Short.class);
	}};
	
	public static final Message createFNPRouteNotFound(long id, short htl) {
		Message msg = new Message(FNPRouteNotFound);
		msg.set(UID, id);
		msg.set(HTL, htl);
		return msg;
	}
	
	public static final MessageType FNPInsertRequest = new MessageType("FNPInsertRequest", PRIORITY_HIGH) {{
		addField(UID, Long.class);
		addField(HTL, Short.class);
		addField(NEAREST_LOCATION, Double.class);
		addField(FREENET_ROUTING_KEY, Key.class);
	}};
	
	public static final Message createFNPInsertRequest(long id, short htl, Key key) {
		Message msg = new Message(FNPInsertRequest);
		msg.set(UID, id);
		msg.set(HTL, htl);
		msg.set(FREENET_ROUTING_KEY, key);
		msg.set(NEAREST_LOCATION, 0.0);
		return msg;
	}
	
	public static final MessageType FNPInsertReply = new MessageType("FNPInsertReply", PRIORITY_LOW) {{
		addField(UID, Long.class);
	}};
	
	public static final Message createFNPInsertReply(long id) {
		Message msg = new Message(FNPInsertReply);
		msg.set(UID, id);
		return msg;
	}
	
	public static final MessageType FNPDataInsert = new MessageType("FNPDataInsert", PRIORITY_HIGH) {{
		addField(UID, Long.class);
		addField(BLOCK_HEADERS, ShortBuffer.class);
	}};
	
	public static final Message createFNPDataInsert(long uid, byte[] headers) {
		Message msg = new Message(FNPDataInsert);
		msg.set(UID, uid);
		msg.set(BLOCK_HEADERS, new ShortBuffer(headers));
		return msg;
	}

	public static final MessageType FNPInsertTransfersCompleted = new MessageType("FNPInsertTransfersCompleted", PRIORITY_LOW) {{
		addField(UID, Long.class);
		addField(ANY_TIMED_OUT, Boolean.class);
	}};

	public static final Message createFNPInsertTransfersCompleted(long uid, boolean anyTimedOut) {
		Message msg = new Message(FNPInsertTransfersCompleted);
		msg.set(UID, uid);
		msg.set(ANY_TIMED_OUT, anyTimedOut);
		return msg;
	}
	
	public static final MessageType FNPRejectedTimeout = new MessageType("FNPTooSlow", PRIORITY_LOW) {{
		addField(UID, Long.class);
	}};
	
	public static final Message createFNPRejectedTimeout(long uid) {
		Message msg = new Message(FNPRejectedTimeout);
		msg.set(UID, uid);
		return msg;
	}
	
	public static final MessageType FNPDataInsertRejected = new MessageType("FNPDataInsertRejected", PRIORITY_LOW) {{
		addField(UID, Long.class);
		addField(DATA_INSERT_REJECTED_REASON, Short.class);
	}};
	
	public static final Message createFNPDataInsertRejected(long uid, short reason) {
		Message msg = new Message(FNPDataInsertRejected);
		msg.set(UID, uid);
		msg.set(DATA_INSERT_REJECTED_REASON, reason);
		return msg;
	}

	public static final short DATA_INSERT_REJECTED_VERIFY_FAILED = 1;
	public static final short DATA_INSERT_REJECTED_RECEIVE_FAILED = 2;
	public static final short DATA_INSERT_REJECTED_SSK_ERROR = 3;
	
	public static final String getDataInsertRejectedReason(short reason) {
		if(reason == DATA_INSERT_REJECTED_VERIFY_FAILED)
			return "Verify failed";
		else if(reason == DATA_INSERT_REJECTED_RECEIVE_FAILED)
			return "Receive failed";
		else if(reason == DATA_INSERT_REJECTED_SSK_ERROR)
			return "SSK error";
		return "Unknown reason code: "+reason;
	}

	public static final MessageType FNPSSKInsertRequest = new MessageType("FNPSSKInsertRequest", PRIORITY_BULK_DATA) {{
		addField(UID, Long.class);
		addField(HTL, Short.class);
		addField(FREENET_ROUTING_KEY, NodeSSK.class);
		addField(NEAREST_LOCATION, Double.class);
		addField(BLOCK_HEADERS, ShortBuffer.class);
		addField(PUBKEY_HASH, ShortBuffer.class);
		addField(DATA, ShortBuffer.class);
	}};
	
	public static Message createFNPSSKInsertRequest(long uid, short htl, NodeSSK myKey, byte[] headers, byte[] data, byte[] pubKeyHash) {
		Message msg = new Message(FNPSSKInsertRequest);
		msg.set(UID, uid);
		msg.set(HTL, htl);
		msg.set(FREENET_ROUTING_KEY, myKey);
		msg.set(NEAREST_LOCATION, 0.0);
		msg.set(BLOCK_HEADERS, new ShortBuffer(headers));
		msg.set(PUBKEY_HASH, new ShortBuffer(pubKeyHash));
		msg.set(DATA, new ShortBuffer(data));
		return msg;
	}
	
	public static final MessageType FNPSSKInsertRequestNew = new MessageType("FNPSSKInsertRequestNew", PRIORITY_HIGH) {{
		addField(UID, Long.class);
		addField(HTL, Short.class);
		addField(FREENET_ROUTING_KEY, NodeSSK.class);
	}};
	
	public static Message createFNPSSKInsertRequestNew(long uid, short htl, NodeSSK myKey) {
		Message msg = new Message(FNPSSKInsertRequestNew);
		msg.set(UID, uid);
		msg.set(HTL, htl);
		msg.set(FREENET_ROUTING_KEY, myKey);
		return msg;
	}

	public static final MessageType FNPSSKInsertRequestHeaders = new MessageType("FNPSSKInsertRequestHeaders", PRIORITY_HIGH) {{
		addField(UID, Long.class);
		addField(BLOCK_HEADERS, ShortBuffer.class);
	}};
	
	public static final Message createFNPSSKInsertRequestHeaders(long uid, byte[] headers) {
		Message msg = new Message(FNPSSKInsertRequestHeaders);
		msg.set(UID, uid);
		msg.set(BLOCK_HEADERS, new ShortBuffer(headers));
		return msg;
	}
	
	public static final MessageType FNPSSKInsertRequestData = new MessageType("FNPSSKInsertRequestData", PRIORITY_BULK_DATA) {{
		addField(UID, Long.class);
		addField(DATA, ShortBuffer.class);
	}};
	
	public static final Message createFNPSSKInsertRequestData(long uid, byte[] data) {
		Message msg = new Message(FNPSSKInsertRequestData);
		msg.set(UID, uid);
		msg.set(DATA, new ShortBuffer(data));
		return msg;
	}
	
	public static final MessageType FNPSSKDataFoundHeaders = new MessageType("FNPSSKDataFoundHeaders", PRIORITY_LOW) {{
		addField(UID, Long.class);
		addField(BLOCK_HEADERS, ShortBuffer.class);
	}};
	
	public static Message createFNPSSKDataFoundHeaders(long uid, byte[] headers) {
		Message msg = new Message(FNPSSKDataFoundHeaders);
		msg.set(UID, uid);
		msg.set(BLOCK_HEADERS, new ShortBuffer(headers));
		return msg;
	}
	
	public static final MessageType FNPSSKDataFoundData = new MessageType("FNPSSKDataFoundData", PRIORITY_BULK_DATA) {{
		addField(UID, Long.class);
		addField(DATA, ShortBuffer.class);
	}};
	
	public static Message createFNPSSKDataFoundData(long uid, byte[] data) {
		Message msg = new Message(FNPSSKDataFoundData);
		msg.set(UID, uid);
		msg.set(DATA, new ShortBuffer(data));
		return msg;
	}
	
	public final static MessageType FNPSSKAccepted = new MessageType("FNPSSKAccepted", PRIORITY_HIGH) {
		{
		addField(UID, Long.class);
		addField(NEED_PUB_KEY, Boolean.class);
	}};
	
	public static final Message createFNPSSKAccepted(long uid, boolean needPubKey) {
		Message msg = new Message(FNPSSKAccepted);
		msg.set(UID, uid);
		msg.set(NEED_PUB_KEY, needPubKey);
		return msg;
	}
	
	public final static MessageType FNPSSKPubKey = new MessageType("FNPSSKPubKey", PRIORITY_BULK_DATA) {
		{
		addField(UID, Long.class);
		addField(PUBKEY_AS_BYTES, ShortBuffer.class);
	}};
	
	public static Message createFNPSSKPubKey(long uid, DSAPublicKey pubkey) {
		Message msg = new Message(FNPSSKPubKey);
		msg.set(UID, uid);
		msg.set(PUBKEY_AS_BYTES, new ShortBuffer(pubkey.asPaddedBytes()));
		return msg;
	}
	
	public final static MessageType FNPSSKPubKeyAccepted = new MessageType("FNPSSKPubKeyAccepted", PRIORITY_HIGH) {
		{
		addField(UID, Long.class);
	}};
	
	public static Message createFNPSSKPubKeyAccepted(long uid) {
		Message msg = new Message(FNPSSKPubKeyAccepted);
		msg.set(UID, uid);
		return msg;
	}
	
	// Opennet completions (not sent to darknet nodes)
	
	/** Sent when a request to an opennet node is completed, but the data source does not want to 
	 * path fold. Sent even on pure darknet. A better name might be FNPRequestCompletedAck. */
	public final static MessageType FNPOpennetCompletedAck = new MessageType("FNPOpennetCompletedAck", PRIORITY_LOW) {
		{
		addField(UID, Long.class);
	}};
	
	public static Message createFNPOpennetCompletedAck(long uid) {
		Message msg = new Message(FNPOpennetCompletedAck);
		msg.set(UID, uid);
		return msg;
	}
	
	/** Sent when a request completes and the data source wants to path fold. Starts a bulk data 
	 * transfer including the (padded) noderef. 
	 */
	public final static MessageType FNPOpennetConnectDestinationNew = new MessageType("FNPConnectDestinationNew",
	        PRIORITY_UNSPECIFIED) {
		{
		addField(UID, Long.class); // UID of original message chain
		addField(TRANSFER_UID, Long.class); // UID of data transfer
		addField(NODEREF_LENGTH, Integer.class); // Size of noderef
		addField(PADDED_LENGTH, Integer.class); // Size of actual transfer i.e. padded length
	}};
	
	public static Message createFNPOpennetConnectDestinationNew(long uid, long transferUID, int noderefLength, int paddedLength) {
		Message msg = new Message(FNPOpennetConnectDestinationNew);
		msg.set(UID, uid);
		msg.set(TRANSFER_UID, transferUID);
		msg.set(NODEREF_LENGTH, noderefLength);
		msg.set(PADDED_LENGTH, paddedLength);
		return msg;
	}
	
	/** Path folding response. Sent when the requestor wants to path fold and has received a noderef 
	 * from the data source. Starts a bulk data transfer including the (padded) noderef. 
	 */
	public final static MessageType FNPOpennetConnectReplyNew = new MessageType("FNPConnectReplyNew",
	        PRIORITY_UNSPECIFIED) {
		{
		addField(UID, Long.class); // UID of original message chain
		addField(TRANSFER_UID, Long.class); // UID of data transfer
		addField(NODEREF_LENGTH, Integer.class); // Size of noderef
		addField(PADDED_LENGTH, Integer.class); // Size of actual transfer i.e. padded length
	}};
	
	public static Message createFNPOpennetConnectReplyNew(long uid, long transferUID, int noderefLength, int paddedLength) {
		Message msg = new Message(FNPOpennetConnectReplyNew);
		msg.set(UID, uid);
		msg.set(TRANSFER_UID, transferUID);
		msg.set(NODEREF_LENGTH, noderefLength);
		msg.set(PADDED_LENGTH, paddedLength);
		return msg;
	}
	
	// Opennet announcement

	/**
	 * Announcement request. Noderef is attached, will be transferred before anything else is done.
	 */
	public final static MessageType FNPOpennetAnnounceRequest = new MessageType("FNPOpennetAnnounceRequest",
	        PRIORITY_HIGH) {
		{
		addField(UID, Long.class);
		addField(TRANSFER_UID, Long.class);
		addField(NODEREF_LENGTH, Integer.class);
		addField(PADDED_LENGTH, Integer.class);
		addField(HTL, Short.class);
		addField(NEAREST_LOCATION, Double.class);
		addField(TARGET_LOCATION, Double.class);
	}};

	public static Message createFNPOpennetAnnounceRequest(long uid, long transferUID, int noderefLength, int paddedLength, double target, short htl) {
		Message msg = new Message(FNPOpennetAnnounceRequest);
		msg.set(UID, uid);
		msg.set(TRANSFER_UID, transferUID);
		msg.set(NODEREF_LENGTH, noderefLength);
		msg.set(PADDED_LENGTH, paddedLength);
		msg.set(HTL, htl);
		msg.set(NEAREST_LOCATION, 0.0);
		msg.set(TARGET_LOCATION, target);
		return msg;
	}
	
	/**
	 * Announcement reply. Noderef is attached, will be transferred, both nodes add the other. A single
	 * request will result in many reply's. When the announcement is done, we return a DataNotFound; if
	 * we run into a dead-end, we return a RejectedLoop; if we can't accept it, RejectedOverload.
	 */
	public final static MessageType FNPOpennetAnnounceReply = new MessageType("FNPOpennetAnnounceReply", PRIORITY_LOW) {
		{
		addField(UID, Long.class);
		addField(TRANSFER_UID, Long.class);
		addField(NODEREF_LENGTH, Integer.class);
		addField(PADDED_LENGTH, Integer.class);
	}};
	
	public static Message createFNPOpennetAnnounceReply(long uid, long transferUID, int noderefLength, int paddedLength) {
		Message msg = new Message(FNPOpennetAnnounceReply);
		msg.set(UID, uid);
		msg.set(TRANSFER_UID, transferUID);
		msg.set(NODEREF_LENGTH, noderefLength);
		msg.set(PADDED_LENGTH, paddedLength);
		return msg;
	}
	
	public final static MessageType FNPOpennetAnnounceCompleted = new MessageType("FNPOpennetAnnounceCompleted",
	        PRIORITY_LOW) {
		{
		addField(UID, Long.class);
	}};
	
	public static Message createFNPOpennetAnnounceCompleted(long uid) {
		Message msg = new Message(FNPOpennetAnnounceCompleted);
		msg.set(UID, uid);
		return msg;
	}
	
	public final static MessageType FNPOpennetDisabled = new MessageType("FNPOpennetDisabled", PRIORITY_HIGH) {
		{
		addField(UID, Long.class);
	}};
	
	public static Message createFNPOpennetDisabled(long uid) {
		Message msg = new Message(FNPOpennetDisabled);
		msg.set(UID, uid);
		return msg;
	}
	
	public final static MessageType FNPOpennetNoderefRejected = new MessageType("FNPOpennetNoderefRejected",
	        PRIORITY_HIGH) {
		{
		addField(UID, Long.class);
		addField(REJECT_CODE, Integer.class);
	}};
	
	public static Message createFNPOpennetNoderefRejected(long uid, int rejectCode) {
		Message msg = new Message(FNPOpennetNoderefRejected);
		msg.set(UID, uid);
		msg.set(REJECT_CODE, rejectCode);
		return msg;
	}
	
	public static String getOpennetRejectedCode(int x) {
		switch(x) {
		case NODEREF_REJECTED_TOO_BIG:
			return "Too big";
		case NODEREF_REJECTED_REAL_BIGGER_THAN_PADDED:
			return "Real length bigger than padded length";
		case NODEREF_REJECTED_TRANSFER_FAILED:
			return "Transfer failed";
		case NODEREF_REJECTED_INVALID:
			return "Invalid noderef";
		default:
			return "Unknown rejection code "+x;
		}
	}
	
	public static final int NODEREF_REJECTED_TOO_BIG = 1;
	public static final int NODEREF_REJECTED_REAL_BIGGER_THAN_PADDED = 2;
	public static final int NODEREF_REJECTED_TRANSFER_FAILED = 3;
	public static final int NODEREF_REJECTED_INVALID = 4;
	
	// FIXME get rid???
	
	public final static MessageType FNPOpennetAnnounceNodeNotWanted = new MessageType(
	        "FNPOpennetAnnounceNodeNotWanted", PRIORITY_LOW) {
		{
		addField(UID, Long.class);
	}};
	
	public static Message createFNPOpennetAnnounceNodeNotWanted(long uid) {
		Message msg = new Message(FNPOpennetAnnounceNodeNotWanted);
		msg.set(UID, uid);
		return msg;
	}
	
	// Key offers (ULPRs)
	
	public final static MessageType FNPOfferKey = new MessageType("FNPOfferKey", PRIORITY_LOW) {
		{
		addField(KEY, Key.class);
		addField(OFFER_AUTHENTICATOR, ShortBuffer.class);
	}};
	
	public static Message createFNPOfferKey(Key key, byte[] authenticator) {
		Message msg = new Message(FNPOfferKey);
		msg.set(KEY, key);
		msg.set(OFFER_AUTHENTICATOR, new ShortBuffer(authenticator));
		return msg;
	}
	
	// Short timeout so PRIORITY_HIGH
	public final static MessageType FNPGetOfferedKey = new MessageType("FNPGetOfferedKey", PRIORITY_HIGH) {
		{
		addField(KEY, Key.class);
		addField(OFFER_AUTHENTICATOR, ShortBuffer.class);
		addField(NEED_PUB_KEY, Boolean.class);
		addField(UID, Long.class);
	}};
	
	public static Message createFNPGetOfferedKey(Key key, byte[] authenticator, boolean needPubkey, long uid) {
		Message msg = new Message(FNPGetOfferedKey);
		msg.set(KEY, key);
		msg.set(OFFER_AUTHENTICATOR, new ShortBuffer(authenticator));
		msg.set(NEED_PUB_KEY, needPubkey);
		msg.set(UID, uid);
		return msg;
	}
	
	// Permanently rejected. RejectedOverload means temporarily rejected.
	public final static MessageType FNPGetOfferedKeyInvalid = new MessageType("FNPGetOfferedKeyInvalid", PRIORITY_HIGH) {
		{ // short timeout
		addField(UID, Long.class);
		addField(REASON, Short.class);
	}};
	
	public static Message createFNPGetOfferedKeyInvalid(long uid, short reason) {
		Message msg = new Message(FNPGetOfferedKeyInvalid);
		msg.set(UID, uid);
		msg.set(REASON, reason);
		return msg;
	}
	
	public final static short GET_OFFERED_KEY_REJECTED_BAD_AUTHENTICATOR = 1;
	public final static short GET_OFFERED_KEY_REJECTED_NO_KEY = 2;
	
	public static final MessageType FNPPing = new MessageType("FNPPing", PRIORITY_HIGH) {{
		addField(PING_SEQNO, Integer.class);
	}};
	
	public static final Message createFNPPing(int seqNo) {
		Message msg = new Message(FNPPing);
		msg.set(PING_SEQNO, seqNo);
		return msg;
	}
	
	public static final MessageType FNPPong = new MessageType("FNPPong", PRIORITY_HIGH) {{
		addField(PING_SEQNO, Integer.class);
	}};
	
	public static final Message createFNPPong(int seqNo) {
		Message msg = new Message(FNPPong);
		msg.set(PING_SEQNO, seqNo);
		return msg;
	}
	
	public static final MessageType FNPRHProbeReply = new MessageType("FNPRHProbeReply", PRIORITY_HIGH) {{
		addField(UID, Long.class);
		addField(NEAREST_LOCATION, Double.class);
		addField(BEST_LOCATION, Double.class);
		addField(COUNTER, Short.class);
		addField(UNIQUE_COUNTER, Short.class);
		addField(LINEAR_COUNTER, Short.class);
	}};
	
	public static final Message createFNPRHProbeReply(long uid, double nearest, double best, short counter, short uniqueCounter, short linearCounter) {
		Message msg = new Message(FNPRHProbeReply);
		msg.set(UID, uid);
		msg.set(NEAREST_LOCATION, nearest);
		msg.set(BEST_LOCATION, best);
		msg.set(COUNTER, counter);
		msg.set(UNIQUE_COUNTER, uniqueCounter);
		msg.set(LINEAR_COUNTER, linearCounter);
		return msg;
	}
	
	public static final MessageType FNPRHProbeRequest = new MessageType("FNPRHProbeRequest", PRIORITY_HIGH) {{
		addField(UID, Long.class);
		addField(TARGET_LOCATION, Double.class);
		addField(NEAREST_LOCATION, Double.class);
		addField(BEST_LOCATION, Double.class);
		addField(HTL, Short.class);
	}};
	
	public static final Message createFNPRHProbeRequest(long uid, double target, double nearest, 
			double best, short htl) {
		Message msg = new Message(FNPRHProbeRequest);
		msg.set(UID, uid);
		msg.set(TARGET_LOCATION, target);
		msg.set(NEAREST_LOCATION, nearest);
		msg.set(BEST_LOCATION, best);
		msg.set(HTL, htl);
		return msg;
	}

	public static final MessageType FNPRHReturnSubMessage = new MessageType("FNPRHReturnSubMessage", PRIORITY_HIGH) {{
		addField(NEAREST_LOCATION, Double.class);
		addField(BEST_LOCATION, Double.class);
		addField(COUNTER, Short.class);
		addField(UNIQUE_COUNTER, Short.class);
		addField(LINEAR_COUNTER, Short.class);
		addField(REASON, String.class);
	}};
	
	public static final Message createFNPRHReturnSubMessage(double nearest, double best, short counter, short uniqueCounter, short linearCounter, String reason) {
		Message msg = new Message(FNPRHReturnSubMessage);
		msg.set(NEAREST_LOCATION, nearest);
		msg.set(BEST_LOCATION, best);
		msg.set(COUNTER, counter);
		msg.set(UNIQUE_COUNTER, uniqueCounter);
		msg.set(LINEAR_COUNTER, linearCounter);
		msg.set(REASON, reason);
		return msg;
	}
	
	public static final MessageType FNPRHProbeTrace = new MessageType("FNPRHProbeTrace", PRIORITY_LOW) {{
		addField(UID, Long.class);
		addField(NEAREST_LOCATION, Double.class);
		addField(BEST_LOCATION, Double.class);
		addField(HTL, Short.class);
		addField(COUNTER, Short.class);
		addField(UNIQUE_COUNTER, Short.class);
		addField(LOCATION, Double.class);
		addField(MY_UID, Long.class);
		addField(PEER_LOCATIONS, ShortBuffer.class);
		addField(PEER_UIDS, ShortBuffer.class);
		addField(FORK_COUNT, Short.class);
		addField(LINEAR_COUNTER, Short.class);
		addField(REASON, String.class);
		addField(PREV_UID, Long.class);
	}};

	public static Message createFNPRHProbeTrace(long uid, double nearest, double best, short htl, short counter, short uniqueCounter, double myLoc, long swapIdentifier, double[] peerLocs, long[] peerUIDs, short forkCount, short linearCounter, String reason, long prevUID) {
		return createFNPRHProbeTrace(uid, nearest, best, htl, counter, uniqueCounter, myLoc, swapIdentifier, new ShortBuffer(Fields.doublesToBytes(peerLocs)), new ShortBuffer(Fields.longsToBytes(peerUIDs)), forkCount, linearCounter, reason, prevUID);
	}
	
	public static Message createFNPRHProbeTrace(long uid, double nearest, double best, short htl, short counter, short uniqueCounter, double myLoc, long swapIdentifier, ShortBuffer peerLocs, ShortBuffer peerUIDs, short forkCount, short linearCounter, String reason, long prevUID) {
		Message msg = new Message(FNPRHProbeTrace);
		msg.set(UID, uid);
		msg.set(NEAREST_LOCATION, nearest);
		msg.set(BEST_LOCATION, best);
		msg.set(HTL, htl);
		msg.set(COUNTER, counter);
		msg.set(UNIQUE_COUNTER, uniqueCounter);
		msg.set(LOCATION, myLoc);
		msg.set(MY_UID, swapIdentifier);
		msg.set(PEER_LOCATIONS, peerLocs);
		msg.set(PEER_UIDS, peerUIDs);
		msg.set(FORK_COUNT, forkCount);
		msg.set(LINEAR_COUNTER, linearCounter);
		msg.set(REASON, reason);
		msg.set(PREV_UID, prevUID);
		return msg;
	}

	public static final MessageType FNPProbeRequest = new MessageType("FNPProbeRequest", PRIORITY_HIGH) {{
		addField(UID, Long.class);
		addField(TARGET_LOCATION, Double.class);
		addField(NEAREST_LOCATION, Double.class);
		addField(BEST_LOCATION, Double.class);
		addField(HTL, Short.class);
		addField(COUNTER, Short.class);
		addField(LINEAR_COUNTER, Short.class);
	}};
	
	public static final MessageType FNPProbeTrace = new MessageType("FNPProbeTrace", PRIORITY_LOW) {{
		addField(UID, Long.class);
		addField(TARGET_LOCATION, Double.class);
		addField(NEAREST_LOCATION, Double.class);
		addField(BEST_LOCATION, Double.class);
		addField(HTL, Short.class);
		addField(COUNTER, Short.class);
		addField(LOCATION, Double.class);
		addField(MY_UID, Long.class);
		addField(PEER_LOCATIONS, ShortBuffer.class);
		addField(PEER_UIDS, ShortBuffer.class);
		addField(FORK_COUNT, Short.class);
		addField(LINEAR_COUNTER, Short.class);
		addField(REASON, String.class);
		addField(PREV_UID, Long.class);
	}};
	
	public static final MessageType FNPProbeReply = new MessageType("FNPProbeReply", PRIORITY_LOW) {{
		addField(UID, Long.class);
		addField(TARGET_LOCATION, Double.class);
		addField(NEAREST_LOCATION, Double.class);
		addField(BEST_LOCATION, Double.class);
		addField(COUNTER, Short.class);
		addField(LINEAR_COUNTER, Short.class);
	}};
	
	public static final MessageType FNPProbeRejected = new MessageType("FNPProbeRejected", PRIORITY_HIGH) {{
		addField(UID, Long.class);
		addField(TARGET_LOCATION, Double.class);
		addField(NEAREST_LOCATION, Double.class);
		addField(BEST_LOCATION, Double.class);
		addField(HTL, Short.class);
		addField(COUNTER, Short.class);
		addField(REASON, Short.class);
		addField(LINEAR_COUNTER, Short.class);
	}};
	
	static public final short PROBE_REJECTED_LOOP = 1;
	static public final short PROBE_REJECTED_RNF = 2;
	static public final short PROBE_REJECTED_OVERLOAD = 3;
	
	public static final MessageType FNPSwapRequest = new MessageType("FNPSwapRequest", PRIORITY_HIGH) {{
		addField(UID, Long.class);
		addField(HASH, ShortBuffer.class);
		addField(HTL, Integer.class);
	}};
	
	public static final Message createFNPSwapRequest(long uid, byte[] buf, int htl) {
		Message msg = new Message(FNPSwapRequest);
		msg.set(UID, uid);
		msg.set(HASH, new ShortBuffer(buf));
		msg.set(HTL, htl);
		return msg;
	}
	
	public static final MessageType FNPSwapRejected = new MessageType("FNPSwapRejected", PRIORITY_HIGH) {{
		addField(UID, Long.class);
	}};
	
	public static final Message createFNPSwapRejected(long uid) {
		Message msg = new Message(FNPSwapRejected);
		msg.set(UID, uid);
		return msg;
	}
	
	public static final MessageType FNPSwapReply = new MessageType("FNPSwapReply", PRIORITY_HIGH) {{
		addField(UID, Long.class);
		addField(HASH, ShortBuffer.class);
	}};
	
	public static final Message createFNPSwapReply(long uid, byte[] buf) {
		Message msg = new Message(FNPSwapReply);
		msg.set(UID, uid);
		msg.set(HASH, new ShortBuffer(buf));
		return msg;
	}

	public static final MessageType FNPSwapCommit = new MessageType("FNPSwapCommit", PRIORITY_HIGH) {{
		addField(UID, Long.class);
		addField(DATA, ShortBuffer.class);
	}};
	
	public static final Message createFNPSwapCommit(long uid, byte[] buf) {
		Message msg = new Message(FNPSwapCommit);
		msg.set(UID, uid);
		msg.set(DATA, new ShortBuffer(buf));
		return msg;
	}
	
	public static final MessageType FNPSwapComplete = new MessageType("FNPSwapComplete", PRIORITY_HIGH) {{
		addField(UID, Long.class);
		addField(DATA, ShortBuffer.class);
	}};
	
	public static final Message createFNPSwapComplete(long uid, byte[] buf) {
		Message msg = new Message(FNPSwapComplete);
		msg.set(UID, uid);
		msg.set(DATA, new ShortBuffer(buf));
		return msg;
	}
		
	public static final MessageType FNPLocChangeNotificationNew = new MessageType("FNPLocationChangeNotification2", PRIORITY_LOW) {{
		addField(LOCATION, Double.class);
		addField(PEER_LOCATIONS, ShortBuffer.class);
	}};
	
	public static final Message createFNPLocChangeNotificationNew(double myLocation, double[] locations) {
		Message msg = new Message(FNPLocChangeNotificationNew);
		ShortBuffer dst = new ShortBuffer(Fields.doublesToBytes(locations));
		msg.set(LOCATION, myLocation);
		msg.set(PEER_LOCATIONS, dst);
		
		return msg;
	}

	public static final MessageType FNPRoutedPing = new MessageType("FNPRoutedPing", PRIORITY_LOW) {{
		addRoutedToNodeMessageFields();
		addField(COUNTER, Integer.class);
		
	}};
	
	public static final Message createFNPRoutedPing(long uid, double targetLocation, short htl, int counter, byte[] nodeIdentity) {
		Message msg = new Message(FNPRoutedPing);
		msg.setRoutedToNodeFields(uid, targetLocation, htl, nodeIdentity);
		msg.set(COUNTER, counter);
		return msg;
	}
	
	public static final MessageType FNPRoutedPong = new MessageType("FNPRoutedPong", PRIORITY_LOW) {{
		addField(UID, Long.class);
		addField(COUNTER, Integer.class);
	}};

	public static final Message createFNPRoutedPong(long uid, int counter) {
		Message msg = new Message(FNPRoutedPong);
		msg.set(UID, uid);
		msg.set(COUNTER, counter);
		return msg;
	}
	
	public static final MessageType FNPSecretPing = new MessageType("FNPSecretPing", PRIORITY_LOW) {{
		addRoutedToNodeMessageFields();
		addField(COUNTER, Integer.class);
		addField(DAWN_HTL, Short.class);
	}};
	
	
	public static final Message createFNPSecretPing(long uid, double targetLocation, short htl, short dawnHtl, int counter, byte[] nodeIdentity) {
		Message msg = new Message(FNPSecretPing);
		msg.setRoutedToNodeFields(uid, targetLocation, htl, nodeIdentity);
		msg.set(COUNTER, counter);
		msg.set(DAWN_HTL, dawnHtl);
		return msg;
	}
	
	public static final MessageType FNPSecretPong = new MessageType("FNPSecretPong", PRIORITY_LOW) {{
		addField(UID, Long.class);
		addField(COUNTER, Integer.class);
		addField(SECRET, Long.class);
	}};
	
	public static final Message createFNPSecretPong(long uid, int counter, long secret) {
		Message msg = new Message(FNPSecretPong);
		msg.set(UID, uid);
		msg.set(COUNTER, counter);
		msg.set(SECRET, secret);
		return msg;
	}
	
	public static final MessageType FNPStoreSecret = new MessageType("FNPStoreSecret", PRIORITY_LOW) {{
		addField(UID, Long.class);
		addField(SECRET, Long.class);
	}};
	
	public static final Message createFNPStoreSecret(long uid, long secret) {
		Message msg = new Message(FNPStoreSecret);
		msg.set(UID, uid);
		msg.set(SECRET, secret);
		return msg;
	}
	
	public static final MessageType FNPNetworkID = new MessageType("FNPNetworkID", PRIORITY_LOW) {{
		addField(UID, Integer.class);
	}};
	
	public static final Message createFNPNetworkID(int id) {
		Message msg = new Message(FNPNetworkID);
		msg.set(UID, id);
		return msg;
	}
	
	public static final MessageType FNPRoutedRejected = new MessageType("FNPRoutedRejected", PRIORITY_UNSPECIFIED) {{
		addField(UID, Long.class);
		addField(HTL, Short.class);
	}};

	public static final Message createFNPRoutedRejected(long uid, short htl) {
		Message msg = new Message(FNPRoutedRejected);
		msg.set(UID, uid);
		msg.set(HTL, htl);
		return msg;
	}

	public static final MessageType FNPDetectedIPAddress = new MessageType("FNPDetectedIPAddress", PRIORITY_HIGH) {{
		addField(EXTERNAL_ADDRESS, Peer.class);
	}};
	
	public static final Message createFNPDetectedIPAddress(Peer peer) {
		Message msg = new Message(FNPDetectedIPAddress);
		msg.set(EXTERNAL_ADDRESS, peer);
		return msg;
	}

	public static final MessageType FNPTime = new MessageType("FNPTime", PRIORITY_HIGH) {{
		addField(TIME, Long.class);
	}};
	
	public static final Message createFNPTime(long time) {
		Message msg = new Message(FNPTime);
		msg.set(TIME, time);
		return msg;
	}
	
	public static final MessageType FNPUptime = new MessageType("FNPUptime", PRIORITY_LOW) {{
		addField(UPTIME_PERCENT_48H, Byte.class);
	}};
	
	public static final Message createFNPUptime(byte uptimePercent) {
		Message msg = new Message(FNPUptime);
		msg.set(UPTIME_PERCENT_48H, uptimePercent);
		return msg;
	}
	
	public static final MessageType FNPSentPackets = new MessageType("FNPSentPackets", PRIORITY_HIGH) {{
		addField(TIME_DELTAS, ShortBuffer.class);
		addField(HASHES, ShortBuffer.class);
		addField(TIME, Long.class);
	}};
	
	public static final Message createFNPSentPackets(int[] timeDeltas, long[] hashes, long baseTime) {
		Message msg = new Message(FNPSentPackets);
		msg.set(TIME_DELTAS, new ShortBuffer(Fields.intsToBytes(timeDeltas)));
		msg.set(HASHES, new ShortBuffer(Fields.longsToBytes(hashes)));
		msg.set(TIME, baseTime);
		return msg;
	}
	
	public static final MessageType FNPVoid = new MessageType("FNPVoid", PRIORITY_LOW) {{
	}};
	
	public static final Message createFNPVoid() {
		Message msg = new Message(FNPVoid);
		return msg;
	}
	
	public static final MessageType FNPDisconnect = new MessageType("FNPDisconnect", PRIORITY_HIGH) {{
		// If true, remove from active routing table, likely to be down for a while.
		// Otherwise just dump all current connection state and keep trying to connect.
		addField(REMOVE, Boolean.class);
		// If true, purge all references to this node. Otherwise, we can keep the node
		// around in secondary tables etc in order to more easily reconnect later. 
		// (Mostly used on opennet)
		addField(PURGE, Boolean.class);
		// Parting message, may be empty. A SimpleFieldSet in exactly the same format 
		// as an N2NTM.
		addField(NODE_TO_NODE_MESSAGE_TYPE, Integer.class);
		addField(NODE_TO_NODE_MESSAGE_DATA, ShortBuffer.class);
	}};
	
	public static final Message createFNPDisconnect(boolean remove, boolean purge, int messageType, ShortBuffer messageData) {
		Message msg = new Message(FNPDisconnect);
		msg.set(REMOVE, remove);
		msg.set(PURGE, purge);
		msg.set(NODE_TO_NODE_MESSAGE_TYPE, messageType);
		msg.set(NODE_TO_NODE_MESSAGE_DATA, messageData);
		return msg;
	}
	
	// Update over mandatory. Not strictly part of FNP. Only goes between nodes at the link
	// level, and will be sent, and parsed, even if the node is out of date. Should be stable 
	// long-term.
	
	// Sent on connect
	public static final MessageType UOMAnnounce = new MessageType("UOMAnnounce", PRIORITY_LOW) {{
		addField(MAIN_JAR_KEY, String.class);
		addField(EXTRA_JAR_KEY, String.class);
		addField(REVOCATION_KEY, String.class);
		addField(HAVE_REVOCATION_KEY, Boolean.class);
		addField(MAIN_JAR_VERSION, Long.class);
		addField(EXTRA_JAR_VERSION, Long.class);
		// Last time (ms ago) we had 3 DNFs in a row on the revocation checker.
		addField(REVOCATION_KEY_TIME_LAST_TRIED, Long.class);
		// Number of DNFs so far this time.
		addField(REVOCATION_KEY_DNF_COUNT, Integer.class);
		// For convenience, may change
		addField(REVOCATION_KEY_FILE_LENGTH, Long.class);
		addField(MAIN_JAR_FILE_LENGTH, Long.class);
		addField(EXTRA_JAR_FILE_LENGTH, Long.class);
		addField(PING_TIME, Integer.class);
		addField(BWLIMIT_DELAY_TIME, Integer.class);
	}};

	public static final Message createUOMAnnounce(String mainKey, String extraKey, String revocationKey,
			boolean haveRevocation, long mainJarVersion, long extraJarVersion, long timeLastTriedRevocationFetch,
			int revocationDNFCount, long revocationKeyLength, long mainJarLength, long extraJarLength, int pingTime, int bwlimitDelayTime) {
		Message msg = new Message(UOMAnnounce);
		
		msg.set(MAIN_JAR_KEY, mainKey);
		msg.set(EXTRA_JAR_KEY, extraKey);
		msg.set(REVOCATION_KEY, revocationKey);
		msg.set(HAVE_REVOCATION_KEY, haveRevocation);
		msg.set(MAIN_JAR_VERSION, mainJarVersion);
		msg.set(EXTRA_JAR_VERSION, extraJarVersion);
		msg.set(REVOCATION_KEY_TIME_LAST_TRIED, timeLastTriedRevocationFetch);
		msg.set(REVOCATION_KEY_DNF_COUNT, revocationDNFCount);
		msg.set(REVOCATION_KEY_FILE_LENGTH, revocationKeyLength);
		msg.set(MAIN_JAR_FILE_LENGTH, mainJarLength);
		msg.set(EXTRA_JAR_FILE_LENGTH, extraJarLength);
		msg.set(PING_TIME, pingTime);
		msg.set(BWLIMIT_DELAY_TIME, bwlimitDelayTime);
		
		return msg;
	}
	
	public static final MessageType UOMRequestRevocation = new MessageType("UOMRequestRevocation", PRIORITY_HIGH) {{
		addField(UID, Long.class);
	}};
	
	public static final Message createUOMRequestRevocation(long uid) {
		Message msg = new Message(UOMRequestRevocation);
		msg.set(UID, uid);
		return msg;
	}
	
	public static final MessageType UOMRequestMain = new MessageType("UOMRequestMain", PRIORITY_LOW) {{
		addField(UID, Long.class);
	}};
	
	public static final Message createUOMRequestMain(long uid) {
		Message msg = new Message(UOMRequestMain);
		msg.set(UID, uid);
		return msg;
	}
	
	public static final MessageType UOMRequestExtra = new MessageType("UOMRequestExtra", PRIORITY_LOW) {{
		addField(UID, Long.class);
	}};
	
	public static final Message createUOMRequestExtra(long uid) {
		Message msg = new Message(UOMRequestExtra);
		msg.set(UID, uid);
		return msg;
	}
	
	public static final MessageType UOMSendingRevocation = new MessageType("UOMSendingRevocation", PRIORITY_HIGH) {{
		addField(UID, Long.class);
		// Probably excessive, but lengths are always long's, and wasting a few bytes here
		// doesn't matter in the least, as it's very rarely called.
		addField(FILE_LENGTH, Long.class);
		addField(REVOCATION_KEY, String.class);
	}};
	
	public static final Message createUOMSendingRevocation(long uid, long length, String key) {
		Message msg = new Message(UOMSendingRevocation);
		msg.set(UID, uid);
		msg.set(FILE_LENGTH, length);
		msg.set(REVOCATION_KEY, key);
		return msg;
	}
	
	public static final MessageType UOMSendingMain = new MessageType("UOMSendingMain", PRIORITY_LOW) {{
		addField(UID, Long.class);
		addField(FILE_LENGTH, Long.class);
		addField(MAIN_JAR_KEY, String.class);
		addField(MAIN_JAR_VERSION, Integer.class);
	}};
	
	public static final Message createUOMSendingMain(long uid, long length, String key, int version) {
		Message msg = new Message(UOMSendingMain);
		msg.set(UID, uid);
		msg.set(FILE_LENGTH, length);
		msg.set(MAIN_JAR_KEY, key);
		msg.set(MAIN_JAR_VERSION, version);
		return msg;
	}
	
	public static final MessageType UOMSendingExtra = new MessageType("UOMSendingExtra", PRIORITY_LOW) {{
		addField(UID, Long.class);
		addField(FILE_LENGTH, Long.class);
		addField(EXTRA_JAR_KEY, String.class);
		addField(EXTRA_JAR_VERSION, Integer.class);
	}};
	
	public static final Message createUOMSendingExtra(long uid, long length, String key, int version) {
		Message msg = new Message(UOMSendingExtra);
		msg.set(UID, uid);
		msg.set(FILE_LENGTH, length);
		msg.set(EXTRA_JAR_KEY, key);
		msg.set(EXTRA_JAR_VERSION, version);
		return msg;
	}
	
	// Secondary messages (debug messages attached to primary messages)
	
	public static final MessageType FNPSwapNodeUIDs = new MessageType("FNPSwapNodeUIDs", PRIORITY_UNSPECIFIED) {{
		addField(NODE_UIDS, ShortBuffer.class);
	}};
	
	public static final Message createFNPSwapLocations(long[] uids) {
		Message msg = new Message(FNPSwapNodeUIDs);
		msg.set(NODE_UIDS, new ShortBuffer(Fields.longsToBytes(uids)));
		return msg;
	}
	
	// More permanent secondary messages (should perhaps be replaced by new main messages when stable)
	
	public static final MessageType FNPBestRoutesNotTaken = new MessageType("FNPBestRoutesNotTaken", PRIORITY_UNSPECIFIED) {{
		// Maybe this should be some sort of typed array?
		// It's just a bunch of double's anyway.
		addField(BEST_LOCATIONS_NOT_VISITED, ShortBuffer.class);
	}};
	
	public static final Message createFNPBestRoutesNotTaken(byte[] locs) {
		Message msg = new Message(FNPBestRoutesNotTaken);
		msg.set(BEST_LOCATIONS_NOT_VISITED, new ShortBuffer(locs));
		return msg;
	}
	
	public static final Message createFNPBestRoutesNotTaken(double[] locs) {
		return createFNPBestRoutesNotTaken(Fields.doublesToBytes(locs));
	}
	
	public static Message createFNPBestRoutesNotTaken(Double[] doubles) {
		double[] locs = new double[doubles.length];
		for(int i=0;i<locs.length;i++) locs[i] = doubles[i].doubleValue();
		return createFNPBestRoutesNotTaken(locs);
	}
	
	public static final MessageType FNPRoutingStatus = new MessageType("FNPRoutingStatus", PRIORITY_HIGH) {{
		addField(ROUTING_ENABLED, Boolean.class);
	}};
	
	public static final Message createRoutingStatus(boolean routeRequests) {
		Message msg = new Message(FNPRoutingStatus);
		msg.set(ROUTING_ENABLED, routeRequests);
		
		return msg;
	}
	
	public static final MessageType FNPSubInsertForkControl = new MessageType("FNPSubInsertForkControl", PRIORITY_HIGH) {{
		addField(ENABLE_INSERT_FORK_WHEN_CACHEABLE, Boolean.class);
	}};
	
	public static final Message createFNPSubInsertForkControl(boolean enableInsertForkWhenCacheable) {
		Message msg = new Message(FNPSubInsertForkControl);
		msg.set(ENABLE_INSERT_FORK_WHEN_CACHEABLE, enableInsertForkWhenCacheable);
		return msg;
	}
	
	public static final MessageType FNPSubInsertPreferInsert = new MessageType("FNPSubInsertPreferInsert", PRIORITY_HIGH) {{
		addField(PREFER_INSERT, Boolean.class);
	}};
	
	public static final Message createFNPSubInsertPreferInsert(boolean preferInsert) {
		Message msg = new Message(FNPSubInsertPreferInsert);
		msg.set(PREFER_INSERT, preferInsert);
		return msg;
		
	}
	
	public static final MessageType FNPSubInsertIgnoreLowBackoff = new MessageType("FNPSubInsertIgnoreLowBackoff", PRIORITY_HIGH) {{
		addField(IGNORE_LOW_BACKOFF, Boolean.class);
	}};
	
	public static final Message createFNPSubInsertIgnoreLowBackoff(boolean ignoreLowBackoff) {
		Message msg = new Message(FNPSubInsertIgnoreLowBackoff);
		msg.set(IGNORE_LOW_BACKOFF, ignoreLowBackoff);
		return msg;
		
	}
	
	public static final MessageType FNPRejectIsSoft = new MessageType("FNPRejectIsSoft", PRIORITY_HIGH) {{
		// No fields???
	}};
	
	public static final Message createFNPRejectIsSoft() {
		return new Message(FNPRejectIsSoft);
	}
	
	// New load management
	
	public static final MessageType FNPPeerLoadStatusByte = new MessageType("FNPPeerLoadStatusByte", PRIORITY_HIGH) {{
		addField(OTHER_TRANSFERS_OUT_CHK, Byte.class);
		addField(OTHER_TRANSFERS_IN_CHK, Byte.class);
		addField(OTHER_TRANSFERS_OUT_SSK, Byte.class);
		addField(OTHER_TRANSFERS_IN_SSK, Byte.class);
		addField(AVERAGE_TRANSFERS_OUT_PER_INSERT, Byte.class);
		addField(OUTPUT_BANDWIDTH_LOWER_LIMIT, Integer.class);
		addField(OUTPUT_BANDWIDTH_UPPER_LIMIT, Integer.class);
		addField(OUTPUT_BANDWIDTH_PEER_LIMIT, Integer.class);
		addField(INPUT_BANDWIDTH_LOWER_LIMIT, Integer.class);
		addField(INPUT_BANDWIDTH_UPPER_LIMIT, Integer.class);
		addField(INPUT_BANDWIDTH_PEER_LIMIT, Integer.class);
		addField(REAL_TIME_FLAG, Boolean.class);
	}};
	
	public static final MessageType FNPPeerLoadStatusShort = new MessageType("FNPPeerLoadStatusShort", PRIORITY_HIGH) {{
		addField(OTHER_TRANSFERS_OUT_CHK, Short.class);
		addField(OTHER_TRANSFERS_IN_CHK, Short.class);
		addField(OTHER_TRANSFERS_OUT_SSK, Short.class);
		addField(OTHER_TRANSFERS_IN_SSK, Short.class);
		addField(AVERAGE_TRANSFERS_OUT_PER_INSERT, Short.class);
		addField(OUTPUT_BANDWIDTH_LOWER_LIMIT, Integer.class);
		addField(OUTPUT_BANDWIDTH_UPPER_LIMIT, Integer.class);
		addField(OUTPUT_BANDWIDTH_PEER_LIMIT, Integer.class);
		addField(INPUT_BANDWIDTH_LOWER_LIMIT, Integer.class);
		addField(INPUT_BANDWIDTH_UPPER_LIMIT, Integer.class);
		addField(INPUT_BANDWIDTH_PEER_LIMIT, Integer.class);
		addField(REAL_TIME_FLAG, Boolean.class);
	}};
	
	public static final MessageType FNPPeerLoadStatusInt = new MessageType("FNPPeerLoadStatusInt", PRIORITY_HIGH) {{
		addField(OTHER_TRANSFERS_OUT_CHK, Integer.class);
		addField(OTHER_TRANSFERS_IN_CHK, Integer.class);
		addField(OTHER_TRANSFERS_OUT_SSK, Integer.class);
		addField(OTHER_TRANSFERS_IN_SSK, Integer.class);
		addField(AVERAGE_TRANSFERS_OUT_PER_INSERT, Integer.class);
		addField(OUTPUT_BANDWIDTH_LOWER_LIMIT, Integer.class);
		addField(OUTPUT_BANDWIDTH_UPPER_LIMIT, Integer.class);
		addField(OUTPUT_BANDWIDTH_PEER_LIMIT, Integer.class);
		addField(INPUT_BANDWIDTH_LOWER_LIMIT, Integer.class);
		addField(INPUT_BANDWIDTH_UPPER_LIMIT, Integer.class);
		addField(INPUT_BANDWIDTH_PEER_LIMIT, Integer.class);
		addField(REAL_TIME_FLAG, Boolean.class);
	}};
	
	public static final Message createFNPPeerLoadStatus(PeerLoadStats stats) {
		Message msg;
		if(stats.expectedTransfersInCHK < 256 && stats.expectedTransfersInSSK < 256 &&
				stats.expectedTransfersOutCHK < 256 && stats.expectedTransfersOutSSK < 256 &&
				stats.averageTransfersOutPerInsert < 256) {
			msg = new Message(FNPPeerLoadStatusByte);
			msg.set(OTHER_TRANSFERS_OUT_CHK, (byte)stats.expectedTransfersOutCHK);
			msg.set(OTHER_TRANSFERS_IN_CHK, (byte)stats.expectedTransfersInCHK);
			msg.set(OTHER_TRANSFERS_OUT_SSK, (byte)stats.expectedTransfersOutSSK);
			msg.set(OTHER_TRANSFERS_IN_SSK, (byte)stats.expectedTransfersInSSK);
			msg.set(AVERAGE_TRANSFERS_OUT_PER_INSERT, (byte)stats.averageTransfersOutPerInsert);
		} else if(stats.expectedTransfersInCHK < 65536 && stats.expectedTransfersInSSK < 65536 &&
				stats.expectedTransfersOutCHK < 65536 && stats.expectedTransfersOutSSK < 65536 &&
				stats.averageTransfersOutPerInsert < 65536) {
			msg = new Message(FNPPeerLoadStatusShort);
			msg.set(OTHER_TRANSFERS_OUT_CHK, (short)stats.expectedTransfersOutCHK);
			msg.set(OTHER_TRANSFERS_IN_CHK, (short)stats.expectedTransfersInCHK);
			msg.set(OTHER_TRANSFERS_OUT_SSK, (short)stats.expectedTransfersOutSSK);
			msg.set(OTHER_TRANSFERS_IN_SSK, (short)stats.expectedTransfersInSSK);
			msg.set(AVERAGE_TRANSFERS_OUT_PER_INSERT, (short)stats.averageTransfersOutPerInsert);
		} else {
			msg = new Message(FNPPeerLoadStatusInt);
			msg.set(OTHER_TRANSFERS_OUT_CHK, stats.expectedTransfersOutCHK);
			msg.set(OTHER_TRANSFERS_IN_CHK, stats.expectedTransfersInCHK);
			msg.set(OTHER_TRANSFERS_OUT_SSK, stats.expectedTransfersOutSSK);
			msg.set(OTHER_TRANSFERS_IN_SSK, stats.expectedTransfersInSSK);
			msg.set(AVERAGE_TRANSFERS_OUT_PER_INSERT, stats.averageTransfersOutPerInsert);
		}
		msg.set(OUTPUT_BANDWIDTH_LOWER_LIMIT, (int)stats.outputBandwidthLowerLimit);
		msg.set(OUTPUT_BANDWIDTH_UPPER_LIMIT, (int)stats.outputBandwidthUpperLimit);
		msg.set(OUTPUT_BANDWIDTH_PEER_LIMIT, (int)stats.outputBandwidthPeerLimit);
		msg.set(INPUT_BANDWIDTH_LOWER_LIMIT, (int)stats.inputBandwidthLowerLimit);
		msg.set(INPUT_BANDWIDTH_UPPER_LIMIT, (int)stats.inputBandwidthUpperLimit);
		msg.set(INPUT_BANDWIDTH_PEER_LIMIT, (int)stats.inputBandwidthPeerLimit);
		msg.set(REAL_TIME_FLAG, stats.realTime);
		return msg;
	}
	
	public static final String AVERAGE_TRANSFERS_OUT_PER_INSERT = "averageTransfersOutPerInsert";
	
	public static final String OTHER_TRANSFERS_OUT_CHK = "otherTransfersOutCHK";
	public static final String OTHER_TRANSFERS_IN_CHK = "otherTransfersOutCHK";
	public static final String OTHER_TRANSFERS_OUT_SSK = "otherTransfersOutCHK";
	public static final String OTHER_TRANSFERS_IN_SSK = "otherTransfersOutCHK";
	
	public static final String OUTPUT_BANDWIDTH_LOWER_LIMIT = "outputBandwidthLowerLimit";
	public static final String OUTPUT_BANDWIDTH_UPPER_LIMIT = "outputBandwidthUpperLimit";
	public static final String OUTPUT_BANDWIDTH_PEER_LIMIT = "outputBandwidthPeerLimit";
	public static final String INPUT_BANDWIDTH_LOWER_LIMIT = "inputBandwidthLowerLimit";
	public static final String INPUT_BANDWIDTH_UPPER_LIMIT = "inputBandwidthUpperLimit";
	public static final String INPUT_BANDWIDTH_PEER_LIMIT = "inputBandwidthPeerLimit";
	
	public static final String REAL_TIME_FLAG = "realTimeFlag";
	
	public static final MessageType FNPRealTimeFlag = new MessageType("FNPRealTimeFlag", PRIORITY_HIGH) {{
		addField(REAL_TIME_FLAG, Boolean.class);
	}};
	
	public static final Message createFNPRealTimeFlag(boolean isBulk) {
		Message msg = new Message(FNPRealTimeFlag);
		msg.set(REAL_TIME_FLAG, isBulk);
		return msg;
	}

	public static boolean getRealTimeFlag(Message m) {
		Message bulk = m.getSubMessage(FNPRealTimeFlag);
		if(bulk == null) return false;
		return bulk.getBoolean(REAL_TIME_FLAG);
	}
	
}
