/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package freenet.client.async;

import java.io.IOException;
import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.Vector;
import java.util.Map.Entry;

import com.db4o.ObjectContainer;

import freenet.client.FetchContext;
import freenet.keys.ClientKey;
import freenet.keys.ClientSSK;
import freenet.keys.ClientSSKBlock;
import freenet.keys.FreenetURI;
import freenet.keys.Key;
import freenet.keys.KeyBlock;
import freenet.keys.KeyDecodeException;
import freenet.keys.NodeSSK;
import freenet.keys.SSKBlock;
import freenet.keys.SSKVerifyException;
import freenet.keys.USK;
import freenet.node.KeysFetchingLocally;
import freenet.node.LowLevelGetException;
import freenet.node.RequestClient;
import freenet.node.RequestScheduler;
import freenet.node.RequestStarter;
import freenet.node.SendableGet;
import freenet.node.SendableRequestItem;
import freenet.support.LogThresholdCallback;
import freenet.support.Logger;
import freenet.support.RemoveRangeArrayList;
import freenet.support.Logger.LogLevel;
import freenet.support.api.Bucket;
import freenet.support.io.BucketTools;

/**
 * 
 * On 0.7, this shouldn't however take more than 10 seconds or so; these are SSKs
 * we are talking about. If this is fast enough then people will use the "-" form.
 * 
 * FProxy should cause USKs with negative edition numbers to be redirected to USKs
 * with positive edition numbers.
 * 
 * If the number specified is up to date, we just do the fetch. If a more recent
 * USK can be found, then we fail with an exception with the new version. The
 * client is expected to redirect to this. The point here is that FProxy will then
 * have the correct number in the location bar, so that if the user copies the URL,
 * it will keep the edition number hint.
 * 
 * A positive number fetch triggers a background fetch.
 * 
 * This class does both background fetches and negative number fetches.
 * 
 * It does them in the same way.
 * 
 * There is one USKFetcher for a given USK, at most. They are registered on the
 * USKManager. They have a list of ClientGetState-implementing callbacks; these
 * are all triggered on completion. 
 * 
 * When a new suggestedEdition is added, if that is later than the
 * currently searched-for version, the USKFetcher will try to fetch that as well
 * as its current pointer.
 * 
 * Current algorithm:
 * - Fetch the next 5 editions all at once.
 * - If we have four consecutive editions with DNF, and no later pending fetches,
 *   then we finish with the last known good version. (There are details relating
 *   to other error codes handled below in the relevant method).
 * - We immediately update the USKManager if we successfully fetch an edition.
 * - If a new, higher suggestion comes in, that is also fetched.
 * 
 * Future extensions:
 * - Binary search.
 * - Hierarchical DBRs.
 * - TUKs (when we have TUKs).
 * - Passive requests (when we have passive requests).
 */
public class USKFetcher implements ClientGetState, USKCallback, HasKeyListener, KeyListener {
    private static volatile boolean logMINOR;
    private static volatile boolean logDEBUG;

    static {
        Logger.registerLogThresholdCallback(new LogThresholdCallback() {

            @Override
            public void shouldUpdate() {
                logMINOR = Logger.shouldLog(LogLevel.MINOR, this);
                logDEBUG = Logger.shouldLog(LogLevel.DEBUG, this);
            }
        });
    }
	
	/** USK manager */
	private final USKManager uskManager;
	
	/** The USK to fetch */
	private final USK origUSK;
	
	/** Callbacks */
	private final LinkedList<USKFetcherCallback> callbacks;

	/** Fetcher context */
	final FetchContext ctx;
	/** Fetcher context ignoring store */
	final FetchContext ctxNoStore;
	
	/** Finished? */
	private boolean completed;
	
	/** Cancelled? */
	private boolean cancelled;

	/** Kill a background poll fetcher when it has lost its last subscriber? */
	private boolean killOnLoseSubscribers;
	
	private final boolean checkStoreOnly;
	
	final ClientRequester parent;

	// We keep the data from the last (highest number) request.
	private Bucket lastRequestData;
	private short lastCompressionCodec;
	private boolean lastWasMetadata;
	
	/** Structure tracking which keys we want. */
	private final USKWatchingKeys watchingKeys;
	
	private final ArrayList<USKAttempt> attemptsToStart;
	
	private static final int WATCH_KEYS = 50;
	
	/**
	 * Callbacks are told when the USKFetcher finishes, and unless background poll is
	 * enabled, they are only sent onFoundEdition *once*, on completion.
	 * 
	 * However they do help to determine the fetcher's priority.
	 * 
	 * FIXME: Don't allow callbacks if backgroundPoll is enabled??
	 * @param cb
	 * @return
	 */
	public boolean addCallback(USKFetcherCallback cb) {
		synchronized(this) {
			if(completed) return false; 
			callbacks.add(cb);
		}
		updatePriorities();
		return true;
	}
	
	class USKAttempt implements USKCheckerCallback {
		/** Edition number */
		long number;
		/** Attempt to fetch that edition number (or null if the fetch has finished) */
		USKChecker checker;
		/** Successful fetch? */
		boolean succeeded;
		/** DNF? */
		boolean dnf;
		boolean cancelled;
		final Lookup lookup;
		public USKAttempt(Lookup l, boolean forever) {
			this.lookup = l;
			this.number = l.val;
			this.succeeded = false;
			this.dnf = false;
			this.checker = new USKChecker(this, l.key, forever ? -1 : ctx.maxUSKRetries, l.ignoreStore ? ctxNoStore : ctx, parent);
		}
		public void onDNF(ClientContext context) {
			checker = null;
			dnf = true;
			USKFetcher.this.onDNF(this, context);
		}
		public void onSuccess(ClientSSKBlock block, ClientContext context) {
			checker = null;
			succeeded = true;
			USKFetcher.this.onSuccess(this, false, block, context);
		}
		
		public void onFatalAuthorError(ClientContext context) {
			checker = null;
			// Counts as success except it doesn't update
			USKFetcher.this.onSuccess(this, true, null, context);
		}
		
		public void onNetworkError(ClientContext context) {
			checker = null;
			// Not a DNF
			USKFetcher.this.onFail(this, context);
		}
		
		public void onCancelled(ClientContext context) {
			checker = null;
			USKFetcher.this.onCancelled(this, context);
		}
		
		public void cancel(ObjectContainer container, ClientContext context) {
			assert(container == null);
			cancelled = true;
			if(checker != null)
				checker.cancel(container, context);
			onCancelled(context);
		}
		
		public void schedule(ObjectContainer container, ClientContext context) {
			assert(container == null);
			if(checker == null) {
				if(logMINOR)
					Logger.minor(this, "Checker == null in schedule() for "+this, new Exception("debug"));
			} else {
				assert(!checker.persistent());
				checker.schedule(container, context);
			}
		}
		
		@Override
		public String toString() {
			return "USKAttempt for "+number+" for "+origUSK.getURI()+" for "+USKFetcher.this;
		}
		
		public short getPriority() {
			if(backgroundPoll) {
				if(progressed && !firstLoop) {
					// Just advanced, boost the priority.
					// Do NOT boost the priority if just started.
					return progressPollPriority;
				} else {
					return normalPollPriority;
				}
			} else
				return parent.getPriorityClass();
		}
	}
	
	private final TreeMap<Long, USKAttempt> runningAttempts = new TreeMap<Long, USKAttempt>();
	private final TreeMap<Long, USKAttempt> pollingAttempts = new TreeMap<Long, USKAttempt>();
	
	private long lastFetchedEdition;

	final long origMinFailures;
	boolean firstLoop;
	
	static final int origSleepTime = 30 * 60 * 1000;
	static final int maxSleepTime = 24 * 60 * 60 * 1000;
	int sleepTime = origSleepTime;

	private long valueAtSchedule;
	
	/** Keep going forever? */
	private final boolean backgroundPoll;
	private boolean progressed;
	
	/** Keep the last fetched data? */
	final boolean keepLastData;
	
	private boolean started;
	
