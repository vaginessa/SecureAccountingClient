package edu.ucla.cs.wing.accountingclient;

import java.util.Timer;
import java.util.TimerTask;

import edu.ucla.cs.wing.accountingclient.EventLog.Type;
import android.app.Service;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.IBinder;
import android.preference.PreferenceManager;

public class BackgroundService extends Service implements ICommander {

	private static ICommander commander;

	public static ICommander getCommander() {
		return commander;
	}

	private Timer monitorTimer;
	private Timer callTimer;
	private Timer signalingTimer;

	private GatewayComm gatewayComm;
	private MobileInfo mobileInfo;

	private CallTask callTask;

	private SharedPreferences prefs;

	@Override
	public void onCreate() {
		super.onCreate();

		commander = this;

		prefs = PreferenceManager.getDefaultSharedPreferences(this);

		MobileInfo.init(this);
		mobileInfo = MobileInfo.getInstance();
		
		PhoneCall.init(this);
		CallMonitor.init(this);
		
		gatewayComm = new GatewayComm(this);
		gatewayComm.init();

		monitorTimer = new Timer();
		long monitorInterval = Long.parseLong(prefs.getString(
				"monitor_interval",
				getString(R.string.pref_default_monitor_interval)));
		monitorTimer.schedule(new MonitorTask(), 0, monitorInterval);

		callTimer = new Timer();
		signalingTimer = new Timer();

	}

	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	public void onDestroy() {
		super.onDestroy();
		
		gatewayComm.destroy();
	}

	public class MonitorTask extends TimerTask {

		private int networkType = 0;
		private int callState = 0;

		@Override
		public void run() {
			// handover
			int nt = mobileInfo.getNetworkType();
			if (nt != networkType) {
				EventLog.write(Type.HANDOVER, "" + networkType
						+ EventLog.SEPARATOR + nt);
				if (networkType == 13
						&& (nt == 3 || nt == 15 || nt == 8 || nt == 10)) {
					// downgrade
					onHandover4Gto3G();
				} else if ((networkType == 3 || networkType == 15
						|| networkType == 8 || networkType == 10)
						&& nt == 13) {
					// upgrade
					onhandover3Gto4G();
				}
			}
			networkType = nt;

			// call state
			int cs = mobileInfo.getCallState();
			if (cs != callState) {
				EventLog.write(Type.CALLSTATE, "" + callState
						+ EventLog.SEPARATOR + cs);
			}
			callState = cs;

			StringBuilder sb = new StringBuilder();
			sb.append(mobileInfo.getNetworkType());
			sb.append(EventLog.SEPARATOR);
			sb.append(mobileInfo.getSignalStrengthDBM());
			sb.append(EventLog.SEPARATOR);
			sb.append(mobileInfo.getMobileRxByte());
			sb.append(EventLog.SEPARATOR);
			sb.append(mobileInfo.getMobileTxByte());
			sb.append(EventLog.SEPARATOR);
			sb.append(mobileInfo.getCellId());
			sb.append(EventLog.SEPARATOR);
			sb.append(mobileInfo.getCallState());
			EventLog.write(Type.MONITOR, sb.toString());
		}

	}

	public static class CallTask extends TimerTask {

		private String phoneNum;
		private boolean avoidOvercharging;
		private long callDuration;
		private long pauseAdvance;

		public boolean isAvoidOvercharging() {
			return avoidOvercharging;
		}

		public void setPauseAdvance(long pauseAdvance) {
			this.pauseAdvance = pauseAdvance;
		}

		private ICommander commander;

		private int state = 0;

		public int getState() {
			return state;
		}

		public void setState(int state) {
			EventLog.write(Type.DEBUG, "CallTask.setState() " + state);
			this.state = state;
		}

		public long getPauseAdvance() {
			return pauseAdvance;
		}

		public CallTask(String phoneNum, boolean avoidOvercharging,
				long callDuration, long pauseAdvance, ICommander commander) {
			this.phoneNum = phoneNum;
			this.avoidOvercharging = avoidOvercharging;
			this.callDuration = callDuration;
			this.pauseAdvance = pauseAdvance;

			this.commander = commander;
		}

		@Override
		public void run() {
			// TODO Auto-generated method stub

			try {
				setState(1);
				PhoneCall.call(phoneNum);
				Thread.sleep(callDuration);

				setState(4);
				if (avoidOvercharging) {
					commander.pauseData();
				}

				setState(5);
				Thread.sleep(pauseAdvance);
				PhoneCall.endCall(phoneNum);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
	}

	@Override
	public void onOutgoingCall(String phoneNum) {
		EventLog.write(Type.DEBUG, "onOutgoingCall()");
		if (callTask != null && callTask.getState() == 1) {
			if (callTask.isAvoidOvercharging()) {
				pauseData();
			}

			try {
				Thread.sleep(callTask.getPauseAdvance());
			} catch (InterruptedException e) {
			}
			callTask.setState(2);
		}
	}

	@Override
	public void csfbCall(boolean avoidOvercharging) {
		SharedPreferences prefs = PreferenceManager
				.getDefaultSharedPreferences(this);

		String phoneNum = prefs.getString("phone_num",
				getString(R.string.pref_default_phone_num));
		long callDuration = Long.parseLong(prefs.getString("call_duration",
				getString(R.string.pref_default_call_duration)));
		long pauseAdvance = Long.parseLong(prefs.getString("pause_advance",
				getString(R.string.pref_default_pause_advance)));

		callTask = new CallTask(phoneNum, avoidOvercharging, callDuration,
				pauseAdvance, this);
		callTimer.schedule(callTask, 0);
	}

	@Override
	public void startData() {
		signalingTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				gatewayComm.startData();
			}
		}, 0);
	}

	@Override
	public void stopData() {
		
		signalingTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				gatewayComm.stopData();
			}
		}, 0);
	}

	@Override
	public void pauseData() {
		signalingTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				gatewayComm.pauseData();
			}
		}, 0);

	}

	@Override
	public void resumeData() {
		signalingTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				gatewayComm.resumeData();
			}
		}, 0);

	}

	@Override
	public void startProbing() {
		// TODO Auto-generated method stub

	}

	@Override
	public void stopProbing() {
		// TODO Auto-generated method stub

	}

	@Override
	public void onHandover4Gto3G() {

		if (callTask != null && callTask.getState() == 2) {
			if (callTask.isAvoidOvercharging()) {
				resumeData();
			}
		}

	}

	@Override
	public void onhandover3Gto4G() {

		if (callTask != null && callTask.getState() == 5) {
			if (callTask.isAvoidOvercharging()) {
				resumeData();
			}
		}

	}

}
