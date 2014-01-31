package edu.ucla.cs.wing.accountingclient;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Date;
import java.util.Timer;
import java.util.TimerTask;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import edu.ucla.cs.wing.accountingclient.EventLog.Type;
import android.R.integer;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.os.Handler;
import android.os.IBinder;
import android.os.Message;
import android.preference.PreferenceManager;

public class BackgroundService extends Service implements ICommander {

	private static ICommander commander;

	public static ICommander getCommander() {
		return commander;
	}

	private Timer monitorTimer;
	private Timer callTimer;
	private Timer dataTimer;
	private Timer probingTimer;
	private Timer autoTestTimer;

	private Timer raUpdateTimer;
	private Timer taUpdateTimer;

	private GatewayComm gatewayComm;
	private MobileInfo mobileInfo;

	private CsfbCallTask callTask;

	private AlarmManager alarmManager;
	private PendingIntent alarmIntent;

	private SharedPreferences prefs;

	private int runs = 0;

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
		monitorTimer.schedule(new UpdateUitask(), 0, 1000);

		callTimer = new Timer();
		dataTimer = new Timer();
		probingTimer = new Timer();
		autoTestTimer = new Timer();

		alarmManager = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
	}

	@Override
	public IBinder onBind(Intent intent) {

		return null;
	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		gatewayComm.destroy();
	}

	public class UpdateUitask extends TimerTask {

		private int traffic = 0;

		@Override
		public void run() {
			if (true) {
				Handler handler = MainActivity.getHandler();
				if (handler != null) {
					int t = gatewayComm.getTrafficVolume();
					int remoteTraffic = gatewayComm.getRemoteTraffic();
					int rate = (t - traffic) * 8 / 1024;
					StringBuilder info = new StringBuilder();
					info.append(t);
					info.append(";");
					info.append(remoteTraffic);
					info.append(";");
					info.append(rate);
					info.append(";");
					info.append(mobileInfo.getSignalStrengthDBM());
					info.append(";");
					// String info = "" + t + ";" + remoteTraffic + ";" + rate ;

					Message msg = new Message();
					msg.what = Msg.MSG_TRAFFIC_INFO;
					msg.obj = info.toString();
					handler.sendMessage(msg);

					traffic = t;
				}
			}

		}

	}

	public class MonitorTask extends TimerTask {

		private int networkType = 0;
		private int callState = 0;

		private Pattern patternCPU = Pattern
				.compile("\\s*(\\d+)\\s+(\\d+)%\\s+.*\\s+([^\\s]+)");

		private int getCpuUtilization() {
			int utilization = 0;

			BufferedReader in = null;

			try {
				Process process = null;
				process = Runtime.getRuntime().exec("top -n 1 -d 0.05");

				in = new BufferedReader(new InputStreamReader(
						process.getInputStream()));

				String line = "";
				while ((line = in.readLine()) != null) {
					Matcher matcher = patternCPU.matcher(line);
					if (matcher.find()) {
						int pid = Integer.parseInt(matcher.group(1).toString());
						int cpuUsage = Integer.parseInt(matcher.group(2)
								.toString());
						String app = matcher.group(3).toString();
						if (!app.equals("top")) {
							utilization += cpuUsage;
						}

					}
				}
			} catch (IOException e) {
				e.printStackTrace();
			} finally {
				if (in != null) {
					try {
						in.close();
					} catch (IOException e) {
						e.printStackTrace();
					}
				}
			}

			return utilization;

		}

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
			sb.append(EventLog.SEPARATOR);
			sb.append(gatewayComm.getTrafficVolume());
			//sb.append(EventLog.SEPARATOR);
			//sb.append(getCpuUtilization());
			EventLog.write(Type.MONITOR, sb.toString());
		}

	}

	public class CsfbCallTask extends TimerTask {

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

		public CsfbCallTask(String phoneNum, boolean avoidOvercharging,
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
					if (!mobileInfo.getOperatorName().equals("T-Mobile")) {
						commander.pauseData();
					}
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

		callTask = new CsfbCallTask(phoneNum, avoidOvercharging, callDuration,
				pauseAdvance, this);
		callTimer.schedule(callTask, 0);
	}

	@Override
	public void startData() {
		dataTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				gatewayComm.startData();
			}
		}, 0);
	}

	@Override
	public void stopData() {

		dataTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				gatewayComm.stopData();
			}
		}, 0);
	}

	@Override
	public void pauseData() {
		dataTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				gatewayComm.pauseData();
			}
		}, 0);

	}

	@Override
	public void resumeData() {
		dataTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				gatewayComm.resumeData();
			}
		}, 0);

	}

	@Override
	public void startSpam() {
		dataTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				gatewayComm.startSpam();
			}
		}, 0);

	}

	@Override
	public void stopSpam() {
		dataTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				gatewayComm.stopSpam();
			}
		}, 0);
	}

	@Override
	public void startProbing() {
		dataTimer.schedule(new TimerTask() {

			@Override
			public void run() {
				gatewayComm.startProbing();

			}
		}, 0);

		if (probingTimer == null) {
			probingTimer = new Timer();
		}
		int probingMethod = Integer.parseInt(prefs.getString("probing_method",
				"0"));
		long interval;
		switch (probingMethod) {
		case 0:
			interval = Integer.parseInt(prefs.getString(
					"probing_check_interval",
					getString(R.string.pref_default_probing_check_interval)));
			probingTimer.schedule(new TimerTask() {
				@Override
				public void run() {
					gatewayComm.sendProbePkt();

				}
			}, 0, interval);
			break;
		case 1:
			int probingRate = Integer.parseInt(prefs.getString("probing_rate",
					getString(R.string.pref_default_probing_rate)));
			interval = 8 * 1024 * 1000 / (probingRate * 1024);
			probingTimer.schedule(new TimerTask() {
				@Override
				public void run() {
					gatewayComm.sendProbePkt();

				}
			}, 0, interval);
			break;
		default:
			break;
		}
	}

	@Override
	public void stopProbing() {
		dataTimer.schedule(new TimerTask() {

			@Override
			public void run() {
				gatewayComm.stopProbing();

			}
		}, 0);

		if (probingTimer != null) {
			probingTimer.cancel();
			probingTimer = null;
		}
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
				if (!mobileInfo.getOperatorName().equals("T-Mobile")) {
					resumeData();
				}

			}
		}

	}

	public class AutoSpamTestTask extends TimerTask {

		private int rate;
		private boolean toStart = true;

		public AutoSpamTestTask(boolean toStart, int rate) {
			this.rate = rate;
			this.toStart = toStart;
		}

		@Override
		public void run() {
			if (toStart) {
				EventLog.write(Type.AUTOTEST, "AUTOSPAM" + EventLog.SEPARATOR
						+ rate);
				gatewayComm.startSpam(rate);
			} else {
				gatewayComm.stopSpam();

			}

		}

	}

	public class AutoCallTask extends TimerTask {
		public static final int MAX_WAIT_DURATION = 5500;
		public static final int MAX_CALL_DURATION = 20000;
		public static final double VOLUME_THRES = 0.1;

		private int id;
		private long interval;
		private String phoneNum;
		private SoundMeter soundMeter = new SoundMeter();

		public AutoCallTask(int id, String phoneNum, long interval) {
			this.id = id;
			this.phoneNum = phoneNum;
			this.interval = interval;
		}

		@Override
		public void run() {
			try {
				Thread.sleep(interval);
			} catch (InterruptedException e1) {
				e1.printStackTrace();
			}
			EventLog.write(Type.DEBUG, "Issue call reqest");
			long t1 = System.currentTimeMillis();
			long duration = 0;
			while ((gatewayComm.isPendingRaUpdate() || gatewayComm
					.isPendingTaUpdate()) && duration < MAX_WAIT_DURATION) {
				EventLog.write(Type.DEBUG, "Call reqest waiting");
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {

				}
				duration += 100;
			}

			long t2 = System.currentTimeMillis();

			PhoneCall.call(phoneNum);
			soundMeter.start();

			duration = 0;
			while (duration < MAX_CALL_DURATION) {
				double amp = soundMeter.getAmplitude();
				// EventLog.write(Type.DEBUG, "amp=" + amp);
				if (amp >= VOLUME_THRES) {
					break;
				}
				duration += 100;
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
					// TODO Auto-generated catch block
					e.printStackTrace();
				}
			}

			long t3 = System.currentTimeMillis();
			soundMeter.stop();
			PhoneCall.endCall(phoneNum);

			StringBuffer sb = new StringBuffer();
			sb.append(id);
			sb.append(EventLog.SEPARATOR);
			sb.append(t1);
			sb.append(EventLog.SEPARATOR);
			sb.append(t2);
			sb.append(EventLog.SEPARATOR);
			sb.append(t3);
			EventLog.write(Type.AUTOCALL, sb.toString());
		}

	}

	@Override
	public void runAutoTestSpam() {
		long delay = 1000;
		int repeat = Integer.parseInt(prefs.getString("auto_repeat",
				getString(R.string.pref_default_auto_repeat)));
		long duration = Long.parseLong(prefs.getString("auto_duration",
				getString(R.string.pref_default_auto_duration)));
		long interval = Long.parseLong(prefs.getString("auto_interval",
				getString(R.string.pref_default_auto_interval)));
		int startRate = Integer.parseInt(prefs.getString("auto_start_rate",
				getString(R.string.pref_default_auto_start_rate)));
		int endRate = Integer.parseInt(prefs.getString("auto_end_rate",
				getString(R.string.pref_default_auto_end_rate)));

		EventLog.newLogFile(EventLog.genLogFileName(new String[] { "autospam",
				"" + System.currentTimeMillis(), mobileInfo.getOperatorName() }));

		for (int rate = startRate; rate <= endRate; rate += 100) {
			for (int run = 0; run < repeat; run++) {
				autoTestTimer.schedule(new AutoSpamTestTask(true, rate), delay);
				delay += duration;
				autoTestTimer.schedule(new AutoSpamTestTask(false, 0), delay);
				delay += interval;
			}
		}

		autoTestTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				EventLog.close();
			}
		}, delay);

	}

	@Override
	public void runAutoConn() {
		long delay = 1000;
		int repeat = Integer.parseInt(prefs.getString("auto_repeat",
				getString(R.string.pref_default_auto_repeat)));
		long interval = Long.parseLong(prefs.getString("auto_interval",
				getString(R.string.pref_default_auto_interval)));

		EventLog.newLogFile(EventLog.genLogFileName(new String[] { "autoconn",
				"" + System.currentTimeMillis(), mobileInfo.getOperatorName(),
				"" + (new Date(System.currentTimeMillis()).getHours()) }));

		for (int run = 0; run < repeat; run++) {
			autoTestTimer.schedule(new TimerTask() {
				@Override
				public void run() {
					gatewayComm.connAuth();
				}
			}, delay);
			delay += interval;
		}

		for (int run = 0; run < repeat; run++) {
			autoTestTimer.schedule(new TimerTask() {
				@Override
				public void run() {
					gatewayComm.conn();
				}
			}, delay);
			delay += interval;
		}

		autoTestTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				EventLog.close();
			}
		}, delay);

	}

	@Override
	public void setupConn() {
		dataTimer.schedule(new TimerTask() {

			@Override
			public void run() {
				gatewayComm.conn();

			}
		}, 0);

	}

	@Override
	public void setupConnAuth() {
		dataTimer.schedule(new TimerTask() {

			@Override
			public void run() {
				gatewayComm.connAuth();
			}
		}, 0);
	}

	@Override
	public void onAlarm() {
		EventLog.write(Type.DEBUG, "BackgroundService.onAlarm()");
		runAutoConn();
	}

	@Override
	public void startAlarm(String function) {
		Intent intent = new Intent(this, AlarmReceiver.class);
		alarmIntent = PendingIntent.getBroadcast(this, 0, intent, 0);
		alarmManager.setRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, 0,
				AlarmManager.INTERVAL_HOUR, alarmIntent);
	}

	@Override
	public void stopAlarm() {
		if (alarmManager != null) {
			alarmManager.cancel(alarmIntent);
		}

	}

	@Override
	public void connectDummyWeb() {
		gatewayComm.connectDummyWeb();

	}

	@Override
	public void runAutoCall() {
		EventLog.newLogFile(EventLog.genLogFileName(new String[] { "autocall",
				String.valueOf(System.currentTimeMillis()) }));

		int repeat = Integer.parseInt(prefs.getString("auto_repeat",
				getString(R.string.pref_default_auto_repeat)));
		long interval = Long.parseLong(prefs.getString("auto_interval",
				getString(R.string.pref_default_auto_interval)));
		String phoneNum = prefs.getString("phone_num",
				getString(R.string.pref_default_phone_num));

		for (int i = 1; i <= repeat; i++) {
			autoTestTimer.schedule(new AutoCallTask(i, phoneNum, interval), 0);
		}
		autoTestTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				EventLog.close();

			}
		}, 1000);

	}

	@Override
	public void startLocationUpdate() {
		taUpdateTimer = new Timer();
		raUpdateTimer = new Timer();
		int taUpdatePeriod = Integer.parseInt(prefs.getString(
				"ta_update_period",
				getString(R.string.pref_default_ta_update_period)));
		int raUpdatePeriod = Integer.parseInt(prefs.getString(
				"ra_update_period",
				getString(R.string.pref_default_ra_update_period)));

		if (taUpdatePeriod > 0) {
			taUpdateTimer.schedule(new TimerTask() {

				@Override
				public void run() {
					gatewayComm.sendTaUpdate();
				}
			}, 0, taUpdatePeriod);

		}

		if (raUpdatePeriod > 0) {
			raUpdateTimer.schedule(new TimerTask() {

				@Override
				public void run() {
					gatewayComm.sendRaUpdate();
				}
			}, 0, raUpdatePeriod);
		}

	}

	@Override
	public void stopLocationUpdate() {
		taUpdateTimer.cancel();
		raUpdateTimer.cancel();

		gatewayComm.setPendingRaUpdate(false);
		gatewayComm.setPendingRaUpdate(false);
	}

	public class AutoTurnDataTask extends TimerTask {

		public static final int MAX_WAIT_1 = 5500; // for location update
		public static final int MAX_WAIT_2 = 10000; // for turn on/off data

		private long duration;
		private long interval;

		public AutoTurnDataTask(long duration, long interval) {
			this.duration = duration;
			this.interval = interval;
		}

		@Override
		public void run() {
			try {
				Thread.sleep(interval);
			} catch (InterruptedException e1) {
				// TODO Auto-generated catch block

			}

			long t1 = System.currentTimeMillis();
			long wait = 0;
			while ((gatewayComm.isPendingRaUpdate() || gatewayComm
					.isPendingTaUpdate()) && wait <= MAX_WAIT_1) {

				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
				}
				wait += 100;
			}

			long t2 = System.currentTimeMillis();
			mobileInfo.setMobileDataEnabled(false);
			wait = 0;
			while (mobileInfo.isConnected() && wait <= MAX_WAIT_2) {
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
				}
				wait += 100;
			}

			long t3 = System.currentTimeMillis();
			try {
				Thread.sleep(duration);
			} catch (InterruptedException e1) {
				// TODO Auto-generated catch block
				e1.printStackTrace();
			}

			long t4 = System.currentTimeMillis();
			wait = 0;
			while ((gatewayComm.isPendingRaUpdate() || gatewayComm
					.isPendingTaUpdate()) && wait <= MAX_WAIT_1) {

				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {

					e.printStackTrace();
				}
				wait += 100;
			}

			long t5 = System.currentTimeMillis();
			mobileInfo.setMobileDataEnabled(true);
			wait = 0;
			while (!mobileInfo.isConnected() && wait <= MAX_WAIT_2) {
				try {
					Thread.sleep(100);
				} catch (InterruptedException e) {
				}
				wait += 100;
			}

			long t6 = System.currentTimeMillis();

			StringBuffer sb = new StringBuffer();
			sb.append(t1);
			sb.append(EventLog.SEPARATOR);
			sb.append(t2);
			sb.append(EventLog.SEPARATOR);
			sb.append(t3);
			sb.append(EventLog.SEPARATOR);
			sb.append(t4);
			sb.append(EventLog.SEPARATOR);
			sb.append(t5);
			sb.append(EventLog.SEPARATOR);
			sb.append(t6);
			EventLog.write(Type.DATAONOFF, sb.toString());
		}

	}

	@Override
	public void runAutoTurnOnOffData() {
		EventLog.newLogFile(EventLog.genLogFileName(new String[] { "turndata",
				String.valueOf(System.currentTimeMillis()) }));

		int repeat = Integer.parseInt(prefs.getString("auto_repeat",
				getString(R.string.pref_default_auto_repeat)));
		long duration = Long.parseLong(prefs.getString("auto_duration",
				getString(R.string.pref_default_auto_duration)));
		long interval = Long.parseLong(prefs.getString("auto_interval",
				getString(R.string.pref_default_auto_interval)));

		for (int i = 0; i < repeat; i++) {
			autoTestTimer.schedule(new AutoTurnDataTask(duration, interval), 0);
		}
		
		autoTestTimer.schedule(new TimerTask() {
			@Override
			public void run() {
				EventLog.close();				
			}
		}, 2000);

	}

	@Override
	public void onNetworkConnectionChange(boolean connected) {
		mobileInfo.setConnected(connected);

	}

	@Override
	public void sendLocationUpdateProbe() {
		dataTimer.schedule(new TimerTask() {
			
			@Override
			public void run() {
				gatewayComm.sendLocUpdateProbe();				
			}
		}, 0);
		
	}

}