	private static short DEFAULT_NORMAL_POLL_PRIORITY = RequestStarter.PREFETCH_PRIORITY_CLASS;
	private short normalPollPriority = DEFAULT_NORMAL_POLL_PRIORITY;
	private static short DEFAULT_PROGRESS_POLL_PRIORITY = RequestStarter.UPDATE_PRIORITY_CLASS;
	private short progressPollPriority = DEFAULT_PROGRESS_POLL_PRIORITY;

	// FIXME use this!
	USKFetcher(USK origUSK, USKManager manager, FetchContext ctx, ClientRequester requester, int minFailures, boolean pollForever, boolean keepLastData, boolean checkStoreOnly) {
		this.parent = requester;
		this.origUSK = origUSK;
		this.uskManager = manager;
		this.origMinFailures = minFailures;
		if(origMinFailures > WATCH_KEYS)
			throw new IllegalArgumentException();
		firstLoop = true;
		callbacks = new LinkedList<USKFetcherCallback>();
		subscribers = new HashSet<USKCallback>();
		lastFetchedEdition = -1;
		if(ctx.followRedirects) {
			this.ctx = ctx.clone();
			this.ctx.followRedirects = false;
		} else {
			this.ctx = ctx;
		}
		if(ctx.ignoreStore) {
			ctxNoStore = this.ctx;
		} else {
			ctxNoStore = this.ctx.clone();
			ctxNoStore.ignoreStore = true;
		}
		this.backgroundPoll = pollForever;
		this.keepLastData = keepLastData;
		this.checkStoreOnly = checkStoreOnly;
		if(checkStoreOnly && logMINOR)
			Logger.minor(this, "Just checking store on "+this);
		// origUSK is a hint. We *do* want to check the edition given.
		// Whereas latestSlot we've definitely fetched, we don't want to re-check.
		watchingKeys = new USKWatchingKeys(origUSK, Math.max(0, uskManager.lookupLatestSlot(origUSK)+1));
		attemptsToStart = new ArrayList<USKAttempt>();
	}
	
	void onDNF(USKAttempt att, ClientContext context) {
		if(logMINOR) Logger.minor(this, "DNF: "+att);
		boolean finished = false;
		long curLatest = uskManager.lookupLatestSlot(origUSK);
		synchronized(this) {
			if(completed || cancelled) return;
			lastFetchedEdition = Math.max(lastFetchedEdition, att.number);
			runningAttempts.remove(att.number);
			if(runningAttempts.isEmpty()) {
				if(logMINOR) Logger.minor(this, "latest: "+curLatest+", last fetched: "+lastFetchedEdition+", curLatest+MIN_FAILURES: "+(curLatest+origMinFailures));
				if(started) {
					finished = true;
				}
			} else if(logMINOR) Logger.minor(this, "Remaining: "+runningAttempts());
		}
		if(finished) {
			finishSuccess(context);
		}
	}
	
	private synchronized String runningAttempts() {
		StringBuffer sb = new StringBuffer();
		boolean first = true;
		for(USKAttempt a : runningAttempts.values()) {
			if(!first) sb.append(", ");
			sb.append(a.number);
			if(a.cancelled)
				sb.append("(cancelled)");
			if(a.succeeded)
				sb.append("(succeeded)");
		}
		return sb.toString();
	}

	private void finishSuccess(ClientContext context) {
		if(logMINOR)
			Logger.minor(this, "finishSuccess() on "+this);
		if(backgroundPoll) {
			long valAtEnd = uskManager.lookupLatestSlot(origUSK);
			long end;
			long now = System.currentTimeMillis();
			synchronized(this) {
				started = false; // don't finish before have rescheduled
                
                //Find out when we should check next ('end'), in an increasing delay (unless we make progress).
                int newSleepTime = sleepTime * 2;
				if(newSleepTime > maxSleepTime) newSleepTime = maxSleepTime;
				sleepTime = newSleepTime;
				end = now + context.random.nextInt(sleepTime);
                
				if(valAtEnd > valueAtSchedule && valAtEnd > origUSK.suggestedEdition) {
					progressed = true;
					// We have advanced; keep trying as if we just started.
					// Only if we actually DO advance, not if we just confirm our suspicion (valueAtSchedule always starts at 0).
					sleepTime = origSleepTime;
					firstLoop = false;
					end = now;
					if(logMINOR)
						Logger.minor(this, "We have advanced: at start, "+valueAtSchedule+" at end, "+valAtEnd);
				} else
					progressed = false;
				if(logMINOR) Logger.minor(this, "Sleep time is "+sleepTime+" this sleep is "+(end-now)+" for "+this);
			}
			schedule(end-now, null, context);
		} else {
			USKFetcherCallback[] cb;
			synchronized(this) {
				completed = true;
				cb = callbacks.toArray(new USKFetcherCallback[callbacks.size()]);
			}
			uskManager.unsubscribe(origUSK, this);
			uskManager.onFinished(this);
			context.getSskFetchScheduler().schedTransient.removePendingKeys((KeyListener)this);
			long ed = uskManager.lookupLatestSlot(origUSK);
			byte[] data;
			if(lastRequestData == null)
				data = null;
			else {
				try {
					data = BucketTools.toByteArray(lastRequestData);
				} catch (IOException e) {
					Logger.error(this, "Unable to turn lastRequestData into byte[]: caught I/O exception: "+e, e);
					data = null;
				}
			}
			for(int i=0;i<cb.length;i++) {
				try {
					if(ed == -1)
						cb[i].onFailure(null, context);
					else
						cb[i].onFoundEdition(ed, origUSK.copy(ed), null, context, lastWasMetadata, lastCompressionCodec, data, false, false);
				} catch (Exception e) {
					Logger.error(this, "An exception occured while dealing with a callback:"+cb[i].toString()+"\n"+e.getMessage(),e);
				}
			}
		}
	}

	void onSuccess(USKAttempt att, boolean dontUpdate, ClientSSKBlock block, final ClientContext context) {
		onSuccess(att, att.number, dontUpdate, block, context);
	}
	
	void onSuccess(USKAttempt att, long curLatest, boolean dontUpdate, ClientSSKBlock block, final ClientContext context) {
		final long lastEd = uskManager.lookupLatestSlot(origUSK);
		if(logMINOR) Logger.minor(this, "Found edition "+curLatest+" for "+origUSK+" official is "+lastEd+" on "+this);
		boolean decode = false;
		Vector<USKAttempt> killAttempts = null;
		boolean registerNow;
		// FIXME call uskManager.updateSlot BEFORE getEditionsToFetch, avoids a possible conflict, but creates another (with onFoundEdition) - we'd probably have to handle this there???
		synchronized(this) {
			if(att != null) runningAttempts.remove(att.number);
			if(completed || cancelled) {
				if(logMINOR) Logger.minor(this, "Finished already: completed="+completed+" cancelled="+cancelled);
				return;
			}
			decode = curLatest >= lastEd && !(dontUpdate && block == null);
			curLatest = Math.max(lastEd, curLatest);
			if(logMINOR) Logger.minor(this, "Latest: "+curLatest+" in onSuccess");
			if(!checkStoreOnly) {
				killAttempts = cancelBefore(curLatest, context);
				USKWatchingKeys.ToFetch list = watchingKeys.getEditionsToFetch(curLatest, context.random, getRunningFetchEditions());
				Lookup[] toPoll = list.toPoll;
				Lookup[] toFetch = list.toFetch;
				for(Lookup i : toPoll) {
					if(logDEBUG) Logger.debug(this, "Polling "+i+" for "+this);
					attemptsToStart.add(add(i, true));	
				}
				for(Lookup i : toFetch) {
					if(logMINOR) Logger.minor(this, "Adding checker for edition "+i+" for "+origUSK);
					attemptsToStart.add(add(i, false));
				}
			}
			registerNow = !fillKeysWatching(curLatest, context);
		}
		finishCancelBefore(killAttempts, context);
		Bucket data = null;
		if(decode && block != null) {
			try {
				data = block.decode(context.getBucketFactory(parent.persistent()), 1025 /* it's an SSK */, true);
			} catch (KeyDecodeException e) {
				data = null;
			} catch (IOException e) {
				data = null;
				Logger.error(this, "An IOE occured while decoding: "+e.getMessage(),e);
			}
		}
		synchronized(this) {
			if (decode) {
				if(block != null) {
					lastCompressionCodec = block.getCompressionCodec();
					lastWasMetadata = block.isMetadata();
					if(keepLastData) {
						if(lastRequestData != null)
							lastRequestData.free();
						lastRequestData = data;
					} else
						data.free();
				} else {
					lastCompressionCodec = -1;
					lastWasMetadata = false;
					lastRequestData = null;
				}
			}
		}
		if(!dontUpdate)
			uskManager.updateSlot(origUSK, curLatest, context);
		if(registerNow)
			registerAttempts(context);
	}

