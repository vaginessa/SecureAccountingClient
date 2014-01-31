package edu.ucla.cs.wing.accountingclient;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.net.SocketAddress;
import java.net.SocketException;
import java.net.SocketTimeoutException;
import java.net.URL;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.Random;

import javax.net.ssl.HttpsURLConnection;
import javax.xml.datatype.Duration;

import edu.ucla.cs.wing.accountingclient.EventLog.Type;
import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

public class GatewayComm {

	public static final int MAX_RETX = 20;
	public static final int RETX_INTERVAL = 100;

	private DatagramSocket socket = null;
	private DatagramSocket socket2 = null;

	private Context context;

	public static final int CMD_NULL = 0;
	public static final int CMD_START_DATA = 1;
	public static final int CMD_STOP_DATA = 2;
	public static final int CMD_PAUSE_DATA = 3;
	public static final int CMD_RESUME_DATA = 4;
	public static final int CMD_START_SPAM = 5;
	public static final int CMD_STOP_SPAM = 6;
	public static final int CMD_AUTH_REQ = 7;
	public static final int CMD_AUTH_RESP = 8;
	public static final int CMD_START_PROBE = 9;
	public static final int CMD_STOP_PROBE = 10;
	public static final int CMD_PROBE_PKT = 11;

	public static final int CMD_TA_UPDATE = 12;
	public static final int CMD_TA_UPDATE_ACK = 13;
	public static final int CMD_RA_UPDATE = 14;
	public static final int CMD_RA_UPDATE_ACK = 15;
	public static final int CMD_LOC_UPDATE_PROB = 16;
	public static final int CMD_LOC_UPDATE_PROB_ACK = 17;

	private String serverIp = null;
	private int signalingPort = 0;
	private int dataPort = 0;
	private int probingPort = 0;
	private int dataRate = 0;

	private int probingMethod = 0;
	private int probingThresLoss = 0;
	private int probingRate = 0;
	private int probingThresRate = 0;
	private int probingCheckInterval = 0;

	private int dummyUpdate = 0;
	private int taUpdateDurationMin = 0;
	private int taUpdateDurationMax = 0;
	private int raUpdateDurationMin = 0;
	private int raUpdateDurationMax = 0;

	private boolean dummyWebConnected = false;

	private boolean dataStarted = false;

	private boolean pendingTaUpdate = false;
	private boolean pendingRaUpdate = false;

	private Random random;

	public void setPendingTaUpdate(boolean pendingTaUpdate) {
		this.pendingTaUpdate = pendingTaUpdate;
	}

	public void setPendingRaUpdate(boolean pendingRaUpdate) {
		this.pendingRaUpdate = pendingRaUpdate;
	}

	public boolean isPendingTaUpdate() {
		return pendingTaUpdate;
	}

	public boolean isPendingRaUpdate() {
		return pendingRaUpdate;
	}

	public boolean isDataStarted() {
		return dataStarted;
	}

	private boolean acked = true;
	private int traffic = 0;
	private int remoteTraffic = 0;

	public int getRemoteTraffic() {
		return remoteTraffic;
	}

	private boolean startRecv = false;

	public GatewayComm(Context context) {
		this.context = context;

		random = new Random(System.currentTimeMillis());

	}

