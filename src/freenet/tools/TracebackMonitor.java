package freenet.tools;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.PrintWriter;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Date;
import java.util.Hashtable;
import java.util.List;

public class TracebackMonitor extends Thread {
	private boolean _filterInsert = false;
	private boolean _filterRequest = false;
	
	private String[] _attackCloadIPs = null;
	private String _saveDirectory = "/Desktop/Freenet_Data/Monitor/";
	private File _outputFile = null;
	
	private ArrayList<Long> _seenUids = new ArrayList<Long>();
	
	public void setAttackCloadIP(String[] ips) throws IOException
	{
		_attackCloadIPs = ips;
		Date time = new Date();
		String fileName = time.toString().replace(" ", "_").replace(":", "-");
		_outputFile = new File(System.getProperty("user.home")+_saveDirectory+"attack_results_"+fileName+".dat");
		_outputFile.getParentFile().mkdirs();
		_outputFile.createNewFile();
	}
	
	public void setInsertFilter(boolean set)
	{
		_filterInsert = set;
	}
	
	public void setRequestFilter(boolean set)
	{
		_filterRequest = set;
	}
	
	public boolean shouldAttackInsert()
	{
		return _filterInsert;
	}
	
	public boolean shouldAttackRequest()
	{
		return _filterRequest;
	}
	
	public void attackInsert(long uid, int htl)
	{
		if(_seenUids.contains(uid))
			return;
		_seenUids.add(uid);
		AttackThread attack = new AttackThread(true, uid, htl, _attackCloadIPs);
		attack.start();
	}
	
	public void attackRequest(long uid, int htl)
	{
		if(_seenUids.contains(uid))
			return;
		_seenUids.add(uid);
		AttackThread attack = new AttackThread(false, uid, htl, _attackCloadIPs);
		attack.start();
	}

	private class AttackThread extends Thread {
		private long _uid = -1;
		private boolean _isInsert = true;
		private String[] _attackCloadIPs = null;
		private int _attackCloadPort = 2323;
		private int _htl = 0;

		public AttackThread(boolean isInsert, long uid, int htl, String[] ips) {
			_uid = uid;
			_isInsert = isInsert;
			_htl = htl;
			_attackCloadIPs = ips;
		}
		public void run()
		{
			try
			{
				if(_attackCloadIPs == null)
					return;
				
				// wait for the control flow signal to begin
				DebugTool tool = DebugTool.getInstance();
				System.out.println("Checking if it is locked");
				while(isLocked(_isInsert, tool) )
				{
					// sleep 20 seconds and try again
					System.out.println("Its locked [sleep]");
					Thread.sleep(20*1000);
				}
				System.out.println("Its unlocked");
				// ok we got the all clear
				
				Hashtable<String, Boolean> results = new Hashtable<String,Boolean>();
				boolean error = false;
				for(String ip : _attackCloadIPs)
				{
					Socket socket = new Socket(ip, _attackCloadPort);
					PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
					
					AttackResponseReader reader = new AttackResponseReader(socket.getInputStream());
					reader.start();
					out.println("TRACEBACKATTACK:"+_uid);
					reader.join(); // wait for results
					socket.close();
					
					error = error || reader.hadError();
					
					for(String key : reader.getResults().keySet())
					{
						if(!results.containsKey(key)) // add new entries
							results.put(key, reader.getResults().get(key));
						if(reader.getResults().get(key) == false) // update false entries
							results.put(key, reader.getResults().get(key));
					}
				}
				
				
				write(_outputFile, error, results, _htl);
				
				// 10 seconds of buffer time
				Thread.sleep(10000); 
				// tell them that we are done
				if(_isInsert)
					tool.setInsertAttackLock(true);
				else
					tool.setRequestAttackLock(true);
			}catch(Exception e)
			{
				
			}
		}
		private synchronized void write(File f, boolean error, Hashtable<String, Boolean> results, int htl) throws Exception
		{
			if(f == null)
				return;
			
			FileWriter writer = new FileWriter(f, true);
			if(error)
				writer.write("~ERROR ");
			writer.write(_uid+":"+ htl+":");
			for(String ip : results.keySet())
				writer.write(ip+","+(results.get(ip)?"true":"false")+",");
			writer.write("\n");
			writer.flush();
			writer.close();
			
		}
		private boolean isLocked(boolean isInsert, DebugTool tool) throws Exception
		{
			if( isInsert )
				return tool.getInsertAttackLock();
			return tool.getRequestAttackLock();
		}
	}
	
	private class AttackResponseReader extends Thread
	{
		private InputStream _input;
		private Hashtable<String, Boolean> _results = new Hashtable<String, Boolean>();
		private boolean _error = false;
		public AttackResponseReader(InputStream i)
		{
			_input = i;
		}
		public Hashtable<String, Boolean> getResults()
		{
			return _results;
		}
		public boolean hadError()
		{
			return _error;
		}
        public void run() {
            try {
                BufferedReader br = new BufferedReader(new InputStreamReader(_input));
                String line;
                while ((line = br.readLine()) != null) {
                	line = line.toLowerCase();
                	System.out.println(line);
                    if(line.contains("result:"))
					{
                    	String[] parsed = line.split(":");
                    	boolean present = parsed[3].contains("true");
                    	String ip = parsed[1];
                    	// have not seen it yet
                    	if(!_results.containsKey(ip))
                    		_results.put(ip, present);
                    	
                    	// false values get precedence over true
                    	if(!present)
                    		_results.put(ip, present);
					}
                    if(line.startsWith("attack_complete"))
                    	break;
                }
            } catch (java.io.IOException e) {
            	_error = true;
            }
        }
    }
	
}