	void onCancelled(USKAttempt att, ClientContext context) {
		synchronized(this) {
			runningAttempts.remove(att.number);
			if(!runningAttempts.isEmpty()) return;
		
			if(cancelled)
				finishCancelled(context);
		}
	}

	private void finishCancelled(ClientContext context) {
		USKFetcherCallback[] cb;
		synchronized(this) {
			completed = true;
			cb = callbacks.toArray(new USKFetcherCallback[callbacks.size()]);
		}
		for(int i=0;i<cb.length;i++)
			cb[i].onCancelled(null, context);
	}

	public void onFail(USKAttempt attempt, ClientContext context) {
		// FIXME what else can we do?
		// Certainly we don't want to continue fetching indefinitely...
		// ... e.g. RNFs don't indicate we should try a later slot, none of them
		// really do.
		onDNF(attempt, context);
	}

	private Vector<USKAttempt> cancelBefore(long curLatest, ClientContext context) {
		Vector<USKAttempt> v = null;
		int count = 0;
		synchronized(this) {
			for(Iterator<USKAttempt> i=runningAttempts.values().iterator();i.hasNext();) {
				USKAttempt att = i.next();
				if(att.number < curLatest) {
					if(v == null) v = new Vector<USKAttempt>(runningAttempts.size()-count);
					v.add(att);
					i.remove();
				}
				count++;
			}
			for(Iterator<Map.Entry<Long, USKAttempt>> i = pollingAttempts.entrySet().iterator();i.hasNext();) {
				Map.Entry<Long, USKAttempt> entry = i.next();
				if(entry.getKey() < curLatest) {
					if(v == null) v = new Vector<USKAttempt>(runningAttempts.size()-count);
					v.add(entry.getValue());
					i.remove();
				} else break; // TreeMap is ordered.
			}
		}
		return v;
	}
	
	private void finishCancelBefore(Vector<USKAttempt> v, ClientContext context) {
		if(v != null) {
			for(int i=0;i<v.size();i++) {
				USKAttempt att = v.get(i);
				att.cancel(null, context);
			}
		}
	}

	/**
	 * Add a USKAttempt for another edition number.
	 * Caller is responsible for calling .schedule().
	 */
	private synchronized USKAttempt add(Lookup l, boolean forever) {
		long i = l.val;
		if(l.val < 0) throw new IllegalArgumentException("Can't check <0 for "+l.val+" on "+this+" for "+origUSK);
		if(cancelled) return null;
		if(checkStoreOnly) return null;
		if(logMINOR) Logger.minor(this, "Adding USKAttempt for "+i+" for "+origUSK.getURI());
		if(forever) {
			if(pollingAttempts.containsKey(i)) {
				if(logMINOR) Logger.minor(this, "Already polling edition: "+i+" for "+this);
				return null;
			}
		} else {
			if(!runningAttempts.isEmpty()) {
				if(runningAttempts.containsKey(i)) {
					if(logMINOR) Logger.minor(this, "Returning because already running for "+origUSK.getURI());
					return null;
				}
			}
		}
		USKAttempt a = new USKAttempt(l, forever);
		if(forever)
			pollingAttempts.put(i, a);
		else {
			runningAttempts.put(i, a);
		}
		if(logMINOR) Logger.minor(this, "Added "+a+" for "+origUSK);
		return a;
	}

	public FreenetURI getURI() {
		return origUSK.getURI();
	}

	public boolean isFinished() {
		synchronized (this) {
			return completed || cancelled;			
		}
	}

	public USK getOriginalUSK() {
		return origUSK;
	}
	
	public void schedule(long delay, ObjectContainer container, final ClientContext context) {
		assert(container == null);
		if (delay<=0) {
			schedule(container, context);
		} else {
			context.ticker.queueTimedJob(new Runnable() {
				public void run() {
					USKFetcher.this.schedule(null, context);
				}
			}, delay);
		}
	}
    
	public void schedule(ObjectContainer container, ClientContext context) {
		synchronized(this) {
			if(cancelled) return;
			if(completed) return;
		}
		context.getSskFetchScheduler().schedTransient.addPendingKeys(this);
		updatePriorities();
		uskManager.subscribe(origUSK, this, false, parent.getClient());
		long lookedUp = uskManager.lookupLatestSlot(origUSK);
		boolean registerNow = false;
		boolean bye = false;
		synchronized(this) {
			valueAtSchedule = Math.max(lookedUp+1, valueAtSchedule);
			bye = cancelled || completed;
			if(!bye) {
				
				// subscribe() above may have called onFoundEdition and thus added a load of stuff. If so, we don't need to do so here.
				if((!checkStoreOnly) && attemptsToStart.isEmpty() && runningAttempts.isEmpty() && pollingAttempts.isEmpty()) {
					USKWatchingKeys.ToFetch list = watchingKeys.getEditionsToFetch(lookedUp, context.random, getRunningFetchEditions());
					Lookup[] toPoll = list.toPoll;
					Lookup[] toFetch = list.toFetch;
					for(Lookup i : toPoll) {
						if(logDEBUG) Logger.debug(this, "Polling "+i+" for "+this);
						attemptsToStart.add(add(i, true));	
					}
					for(Lookup i : toFetch) {
						if(logMINOR) Logger.minor(this, "Adding checker for edition "+i+" for "+origUSK);
						attemptsToStart.add(add(i, false));
					}
				}
				
				started = true;
				registerNow = !fillKeysWatching(lookedUp, context);
			}
		}
		if(registerNow)
			registerAttempts(context);
		if(!bye) return;
		// We have been cancelled.
		uskManager.unsubscribe(origUSK, this);
		context.getSskFetchScheduler().schedTransient.removePendingKeys((KeyListener)this);
		uskManager.onFinished(this, true);
	}

	public void cancel(ObjectContainer container, ClientContext context) {
		uskManager.unsubscribe(origUSK, this);
		context.getSskFetchScheduler().schedTransient.removePendingKeys((KeyListener)this);
		assert(container == null);
		USKAttempt[] attempts;
		USKAttempt[] polling;
		uskManager.onFinished(this);
		SendableGet storeChecker;
		synchronized(this) {
			if(cancelled) Logger.error(this, "Already cancelled "+this);
			if(completed) Logger.error(this, "Already completed "+this);
			cancelled = true;
			attempts = runningAttempts.values().toArray(new USKAttempt[runningAttempts.size()]);
			polling = pollingAttempts.values().toArray(new USKAttempt[pollingAttempts.size()]);
			attemptsToStart.clear();
			storeChecker = runningStoreChecker;
			runningStoreChecker = null;
		}
		for(int i=0;i<attempts.length;i++)
			attempts[i].cancel(container, context);
		for(int i=0;i<polling.length;i++)
			polling[i].cancel(container, context);
		if(storeChecker != null)
			// Remove from the store checker queue.
			storeChecker.unregister(container, context, storeChecker.getPriorityClass(container));
	}

