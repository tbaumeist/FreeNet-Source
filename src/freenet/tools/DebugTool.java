package freenet.tools;

import freenet.node.Node;
import DebugMessenger.*;

public class DebugTool {
	
	private static DebugTool _instance = null;
	
	private String _ip = "";
	private Node _node = null;
	private int _port = 0;
	private DebugMessengerClientSender _sender = null;
	
	private DebugTool()
	{
	}
	
	public void sendMessage(DebugMessage mess)
	{
		if(_ip.equals("") || _port == 0)
			return;
		
		DebugMessengerClientSender sender = getSender();

		if(_node == null || 
				_node.ipDetector == null ||
				_node.ipDetector.lastIPAddress.length == 0)
			return;
		String ip = _node.ipDetector.lastIPAddress[0].getAddress().toString().replace("/","");
		mess.setUniqueId(ip);
		
		sender.SendMessage(mess);
	}
	
	public boolean getInsertAttackLock() throws Exception
	{
		DebugMessengerClientSender sender = getSender();
		return sender.getInsertLock();
	}
	public boolean getRequestAttackLock() throws Exception
	{
		DebugMessengerClientSender sender = getSender();
		return sender.getRequestLock();
	}
	public void setInsertAttackLock(boolean set) throws Exception
	{
		DebugMessengerClientSender sender = getSender();
		sender.setInsertLock(set);
	}
	public void setRequestAttackLock(boolean set) throws Exception
	{
		DebugMessengerClientSender sender = getSender();
		sender.setRequestLock(set);
	}
	
	public void resetConnection()
	{
		_sender = null;
	}
	
	public void setServerInformation(String ip, int port)
	{
		_ip = ip;
		_port = port;
	}
	
	private DebugMessengerClientSender getSender()
	{
		if(_sender == null)
			_sender = new DebugMessengerClientSender(_ip, _port);
		return _sender;
	}
	
	public void setNode(Node node)
	{
		_node = node;
	}
	
	public static DebugTool getInstance()
	{
		if(_instance == null)
			_instance = new DebugTool();
		return _instance;
	}

}
