package freenet.store;

import java.io.IOException;
import java.util.Arrays;
import java.util.Enumeration;

import com.sleepycat.je.DatabaseException;

import freenet.keys.KeyVerifyException;
import freenet.node.stats.StoreAccessStats;
import freenet.support.ByteArrayWrapper;
import freenet.support.LRUHashtable;
import freenet.support.Logger;

/**
 * LRU in memory store.
 * 
 * For debugging / simulation only
 */
public class RAMFreenetStore<T extends StorableBlock> implements FreenetStore<T> {

	private final static class Block {
		byte[] header;
		byte[] data;
		byte[] fullKey;
		boolean oldBlock;
	}
	
	private final LRUHashtable<ByteArrayWrapper, Block> blocksByRoutingKey;
	
	private final StoreCallback<T> callback;
	
	private int maxKeys;
	
	private long hits;
	private long misses;
	private long writes;
	
	public RAMFreenetStore(StoreCallback<T> callback, int maxKeys) {
		this.callback = callback;
		this.blocksByRoutingKey = new LRUHashtable<ByteArrayWrapper, Block>();
		this.maxKeys = maxKeys;
		callback.setStore(this);
	}
	
	public synchronized T fetch(byte[] routingKey, byte[] fullKey,
			boolean dontPromote, boolean canReadClientCache, boolean canReadSlashdotCache, boolean ignoreOldBlocks, BlockMetadata meta) throws IOException {
		ByteArrayWrapper key = new ByteArrayWrapper(routingKey);
		Block block = blocksByRoutingKey.get(key);
		if(block == null) {
			misses++;
			return null;
		}
		if(ignoreOldBlocks && block.oldBlock) {
			Logger.normal(this, "Ignoring old block");
			return null;
		}
		try {
			T ret =
				callback.construct(block.data, block.header, routingKey, block.fullKey, canReadClientCache, canReadSlashdotCache, meta, null);
			hits++;
			if(!dontPromote)
				blocksByRoutingKey.push(key, block);
			if(meta != null && block.oldBlock)
				meta.setOldBlock();
			return ret;
		} catch (KeyVerifyException e) {
			blocksByRoutingKey.removeKey(key);
			misses++;
			return null;
		}
	}

	public synchronized long getMaxKeys() {
		return maxKeys;
	}

	public synchronized long hits() {
		return hits;
	}

	public synchronized long keyCount() {
		return blocksByRoutingKey.size();
	}

	public synchronized long misses() {
		return misses;
	}

	public synchronized void put(T block, byte[] data, byte[] header, boolean overwrite, boolean isOldBlock) throws KeyCollisionException {
		byte[] routingkey = block.getRoutingKey();
		byte[] fullKey = block.getFullKey();
		
		writes++;
		ByteArrayWrapper key = new ByteArrayWrapper(routingkey);
		Block oldBlock = blocksByRoutingKey.get(key);
		boolean storeFullKeys = callback.storeFullKeys();
		if(oldBlock != null) {
			if(callback.collisionPossible()) {
				boolean equals = Arrays.equals(oldBlock.data, data) &&
					Arrays.equals(oldBlock.header, header) &&
					(storeFullKeys ? Arrays.equals(oldBlock.fullKey, fullKey) : true);
				if(equals) {
					if(!isOldBlock)
						oldBlock.oldBlock = false;
					return;
				}
				if(overwrite) {
					oldBlock.data = data;
					oldBlock.header = header;
					if(storeFullKeys)
						oldBlock.fullKey = fullKey;
					oldBlock.oldBlock = isOldBlock;
				} else {
					throw new KeyCollisionException();
				}
				return;
			} else {
				if(!isOldBlock)
					oldBlock.oldBlock = false;
				return;
			}
		}
		Block storeBlock = new Block();
		storeBlock.data = data;
		storeBlock.header = header;
		if(storeFullKeys)
			storeBlock.fullKey = fullKey;
		storeBlock.oldBlock = isOldBlock;
		blocksByRoutingKey.push(key, storeBlock);
		while(blocksByRoutingKey.size() > maxKeys) {
			blocksByRoutingKey.popKey();
		}
	}

	public synchronized void setMaxKeys(long maxStoreKeys, boolean shrinkNow)
			throws DatabaseException, IOException {
		this.maxKeys = (int)Math.min(Integer.MAX_VALUE, maxStoreKeys);
		// Always shrink now regardless of parameter as we will shrink on the next put() anyway.
		while(blocksByRoutingKey.size() > maxKeys) {
			blocksByRoutingKey.popKey();
		}
	}

	public long writes() {
		return writes;
	}

	public long getBloomFalsePositive() {
		return -1;
	}
	
	public boolean probablyInStore(byte[] routingKey) {
		ByteArrayWrapper key = new ByteArrayWrapper(routingKey);
		return blocksByRoutingKey.get(key) != null;
	}

	public void clear() {
		blocksByRoutingKey.clear();
	}

	public void migrateTo(StoreCallback<T> target, boolean canReadClientCache) throws IOException {
		Enumeration<ByteArrayWrapper> keys = blocksByRoutingKey.keys();
		while(keys.hasMoreElements()) {
			ByteArrayWrapper routingKeyWrapped = keys.nextElement();
			byte[] routingKey = routingKeyWrapped.get();
			Block block = blocksByRoutingKey.get(routingKeyWrapped);
			
			T ret;
			try {
				ret = callback.construct(block.data, block.header, routingKey, block.fullKey, canReadClientCache, false, null, null);
			} catch (KeyVerifyException e) {
				Logger.error(this, "Caught while migrating: "+e, e);
				continue;
			}
			try {
				target.getStore().put(ret, block.data, block.header, false, block.oldBlock);
			} catch (KeyCollisionException e) {
				// Ignore
			}
		}
	}
	
	public StoreAccessStats getSessionAccessStats() {
		return new StoreAccessStats() {

			@Override
			public long hits() {
				return hits;
			}

			@Override
			public long misses() {
				return misses;
			}

			@Override
			public long falsePos() {
				return 0;
			}

			@Override
			public long writes() {
				return writes;
			}
			
		};
	}

	public StoreAccessStats getTotalAccessStats() {
		return null;
	}

	public String getDatabaseContents() {
		return "This method not implemented for RAMFreenetStore.\n";
	}
}