	/** Set of interested USKCallbacks. Note that we don't actually
	 * send them any information - they are essentially placeholders,
	 * an alternative to a refcount. This could be replaced with a 
	 * Bloom filter or whatever, we only need .exists and .count.
	 */
	final HashSet<USKCallback> subscribers;
	/** Map from subscribers to hint editions. */
	final HashMap<USKCallback, Long> subscriberHints = new HashMap<USKCallback, Long>();
	
	/**
	 * Add a subscriber. Subscribers are not directly sent onFoundEdition()'s by the
	 * USKFetcher, we just use them to determine the priority of our requests and 
	 * whether we should continue to request.
	 * @param cb
	 */
	public void addSubscriber(USKCallback cb, long hint) {
		Long[] hints;
		synchronized(this) {
			subscribers.add(cb);
			subscriberHints.put(cb, hint);
			hints = subscriberHints.values().toArray(new Long[subscriberHints.size()]);
		}
		updatePriorities();
		watchingKeys.updateSubscriberHints(hints, uskManager.lookupLatestSlot(origUSK));
	}

	private void updatePriorities() {
		// FIXME should this be synchronized? IMHO it doesn't matter that much if we get the priority
		// wrong for a few requests... also, we avoid any possible deadlock this way if the callbacks
		// take locks...
		short normalPrio = RequestStarter.MINIMUM_PRIORITY_CLASS;
		short progressPrio = RequestStarter.MINIMUM_PRIORITY_CLASS;
		USKCallback[] localCallbacks;
		USKFetcherCallback[] fetcherCallbacks;
		synchronized(this) {
			localCallbacks = subscribers.toArray(new USKCallback[subscribers.size()]);
			// Callbacks also determine the fetcher's priority.
			// Otherwise USKFetcherTag would have no way to tell us the priority we should run at.
			fetcherCallbacks = callbacks.toArray(new USKFetcherCallback[callbacks.size()]);
		}
		if(localCallbacks.length == 0 && fetcherCallbacks.length == 0) {
			normalPollPriority = DEFAULT_NORMAL_POLL_PRIORITY;
			progressPollPriority = DEFAULT_PROGRESS_POLL_PRIORITY;
			if(logMINOR) Logger.minor(this, "Updating priorities: normal = "+normalPollPriority+" progress = "+progressPollPriority+" for "+this+" for "+origUSK);
			return;
		}
		
		for(int i=0;i<localCallbacks.length;i++) {
			USKCallback cb = localCallbacks[i];
			short prio = cb.getPollingPriorityNormal();
			if(logDEBUG) Logger.debug(this, "Normal priority for "+cb+" : "+prio);
			if(prio < normalPrio) normalPrio = prio;
			if(logDEBUG) Logger.debug(this, "Progress priority for "+cb+" : "+prio);
			prio = cb.getPollingPriorityProgress();
			if(prio < progressPrio) progressPrio = prio;
		}
		for(int i=0;i<fetcherCallbacks.length;i++) {
			USKFetcherCallback cb = fetcherCallbacks[i];
			short prio = cb.getPollingPriorityNormal();
			if(logDEBUG) Logger.debug(this, "Normal priority for "+cb+" : "+prio);
			if(prio < normalPrio) normalPrio = prio;
			if(logDEBUG) Logger.debug(this, "Progress priority for "+cb+" : "+prio);
			prio = cb.getPollingPriorityProgress();
			if(prio < progressPrio) progressPrio = prio;
		}
		if(logMINOR) Logger.minor(this, "Updating priorities: normal="+normalPrio+" progress="+progressPrio+" for "+this+" for "+origUSK);
		synchronized(this) {
			normalPollPriority = normalPrio;
			progressPollPriority = progressPrio;
		}
	}

	public synchronized boolean hasSubscribers() {
		return !subscribers.isEmpty();
	}
	
	public synchronized boolean hasCallbacks() {
		return !callbacks.isEmpty();
	}
	
	public void removeSubscriber(USKCallback cb, ClientContext context) {
		Long[] hints;
		synchronized(this) {
			subscribers.remove(cb);
			subscriberHints.remove(cb);
			hints = subscriberHints.values().toArray(new Long[subscriberHints.size()]);
		}
		updatePriorities();
		watchingKeys.updateSubscriberHints(hints, uskManager.lookupLatestSlot(origUSK));
	}

	public void removeCallback(USKCallback cb) {
		Long[] hints;
		synchronized(this) {
			subscribers.remove(cb);
			subscriberHints.remove(cb);
			hints = subscriberHints.values().toArray(new Long[subscriberHints.size()]);
		}
		watchingKeys.updateSubscriberHints(hints, uskManager.lookupLatestSlot(origUSK));
	}

	public synchronized boolean hasLastData() {
		return this.lastRequestData != null;
	}

	public synchronized boolean lastContentWasMetadata() {
		return this.lastWasMetadata;
	}

	public synchronized short lastCompressionCodec() {
		return this.lastCompressionCodec;
	}

	public synchronized Bucket getLastData() {
		return this.lastRequestData;
	}

	public synchronized void freeLastData() {
		if(lastRequestData == null) return;
		lastRequestData.free(); // USKFetcher's cannot be persistent, so no need to removeFrom()
		lastRequestData = null;
	}

	public synchronized void killOnLoseSubscribers() {
		this.killOnLoseSubscribers = true;
	}

	public long getToken() {
		return -1;
	}

	public void removeFrom(ObjectContainer container, ClientContext context) {
		throw new UnsupportedOperationException();
	}
	
	public boolean objectCanNew(ObjectContainer container) {
		Logger.error(this, "Not storing USKFetcher in database", new Exception("error"));
		return false;
	}

	public short getPollingPriorityNormal() {
		throw new UnsupportedOperationException();
	}

	public short getPollingPriorityProgress() {
		throw new UnsupportedOperationException();
	}

	public void onFoundEdition(long ed, USK key, ObjectContainer container, final ClientContext context, boolean metadata, short codec, byte[] data, boolean newKnownGood, boolean newSlotToo) {
		if(newKnownGood && !newSlotToo) return; // Only interested in slots
		// Because this is frequently run off-thread, it is actually possible that the looked up edition is not the same as the edition we are being notified of.
		final long lastEd = uskManager.lookupLatestSlot(origUSK);
		boolean decode = false;
		Vector<USKAttempt> killAttempts = null;
		boolean registerNow = false;
		synchronized(this) {
			if(completed || cancelled) return;
			decode = lastEd == ed && data != null;
			ed = Math.max(lastEd, ed);
			if(logMINOR) Logger.minor(this, "Latest: "+ed+" in onFoundEdition");
			
			if(!checkStoreOnly) {
				killAttempts = cancelBefore(ed, context);
				USKWatchingKeys.ToFetch list = watchingKeys.getEditionsToFetch(ed, context.random, getRunningFetchEditions());
				Lookup[] toPoll = list.toPoll;
				Lookup[] toFetch = list.toFetch;
				for(Lookup i : toPoll) {
					if(logMINOR) Logger.minor(this, "Polling "+i+" for "+this);
					attemptsToStart.add(add(i, true));	
				}
				for(Lookup i : toFetch) {
					if(logMINOR) Logger.minor(this, "Adding checker for edition "+i+" for "+origUSK);
					attemptsToStart.add(add(i, false));
				}
			}
			registerNow = !fillKeysWatching(ed, context);
			
		}
		finishCancelBefore(killAttempts, context);
		if(registerNow)
			registerAttempts(context);
		synchronized(this) {
			if (decode) {
				lastCompressionCodec = codec;
				lastWasMetadata = metadata;
				if(keepLastData) {
					// FIXME inefficient to convert from bucket to byte[] to bucket
					if(lastRequestData != null)
						lastRequestData.free();
					try {
						lastRequestData = BucketTools.makeImmutableBucket(context.tempBucketFactory, data);
					} catch (IOException e) {
						Logger.error(this, "Caught "+e, e);
					}
				}
			}
		}
	}