	public void init() {
		try {
			socket = new DatagramSocket();
			socket2 = new DatagramSocket();

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

		probingMethod = Integer
				.parseInt(prefs.getString("probing_method", "0"));
		probingThresLoss = Integer.parseInt(prefs.getString(
				"probing_thres_loss",
				context.getString(R.string.pref_default_probing_thres_loss)));
		probingRate = Integer.parseInt(prefs.getString("probing_rate",
				context.getString(R.string.pref_default_probing_rate)));
		probingThresRate = Integer.parseInt(prefs.getString(
				"probing_thres_rate",
				context.getString(R.string.pref_default_probing_thres_rate)));
		probingCheckInterval = Integer
				.parseInt(prefs.getString(
						"probing_check_interval",
						context.getString(R.string.pref_default_probing_check_interval)));

		dummyUpdate = Integer.parseInt(prefs.getString("dummy_update", "0"));
		taUpdateDurationMin = Integer
				.parseInt(prefs.getString(
						"ta_update_duration_min",
						context.getString(R.string.pref_default_ta_update_duration_min)));
		taUpdateDurationMax = Integer
				.parseInt(prefs.getString(
						"ta_update_duration_max",
						context.getString(R.string.pref_default_ta_update_duration_max)));
		raUpdateDurationMin = Integer
				.parseInt(prefs.getString(
						"ra_update_duration_min",
						context.getString(R.string.pref_default_ra_update_duration_min)));
		raUpdateDurationMax = Integer
				.parseInt(prefs.getString(
						"ra_update_duration_max",
						context.getString(R.string.pref_default_ra_update_duration_max)));

	}

	private int sendSignaling(DatagramSocket sock, byte[] buffer) {
		int res = 0;
		DatagramPacket req;
		try {
			req = new DatagramPacket(buffer, buffer.length,
					InetAddress.getByName(serverIp), signalingPort);
			sock.send(req);
		} catch (Exception e1) {
			EventLog.write(Type.DEBUG, e1.toString());
		}
		return res;
	}

	private int sendSignalingReliably(DatagramSocket sock, byte[] buffer) {
		int res = 0;
		try {
			DatagramPacket req = new DatagramPacket(buffer, buffer.length,
					InetAddress.getByName(serverIp), signalingPort);
			sock.send(req);

			// socket.setSoTimeout(RETX_INTERVAL); // set the timeout in
			// millisecounds.
			int retxCnt = 0;
			acked = false;
			while (true) { // recieve data until timeout
				sock.send(req);
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

	public void connectDummyWeb() {
		updateParameter();

		if (dummyWebConnected) {
			dummyWebConnected = false;
		} else {
			dummyWebConnected = true;
			new Thread() {
				@Override
				public void run() {
					try {
						Socket socket = new Socket();
						socket.connect(new InetSocketAddress(serverIp,
								signalingPort));
						InputStreamReader reader = new InputStreamReader(
								socket.getInputStream());
						OutputStreamWriter writer = new OutputStreamWriter(
								socket.getOutputStream());
						char[] buf = new char[1000];
						writer.write(buf);
						writer.flush();
						EventLog.write(Type.DEBUG, "send request");
						while (dummyWebConnected) {
							reader.read(buf);

						}
					} catch (IOException e) {
						// TODO Auto-generated catch block
						EventLog.write(Type.DEBUG, e.toString());
					}

				}
			}.start();
		}

	}

	public void recv() {
		byte[] inBuf = new byte[2000];
		DatagramPacket response = new DatagramPacket(inBuf, inBuf.length);

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
					traffic += response.getLength();

					if (!acked) {
						acked = true;
					}
				}

				if (response.getLength() < 100) {
					ByteBuffer byteBuffer = ByteBuffer.wrap(inBuf);
					int cmd = byteBuffer.getInt();
					EventLog.write(Type.DEBUG, "recv signaling: " + cmd);
					switch (cmd) {
					case CMD_STOP_DATA: {
						int remoteTrafficVolume = byteBuffer.getInt();
						EventLog.write(Type.DATAFLOW, "TRAFFIC"
								+ EventLog.SEPARATOR + traffic
								+ EventLog.SEPARATOR + remoteTrafficVolume);
						remoteTraffic = remoteTrafficVolume;
						break;
					}
					case CMD_STOP_SPAM: {
						int remoteTrafficVolume = byteBuffer.getInt();
						int icmpCnt = byteBuffer.getInt();
						EventLog.write(Type.SPAMFLOW, "TRAFFIC"
								+ EventLog.SEPARATOR + traffic
								+ EventLog.SEPARATOR + remoteTrafficVolume
								+ EventLog.SEPARATOR + icmpCnt);
						break;
					}
					case CMD_TA_UPDATE_ACK:
						if (pendingTaUpdate) {
							EventLog.write(Type.LOCATION, "TAUA");
							pendingTaUpdate = false;
						}

						break;
					case CMD_RA_UPDATE_ACK:
						if (pendingRaUpdate) {
							EventLog.write(Type.LOCATION, "RAUA");
							pendingRaUpdate = false;
						}
						break;
						
					case CMD_LOC_UPDATE_PROB_ACK:
						EventLog.write(Type.LOCATION, "PROBA");
						break;
					default:
						break;
					}
				}

			} catch (SocketTimeoutException e1) {

			} catch (IOException e) {
				EventLog.write(Type.DEBUG, e.toString());
			}
		}

		EventLog.write(Type.DEBUG, "Finish recv data");

	}

	public int getTrafficVolume() {
		return traffic;
	}

	public void sendProbePkt() {
		int size = probingMethod == 1 ? 1024 : 100;
		ByteBuffer buffer = ByteBuffer.allocate(size);
		buffer.putInt(CMD_PROBE_PKT);

		try {
			DatagramPacket req = new DatagramPacket(buffer.array(),
					buffer.array().length, InetAddress.getByName(serverIp),
					probingPort);
			socket.send(req);
		} catch (IOException e) {
		}

	}

	public void sendNullPkt() {
		updateParameter();
		ByteBuffer buffer = ByteBuffer.allocate(4);
		buffer.putInt(CMD_NULL);
		sendSignaling(socket, buffer.array());
	}

	public void startData() {

		EventLog.write(Type.DATAFLOW, "START");

		updateParameter();
		ByteBuffer buffer = ByteBuffer.allocate(8);
		buffer.putInt(CMD_START_DATA);
		buffer.putInt(dataRate);

		// sendSignalingReliably(buffer.array());
		sendSignaling(socket, buffer.array());

		dataStarted = true;
		traffic = 0;
	}

	public void stopData() {
		EventLog.write(Type.DATAFLOW, "STOP");

		ByteBuffer buffer = ByteBuffer.allocate(4);
		buffer.putInt(CMD_STOP_DATA);
		sendSignaling(socket, buffer.array());

		dataStarted = false;

		try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {
		}

	}

	public void startProbing() {
		EventLog.write(Type.PROBING, "START");
		updateParameter();

		ByteBuffer buffer = ByteBuffer.allocate(20);
		buffer.putInt(CMD_START_PROBE);
		buffer.putInt(probingMethod);
		switch (probingMethod) {
		case 0:
			buffer.putInt(probingCheckInterval);
			buffer.putInt(probingThresLoss);
			break;
		case 1:
			buffer.putInt(probingCheckInterval);
			buffer.putInt(probingRate);
			buffer.putInt(probingThresRate);
			break;
		default:
			return;
		}

		sendSignaling(socket, buffer.array());
	}

	public void stopProbing() {
		EventLog.write(Type.PROBING, "STOP");

		ByteBuffer buffer = ByteBuffer.allocate(12);
		buffer.putInt(CMD_STOP_PROBE);
		buffer.putInt(probingRate);
		buffer.putInt(probingThresRate);

		sendSignaling(socket, buffer.array());
	}

	public void startSpam(int rate) {
		updateParameter();

		EventLog.write(Type.SPAMFLOW, "START");

		ByteBuffer buffer = ByteBuffer.allocate(8);

		buffer.putInt(CMD_START_SPAM);
		buffer.putInt(rate);

		// sendSignalingReliably(buffer.array());
		DatagramSocket tmpSocket;
		try {
			tmpSocket = new DatagramSocket();
			sendSignaling(tmpSocket, buffer.array());
			tmpSocket.close();
		} catch (SocketException e) {
			EventLog.write(Type.DEBUG, e.toString());
		}

		dataStarted = true;
		traffic = 0;

	}

	public void startSpam() {
		updateParameter();
		startSpam(dataRate);
	}

	public void stopSpam() {

		EventLog.write(Type.SPAMFLOW, "STOP");

		ByteBuffer buffer = ByteBuffer.allocate(4);
		buffer.putInt(CMD_STOP_SPAM);
		sendSignaling(socket, buffer.array());

		dataStarted = false;

		try {
			Thread.sleep(3000);
		} catch (InterruptedException e) {
		}
		// EventLog.close();

	}

	public void pauseData() {
		EventLog.write(Type.DATAFLOW, "PAUSE");

		ByteBuffer buffer = ByteBuffer.allocate(4);
		buffer.putInt(CMD_PAUSE_DATA);

		// sendSignalingReliably(buffer.array());
		sendSignaling(socket, buffer.array());

	}

	public void resumeData() {
		EventLog.write(Type.DATAFLOW, "RESUME");

		ByteBuffer buffer = ByteBuffer.allocate(4);
		buffer.putInt(CMD_RESUME_DATA);

		sendSignalingReliably(socket, buffer.array());
	}

	public void conn() {
		updateParameter();

		long t1, t2;
		try {
			sendNullPkt();

			t1 = System.currentTimeMillis();
			Socket tcpSocket = new Socket();
			tcpSocket.connect(new InetSocketAddress(serverIp, 80));
			t2 = System.currentTimeMillis();
			tcpSocket.close();
			EventLog.write(Type.CONN, "0" + EventLog.SEPARATOR + (t2 - t1));
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}

	}

	public void connAuth() {
		updateParameter();

		long t1, t2;

		ByteBuffer buffer = ByteBuffer.allocate(4);
		buffer.putInt(CMD_AUTH_REQ);
		byte[] outBuf = buffer.array();

		byte[] inBuf = new byte[4];
		DatagramPacket response = new DatagramPacket(inBuf, inBuf.length);

		Socket tcpSocket = null;
		try {
			DatagramPacket req = new DatagramPacket(outBuf, outBuf.length,
					InetAddress.getByName(serverIp), signalingPort);
			socket2.setSoTimeout(1000);

			sendNullPkt();

			t1 = System.currentTimeMillis();
			tcpSocket = new Socket();

			socket2.send(req);
			socket2.receive(response);

			tcpSocket.connect(new InetSocketAddress(serverIp, 80));
			t2 = System.currentTimeMillis();

			EventLog.write(Type.CONN, "1" + EventLog.SEPARATOR + (t2 - t1));
		} catch (IOException e) {

		}

		if (tcpSocket != null)
			try {
				tcpSocket.close();
			} catch (IOException e) {

			}

	}

	public void sendTaUpdate() {
		updateParameter();

		ByteBuffer buffer = ByteBuffer.allocate(8);
		int taDuration = taUpdateDurationMin == taUpdateDurationMax ? taUpdateDurationMin
				: taUpdateDurationMin
						+ random.nextInt(taUpdateDurationMax
								- taUpdateDurationMin);
		pendingTaUpdate = true;
		if (dummyUpdate == 0) {
			buffer.putInt(CMD_TA_UPDATE);
			buffer.putInt(taDuration);
			sendSignaling(socket, buffer.array());
			EventLog.write(Type.LOCATION, "TAU" + EventLog.SEPARATOR + taDuration);			
		} else {
			try {
				Thread.sleep(taDuration);
			} catch (InterruptedException e) {
			
			}
			pendingTaUpdate = false;
		}
		

	}

	public void sendRaUpdate() {
		updateParameter();

		ByteBuffer buffer = ByteBuffer.allocate(8);
		int raDuration = raUpdateDurationMin == raUpdateDurationMax ? raUpdateDurationMin
				: raUpdateDurationMin
						+ random.nextInt(raUpdateDurationMax
								- raUpdateDurationMin);
		pendingRaUpdate = true;
		
		if (dummyUpdate == 0) {
			buffer.putInt(CMD_RA_UPDATE);
			buffer.putInt(raDuration);
			sendSignaling(socket, buffer.array());
			EventLog.write(Type.LOCATION, "RAU" + EventLog.SEPARATOR + raDuration);
		} else {			
			try {
				Thread.sleep(raDuration);
			} catch (InterruptedException e) {
			
			}
			pendingRaUpdate = false;
		}
	}
	
	public void sendLocUpdateProbe() {
		updateParameter();
		ByteBuffer buffer = ByteBuffer.allocate(4);
		buffer.putInt(CMD_LOC_UPDATE_PROB);
		EventLog.write(Type.LOCATION, "PROB");
		sendSignaling(socket, buffer.array());
	}

}
