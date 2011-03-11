/* This code is part of Freenet. It is distributed under the GNU General
 * Public License, version 2 (or at your option any later version). See
 * http://www.gnu.org/ for further details of the GPL. */
package freenet.clients.http;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.util.HashMap;

import freenet.client.HighLevelSimpleClient;
import freenet.l10n.NodeL10n;
import freenet.node.DarknetPeerNode;
import freenet.node.Node;
import freenet.node.NodeClientCore;
import freenet.node.PeerManager;
import freenet.support.HTMLNode;
import freenet.support.Logger;
import freenet.support.MultiValueTable;
import freenet.support.api.HTTPRequest;

public class N2NTMToadlet extends Toadlet {
	private Node node;
	private NodeClientCore core;

	protected N2NTMToadlet(Node n, NodeClientCore core,
			HighLevelSimpleClient client) {
		super(client);
		this.node = n;
		this.core = core;
	}

	public void handleMethodGET(URI uri, HTTPRequest request, ToadletContext ctx)
			throws ToadletContextClosedException, IOException,
			RedirectException {

		if (!ctx.isAllowedFullAccess()) {
			super.sendErrorPage(ctx, 403, "Unauthorized", NodeL10n.getBase()
					.getString("Toadlet.unauthorized"));
			return;
		}

		if (request.isParameterSet("peernode_hashcode")) {
			PageNode page = ctx.getPageMaker().getPageNode(
					l10n("sendMessage"), ctx);
			HTMLNode pageNode = page.outer;
			HTMLNode contentNode = page.content;

			String peernode_name = null;
			String input_hashcode_string = request
					.getParam("peernode_hashcode");
			int input_hashcode = -1;
			try {
				input_hashcode = (Integer.valueOf(input_hashcode_string))
						.intValue();
			} catch (NumberFormatException e) {
				// ignore here, handle below
			}
			if (input_hashcode != -1) {
				DarknetPeerNode[] peerNodes = node.getDarknetConnections();
				for (int i = 0; i < peerNodes.length; i++) {
					int peer_hashcode = peerNodes[i].hashCode();
					if (peer_hashcode == input_hashcode) {
						peernode_name = peerNodes[i].getName();
						break;
					}
				}
			}
			if (peernode_name == null) {
				contentNode.addChild(createPeerInfobox("infobox-error",
						l10n("peerNotFoundTitle"), l10n("peerNotFoundWithHash",
								"hash", input_hashcode_string)));
				this.writeHTMLReply(ctx, 200, "OK", pageNode
						.generate());
				return;
			}
			HashMap<String, String> peers = new HashMap<String, String>();
			peers.put(input_hashcode_string, peernode_name);
			createN2NTMSendForm(pageNode, contentNode, ctx, peers);
			this.writeHTMLReply(ctx, 200, "OK", pageNode.generate());
			return;
		}
		MultiValueTable<String, String> headers = new MultiValueTable<String, String>();
		headers.put("Location", "/friends/");
		ctx.sendReplyHeaders(302, "Found", headers, null, 0);
	}

	private String l10n(String key, String pattern, String value) {
		return NodeL10n.getBase().getString("N2NTMToadlet." + key, new String[] { pattern },
				new String[] { value });
	}

	private static String l10n(String key) {
		return NodeL10n.getBase().getString("N2NTMToadlet." + key);
	}

	private static HTMLNode createPeerInfobox(String infoboxType,
			String header, String message) {
		HTMLNode infobox = new HTMLNode("div", "class", "infobox "
				+ infoboxType);
		infobox.addChild("div", "class", "infobox-header", header);
		HTMLNode infoboxContent = infobox.addChild("div", "class",
				"infobox-content");
		infoboxContent.addChild("#", message);
		HTMLNode list = infoboxContent.addChild("ul");
		Toadlet.addHomepageLink(list);
		list.addChild("li").addChild("a", new String[] { "href", "title" },
				new String[] { "/friends/", l10n("returnToFriends") },
				l10n("friends"));
		return infobox;
	}