	private synchronized ArrayList<Lookup> getRunningFetchEditions() {
		ArrayList<Lookup> ret = new ArrayList<Lookup>();
		for(USKAttempt a : runningAttempts.values()) {
			if(!ret.contains(a.lookup)) ret.add(a.lookup);
		}
		for(USKAttempt a : pollingAttempts.values()) {
			if(!ret.contains(a.lookup)) ret.add(a.lookup);
		}
		return ret;
	}

	private void registerAttempts(ClientContext context) {
		USKAttempt[] attempts;
		synchronized(USKFetcher.this) {
			if(cancelled || completed) return;
			attempts = attemptsToStart.toArray(new USKAttempt[attemptsToStart.size()]);
			attemptsToStart.clear();
		}
		
		if(attempts.length > 0)
			parent.toNetwork(null, context);
		if(logMINOR)
			Logger.minor(this, "Registering "+attempts.length+" USKChecker's for "+this+" running="+runningAttempts.size()+" polling="+pollingAttempts.size());
		for(int i=0;i<attempts.length;i++) {
			long lastEd = uskManager.lookupLatestSlot(origUSK);
			// FIXME not sure this condition works, test it!
			if(keepLastData && lastRequestData == null && lastEd == origUSK.suggestedEdition)
				lastEd--; // If we want the data, then get it for the known edition, so we always get the data, so USKInserter can compare it and return the old edition if it is identical.
			if(attempts[i] == null) continue;
			if(attempts[i].number > lastEd)
				attempts[i].schedule(null, context);
			else {
				synchronized(USKFetcher.this) {
					runningAttempts.remove(attempts[i].number);
				}
			}
		}
	}

	private StoreCheckerGetter runningStoreChecker = null;
	
	class USKStoreChecker {
		
		final USKWatchingKeys.KeyList.StoreSubChecker[] checkers;

		public USKStoreChecker(ArrayList<USKWatchingKeys.KeyList.StoreSubChecker> c) {
			checkers = c.toArray(new USKWatchingKeys.KeyList.StoreSubChecker[c.size()]);
		}

		public USKStoreChecker(USKWatchingKeys.KeyList.StoreSubChecker[] checkers2) {
			checkers = checkers2;
		}

		public Key[] getKeys() {
			if(checkers.length == 0) return new Key[0];
			else if(checkers.length == 1) return checkers[0].keysToCheck;
			else {
				int x = 0;
				for(USKWatchingKeys.KeyList.StoreSubChecker checker : checkers) {
					x += checker.keysToCheck.length;
				}
				Key[] keys = new Key[x];
				int ptr = 0;
				// FIXME more intelligent (cheaper) merging algorithm, e.g. considering the ranges in each.
				HashSet<Key> check = new HashSet<Key>();
				for(USKWatchingKeys.KeyList.StoreSubChecker checker : checkers) {
					if(checker == null) continue;
					for(Key k : checker.keysToCheck) {
						if(!check.add(k)) continue;
						keys[ptr++] = k;
					}
				}
				if(keys.length != ptr) {
					Key[] newKeys = new Key[ptr];
					System.arraycopy(keys, 0, newKeys, 0, ptr);
					keys = newKeys;
				}
				return keys;
			}
		}

		public void checked() {
			for(USKWatchingKeys.KeyList.StoreSubChecker checker : checkers) {
				checker.checked();
			}
		}
		
	}

	private synchronized boolean fillKeysWatching(long ed, ClientContext context) {
		// Do not run a new one until this one has finished. 
		// StoreCheckerGetter itself will automatically call back to fillKeysWatching so there is no chance of losing it.
		if(runningStoreChecker != null) return true;
		final USKStoreChecker checker = watchingKeys.getDatastoreChecker(ed);
		if(checker == null) {
			if(logMINOR) Logger.minor(this, "No datastore checker");
			return false;
		}
			
		runningStoreChecker = new StoreCheckerGetter(parent, checker);
		try {
			context.getSskFetchScheduler().register(null, new SendableGet[] { runningStoreChecker } , false, null, null, false);
		} catch (KeyListenerConstructionException e1) {
			// Impossible
			runningStoreChecker = null;
		} catch (Throwable t) {
			runningStoreChecker = null;
			Logger.error(this, "Unable to start: "+t, t);
			try {
				runningStoreChecker.unregister(null, context, progressPollPriority);
			} catch (Throwable ignored) {
				// Ignore, hopefully it's already unregistered
			}
		}
		return true;
	}
	
	class StoreCheckerGetter extends SendableGet {
		
		public StoreCheckerGetter(ClientRequester parent, USKStoreChecker c) {
			super(parent);
			checker = c;
		}

		public final USKStoreChecker checker;
		
		boolean done = false;

		@Override
		public FetchContext getContext(ObjectContainer container) {
			return ctx;
		}

		@Override
		public long getCooldownWakeup(Object token, ObjectContainer container, ClientContext context) {
			return -1;
		}

		@Override
		public long getCooldownWakeupByKey(Key key, ObjectContainer container, ClientContext context) {
			return -1;
		}

		@Override
		public ClientKey getKey(Object token, ObjectContainer container) {
			return null;
		}

		@Override
		public Key[] listKeys(ObjectContainer container) {
			return checker.getKeys();
		}

		@Override
		public void onFailure(LowLevelGetException e, Object token, ObjectContainer container, ClientContext context) {
			// Ignore
		}

		@Override
		public void requeueAfterCooldown(Key key, long time, ObjectContainer container, ClientContext context) {
			// Ignore
		}

		@Override
		public void preRegister(ObjectContainer container, ClientContext context, boolean toNetwork) {
			unregister(container, context, getPriorityClass(container));
			USKAttempt[] attempts;
			synchronized(USKFetcher.this) {
				if(cancelled) return;
				runningStoreChecker = null;
				// FIXME should we only start the USKAttempt's if the datastore check hasn't made progress?
				attempts = attemptsToStart.toArray(new USKAttempt[attemptsToStart.size()]);
				attemptsToStart.clear();
				done = true;
			}
			checker.checked();
			
			if(logMINOR) Logger.minor(this, "Checked datastore, finishing registration for "+attempts.length+" checkers for "+USKFetcher.this+" for "+origUSK);
			if(attempts.length > 0)
				parent.toNetwork(container, context);
			for(int i=0;i<attempts.length;i++) {
				long lastEd = uskManager.lookupLatestSlot(origUSK);
				// FIXME not sure this condition works, test it!
				if(keepLastData && lastRequestData == null && lastEd == origUSK.suggestedEdition)
					lastEd--; // If we want the data, then get it for the known edition, so we always get the data, so USKInserter can compare it and return the old edition if it is identical.
				if(attempts[i] == null) continue;
				if(attempts[i].number > lastEd)
					attempts[i].schedule(container, context);
				else {
					synchronized(USKFetcher.this) {
						runningAttempts.remove(attempts[i].number);
					}
				}
			}
			long lastEd = uskManager.lookupLatestSlot(origUSK);
			// Do not check beyond WATCH_KEYS after the current slot.
			if(!fillKeysWatching(lastEd, context)) {
				if(checkStoreOnly) {
					if(logMINOR)
						Logger.minor(this, "Just checking store, terminating "+USKFetcher.this+" ...");
					finishSuccess(context);
				}
				// No need to call registerAttempts as we have already registered them.
			}
			
		}

		@Override
		public SendableRequestItem chooseKey(KeysFetchingLocally keys, ObjectContainer container, ClientContext context) {
			return null;
		}

		@Override
		public long countAllKeys(ObjectContainer container, ClientContext context) {
			return watchingKeys.size();
		}

		@Override
		public long countSendableKeys(ObjectContainer container, ClientContext context) {
			return 0;
		}

		@Override
		public RequestClient getClient(ObjectContainer container) {
			return USKFetcher.this.uskManager;
		}

		@Override
		public ClientRequester getClientRequest() {
			return parent;
		}

		@Override
		public short getPriorityClass(ObjectContainer container) {
			return progressPollPriority; // FIXME
		}

		@Override
		public boolean isCancelled(ObjectContainer container) {
			return done;
		}

		@Override
		public boolean isSSK() {
			return true;
		}

