package freenet.clients.http;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.SocketException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import com.db4o.ObjectContainer;

import freenet.client.DefaultMIMETypes;
import freenet.client.FetchContext;
import freenet.client.FetchException;
import freenet.client.FetchResult;
import freenet.client.HighLevelSimpleClient;
import freenet.client.async.ClientContext;
import freenet.client.filter.ContentFilter;
import freenet.client.filter.FoundURICallback;
import freenet.client.filter.MIMEType;
import freenet.client.filter.PushingTagReplacerCallback;
import freenet.client.filter.UnsafeContentTypeException;
import freenet.clients.http.ajaxpush.DismissAlertToadlet;
import freenet.clients.http.ajaxpush.LogWritebackToadlet;
import freenet.clients.http.ajaxpush.PushDataToadlet;
import freenet.clients.http.ajaxpush.PushFailoverToadlet;
import freenet.clients.http.ajaxpush.PushKeepaliveToadlet;
import freenet.clients.http.ajaxpush.PushLeavingToadlet;
import freenet.clients.http.ajaxpush.PushNotificationToadlet;
import freenet.clients.http.ajaxpush.PushTesterToadlet;
import freenet.clients.http.bookmark.BookmarkManager;
import freenet.clients.http.updateableelements.ProgressBarElement;
import freenet.clients.http.updateableelements.ProgressInfoElement;
import freenet.config.Config;
import freenet.config.SubConfig;
import freenet.crypt.SHA256;
import freenet.keys.FreenetURI;
import freenet.keys.USK;
import freenet.l10n.NodeL10n;
import freenet.node.Node;
import freenet.node.NodeClientCore;
import freenet.node.RequestClient;
import freenet.node.RequestStarter;
import freenet.node.SecurityLevels.NETWORK_THREAT_LEVEL;
import freenet.node.SecurityLevels.PHYSICAL_THREAT_LEVEL;
import freenet.pluginmanager.PluginInfoWrapper;
import freenet.support.HTMLEncoder;
import freenet.support.HTMLNode;
import freenet.support.HexUtil;
import freenet.support.LogThresholdCallback;
import freenet.support.Logger;
import freenet.support.MultiValueTable;
import freenet.support.SizeUtil;
import freenet.support.URIPreEncoder;
import freenet.support.URLEncoder;
import freenet.support.Logger.LogLevel;
import freenet.support.api.Bucket;
import freenet.support.api.BucketFactory;
import freenet.support.api.HTTPRequest;
import freenet.support.io.BucketTools;
import freenet.support.io.Closer;
import freenet.support.io.FileUtil;
import freenet.support.io.NoFreeBucket;

public final class FProxyToadlet extends Toadlet implements RequestClient {
	
	private static byte[] random;
	final NodeClientCore core;
	final ClientContext context;
	final FProxyFetchTracker fetchTracker;
	
	private static FoundURICallback prefetchHook;
	static final Set<String> prefetchAllowedTypes = new HashSet<String>();
	static {
		// Only valid inlines
		prefetchAllowedTypes.add("image/png");
		prefetchAllowedTypes.add("image/jpeg");
		prefetchAllowedTypes.add("image/gif");
	}
	
	// ?force= links become invalid after 2 hours.
	private static final long FORCE_GRAIN_INTERVAL = 60*60*1000;
	/** Maximum size for transparent pass-through, should be a config option */
	public static long MAX_LENGTH = (2*1024*1024 * 11) / 10; // 2MB plus a bit due to buggy inserts
	