	public void handleMethodPOST(URI uri, HTTPRequest request, ToadletContext ctx)
			throws ToadletContextClosedException, IOException,
			RedirectException {
		String pass = request.getPartAsString("formPassword", 32);
		if ((pass == null) || !pass.equals(core.formPassword)) {
			MultiValueTable<String, String> headers = new MultiValueTable<String, String>();
			headers.put("Location", "/send_n2ntm/");
			ctx.sendReplyHeaders(302, "Found", headers, null, 0);
			return;
		}

		if (!ctx.isAllowedFullAccess()) {
			super.sendErrorPage(ctx, 403, "Unauthorized", NodeL10n.getBase()
					.getString("Toadlet.unauthorized"));
			return;
		}

		if (request.isPartSet("send")) {
			String message = request.getPartAsString("message", 5 * 1024);
			message = message.trim();
			if (message.length() > 1024) {
				this.writeTextReply(ctx, 400, "Bad request",
						l10n("tooLong"));
				return;
			}
			PageNode page =  ctx.getPageMaker().getPageNode(
					l10n("processingSend"), ctx);
			HTMLNode pageNode = page.outer;
			HTMLNode contentNode = page.content;
			HTMLNode peerTableInfobox = contentNode.addChild("div", "class",
					"infobox infobox-normal");
			DarknetPeerNode[] peerNodes = node.getDarknetConnections();
			String fnam = request.getPartAsString("filename", 1024);
			File filename = null;
			if(fnam != null && fnam.length() > 0) {
				filename = new File(fnam);
				if(!(filename.exists() && filename.canRead())) {
					peerTableInfobox.addChild("#", l10n("noSuchFileOrCannotRead"));
					Toadlet.addHomepageLink(peerTableInfobox);
					this.writeHTMLReply(ctx, 400, "OK", pageNode.generate());
					return;
				}
			}
			HTMLNode peerTable = peerTableInfobox.addChild("table", "class",
			"n2ntm-send-statuses");
			HTMLNode peerTableHeaderRow = peerTable.addChild("tr");
			peerTableHeaderRow.addChild("th", l10n("peerName"));
			peerTableHeaderRow.addChild("th", l10n("sendStatus"));
			for (int i = 0; i < peerNodes.length; i++) {
				if (request.isPartSet("node_" + peerNodes[i].hashCode())) {
					DarknetPeerNode pn = peerNodes[i];
					
					int status;
					
					if(filename != null) {
						try {
							status = pn.sendFileOffer(filename, message);
						} catch (IOException e) {
							peerTableInfobox.addChild("#", l10n("noSuchFileOrCannotRead"));
							Toadlet.addHomepageLink(peerTableInfobox);
							this.writeHTMLReply(ctx, 200, "OK", pageNode.generate());
							return;
						}
					} else {
						status = pn.sendTextFeed(message);
					}
					
					String sendStatusShort;
					String sendStatusLong;
					String sendStatusClass;
					if(status == PeerManager.PEER_NODE_STATUS_ROUTING_BACKED_OFF) {
						sendStatusShort = l10n("delayedTitle");
						sendStatusLong = l10n("delayed");
						sendStatusClass = "n2ntm-send-delayed";
						Logger.normal(this, "Sent N2NTM to '"
								+ pn.getName() + "': " + message);
					} else if(status == PeerManager.PEER_NODE_STATUS_CONNECTED) {
						sendStatusShort = l10n("sentTitle");
						sendStatusLong = l10n("sent");
						sendStatusClass = "n2ntm-send-sent";
						Logger.normal(this, "Sent N2NTM to '"
								+ pn.getName() + "': " + message);
					} else {
						sendStatusShort = l10n("queuedTitle");
						sendStatusLong = l10n("queued");
						sendStatusClass = "n2ntm-send-queued";
						Logger.normal(this, "Queued N2NTM to '"
								+ pn.getName() + "': " + message);
					}
					HTMLNode peerRow = peerTable.addChild("tr");
					peerRow.addChild("td", "class", "peer-name").addChild("#",
							pn.getName());
					peerRow
							.addChild("td", "class", sendStatusClass)
							.addChild(
									"span",
									new String[] { "title", "style" },
									new String[] { sendStatusLong,
											"border-bottom: 1px dotted; cursor: help;" },
									sendStatusShort);
				}
			}
			HTMLNode infoboxContent = peerTableInfobox.addChild("div", "class",
					"n2ntm-message-text");
			infoboxContent.addChild("#", message);
			HTMLNode list = peerTableInfobox.addChild("ul");
			Toadlet.addHomepageLink(list);
			list.addChild("li").addChild("a", new String[] { "href", "title" },
					new String[] { "/friends/", l10n("returnToFriends") },
					l10n("friends"));
			this.writeHTMLReply(ctx, 200, "OK", pageNode.generate());
			return;
		}
		MultiValueTable<String, String> headers = new MultiValueTable<String, String>();
		headers.put("Location", "/friends/");
		ctx.sendReplyHeaders(302, "Found", headers, null, 0);
	}

	public static void createN2NTMSendForm(HTMLNode pageNode,
			HTMLNode contentNode, ToadletContext ctx, HashMap<String, String> peers)
			throws ToadletContextClosedException, IOException {
		HTMLNode infobox = contentNode.addChild("div", new String[] { "class",
				"id" }, new String[] { "infobox", "n2nbox" });
		infobox.addChild("div", "class", "infobox-header", l10n("sendMessage"));
		HTMLNode messageTargets = infobox.addChild("div", "class",
				"infobox-content");
		messageTargets.addChild("p", l10n("composingMessageLabel"));
		HTMLNode messageTargetList = messageTargets.addChild("ul");
		// Iterate peers
		for (String peer_name: peers.values()) {
			messageTargetList.addChild("li", peer_name);
		}
		HTMLNode infoboxContent = infobox.addChild("div", "class",
				"infobox-content");
		HTMLNode messageForm = ctx.addFormChild(infoboxContent, "/send_n2ntm/",
				"sendN2NTMForm");
		// Iterate peers
		for (String peerNodeHash : peers.keySet()) {
			messageForm.addChild("input", new String[] { "type", "name",
					"value" }, new String[] { "hidden", "node_" + peerNodeHash,
					"1" });
		}
		messageForm.addChild("textarea", new String[] { "id", "name", "rows",
				"cols" }, new String[] { "n2ntmtext", "message", "8", "74" });
		messageForm.addChild("br");
		messageForm.addChild("#", "You may attach a file:");
		messageForm.addChild("input", new String[] { "type", "name", "value" },
				new String[] { "text", "filename", "" });
		messageForm.addChild("input", new String[] { "type", "name", "value" },
				new String[] { "submit", "send", l10n("sendMessageShort") });
	}

	@Override
	public String path() {
		return "/send_n2ntm/";
	}
}