		@Override
		public List<PersistentChosenBlock> makeBlocks(PersistentChosenRequest request, RequestScheduler sched, ObjectContainer container, ClientContext context) {
			return null;
		}

		public boolean isEmpty(ObjectContainer container) {
			return done;
		}

		public long getCooldownTime(ObjectContainer container,
				ClientContext context, long now) {
			return 0;
		}
		
	};


	public synchronized boolean isCancelled(ObjectContainer container) {
		return completed || cancelled;
	}

	public KeyListener makeKeyListener(ObjectContainer container, ClientContext context, boolean onStartup) throws KeyListenerConstructionException {
		return this;
	}

	public void onFailed(KeyListenerConstructionException e, ObjectContainer container, ClientContext context) {
		Logger.error(this, "Failed to construct KeyListener on USKFetcher: "+e, e);
	}

	public synchronized long countKeys() {
		return watchingKeys.size();
	}

	public short definitelyWantKey(Key key, byte[] saltedKey, ObjectContainer container, ClientContext context) {
		if(!(key instanceof NodeSSK)) return -1;
		NodeSSK k = (NodeSSK) key;
		if(!Arrays.equals(k.getPubKeyHash(), origUSK.pubKeyHash))
			return -1;
		long lastSlot = uskManager.lookupLatestSlot(origUSK) + 1;
		synchronized(this) {
			if(watchingKeys.match(k, lastSlot) != -1) return progressPollPriority;
		}
		return -1;
	}

	public HasKeyListener getHasKeyListener() {
		return this;
	}

	public short getPriorityClass(ObjectContainer container) {
		return progressPollPriority;
	}

	public SendableGet[] getRequestsForKey(Key key, byte[] saltedKey, ObjectContainer container, ClientContext context) {
		return new SendableGet[0];
	}

	public boolean handleBlock(Key key, byte[] saltedKey, KeyBlock found, ObjectContainer container, ClientContext context) {
		if(!(found instanceof SSKBlock)) return false;
		long lastSlot = uskManager.lookupLatestSlot(origUSK) + 1;
		long edition = watchingKeys.match((NodeSSK)key, lastSlot);
		if(edition == -1) return false;
		if(logMINOR) Logger.minor(this, "Matched edition "+edition+" for "+origUSK);
		
		ClientSSKBlock data;
		try {
			data = watchingKeys.decode((SSKBlock)found, edition);
		} catch (SSKVerifyException e) {
			data = null;
		}
		onSuccess(null, edition, false, data, context);
		return true;
	}

	public synchronized boolean isEmpty() {
		return cancelled || completed;
	}

	public boolean isSSK() {
		return true;
	}

	public void onRemove() {
		// Ignore
	}

	public boolean persistent() {
		return false;
	}

	public boolean probablyWantKey(Key key, byte[] saltedKey) {
		if(!(key instanceof NodeSSK)) return false;
		NodeSSK k = (NodeSSK) key;
		if(!Arrays.equals(k.getPubKeyHash(), origUSK.pubKeyHash))
			return false;
		long lastSlot = uskManager.lookupLatestSlot(origUSK) + 1;
		synchronized(this) {
			return watchingKeys.match(k, lastSlot) != -1;
		}
	}

	/**
	 * Tracks the list of editions that we want to fetch, from various sources - subscribers, origUSK,
	 * last known slot from USKManager, etc.
	 * 
	 * LOCKING: Take the lock on this class last and always pass in lookup values. Do not lookup values
	 * in USKManager inside this class's lock.
	 * @author Matthew Toseland <toad@amphibian.dyndns.org> (0xE43DA450)
	 */
	private class USKWatchingKeys {
		
		// Common for whole USK
		final byte[] pubKeyHash;
		final byte cryptoAlgorithm;
		
		// List of slots since the USKManager's current last known good edition.
		private final KeyList fromLastKnownSlot;
		private TreeMap<Long, KeyList> fromSubscribers;
		private TreeSet<Long> persistentHints = new TreeSet<Long>();
		//private ArrayList<KeyList> fromCallbacks;
		
		// FIXME add more WeakReference<KeyList>'s: one for the origUSK, one for each subscriber who gave an edition number. All of which should disappear on the subscriber going or on the last known superceding.
		
		public USKWatchingKeys(USK origUSK, long lookedUp) {
			this.pubKeyHash = origUSK.pubKeyHash;
			this.cryptoAlgorithm = origUSK.cryptoAlgorithm;
			if(logMINOR) Logger.minor(this, "Creating KeyList from last known good: "+lookedUp);
			fromLastKnownSlot = new KeyList(lookedUp);
			fromSubscribers = new TreeMap<Long, KeyList>();
			if(origUSK.suggestedEdition > lookedUp)
				fromSubscribers.put(origUSK.suggestedEdition, new KeyList(origUSK.suggestedEdition));
		}
		
		class ToFetch {

			public ToFetch(ArrayList<Lookup> toFetch2, ArrayList<Lookup> toPoll2) {
				toFetch = toFetch2.toArray(new Lookup[toFetch2.size()]);
				toPoll = toPoll2.toArray(new Lookup[toPoll2.size()]);
			}
			public final Lookup[] toFetch;
			public final Lookup[] toPoll;
			
		}
		
		/**
		 * Get a bunch of editions to probe for.
		 * @param lookedUp The current best known slot, from USKManager.
		 * @param random The random number generator.
		 * @param alreadyRunning This will be modified: We will remove anything that should still be running from it.
		 * @return Editions to fetch and editions to poll for.
		 */
		public synchronized ToFetch getEditionsToFetch(long lookedUp, Random random, ArrayList<Lookup> alreadyRunning) {
			
			if(logMINOR) Logger.minor(this, "Get editions to fetch, latest slot is "+lookedUp);
			
			ArrayList<Lookup> toFetch = new ArrayList<Lookup>();
			ArrayList<Lookup> toPoll = new ArrayList<Lookup>();
			
			boolean probeFromLastKnownGood = 
				lookedUp > -1 || (backgroundPoll && !firstLoop) || fromSubscribers.isEmpty();
			
			if(probeFromLastKnownGood)
				fromLastKnownSlot.getNextEditions(toFetch, toPoll, lookedUp, alreadyRunning, random);
			
			// If we have moved past the origUSK, then clear the KeyList for it.
			for(Iterator<Entry<Long,KeyList>> it = fromSubscribers.entrySet().iterator();it.hasNext();) {
				Entry<Long,KeyList> entry = it.next();
				long l = entry.getKey();
				if(l <= lookedUp)
					it.remove();
				entry.getValue().getNextEditions(toFetch, toPoll, l, alreadyRunning, random);
			}
			
			// Now getRandomEditions
			// But how many???
			int runningRandom = 0;
			for(Lookup l : alreadyRunning) {
				if(toFetch.contains(l) || toPoll.contains(l)) continue;
				runningRandom++;
			}
			
			int allowedRandom = 2 + 2*fromSubscribers.size();
			if(logMINOR) Logger.minor(this, "Running random requests: "+runningRandom+" total allowed: "+allowedRandom+" looked up is "+lookedUp+" for "+USKFetcher.this);
			
			allowedRandom -= runningRandom;
			
			if(allowedRandom > 0 && probeFromLastKnownGood) {
				fromLastKnownSlot.getRandomEditions(toFetch, lookedUp, alreadyRunning, random, Math.min(2, allowedRandom));
				allowedRandom-=2;
			}
			
			for(Iterator<KeyList> it = fromSubscribers.values().iterator(); allowedRandom >= 2 && it.hasNext();) {
				KeyList k = it.next();
				k.getRandomEditions(toFetch, lookedUp, alreadyRunning, random, Math.min(2, allowedRandom));
				allowedRandom -= 2;
			}
			
			return new ToFetch(toFetch, toPoll);
		}

