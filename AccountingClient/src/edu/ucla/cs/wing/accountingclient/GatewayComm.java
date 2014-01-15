package edu.ucla.cs.wing.accountingclient;

import java.io.IOException;
import java.io.OutputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.Socket;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;

import edu.ucla.cs.wing.accountingclient.EventLog.Type;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class GatewayComm {

	public static final int MAX_RETX = 20;
	public static final int RETX_INTERVAL = 100;

	private DatagramSocket socket = null;
	private DatagramSocket dataSocket = null;

	private Context context;

	public static final int CMD_START_DATA = 1;
	public static final int CMD_STOP_DATA = 2;
	public static final int CMD_PAUSE_DATA = 3;
	public static final int CMD_RESUME_DATA = 4;

	private String serverIp = "";
	private int signalingPort = 0;
	private int dataPort = 0;
	private int probingPort = 0;
	private int dataRate = 0;

	private boolean dataStarted = false;
	private boolean acked = true;
	private int trafficVolume = 0;
	private int remoteTrafficVolume = 0;
	
	private boolean startRecv = false;

	public GatewayComm(Context context) {
		this.context = context;

		
	}
	
	public void init() {
		try {
			socket = new DatagramSocket();
			//dataSocket = new DatagramSocket();
			
			startRecv = true;
			
			(new Thread() {
				@Override
				public void run() {
					recv();
				}
			}).start();
		} catch (SocketException e) {

		}
		
	}
	
	public void destroy() {
		startRecv = false;
		
	}

	private void updateParameter() {
		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(context);
		serverIp = prefs.getString("server_ip",
				context.getString(R.string.pref_default_server_ip));
		signalingPort = Integer.parseInt(prefs.getString("signaling_port",
				context.getString(R.string.pref_default_signaling_port)));
		dataPort = Integer.parseInt(prefs.getString("data_port",
				context.getString(R.string.pref_default_data_port)));
		probingPort = Integer.parseInt(prefs.getString("probing_port",
				context.getString(R.string.pref_default_probing_port)));
		dataRate = Integer.parseInt(prefs.getString("data_rate",
				context.getString(R.string.pref_default_data_rate)));
	}

	private int sendSignaling(byte[] buffer) {
		int res = 0;
		DatagramPacket req;
		try {
			req = new DatagramPacket(buffer, buffer.length,
					InetAddress.getByName(serverIp), signalingPort);
			socket.send(req);
		} catch (Exception e1) {
			EventLog.write(Type.DEBUG, e1.toString());
		}
		return res;
	}

	private int sendSignalingReliably(byte[] buffer) {
		int res = 0;
		try {
			DatagramPacket req = new DatagramPacket(buffer, buffer.length,
					InetAddress.getByName(serverIp), signalingPort);
			socket.send(req);

			// socket.setSoTimeout(RETX_INTERVAL); // set the timeout in
			// millisecounds.
			int retxCnt = 0;
			acked = false;
			while (true) { // recieve data until timeout
				socket.send(req);
				Thread.sleep(RETX_INTERVAL);

				if (acked || retxCnt > MAX_RETX) {
					break;
				} else {
					retxCnt++;
				}
			}

		} catch (SocketException e1) {

			// e1.printStackTrace();
			System.out.println("Socket closed " + e1);

		} catch (IOException e) {

			e.printStackTrace();
		} catch (InterruptedException e) {

			e.printStackTrace();
		}
		return res;
	}
	
	public void recv() {
		byte[] inBuf = new byte[2000];
		DatagramPacket response = new DatagramPacket(inBuf,
				inBuf.length);
		
		try {
			socket.setSoTimeout(100);
		} catch (SocketException e1) {
			e1.printStackTrace();
		}
		
		EventLog.write(Type.DEBUG, "Start recv data");

		while (startRecv) {
			try {
				socket.receive(response);
				
				if (dataStarted) {
					trafficVolume += response.getLength();

					if (!acked) {
						acked = true;
					}					
				}
				
				if (response.getLength() < 100) {
					ByteBuffer byteBuffer = ByteBuffer.wrap(inBuf);
					int cmd = byteBuffer.getInt();
					EventLog.write(Type.DEBUG, "recv signaling: " + cmd);
					if (cmd == CMD_STOP_DATA) {
						remoteTrafficVolume = byteBuffer.getInt();
						EventLog.write(Type.DATAFLOW, "TRAFFIC"
								+ EventLog.SEPARATOR + trafficVolume
								+ EventLog.SEPARATOR
								+ remoteTrafficVolume);
					}
				}
				
			} catch (SocketTimeoutException e1) {
				
			} catch (IOException e) {
				EventLog.write(Type.DEBUG, e.toString());
			} 
		}

		EventLog.write(Type.DEBUG, "Finish recv data");
		
	}

	public void startData() {
		EventLog.newLogFile(EventLog.genLogFileName(new String[] {"" + System.currentTimeMillis()}));
		
		EventLog.write(Type.DATAFLOW, "START");

		updateParameter();

		ByteBuffer buffer = ByteBuffer.allocate(8);
		buffer.putInt(CMD_START_DATA);
		buffer.putInt(dataRate);

		// sendSignalingReliably(buffer.array());
		sendSignaling(buffer.array());

		dataStarted = true;
		trafficVolume = 0;		
	}

	public void stopData() {
		EventLog.write(Type.DATAFLOW, "STOP");

		ByteBuffer buffer = ByteBuffer.allocate(4);
		buffer.putInt(CMD_STOP_DATA);
		sendSignaling(buffer.array());

		dataStarted = false;
		
		try {
			Thread.sleep(1000);
		} catch (InterruptedException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
		EventLog.close();
	}

	public void pauseData() {
		EventLog.write(Type.DATAFLOW, "PAUSE");

		ByteBuffer buffer = ByteBuffer.allocate(4);
		buffer.putInt(CMD_PAUSE_DATA);

		// sendSignalingReliably(buffer.array());
		sendSignaling(buffer.array());

	}

	public void resumeData() {
		EventLog.write(Type.DATAFLOW, "RESUME");

		ByteBuffer buffer = ByteBuffer.allocate(4);
		buffer.putInt(CMD_RESUME_DATA);
		// sendSignalingReliably(buffer.array());
		sendSignalingReliably(buffer.array());
	}

}