	static final URI welcome;
	public static final short PRIORITY = RequestStarter.INTERACTIVE_PRIORITY_CLASS;
	static {
		try {
			welcome = new URI("/welcome/");
		} catch (URISyntaxException e) {
			throw new Error("Broken URI constructor: "+e, e);
		}
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
	
	public FProxyToadlet(final HighLevelSimpleClient client, NodeClientCore core, FProxyFetchTracker tracker) {
		super(client);
		client.setMaxLength(MAX_LENGTH);
		client.setMaxIntermediateLength(MAX_LENGTH);
		this.core = core;
		this.context = core.clientContext;
		prefetchHook = new FoundURICallback() {

				public void foundURI(FreenetURI uri) {
					// Ignore
				}
				
				public void foundURI(FreenetURI uri, boolean inline) {
					if(!inline) return;
					if(logMINOR) Logger.minor(this, "Prefetching "+uri);
					client.prefetch(uri, 60*1000, 512*1024, prefetchAllowedTypes);
				}

				public void onText(String text, String type, URI baseURI) {
					// Ignore
				}
				
			};
		fetchTracker = tracker;
	}

	public void handleMethodPOST(URI uri, HTTPRequest req, ToadletContext ctx) throws ToadletContextClosedException, IOException, RedirectException {
		String ks = uri.getPath();
		
		if (ks.equals("/")||ks.startsWith("/servlet/")) {
			try {
	            throw new RedirectException("/welcome/");
			} catch (URISyntaxException e) {
				// HUH!?!
			}
		}		
	}

	public static void handleDownload(ToadletContext context, Bucket data, BucketFactory bucketFactory, String mimeType, String requestedMimeType, String forceString, boolean forceDownload, String basePath, FreenetURI key, String extras, String referrer, boolean downloadLink, ToadletContext ctx, NodeClientCore core, boolean dontFreeData, String maybeCharset) throws ToadletContextClosedException, IOException {
		ToadletContainer container = context.getContainer();
		if(logMINOR)
			Logger.minor(FProxyToadlet.class, "handleDownload(data.size="+data.size()+", mimeType="+mimeType+", requestedMimeType="+requestedMimeType+", forceDownload="+forceDownload+", basePath="+basePath+", key="+key);
		String extrasNoMime = extras; // extras will not include MIME type to start with - REDFLAG maybe it should be an array
		if(requestedMimeType != null) {
			if(mimeType == null || !requestedMimeType.equals(mimeType)) {
				if(extras == null) extras = "";
				extras = extras + "&type=" + requestedMimeType;
			}
		}
		long size = data.size();
		
		long now = System.currentTimeMillis();
		boolean force = false;
		if(forceString != null) {
			if(forceString.equals(getForceValue(key, now)) || 
					forceString.equals(getForceValue(key, now-FORCE_GRAIN_INTERVAL)))
				force = true;
		}

		if((!force) && (!forceDownload)) {
			//Horrible hack needed for GWT as it relies on document.write() which is not supported in xhtml
			if(mimeType.compareTo("application/xhtml+xml")==0){
				mimeType="text/html";
			}
			if(horribleEvilHack(data) && !(mimeType.startsWith("application/rss+xml"))) {
				PageNode page = context.getPageMaker().getPageNode(l10n("dangerousRSSTitle"), context);
				HTMLNode pageNode = page.outer;
				HTMLNode contentNode = page.content;
				
				HTMLNode infobox = contentNode.addChild("div", "class", "infobox infobox-alert");
				infobox.addChild("div", "class", "infobox-header", l10n("dangerousRSSSubtitle"));
				HTMLNode infoboxContent = infobox.addChild("div", "class", "infobox-content");
				infoboxContent.addChild("#", NodeL10n.getBase().getString("FProxyToadlet.dangerousRSS", new String[] { "type" }, new String[] { mimeType }));
				infoboxContent.addChild("p", l10n("options"));
				HTMLNode optionList = infoboxContent.addChild("ul");
				HTMLNode option = optionList.addChild("li");
				
				NodeL10n.getBase().addL10nSubstitution(option, "FProxyToadlet.openPossRSSAsPlainText", new String[] { "link", "bold" },
						new HTMLNode[] {
						HTMLNode.link(basePath+key.toString()+"?type=text/plain&force="+getForceValue(key,now)+extrasNoMime),
						HTMLNode.STRONG
				});
				// 	FIXME: is this safe? See bug #131
				option = optionList.addChild("li");
				NodeL10n.getBase().addL10nSubstitution(option, "FProxyToadlet.openPossRSSForceDisk", new String[] { "link", "bold" },
						new HTMLNode[] {
						HTMLNode.link(basePath+key.toString()+"?forcedownload"+extras),
						HTMLNode.STRONG
				});
				boolean mimeRSS = mimeType.startsWith("application/xml+rss") || mimeType.startsWith("text/xml"); /* blergh! */
				if(!(mimeRSS || mimeType.startsWith("text/plain"))) {
					option = optionList.addChild("li");
					NodeL10n.getBase().addL10nSubstitution(option, "FProxyToadlet.openRSSForce", new String[] { "link", "bold", "mime" },
							new HTMLNode[] {
							HTMLNode.link(basePath+key.toString()+"?force="+getForceValue(key, now)+extras), HTMLNode.STRONG, HTMLNode.text(mimeType) });
				}
				option = optionList.addChild("li");
				NodeL10n.getBase().addL10nSubstitution(option, "FProxyToadlet.openRSSAsRSS", new String[] { "link", "bold" },
						new HTMLNode[] {
						HTMLNode.link(basePath + key.toString() + "?type=application/xml+rss&force=" + getForceValue(key, now)+extrasNoMime),
						HTMLNode.STRONG });
				if(referrer != null) {
					option = optionList.addChild("li");
					NodeL10n.getBase().addL10nSubstitution(option, "FProxyToadlet.backToReferrer", new String[] { "link" },
							new HTMLNode[] { HTMLNode.link(referrer) });
				}
				option = optionList.addChild("li");
				NodeL10n.getBase().addL10nSubstitution(option, "FProxyToadlet.backToFProxy", new String[] { "link" },
						new HTMLNode[] { HTMLNode.link("/") });
				
				byte[] pageBytes = pageNode.generate().getBytes("UTF-8");
				context.sendReplyHeaders(200, "OK", new MultiValueTable<String, String>(), "text/html; charset=utf-8", pageBytes.length);
				context.writeData(pageBytes);
				return;
			}
		}
		
		if (forceDownload) {
			MultiValueTable<String, String> headers = new MultiValueTable<String, String>();
			headers.put("Content-Disposition", "attachment; filename=\"" + key.getPreferredFilename() + '"');
			headers.put("Cache-Control", "private");
			headers.put("Content-Transfer-Encoding", "binary");
			// really the above should be enough, but ...
			// was application/x-msdownload, but some unix browsers offer to open that in Wine as default!
			// it is important that this type not be understandable, but application/octet-stream doesn't work.
			// see http://onjava.com/pub/a/onjava/excerpt/jebp_3/index3.html
			// Testing on FF3.5.1 shows that application/x-force-download wants to run it in wine, 
			// whereas application/force-download wants to save it.
			context.sendReplyHeaders(200, "OK", headers, "application/force-download", data.size());
			context.writeData(data);
		} else {
			// Send the data, intact
			MultiValueTable<String, String> hdr = context.getHeaders();
			String rangeStr = hdr.get("range");
			// was a range request
			if (rangeStr != null) {
				
				long range[];
				try {
					range = parseRange(rangeStr);
				} catch (HTTPRangeException e) {
					ctx.sendReplyHeaders(416, "Requested Range Not Satisfiable", null, null, 0);
					return;
				}
				if (range[1] == -1 || range[1] >= data.size()) {
					range[1] = data.size() - 1;
				}
				InputStream is = null;
				OutputStream os = null;
				Bucket tmpRange = bucketFactory.makeBucket(range[1] - range[0]);
				try {
					is = data.getInputStream();
					os = tmpRange.getOutputStream();
					if (range[0] > 0)
						FileUtil.skipFully(is, range[0]);
					FileUtil.copy(is, os, range[1] - range[0] + 1);
					os.close();
					os = null;
					is.close();
					is = null;
					// FIXME catch IOException here and tell the user there is a problem instead of just closing the connection.
					// Currently there is no way to tell the difference between an IOE caused by the connection to the client and an internal one, we just close the connection in both cases.
				} finally {
					Closer.close(is);
					Closer.close(os);
				}
				MultiValueTable<String, String> retHdr = new MultiValueTable<String, String>();
				retHdr.put("Content-Range", "bytes " + range[0] + "-" + range[1] + "/" + data.size());
				context.sendReplyHeaders(206, "Partial content", retHdr, mimeType, tmpRange.size());
				context.writeData(tmpRange);
			} else {
				context.sendReplyHeaders(200, "OK", new MultiValueTable<String, String>(), mimeType, data.size());
				context.writeData(data);
			}
		}
	}
	
	static final HTMLNode DOWNLOADS_LINK = QueueToadlet.DOWNLOADS_LINK;

	private static void addDownloadOptions(ToadletContext ctx, HTMLNode optionList, FreenetURI key, String mimeType, boolean disableFiltration, boolean dontShowFilter, NodeClientCore core) {
		PHYSICAL_THREAT_LEVEL threatLevel = core.node.securityLevels.getPhysicalThreatLevel();
		NETWORK_THREAT_LEVEL netLevel = core.node.securityLevels.getNetworkThreatLevel();
		boolean filterChecked = !(((threatLevel == PHYSICAL_THREAT_LEVEL.LOW && netLevel == NETWORK_THREAT_LEVEL.LOW)) || disableFiltration);
		if((filterChecked) && mimeType != null && !mimeType.equals("application/octet-stream") && !mimeType.equals("")) {
			MIMEType type = ContentFilter.getMIMEType(mimeType);
			if((type == null || (!(type.safeToRead || type.readFilter != null))) && !(threatLevel == PHYSICAL_THREAT_LEVEL.HIGH || threatLevel == PHYSICAL_THREAT_LEVEL.MAXIMUM || netLevel == NETWORK_THREAT_LEVEL.HIGH || netLevel == NETWORK_THREAT_LEVEL.MAXIMUM))
				filterChecked = false;
		}
		if(threatLevel != PHYSICAL_THREAT_LEVEL.MAXIMUM) {
			HTMLNode option = optionList.addChild("li");
			HTMLNode optionForm = ctx.addFormChild(option, "/downloads/", "tooBigQueueForm");
			optionForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "hidden", "key", key.toString() });
			optionForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "hidden", "return-type", "disk" });
			optionForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "hidden", "persistence", "forever" });
			if (mimeType != null && !mimeType.equals("")) {
				optionForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "hidden", "type", mimeType });
			}
			optionForm.addChild("input", new String[] { "type", "name", "value" },
					new String[] { "submit", "download", l10n("downloadInBackgroundToDiskButton") });
			NodeL10n.getBase().addL10nSubstitution(optionForm, "FProxyToadlet.downloadInBackgroundToDisk",
					new String[] { "dir", "page" },
					new HTMLNode[] { HTMLNode.text(core.getDownloadDir().getAbsolutePath()), DOWNLOADS_LINK });
			if(!dontShowFilter) {
				HTMLNode filterControl = optionForm.addChild("div", l10n("filterData"));
				HTMLNode f = filterControl.addChild("input", new String[] { "type", "name", "value" }, new String[] { "checkbox", "filterData", "filterData"});
				if(filterChecked) f.addAttribute("checked", "checked");
				filterControl.addChild("div", l10n("filterDataMessage"));
			}
			if (threatLevel == PHYSICAL_THREAT_LEVEL.HIGH) {
				optionForm.addChild("br");
				NodeL10n.getBase().addL10nSubstitution(optionForm, "FProxyToadlet.downloadToDiskSecurityWarning",
						new String[] {"bold" }, new HTMLNode[] { HTMLNode.STRONG });
				//optionForm.addChild("#", l10n("downloadToDiskSecurityWarning") + " ");
			}
		}
		
		if(threatLevel == PHYSICAL_THREAT_LEVEL.HIGH || threatLevel == PHYSICAL_THREAT_LEVEL.MAXIMUM) {
			HTMLNode option = optionList.addChild("li");
			HTMLNode optionForm = ctx.addFormChild(option, "/downloads/", "tooBigQueueForm");
			optionForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "hidden", "key", key.toString() });
			optionForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "hidden", "return-type", "direct" });
			optionForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "hidden", "persistence", "forever" });
			if (mimeType != null && !mimeType.equals("")) {
				optionForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "hidden", "type", mimeType });
			}
			optionForm.addChild("input", new String[] { "type", "name", "value" },
					new String[] { "submit", "download", l10n("downloadInBackgroundToTempSpaceButton") });
			HTMLNode filterControl = optionForm.addChild("div", l10n("filterData"));
			HTMLNode f = filterControl.addChild("input", new String[] { "type", "name", "value", "checked" }, new String[] { "checkbox", "filterData", "filterData", "checked"});
			if(filterChecked) f.addAttribute("checked", "checked");
			filterControl.addChild("div", l10n("filterDataMessage"));
			NodeL10n.getBase().addL10nSubstitution(optionForm, "FProxyToadlet.downloadInBackgroundToTempSpace",
					new String[] { "page", "bold" }, new HTMLNode[] { DOWNLOADS_LINK, HTMLNode.STRONG });
		}
	}
	
	public static String l10n(String msg) {
		return NodeL10n.getBase().getString("FProxyToadlet."+msg);
	}

	/** Does the first 512 bytes of the data contain anything that Firefox might regard as RSS?
	 * This is a horrible evil hack; we shouldn't be doing blacklisting, we should be doing whitelisting.
	 * REDFLAG Expect future security issues! 
	 * @throws IOException */
	private static boolean horribleEvilHack(Bucket data) throws IOException {
		DataInputStream is = null;
		try {
			int sz = (int) Math.min(data.size(), 512);
			if(sz == 0)
				return false;
			is = new DataInputStream(data.getInputStream());
			byte[] buf = new byte[sz];
			// FIXME Fortunately firefox doesn't detect RSS in UTF16 etc ... yet
			is.readFully(buf);
			/**
		 * Look for any of the following strings:
		 * <rss
		 * &lt;feed
		 * &lt;rdf:RDF
		 * 
		 * If they start at the beginning of the file, or are preceded by one or more &lt;! or &lt;? tags,
		 * then firefox will read it as RSS. In which case we must force it to be downloaded to disk. 
		 */
			if(checkForString(buf, "<rss"))
				return true;
			if(checkForString(buf, "<feed"))
				return true;
			if(checkForString(buf, "<rdf:RDF"))
				return true;
		}
		finally {
			Closer.close(is);
		}
		return false;
	}

	/** Scan for a US-ASCII (byte = char) string within a given buffer of possibly binary data */
	private static boolean checkForString(byte[] buf, String find) {
		int offset = 0;
		int bufProgress = 0;
		while(offset < buf.length) {
			byte b = buf[offset];
			if(b == find.charAt(bufProgress)) {
				bufProgress++;
				if(bufProgress == find.length()) return true;
				offset++;
			} else {
				if(bufProgress == 0)
					offset++; // Try the next byte.
				else
					bufProgress = 0; // Reset to the first char of the keyword.
			}
		}
		return false;
	}

	public void handleMethodGET(URI uri, HTTPRequest httprequest, ToadletContext ctx) 
	throws ToadletContextClosedException, IOException, RedirectException {
		innerHandleMethodGET(uri, httprequest, ctx, 0);
	}
	
	static final int MAX_RECURSION = 5;
	
	private void innerHandleMethodGET(URI uri, HTTPRequest httprequest, ToadletContext ctx, int recursion) 
			throws ToadletContextClosedException, IOException, RedirectException {

		String ks = uri.getPath();
		
		if (ks.equals("/")) {
			if (httprequest.isParameterSet("key")) {
				String k = httprequest.getParam("key");
				FreenetURI newURI;
				try {
					newURI = new FreenetURI(k);
				} catch (MalformedURLException e) {
					Logger.normal(this, "Invalid key: "+e+" for "+k, e);
					sendErrorPage(ctx, 404, l10n("notFoundTitle"), NodeL10n.getBase().getString("FProxyToadlet.invalidKeyWithReason", new String[] { "reason" }, new String[] { e.toString() }));
					return;
				}
				
				if(logMINOR) Logger.minor(this, "Redirecting to FreenetURI: "+newURI);
				String requestedMimeType = httprequest.getParam("type");
				long maxSize = httprequest.getLongParam("max-size", MAX_LENGTH);
				String location = getLink(newURI, requestedMimeType, maxSize, httprequest.getParam("force", null), httprequest.isParameterSet("forcedownload"));
				writeTemporaryRedirect(ctx, null, location);
				return;
			}
			
			try {
				String querystring = uri.getQuery();
				
				if (querystring == null) {
					throw new RedirectException(welcome);
				} else {
					// TODP possibly a proper URLEncode method
					querystring = querystring.replace(' ', '+');
					throw new RedirectException("/welcome/?" + querystring);
				}
			} catch (URISyntaxException e) {
				// HUH!?!
			}
		}else if(ks.equals("/favicon.ico")){
			byte[] buf = new byte[1024];
			int len;
			InputStream strm = getClass().getResourceAsStream("staticfiles/favicon.ico");
			
			try {
				if (strm == null) {
					this.sendErrorPage(ctx, 404, l10n("pathNotFoundTitle"), l10n("pathNotFound"));
					return;
				}
				ctx.sendReplyHeaders(200, "OK", null, "image/x-icon", strm.available());
				
				while ( (len = strm.read(buf)) > 0) {
					ctx.writeData(buf, 0, len);
				}
			} finally {
				strm.close();
			}
			return;
		} else if(ks.startsWith("/feed/") || ks.equals("/feed")) {
			//TODO Better way to find the host. Find if https is used?
			String host = ctx.getHeaders().get("host");
			String atom = core.alerts.getAtom("http://" + host);
			byte[] buf = atom.getBytes("UTF-8");
			ctx.sendReplyHeaders(200, "OK", null, "application/atom+xml", buf.length);
			ctx.writeData(buf, 0, buf.length);
			return;
		}else if(ks.equals("/robots.txt") && ctx.doRobots()){
			this.writeTextReply(ctx, 200, "Ok", "User-agent: *\nDisallow: /");
			return;
		}else if(ks.startsWith("/darknet/") || ks.equals("/darknet")) { //TODO (pre-build 1045 url format) remove when obsolete
			writePermanentRedirect(ctx, "obsoleted", "/friends/");
			return;
		}else if(ks.startsWith("/opennet/") || ks.equals("/opennet")) { //TODO (pre-build 1045 url format) remove when obsolete
			writePermanentRedirect(ctx, "obsoleted", "/strangers/");
			return;
		} else if(ks.startsWith("/queue/")) {
			writePermanentRedirect(ctx, "obsoleted", "/downloads/");
			return;
		} else if(ks.startsWith("/config/")) {
			writePermanentRedirect(ctx, "obsoleted", "/config/node");
			return;
		}
		
		if(ks.startsWith("/"))
			ks = ks.substring(1);
		
		long maxSize;
		
		boolean restricted = (container.publicGatewayMode() && !ctx.isAllowedFullAccess());
		
		if(restricted)
			maxSize = MAX_LENGTH;
		else 
			maxSize = httprequest.getLongParam("max-size", MAX_LENGTH);

		//first check of httprange before get
		// only valid number format is checked here
		String rangeStr = ctx.getHeaders().get("range");
		if (rangeStr != null) {
			try {
				parseRange(rangeStr);
			} catch (HTTPRangeException e) {
				Logger.normal(this, "Invalid Range Header: "+rangeStr, e);
				ctx.sendReplyHeaders(416, "Requested Range Not Satisfiable", null, null, 0);
				return;
			}
		}
		
		FreenetURI key;
		try {
			key = new FreenetURI(ks);
		} catch (MalformedURLException e) {
			PageNode page = ctx.getPageMaker().getPageNode(l10n("invalidKeyTitle"), ctx);
			HTMLNode pageNode = page.outer;
			HTMLNode contentNode = page.content;

			HTMLNode errorInfobox = contentNode.addChild("div", "class", "infobox infobox-error");
			errorInfobox.addChild("div", "class", "infobox-header", NodeL10n.getBase().getString("FProxyToadlet.invalidKeyWithReason", new String[] { "reason" }, new String[] { e.toString() }));
			HTMLNode errorContent = errorInfobox.addChild("div", "class", "infobox-content");
			errorContent.addChild("#", l10n("expectedKeyButGot"));
			errorContent.addChild("code", ks);
			errorContent.addChild("br");
			errorContent.addChild(ctx.getPageMaker().createBackLink(ctx, l10n("goBack")));
			errorContent.addChild("br");
			addHomepageLink(errorContent);

			this.writeHTMLReply(ctx, 400, l10n("invalidKeyTitle"), pageNode.generate());
			return;
		}

		FetchContext fctx = getFetchContext(maxSize);

		//We should run the ContentFilter by default
		String forceString = httprequest.getParam("force");
		long now = System.currentTimeMillis();
		boolean force = false;
		if(forceString != null) {
			if(forceString.equals(getForceValue(key, now)) ||
					forceString.equals(getForceValue(key, now-FORCE_GRAIN_INTERVAL)))
				force = true;
		}
		if (!force && !httprequest.isParameterSet("forcedownload")) fctx.filterData = true;
		else if(logMINOR) Logger.minor(this, "Content filter disabled via request parameter");
		//Load the fetch context with the callbacks needed for web-pushing, if enabled
		if(container.enableInlinePrefetch()) fctx.prefetchHook = prefetchHook;
		if(container.isFProxyWebPushingEnabled()) fctx.tagReplacer = new PushingTagReplacerCallback(core.getFProxy().fetchTracker, MAX_LENGTH, ctx);

		String requestedMimeType = httprequest.getParam("type", null);
		fctx.overrideMIME = requestedMimeType;
		String override = (requestedMimeType == null) ? "" : "?type="+URLEncoder.encode(requestedMimeType,true);
		String maybeCharset = httprequest.isParameterSet("maybecharset") ? httprequest.getParam("maybecharset", null) : null;
		fctx.charset = maybeCharset;
		if(override.equals("") && maybeCharset != null)
			override = "?maybecharset="+URLEncoder.encode(maybeCharset, true);
		// No point passing ?force= across a redirect, since the key will change.
		// However, there is every point in passing ?forcedownload.
		if(httprequest.isParameterSet("forcedownload")) {
			if(override.length() == 0) override = "?forcedownload";
			else override = override+"&forcedownload";
		}

		Bucket data = null;
		String mimeType = null;
		String referer = sanitizeReferer(ctx);
		FetchException fe = null;
		

		MultiValueTable<String,String> headers = ctx.getHeaders();
		String ua = headers.get("user-agent");
		String accept = headers.get("accept");
		FProxyFetchResult fr = null;
		if(logMINOR) Logger.minor(this, "UA = "+ua+" accept = "+accept);
		
		boolean canSendProgress = 
			isBrowser(ua) && !ctx.disableProgressPage() && (accept == null || accept.indexOf("text/html") > -1) && !httprequest.isParameterSet("forcedownload");
		
			FProxyFetchWaiter fetch = null;
			try {
				fetch = fetchTracker.makeFetcher(key, maxSize, fctx, ctx.getReFilterPolicy());
			} catch (FetchException e) {
				fe = fr.failed;
			}
			if(fetch != null)
			while(true) {
			fr = fetch.getResult(!canSendProgress);
			if(fr.hasData()) {
				
				if(fr.getFetchCount() > 1 && !fr.hasWaited() && fr.getFetchCount() > 1 && key.isUSK() && context.uskManager.lookupKnownGood(USK.create(key)) > key.getSuggestedEdition()) {
					Logger.normal(this, "Loading later edition...");
					fetch.progress.requestImmediateCancel();
					fr = null;
					fetch = null;
					try {
						fetch = fetchTracker.makeFetcher(key, maxSize, fctx, ctx.getReFilterPolicy());
					} catch (FetchException e) {
						fe = fr.failed;
					}
					if(fetch == null) break;
					continue;
				}
				
				if(logMINOR) Logger.minor(this, "Found data");
				data = new NoFreeBucket(fr.data);
				mimeType = fr.mimeType;
				fetch.close(); // Not waiting any more, but still locked the results until sent
				break;
			} else if(fr.failed != null) {
				if(logMINOR) Logger.minor(this, "Request failed");
				fe = fr.failed;
				fetch.close(); // Not waiting any more, but still locked the results until sent
				break;
			} else if(canSendProgress) {
				if(logMINOR) Logger.minor(this, "Still in progress");
				// Still in progress
				boolean isJsEnabled=ctx.getContainer().isFProxyJavascriptEnabled() && ua != null && !ua.contains("AppleWebKit/");
				boolean isWebPushingEnabled = false;
				PageNode page = ctx.getPageMaker().getPageNode(l10n("fetchingPageTitle"), ctx);
				HTMLNode pageNode = page.outer;
				String location = getLink(key, requestedMimeType, maxSize, httprequest.getParam("force", null), httprequest.isParameterSet("forcedownload"));
				HTMLNode headNode=page.headNode;
				if(isJsEnabled){
					//If the user has enabled javascript, we add a <noscript> http refresh(if he has disabled it in the browser)
					headNode.addChild("noscript").addChild("meta", "http-equiv", "Refresh").addAttribute("content", "2;URL=" + location);
						// If pushing is disabled, but js is enabled, then we add the original progresspage.js
						if ((isWebPushingEnabled = ctx.getContainer().isFProxyWebPushingEnabled()) == false) {
							HTMLNode scriptNode = headNode.addChild("script", "//abc");
							scriptNode.addAttribute("type", "text/javascript");
							scriptNode.addAttribute("src", "/static/js/progresspage.js");
						}
				}else{
					//If he disabled it, we just put the http refresh meta, without the noscript
					headNode.addChild("meta", "http-equiv", "Refresh").addAttribute("content", "2;URL=" + location);
				}
				HTMLNode contentNode = page.content;
				HTMLNode infobox = contentNode.addChild("div", "class", "infobox infobox-information");
				infobox.addChild("div", "class", "infobox-header", l10n("fetchingPageBox"));
				HTMLNode infoboxContent = infobox.addChild("div", "class", "infobox-content");
				infoboxContent.addAttribute("id", "infoContent");
				infoboxContent.addChild(new ProgressInfoElement(fetchTracker, key, fctx, maxSize, core.isAdvancedModeEnabled(), ctx, isWebPushingEnabled));

				
				HTMLNode table = infoboxContent.addChild("table", "border", "0");
				HTMLNode progressCell = table.addChild("tr").addChild("td", "class", "request-progress");
				if(fr.totalBlocks <= 0)
					progressCell.addChild("#", NodeL10n.getBase().getString("QueueToadlet.unknown"));
				else {
					progressCell.addChild(new ProgressBarElement(fetchTracker,key,fctx,maxSize,ctx, isWebPushingEnabled));
				}
				
				infobox = contentNode.addChild("div", "class", "infobox infobox-information");
				infobox.addChild("div", "class", "infobox-header", l10n("fetchingPageOptions"));
				infoboxContent = infobox.addChild("div", "class", "infobox-content");

				HTMLNode optionList = infoboxContent.addChild("ul");
				optionList.addChild("li").addChild("p", l10n("progressOptionZero"));
				
				addDownloadOptions(ctx, optionList, key, mimeType, false, false, core);

				optionList.addChild("li").addChild(ctx.getPageMaker().createBackLink(ctx, l10n("goBackToPrev")));
				optionList.addChild("li").addChild("a", new String[] { "href", "title" },
						new String[] { "/", NodeL10n.getBase().getString("Toadlet.homepage") }, l10n("abortToHomepage"));
				
				MultiValueTable<String, String> retHeaders = new MultiValueTable<String, String>();
				//retHeaders.put("Refresh", "2; url="+location);
				writeHTMLReply(ctx, 200, "OK", retHeaders, pageNode.generate());
				fr.close();
				fetch.close();
				return;
			} else if(fr != null)
				fr.close();
			}
		
		try {
			if(logMINOR)
				Logger.minor(this, "FProxy fetching "+key+" ("+maxSize+ ')');
			if(data == null && fe == null) {
				boolean needsFetch=true;
				//If we don't have the data, then check if an FProxyFetchInProgress has. It can happen when one FetchInProgress downloaded an image
				//asynchronously, then loads it. This way a FetchInprogress will have the full image, and no need to block.
				FProxyFetchInProgress progress=fetchTracker.getFetchInProgress(key, maxSize, fctx);
				if(progress!=null){
					FProxyFetchWaiter waiter=null;
					FProxyFetchResult result=null;
					try{
						waiter=progress.getWaiter();
						result=waiter.getResult(false);
						if(result.failed==null && result.data!=null){
							mimeType=result.mimeType;
							data=result.data;
							data=ctx.getBucketFactory().makeBucket(result.data.size());
							BucketTools.copy(result.data, data);
							needsFetch=false;
						}
					}finally{
						if(waiter!=null){
							progress.close(waiter);
						}
						if(result!=null){
							progress.close(result);
						}
					}
				}
				if(needsFetch){
					//If we don't have the data, then we need to fetch it and block until it is available
					FetchResult result = fetch(key, maxSize, new RequestClient() {
						public boolean persistent() {
							return false;
						}
						public void removeFrom(ObjectContainer container) {
							throw new UnsupportedOperationException();
						} }, fctx); 
					
					// Now, is it safe?
					
					data = result.asBucket();
					mimeType = result.getMimeType();
				}
			} else if(fe != null) throw fe;
			
			handleDownload(ctx, data, ctx.getBucketFactory(), mimeType, requestedMimeType, forceString, httprequest.isParameterSet("forcedownload"), "/", key, maxSize != MAX_LENGTH ? "&max-size="+maxSize : "", referer, true, ctx, core, fr != null, maybeCharset);
		} catch (FetchException e) {
			//Handle exceptions thrown from the ContentFilter
			String msg = e.getMessage();
			if(logMINOR) {
				Logger.minor(this, "Failed to fetch "+uri+" : "+e);
			}
			if(e.newURI != null) {
				if(accept != null && (accept.startsWith("text/css") || accept.startsWith("image/")) && recursion++ < MAX_RECURSION) {
					// If it's an image or a CSS fetch, auto-follow the redirect, up to a limit.
					String link = getLink(e.newURI, requestedMimeType, maxSize, httprequest.getParam("force", null), httprequest.isParameterSet("forcedownload"));
					try {
						uri = new URI(link);
						innerHandleMethodGET(uri, httprequest, ctx, recursion);
						return;
					} catch (URISyntaxException e1) {
						Logger.error(this, "Caught "+e1+" parsing new link "+link, e1);
					}
				}
				Toadlet.writePermanentRedirect(ctx, msg,
					getLink(e.newURI, requestedMimeType, maxSize, httprequest.getParam("force", null), httprequest.isParameterSet("forcedownload")));
			} else if(e.mode == FetchException.TOO_BIG) {
				PageNode page = ctx.getPageMaker().getPageNode(l10n("fileInformationTitle"), ctx);
				HTMLNode pageNode = page.outer;
				HTMLNode contentNode = page.content;
				
				HTMLNode infobox = contentNode.addChild("div", "class", "infobox infobox-information");
				infobox.addChild("div", "class", "infobox-header", l10n("largeFile"));
				HTMLNode infoboxContent = infobox.addChild("div", "class", "infobox-content");
				HTMLNode fileInformationList = infoboxContent.addChild("ul");
				HTMLNode option = fileInformationList.addChild("li");
				option.addChild("#", (l10n("filenameLabel") + ' '));
				option.addChild("a", "href", '/' + key.toString(), getFilename(key, e.getExpectedMimeType()));

				String mime = writeSizeAndMIME(fileInformationList, e);
				
				infobox = contentNode.addChild("div", "class", "infobox infobox-information");
				infobox.addChild("div", "class", "infobox-header", l10n("explanationTitle"));
				infoboxContent = infobox.addChild("div", "class", "infobox-content");
				infoboxContent.addChild("#", l10n("largeFileExplanationAndOptions"));
				HTMLNode optionList = infoboxContent.addChild("ul");
				//HTMLNode optionTable = infoboxContent.addChild("table", "border", "0");
				if(!restricted) {
					option = optionList.addChild("li");
					HTMLNode optionForm = option.addChild("form", new String[] { "action", "method" }, new String[] {'/' + key.toString(), "get" });
					optionForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "hidden", "max-size", String.valueOf(e.expectedSize == -1 ? Long.MAX_VALUE : e.expectedSize*2) });
					optionForm.addChild("input", new String[] { "type", "name", "value" }, new String[] { "submit", "fetch", l10n("fetchLargeFileAnywayAndDisplayButton") });
					optionForm.addChild("#", " - " + l10n("fetchLargeFileAnywayAndDisplay"));
					addDownloadOptions(ctx, optionList, key, mime, false, false, core);
				}
				

				//optionTable.addChild("tr").addChild("td", "colspan", "2").addChild("a", new String[] { "href", "title" }, new String[] { "/", NodeL10n.getBase().getString("Toadlet.homepage") }, l10n("abortToHomepage"));
				optionList.addChild("li").addChild("a", new String[] { "href", "title" }, new String[] { "/", NodeL10n.getBase().getString("Toadlet.homepage") }, l10n("abortToHomepage"));
				
				//option = optionTable.addChild("tr").addChild("td", "colspan", "2");
				optionList.addChild("li").addChild(ctx.getPageMaker().createBackLink(ctx, l10n("goBackToPrev")));
				
				writeHTMLReply(ctx, 200, "OK", pageNode.generate());
			} else {
				PageNode page = ctx.getPageMaker().getPageNode(e.getShortMessage(), ctx);
				HTMLNode pageNode = page.outer;
				HTMLNode contentNode = page.content;

				HTMLNode infobox = contentNode.addChild("div", "class", "infobox infobox-error");
				infobox.addChild("div", "class", "infobox-header", l10n("errorWithReason", "error", e.getShortMessage()));
				HTMLNode infoboxContent = infobox.addChild("div", "class", "infobox-content");
				HTMLNode fileInformationList = infoboxContent.addChild("ul");
				HTMLNode option = fileInformationList.addChild("li");
				option.addChild("#", (l10n("filenameLabel") + ' '));
				option.addChild("a", "href", '/' + key.toString(), getFilename(key, e.getExpectedMimeType()));

				String mime = writeSizeAndMIME(fileInformationList, e);
				infobox = contentNode.addChild("div", "class", "infobox infobox-error");
				infobox.addChild("div", "class", "infobox-header", l10n("explanationTitle"));
				infoboxContent = infobox.addChild("div", "class", "infobox-content");
				infoboxContent.addChild("p", l10n("unableToRetrieve"));
				UnsafeContentTypeException filterException = null;
				if(e.getCause() != null && e.getCause() instanceof UnsafeContentTypeException) {
					filterException = (UnsafeContentTypeException)e.getCause();
				}
				if(e.isFatal() && filterException == null)
					infoboxContent.addChild("p", l10n("errorIsFatal"));
				infoboxContent.addChild("p", msg);
				if(filterException != null) {
					if(filterException.details() != null) {
						HTMLNode detailList = infoboxContent.addChild("ul");
						for(String detail : filterException.details()) {
							detailList.addChild("li", detail);
						}
					}
				}
				if(e.errorCodes != null) {
					infoboxContent.addChild("p").addChild("pre").addChild("#", e.errorCodes.toVerboseString());
				}

				infobox = contentNode.addChild("div", "class", "infobox infobox-error");
				infobox.addChild("div", "class", "infobox-header", l10n("options"));
				infoboxContent = infobox.addChild("div", "class", "infobox-content");
				
				HTMLNode optionList = infoboxContent.addChild("ul");

				PluginInfoWrapper keyUtil;
				if((e.mode == FetchException.NOT_IN_ARCHIVE || e.mode == FetchException.NOT_ENOUGH_PATH_COMPONENTS)) { 
					// first look for the newest version
					if ((keyUtil = core.node.pluginManager.getPluginInfo("plugins.KeyUtils.KeyUtilsPlugin")) != null) {
						option = optionList.addChild("li");
						NodeL10n.getBase().addL10nSubstitution(option, "FProxyToadlet.openWithKeyExplorer", new String[] { "link" }, new HTMLNode[] { HTMLNode.link("/KeyUtils/?automf=true&key=" + key.toString()) });
					} else if ((keyUtil = core.node.pluginManager.getPluginInfo("plugins.KeyExplorer.KeyExplorer")) != null) {
						option = optionList.addChild("li");
						if (keyUtil.getPluginLongVersion() > 4999)
							NodeL10n.getBase().addL10nSubstitution(option, "FProxyToadlet.openWithKeyExplorer", new String[] { "link" }, new HTMLNode[] { HTMLNode.link("/KeyExplorer/?automf=true&key=" + key.toString())});
						else
							NodeL10n.getBase().addL10nSubstitution(option, "FProxyToadlet.openWithKeyExplorer", new String[] { "link" }, new HTMLNode[] { HTMLNode.link("/plugins/plugins.KeyExplorer.KeyExplorer/?key=" + key.toString())});
					}
				}
				if(filterException != null) {
					if((mime.equals("application/x-freenet-index")) && (core.node.pluginManager.isPluginLoaded("plugins.ThawIndexBrowser.ThawIndexBrowser"))) {
						option = optionList.addChild("li");
						NodeL10n.getBase().addL10nSubstitution(option, "FProxyToadlet.openAsThawIndex", new String[] { "link" }, new HTMLNode[] { HTMLNode.link("/plugins/plugins.ThawIndexBrowser.ThawIndexBrowser/?key=" + key.toString()).addChild("b") });
					}
					option = optionList.addChild("li");
					// FIXME: is this safe? See bug #131
					NodeL10n.getBase().addL10nSubstitution(option, "FProxyToadlet.openAsText", new String[] { "link" }, new HTMLNode[] { HTMLNode.link(getLink(key, "text/plain", maxSize, null, false)) });
					option = optionList.addChild("li");
					NodeL10n.getBase().addL10nSubstitution(option, "FProxyToadlet.openForceDisk", new String[] { "link" }, new HTMLNode[] { HTMLNode.link(getLink(key, mime, maxSize, null, true)) });
					if(!(mime.equals("application/octet-stream") || mime.equals("application/x-msdownload"))) {
						option = optionList.addChild("li");
						NodeL10n.getBase().addL10nSubstitution(option, "FProxyToadlet.openForce", new String[] { "link", "mime" }, new HTMLNode[] { HTMLNode.link(getLink(key, mime, maxSize, getForceValue(key, now), false)), HTMLNode.text(HTMLEncoder.encode(mime))});
					}
				}

				if((!e.isFatal() || filterException != null) && (ctx.isAllowedFullAccess() || !container.publicGatewayMode())) {
					addDownloadOptions(ctx, optionList, key, mimeType, filterException != null, filterException != null, core);
					optionList.addChild("li").
						addChild("a", "href", getLink(key, requestedMimeType, maxSize, httprequest.getParam("force", null),
									httprequest.isParameterSet("forcedownload"))).addChild("#", l10n("retryNow"));
				}
				
				optionList.addChild("li").addChild("a", new String[] { "href", "title" }, new String[] { "/", NodeL10n.getBase().
						getString("Toadlet.homepage") }, l10n("abortToHomepage"));
				
				optionList.addChild("li").addChild(ctx.getPageMaker().createBackLink(ctx, l10n("goBackToPrev")));
				this.writeHTMLReply(ctx, (e.mode == 10) ? 404 : 500 /* close enough - FIXME probably should depend on status code */,
						"Internal Error", pageNode.generate());
			}
		} catch (SocketException e) {
			// Probably irrelevant
			if(e.getMessage().equals("Broken pipe")) {
				if(logMINOR)
					Logger.minor(this, "Caught "+e+" while handling GET", e);
			} else {
				Logger.normal(this, "Caught "+e);
			}
			throw e;
		} catch (Throwable t) {
			writeInternalError(t, ctx);
		} finally {
			if(fr == null && data != null) data.free();
			if(fr != null) fr.close();
		}
	}

	private static String getDownloadReturnType(Node node) {
		if(node.securityLevels.getPhysicalThreatLevel() != PHYSICAL_THREAT_LEVEL.LOW)
			// Default to save to temp space
			return "direct";
		else
			return "disk";
	}

	private boolean isBrowser(String ua) {
		if(ua == null) return false;
		if(ua.indexOf("Mozilla/") > -1) return true;
		if(ua.indexOf("Opera/") > -1) return true;
		return false;
	}

	private static String writeSizeAndMIME(HTMLNode fileInformationList, FetchException e) {
		boolean finalized = e.finalizedSize();
		long size = e.expectedSize;
		return writeSizeAndMIME(fileInformationList, size, e.getExpectedMimeType(), finalized);
	}

	private static String writeSizeAndMIME(HTMLNode fileInformationList, long size, String mime, boolean finalized) {
		if(size > 0) {
			if (finalized) {
				fileInformationList.addChild("li", (l10n("sizeLabel") + ' ') + SizeUtil.formatSize(size));
			} else {
				fileInformationList.addChild("li", (l10n("sizeLabel") + ' ')+ SizeUtil.formatSize(size) + l10n("mayChange"));
			}
		} else {
			fileInformationList.addChild("li", l10n("sizeUnknown"));
		}
		if(mime != null) {
			fileInformationList.addChild("li", NodeL10n.getBase().getString("FProxyToadlet."+(finalized ? "mimeType" : "expectedMimeType"), new String[] { "mime" }, new String[] { mime }));
		} else {
			fileInformationList.addChild("li", l10n("unknownMIMEType"));
			mime = l10n("unknownMIMEType");
		}
		return mime;
	}
	
	private String l10n(String key, String pattern, String value) {
		return NodeL10n.getBase().getString("FProxyToadlet."+key, new String[] { pattern }, new String[] { value });
	}
	
	public static String l10n(String key, String[] pattern, String[] value) {
		return NodeL10n.getBase().getString("FProxyToadlet."+key, pattern, value);
	}

	private String getLink(FreenetURI uri, String requestedMimeType, long maxSize, String force, 
			boolean forceDownload) {
		StringBuilder sb = new StringBuilder();
		sb.append("/");
		sb.append(uri.toASCIIString());
		char c = '?';
		if(requestedMimeType != null && requestedMimeType.length() != 0) {
			sb.append(c).append("type=").append(URLEncoder.encode(requestedMimeType,false)); c = '&';
		}
		if(maxSize > 0 && maxSize != MAX_LENGTH) {
			sb.append(c).append("max-size=").append(maxSize); c = '&';
		}
		if(force != null) {
			sb.append(c).append("force=").append(force); c = '&';
		}
		if(forceDownload) {
			sb.append(c).append("forcedownload=true"); c = '&';
		}
		return sb.toString();
	}

	private String sanitizeReferer(ToadletContext ctx) {
		// FIXME we do something similar in the GenericFilterCallback thingy?
		String referer = ctx.getHeaders().get("referer");
		if(referer != null) {
			try {
				URI refererURI = new URI(URIPreEncoder.encode(referer));
				String path = refererURI.getPath();
				while(path.startsWith("/")) path = path.substring(1);
				if("".equals(path)) return "/";
				FreenetURI furi = new FreenetURI(path);
				HTTPRequest req = new HTTPRequestImpl(refererURI, "GET");
				String type = req.getParam("type");
				referer = "/" + furi.toString();
				if(type != null && type.length() > 0)
					referer += "?type=" + type;
			} catch (MalformedURLException e) {
				referer = "/";
				Logger.normal(this, "Caught MalformedURLException on the referer : "+e.getMessage());
			} catch (Throwable t) {
				Logger.error(this, "Caught handling referrer: "+t+" for "+referer, t);
				referer = null;
			}
		}
		return referer;
	}

	private static String getForceValue(FreenetURI key, long time) {
		ByteArrayOutputStream bos = new ByteArrayOutputStream();
		
		try{
			bos.write(random);
			bos.write(key.toString().getBytes("UTF-8"));
			bos.write(Long.toString(time / FORCE_GRAIN_INTERVAL).getBytes("UTF-8"));
		} catch (IOException e) {
			throw new Error(e);
		}
		
		String f = HexUtil.bytesToHex(SHA256.digest(bos.toByteArray()));
		return f;
	}

	public static void maybeCreateFProxyEtc(NodeClientCore core, Node node, Config config, SimpleToadletServer server, BookmarkManager bookmarks) throws IOException {
		
		// FIXME how to change these on the fly when the interface language is changed?
		
		HighLevelSimpleClient client = core.makeClient(RequestStarter.INTERACTIVE_PRIORITY_CLASS, true);
		
		random = new byte[32];
		core.random.nextBytes(random);
		
		FProxyFetchTracker fetchTracker = new FProxyFetchTracker(core.clientContext, client.getFetchContext(), new RequestClient() {

			public boolean persistent() {
				return false;
			}

			public void removeFrom(ObjectContainer container) {
				// Do nothing.
			}
			
		});

		
		FProxyToadlet fproxy = new FProxyToadlet(client, core, fetchTracker);
		core.setFProxy(fproxy);
		
		server.registerMenu("/", "FProxyToadlet.categoryBrowsing", "FProxyToadlet.categoryTitleBrowsing", null);
		server.registerMenu("/downloads/", "FProxyToadlet.categoryQueue", "FProxyToadlet.categoryTitleQueue", null);
		server.registerMenu("/friends/", "FProxyToadlet.categoryFriends", "FProxyToadlet.categoryTitleFriends", null);
		server.registerMenu("/chat/", "FProxyToadlet.categoryChat", "FProxyToadlet.categoryTitleChat", null);
		server.registerMenu("/alerts/", "FProxyToadlet.categoryStatus", "FProxyToadlet.categoryTitleStatus", null);
		server.registerMenu("/seclevels/", "FProxyToadlet.categoryConfig", "FProxyToadlet.categoryTitleConfig", null);
		
		
		server.register(fproxy, "FProxyToadlet.categoryBrowsing", "/", false, "FProxyToadlet.welcomeTitle", "FProxyToadlet.welcome", false, null);
		
		InsertFreesiteToadlet siteinsert = new InsertFreesiteToadlet(client, core.alerts);
		server.register(siteinsert, "FProxyToadlet.categoryBrowsing", "/insertsite/", true, "FProxyToadlet.insertFreesiteTitle", "FProxyToadlet.insertFreesite", false, null);
		
		UserAlertsToadlet alerts = new UserAlertsToadlet(client, node, core);
		server.register(alerts, "FProxyToadlet.categoryStatus", "/alerts/", true, "FProxyToadlet.alertsTitle", "FProxyToadlet.alerts", true, null);

		
		QueueToadlet downloadToadlet = new QueueToadlet(core, core.getFCPServer(), client, false);
		server.register(downloadToadlet, "FProxyToadlet.categoryQueue", "/downloads/", true, "FProxyToadlet.downloadsTitle", "FProxyToadlet.downloads", false, downloadToadlet);
		QueueToadlet uploadToadlet = new QueueToadlet(core, core.getFCPServer(), client, true);
		server.register(uploadToadlet, "FProxyToadlet.categoryQueue", "/uploads/", true, "FProxyToadlet.uploadsTitle", "FProxyToadlet.uploads", false, uploadToadlet);
		
		FileInsertWizardToadlet fiw = new FileInsertWizardToadlet(client, core);
		server.register(fiw, "FProxyToadlet.categoryQueue", FileInsertWizardToadlet.PATH, true, "FProxyToadlet.uploadFileWizardTitle", "FProxyToadlet.uploadFileWizard", false, fiw);
		uploadToadlet.setFIW(fiw);
		
		SymlinkerToadlet symlinkToadlet = new SymlinkerToadlet(client, node);
		server.register(symlinkToadlet, null, "/sl/", true, false);
		
		SecurityLevelsToadlet seclevels = new SecurityLevelsToadlet(client, node, core);
		server.register(seclevels, "FProxyToadlet.categoryConfig", "/seclevels/", true, "FProxyToadlet.seclevelsTitle", "FProxyToadlet.seclevels", true, null);

		PproxyToadlet pproxy = new PproxyToadlet(client, node, core);
		server.register(pproxy, "FProxyToadlet.categoryConfig", "/plugins/", true, "FProxyToadlet.pluginsTitle", "FProxyToadlet.plugins", true, null);
		
		SubConfig[] sc = config.getConfigs();
		Arrays.sort(sc);
		
		for(SubConfig cfg : sc) {
			String prefix = cfg.getPrefix();
			if(prefix.equals("security-levels") || prefix.equals("pluginmanager")) continue;
			ConfigToadlet configtoadlet = new ConfigToadlet(client, config, cfg, node, core);
			server.register(configtoadlet, "FProxyToadlet.categoryConfig", "/config/"+prefix, true, "ConfigToadlet."+prefix, "ConfigToadlet.title."+prefix, true, configtoadlet);
		}
		
		WelcomeToadlet welcometoadlet = new WelcomeToadlet(client, core, node, bookmarks);
		server.register(welcometoadlet, null, "/welcome/", true, false);
		
		
		DarknetConnectionsToadlet friendsToadlet = new DarknetConnectionsToadlet(node, core, client);
		server.register(friendsToadlet, "FProxyToadlet.categoryFriends", "/friends/", true, "FProxyToadlet.friendsTitle", "FProxyToadlet.friends", true, null);
		
		DarknetAddRefToadlet addRefToadlet = new DarknetAddRefToadlet(node, core, client);
		server.register(addRefToadlet, "FProxyToadlet.categoryFriends", "/addfriend/", true, "FProxyToadlet.addFriendTitle", "FProxyToadlet.addFriend", true, null);
		
		OpennetConnectionsToadlet opennetToadlet = new OpennetConnectionsToadlet(node, core, client);
		server.register(opennetToadlet, "FProxyToadlet.categoryStatus", "/strangers/", true, "FProxyToadlet.opennetTitle", "FProxyToadlet.opennet", true, opennetToadlet);
		
		ChatForumsToadlet chatForumsToadlet = new ChatForumsToadlet(client, core.alerts, node.pluginManager, core.node);
		server.register(chatForumsToadlet, "FProxyToadlet.categoryChat", "/chat/", true, "FProxyToadlet.chatForumsTitle", "FProxyToadlet.chatForums", true, chatForumsToadlet);
		
		N2NTMToadlet n2ntmToadlet = new N2NTMToadlet(node, core, client);
		server.register(n2ntmToadlet, null, "/send_n2ntm/", true, true);
		LocalFileInsertToadlet localFileInsertToadlet = new LocalFileInsertToadlet(core, client);
		server.register(localFileInsertToadlet, null, "/files/", true, false);
		
		BookmarkEditorToadlet bookmarkEditorToadlet = new BookmarkEditorToadlet(client, core, bookmarks);
		server.register(bookmarkEditorToadlet, null, "/bookmarkEditor/", true, false);
		
		BrowserTestToadlet browsertTestToadlet = new BrowserTestToadlet(client, core);
		server.register(browsertTestToadlet, null, "/test/", true, false);
			
		StatisticsToadlet statisticsToadlet = new StatisticsToadlet(node, core, client);
		server.register(statisticsToadlet, "FProxyToadlet.categoryStatus", "/stats/", true, "FProxyToadlet.statsTitle", "FProxyToadlet.stats", true, null);
		
		ConnectivityToadlet connectivityToadlet = new ConnectivityToadlet(client, node, core);
		server.register(connectivityToadlet, "FProxyToadlet.categoryStatus", "/connectivity/", true, "ConnectivityToadlet.connectivityTitle", "ConnectivityToadlet.connectivity", true, null);
		
		TranslationToadlet translationToadlet = new TranslationToadlet(client, core);
		server.register(translationToadlet, "FProxyToadlet.categoryConfig", TranslationToadlet.TOADLET_URL, true, "TranslationToadlet.title", "TranslationToadlet.titleLong", true, null);
		
		FirstTimeWizardToadlet firstTimeWizardToadlet = new FirstTimeWizardToadlet(client, node, core);
		server.register(firstTimeWizardToadlet, null, FirstTimeWizardToadlet.TOADLET_URL, true, false);
		
		SimpleHelpToadlet simpleHelpToadlet = new SimpleHelpToadlet(client, core);
		server.register(simpleHelpToadlet, null, "/help/", true, false);
		
		PushDataToadlet pushDataToadlet = new PushDataToadlet(client);
		server.register(pushDataToadlet, null, pushDataToadlet.path(), true, false);
		
		PushNotificationToadlet pushNotificationToadlet = new PushNotificationToadlet(client);
		server.register(pushNotificationToadlet, null, pushNotificationToadlet.path(), true, false);
		
		PushKeepaliveToadlet pushKeepaliveToadlet = new PushKeepaliveToadlet(client);
		server.register(pushKeepaliveToadlet, null, pushKeepaliveToadlet.path(), true, false);
		
		PushFailoverToadlet pushFailoverToadlet = new PushFailoverToadlet(client);
		server.register(pushFailoverToadlet, null, pushFailoverToadlet.path(), true, false);
		
		PushTesterToadlet pushTesterToadlet = new PushTesterToadlet(client);
		server.register(pushTesterToadlet, null, pushTesterToadlet.path(), true, false);
		
		PushLeavingToadlet pushLeavingToadlet = new PushLeavingToadlet(client);
		server.register(pushLeavingToadlet, null, pushLeavingToadlet.path(), true, false);
		
		ImageCreatorToadlet imageCreatorToadlet = new ImageCreatorToadlet(client);
		server.register(imageCreatorToadlet, null, imageCreatorToadlet.path(), true, false);
		
		LogWritebackToadlet logWritebackToadlet = new LogWritebackToadlet(client);
		server.register(logWritebackToadlet, null, logWritebackToadlet.path(), true, false);
		
		DismissAlertToadlet dismissAlertToadlet = new DismissAlertToadlet(client);
		server.register(dismissAlertToadlet, null, dismissAlertToadlet.path(), true, false);
	}
	
	/**
	 * Get expected filename for a file.
	 * @param e The FetchException.
	 * @param uri The original URI.
	 * @param expectedMimeType The expected MIME type.
	 */
	private static String getFilename(FreenetURI uri, String expectedMimeType) {
		String s = uri.getPreferredFilename();
		int dotIdx = s.lastIndexOf('.');
		String ext = DefaultMIMETypes.getExtension(expectedMimeType);
		if(ext == null)
			ext = "bin";
		if((dotIdx == -1) && (expectedMimeType != null)) {
			s += '.' + ext;
			return s;
		}
		if(dotIdx != -1) {
			String oldExt = s.substring(dotIdx+1);
			if(DefaultMIMETypes.isValidExt(expectedMimeType, oldExt))
				return s;
			return s + '.' + ext;
		}
		return s + '.' + ext;
	}
	
	private static long[] parseRange(String hdrrange) throws HTTPRangeException {
		
		long result[] = new long[2];
		try {
			String[] units = hdrrange.split("=", 2);
			// FIXME are MBytes and co valid? if so, we need to adjust the values and
			// return always bytes
			if (!"bytes".equals(units[0])) {
				throw new HTTPRangeException("Unknown unit, only 'bytes' supportet yet");
			}
			String[] range = units[1].split("-", 2);
			result[0] = Long.parseLong(range[0]);
			if (result[0] < 0)
				throw new HTTPRangeException("Negative 'from' value");
			if (range[1].trim().length() > 0) {
				result[1] = Long.parseLong(range[1]);
				if (result[1] <= result[0])
					throw new HTTPRangeException("'from' value must be less then 'to' value");
			} else {
				result[1] = -1;
			}
		} catch (NumberFormatException nfe) {
			throw new HTTPRangeException(nfe);
		} catch (IndexOutOfBoundsException ioobe) {
			throw new HTTPRangeException(ioobe);
		}
		return result;
	}

	public boolean persistent() {
		return false;
	}

	public void removeFrom(ObjectContainer container) {
		throw new UnsupportedOperationException();
	}

	@Override
	public String path() {
		return "/";
	}
	
}