		public synchronized void updateSubscriberHints(Long[] hints, long lookedUp) {
			ArrayList<Long> surviving = new ArrayList<Long>();
			Arrays.sort(hints);
			long prev = -1;
			for(Long hint : hints) {
				if(hint == prev) continue;
				prev = hint;
				if(hint <= lookedUp) continue;
				surviving.add(hint);
			}
			for(Iterator<Long> i = persistentHints.iterator();i.hasNext();) {
				Long hint = i.next();
				if(hint <= lookedUp) {
					i.remove();
				}
				if(surviving.contains(hint)) continue;
				surviving.add(hint);
			}
			if(origUSK.suggestedEdition > lookedUp && !surviving.contains(origUSK.suggestedEdition))
				surviving.add(origUSK.suggestedEdition);
			for(Iterator<Long> it = fromSubscribers.keySet().iterator();it.hasNext();) {
				Long l = it.next();
				if(surviving.contains(l)) continue;
				it.remove();
			}
			for(Long l : surviving) {
				if(fromSubscribers.containsKey(l)) continue;
				fromSubscribers.put(l, new KeyList(l));
			}
		}
		
		public synchronized void addHintEdition(long suggestedEdition, long lookedUp) {
			if(suggestedEdition <= lookedUp) return;
			if(!persistentHints.add(suggestedEdition)) return;
			if(fromSubscribers.containsKey(suggestedEdition)) return;
			fromSubscribers.put(suggestedEdition, new KeyList(suggestedEdition));
		}
		
		public synchronized long size() {
			return WATCH_KEYS + fromSubscribers.size() * WATCH_KEYS; // FIXME take overlap into account
		}

		/** A precomputed list of E(H(docname))'s for each slot we might match.
		 * This is from an edition number which might be out of date. */
		class KeyList {

			/** The USK edition number of the first slot */
			long firstSlot;
			/** The precomputed E(H(docname)) for each such slot. */
			private WeakReference<RemoveRangeArrayList<byte[]>> cache;
			/** We have checked the datastore from this point. */
			private long checkedDatastoreFrom = -1;
			/** We have checked the datastore up to this point. */
			private long checkedDatastoreTo = -1;
			
			public KeyList(long slot) {
				if(logMINOR) Logger.minor(this, "Creating KeyList from "+slot+" on "+USKFetcher.this+" "+this, new Exception("debug"));
				firstSlot = slot;
				RemoveRangeArrayList<byte[]> ehDocnames = new RemoveRangeArrayList<byte[]>(WATCH_KEYS);
				cache = new WeakReference<RemoveRangeArrayList<byte[]>>(ehDocnames);
				generate(firstSlot, WATCH_KEYS, ehDocnames);
			}

			/** Add the next bunch of editions to fetch to toFetch and toPoll. If they are already running,
			 * REMOVE THEM from the alreadyRunning array.
			 * @param toFetch
			 * @param toPoll
			 * @param lookedUp
			 * @param alreadyRunning
			 * @param random
			 */
			public synchronized void getNextEditions(ArrayList<Lookup> toFetch, ArrayList<Lookup> toPoll, long lookedUp, ArrayList<Lookup> alreadyRunning, Random random) {
				if(logMINOR) Logger.minor(this, "Getting next editions from "+lookedUp);
				if(lookedUp < 0) lookedUp = 0;
				for(int i=0;i<origMinFailures;i++) {
					long ed = i + lookedUp;
					Lookup l = new Lookup();
					l.val = ed;
					boolean poll = backgroundPoll;
					if(((!poll) && toFetch.contains(l)) || (poll && toPoll.contains(l)))
						continue;
					if(alreadyRunning.contains(l)) {
						alreadyRunning.remove(l);
						continue;
					}
					ClientSSK key;
					// FIXME reuse ehDocnames somehow
					// The problem is we need a ClientSSK for the high level stuff.
					key = origUSK.getSSK(ed);
					l.key = key;
					l.ignoreStore = true;
					if(poll) {
						if(!toPoll.contains(l))
							toPoll.add(l);
					} else {
						if(!toFetch.contains(l))
							toFetch.add(l);
					}
				}
			}
			
			public synchronized void getRandomEditions(ArrayList<Lookup> toFetch, long lookedUp, ArrayList<Lookup> alreadyRunning, Random random, int allowed) {
				// Then add a couple of random editions for catch-up.
				long baseEdition = lookedUp + origMinFailures;
				for(int i=0;i<allowed;i++) {
					while(true) {
						// Geometric distribution.
						// 20% chance of mean 100, 80% chance of mean 10. Thanks evanbd.
						int mean = random.nextInt(5) == 0 ? 100 : 10;
						long fetch = baseEdition + (long)Math.floor(Math.log(random.nextFloat()) / Math.log(1.0 - 1.0/mean));
						if(fetch < baseEdition) continue;
						Lookup l = new Lookup();
						l.val = fetch;
						if(toFetch.contains(l)) continue;
						if(alreadyRunning.contains(l)) continue;
						l.key = origUSK.getSSK(fetch);
						l.ignoreStore = !(fetch - lookedUp >= WATCH_KEYS);
						toFetch.add(l);
						if(logMINOR) Logger.minor(this, "Trying random future edition "+fetch+" for "+origUSK+" current edition "+lookedUp);
						break;
					}
				}
			}

			public class StoreSubChecker {
				
				/** Keys to check */
				final NodeSSK[] keysToCheck;
				/** The edition from which we will have checked after we have executed this. */
				private final long checkedFrom;
				/** The edition up to which we have checked after we have executed this. */
				private final long checkedTo;
				
				private StoreSubChecker(NodeSSK[] keysToCheck, long checkFrom, long checkTo) {
					this.keysToCheck = keysToCheck;
					this.checkedFrom = checkFrom;
					this.checkedTo = checkTo;
					if(logMINOR) Logger.minor(this, "Checking datastore from "+checkFrom+" to "+checkTo+" for "+USKFetcher.this+" on "+this);
				}

				/** The keys have been checked. */
				void checked() {
					synchronized(KeyList.this) {
						if(checkedDatastoreTo >= checkedFrom && checkedDatastoreFrom <= checkedFrom) {
							// checkedFrom is unchanged
							checkedDatastoreTo = checkedTo;
						} else {
							checkedDatastoreFrom = checkedFrom;
							checkedDatastoreTo = checkedTo;
						}
						if(logMINOR) Logger.minor(this, "Checked from "+checkedFrom+" to "+checkedTo+" (now overall is "+checkedDatastoreFrom+" to "+checkedDatastoreTo+") for "+USKFetcher.this+" for "+origUSK);
					}
					
				}
				
			}

			/**
			 * Check for WATCH_KEYS from lastSlot, but do not check any slots earlier than checkedDatastoreUpTo.
			 * Re-use the cache if possible, and extend it if necessary; all we need to construct a NodeSSK is the base data and the E(H(docname)), and we have that.
			 */
			public synchronized StoreSubChecker checkStore(long lastSlot) {
				if(logDEBUG) Logger.minor(this, "check store from "+lastSlot+" current first slot "+firstSlot);
				long checkFrom = lastSlot;
				long checkTo = lastSlot + WATCH_KEYS;
				if(checkedDatastoreTo >= checkFrom) {
					checkFrom = checkedDatastoreTo;
				}
				if(checkFrom >= checkTo) return null; // Nothing to check.
				// Update the cache.
				RemoveRangeArrayList<byte[]> ehDocnames = updateCache(lastSlot);
				// Now create NodeSSK[] from the part of the cache that
				// ehDocnames[0] is firstSlot
				// ehDocnames[checkFrom-firstSlot] is checkFrom
				int offset = (int)(checkFrom - firstSlot);
				NodeSSK[] keysToCheck = new NodeSSK[WATCH_KEYS - offset];
				for(int x=0, i=offset; i<WATCH_KEYS; i++, x++) {
					keysToCheck[x] = new NodeSSK(pubKeyHash, ehDocnames.get(i), cryptoAlgorithm);
				}
				return new StoreSubChecker(keysToCheck, checkFrom, checkTo);
			}

