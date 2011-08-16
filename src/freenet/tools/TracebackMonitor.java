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
import java.util.List;

public class TracebackMonitor extends Thread {
	private boolean _filterInsert = false;
	private boolean _filterRequest = false;
	
	private String _attackCloadIP = "";
	private String _saveDirectory = "/Desktop/Freenet_Data/Monitor/";
	private File _outputFile = null;
	
	private ArrayList<Long> _seenUids = new ArrayList<Long>();
	
	public void setAttackCloadIP(String ip) throws IOException
	{
		_attackCloadIP = ip;
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
		AttackThread attack = new AttackThread(true, uid, htl, _attackCloadIP);
		attack.start();
	}
	
	public void attackRequest(long uid, int htl)
	{
		if(_seenUids.contains(uid))
			return;
		_seenUids.add(uid);
		AttackThread attack = new AttackThread(false, uid, htl, _attackCloadIP);
		attack.start();
	}

	private class AttackThread extends Thread {
		private long _uid = -1;
		private boolean _isInsert = true;
		private String _attackCloadIP = "";
		private int _attackCloadPort = 2323;
		private int _htl = 0;

		public AttackThread(boolean isInsert, long uid, int htl, String ip) {
			_uid = uid;
			_isInsert = isInsert;
			_htl = htl;
			_attackCloadIP = ip;
		}
		public void run()
		{
			try
			{
				// wait for the control flow signal to begin
				DebugTool tool = DebugTool.getInstance();
				while(isLocked(_isInsert, tool) != false )
				{
					// sleep 20 seconds and try again
					Thread.sleep(20*1000);
				}
				
				// ok we got the all clear
				Socket socket = new Socket(_attackCloadIP, _attackCloadPort);
				PrintWriter out = new PrintWriter(socket.getOutputStream(), true);
				
				AttackResponseReader reader = new AttackResponseReader(socket.getInputStream());
				reader.start();
				out.println("TRACEBACKATTACK:"+_uid+":"+_htl);
				reader.join(); // wait for results
				socket.close();
				
				write(_outputFile, reader);
				
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
		private synchronized void write(File f, AttackResponseReader reader) throws Exception
		{
			if(f == null)
				return;
			
			FileWriter writer = new FileWriter(f, true);
			if(reader.hadError())
				writer.write("~ERROR ");
			writer.write(_uid+":");
			for(ResultData d : reader.getResults())
				writer.write(d.ip+","+d.htl+","+(d.present?"true":"false")+",");
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
		private ArrayList<ResultData> _results = new ArrayList<ResultData>();
		private boolean _error = false;
		public AttackResponseReader(InputStream i)
		{
			_input = i;	
		}
		public List<ResultData> getResults()
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
                    if(line.contains("result:"))
					{
                    	String[] parsed = line.split(":");
                    	boolean present = parsed[4].contains("true");
                    	int htl = Integer.parseInt(parsed[1]);
                    	_results.add(new ResultData(parsed[2],htl,present));
					}
                    if(line.startsWith("attack_complete"))
                    	break;
                }
            } catch (java.io.IOException e) {
            	_error = true;
            }
        }
    }
	private class ResultData
	{
		public String ip="";
		public boolean present;
		public int htl = 0;
		public ResultData(String s, int h, boolean b)
		{
			ip = s;
			present = b;
			htl = h;
		}
	}
	
}