			synchronized RemoveRangeArrayList<byte[]> updateCache(long curBaseEdition) {
				if(logMINOR) Logger.minor(this, "update cache from "+curBaseEdition+" current first slot "+firstSlot);
				RemoveRangeArrayList<byte[]> ehDocnames = null;
				if(cache == null || (ehDocnames = cache.get()) == null) {
					ehDocnames = new RemoveRangeArrayList<byte[]>(WATCH_KEYS);
					cache = new WeakReference<RemoveRangeArrayList<byte[]>>(ehDocnames);
					firstSlot = curBaseEdition;
					if(logMINOR) Logger.minor(this, "Regenerating because lost cached keys");
					generate(firstSlot, WATCH_KEYS, ehDocnames);
					return ehDocnames;
				}
				match(null, curBaseEdition, ehDocnames);
				return ehDocnames;
			}
			
			/** Update the key list if necessary based on the new base edition.
			 * Then try to match the given key. If it matches return the edition number.
			 * @param key The key we are trying to match. If null, just update the cache, do not 
			 * do any matching (used by checkStore(); it is only necessary to update the cache 
			 * if you are actually going to use it).
			 * @param curBaseEdition The new base edition.
			 * @return The edition number for the key, or -1 if the key is not a match.
			 */
			public synchronized long match(NodeSSK key, long curBaseEdition) {
				if(logDEBUG) Logger.minor(this, "match from "+curBaseEdition+" current first slot "+firstSlot);
				RemoveRangeArrayList<byte[]> ehDocnames = null;
				if(cache == null || (ehDocnames = cache.get()) == null) {
					ehDocnames = new RemoveRangeArrayList<byte[]>(WATCH_KEYS);
					cache = new WeakReference<RemoveRangeArrayList<byte[]>>(ehDocnames);
					firstSlot = curBaseEdition;
					generate(firstSlot, WATCH_KEYS, ehDocnames);
					return key == null ? -1 : innerMatch(key, ehDocnames, 0, ehDocnames.size(), firstSlot);
				}
				// Might as well check first.
				long x = innerMatch(key, ehDocnames, 0, ehDocnames.size(), firstSlot);
				if(x != -1) return x;
				return match(key, curBaseEdition, ehDocnames);
			}

			/** Update ehDocnames as needed according to the new curBaseEdition, then innerMatch against *only
			 * the changed parts*. The caller must already have done innerMatch over the passed in ehDocnames.
			 * @param curBaseEdition The edition to check from. If this is different to firstSlot, we will
			 * update ehDocnames. */
			private long match(NodeSSK key, long curBaseEdition, RemoveRangeArrayList<byte[]> ehDocnames) {
				if(logMINOR) Logger.minor(this, "Matching "+key+" cur base edition "+curBaseEdition+" first slot was "+firstSlot);
				if(firstSlot < curBaseEdition) {
					if(firstSlot + ehDocnames.size() <= curBaseEdition) {
						// No overlap. Clear it and start again.
						ehDocnames.clear();
						firstSlot = curBaseEdition;
						generate(curBaseEdition, WATCH_KEYS, ehDocnames);
						return key == null ? -1 : innerMatch(key, ehDocnames, 0, ehDocnames.size(), firstSlot);
					} else {
						// There is some overlap. Delete the first part of the array then add stuff at the end.
						// ehDocnames[i] is slot firstSlot + i
						// We want to get rid of anything before curBaseEdition
						// So the first slot that is useful is the slot at i = curBaseEdition - firstSlot
						// Which is the new [0], whose edition is curBaseEdition
						ehDocnames.removeRange(0, (int)(curBaseEdition - firstSlot));
						int size = ehDocnames.size();
						firstSlot = curBaseEdition;
						generate(curBaseEdition + size, WATCH_KEYS - size, ehDocnames);
						return key == null ? -1 : innerMatch(key, ehDocnames, WATCH_KEYS - size, size, firstSlot);
					}
				} else if(firstSlot > curBaseEdition) {
					// It has regressed???
					Logger.error(this, "First slot was "+firstSlot+" now is "+curBaseEdition+" on "+USKFetcher.this+" "+this, new Exception("debug"));
					firstSlot = curBaseEdition;
					ehDocnames.clear();
					generate(curBaseEdition, WATCH_KEYS, ehDocnames);
					return key == null ? -1 : innerMatch(key, ehDocnames, 0, ehDocnames.size(), firstSlot);
				}
				return -1;
			}

			/** Do the actual match, using the current firstSlot, and a specified offset and length within the array. */
			private long innerMatch(NodeSSK key, RemoveRangeArrayList<byte[]> ehDocnames, int offset, int size, long firstSlot) {
				byte[] data = key.getKeyBytes();
				for(int i=offset;i<(offset+size);i++) {
					if(Arrays.equals(data, ehDocnames.get(i))) {
						if(logMINOR) Logger.minor(this, "Found edition "+(firstSlot+i)+" for "+origUSK);
						return firstSlot+i;
					}
				}
				return -1;
			}

			/** Append a series of E(H(docname))'s to the array.
			 * @param baseEdition The edition to start from.
			 * @param keys The number of keys to add.
			 */
			private void generate(long baseEdition, int keys, RemoveRangeArrayList<byte[]> ehDocnames) {
				if(logMINOR) Logger.minor(this, "generate() from "+baseEdition+" for "+origUSK);
				assert(baseEdition >= 0);
				for(int i=0;i<keys;i++) {
					long ed = baseEdition + i;
					ehDocnames.add(origUSK.getSSK(ed).ehDocname);
				}
			}

		}
		
		public synchronized USKStoreChecker getDatastoreChecker(long lastSlot) {
			// Check WATCH_KEYS from last known good slot.
			// FIXME: Take into account origUSK, subscribers, etc.
			if(logMINOR) Logger.minor(this, "Getting datastore checker from "+lastSlot+" for "+origUSK+" on "+USKFetcher.this, new Exception("debug"));
			ArrayList<KeyList.StoreSubChecker> checkers = new ArrayList<KeyList.StoreSubChecker>();
			KeyList.StoreSubChecker c = fromLastKnownSlot.checkStore(lastSlot+1);
			if(c != null) checkers.add(c);
			// If we have moved past the origUSK, then clear the KeyList for it.
			for(Iterator<Entry<Long,KeyList>> it = fromSubscribers.entrySet().iterator(); it.hasNext(); ) {
				Entry<Long,KeyList> entry = it.next();
				long l = entry.getKey();
				if(l <= lastSlot)
					it.remove();
				c = entry.getValue().checkStore(l);
				if(c != null) checkers.add(c);
			}
			if(checkers.size() > 0)
				return new USKStoreChecker(checkers);
			else return null;
		}

		public ClientSSKBlock decode(SSKBlock block, long edition) throws SSKVerifyException {
			ClientSSK csk = origUSK.getSSK(edition);
			assert(Arrays.equals(csk.ehDocname, block.getKey().getKeyBytes()));
			return ClientSSKBlock.construct(block, csk);
		}
		
		public synchronized long match(NodeSSK key, long lastSlot) {
			long ret = fromLastKnownSlot.match(key, lastSlot);
			if(ret != -1) return ret;
			
			for(Iterator<Entry<Long,KeyList>> it = fromSubscribers.entrySet().iterator(); it.hasNext(); ) {
				Entry<Long,KeyList> entry = it.next();
				long l = entry.getKey();
				if(l <= lastSlot)
					it.remove();
				ret = entry.getValue().match(key, l);
				if(ret != -1) return ret;
			}
			return -1;
		}

	}

	public void addHintEdition(long suggestedEdition) {
		watchingKeys.addHintEdition(suggestedEdition, uskManager.lookupLatestSlot(origUSK));
	}
	
	private class Lookup {
		long val;
		ClientSSK key;
		boolean ignoreStore;
		
		public boolean equals(Object o) {
			if(o instanceof Lookup) {
				return ((Lookup)o).val == val;
			} else return false;
		}
		
		public String toString() {
			return origUSK+":"+val;
		}
		
	}
	
}
