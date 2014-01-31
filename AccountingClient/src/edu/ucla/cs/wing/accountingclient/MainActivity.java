package edu.ucla.cs.wing.accountingclient;

import java.util.Date;

import edu.ucla.cs.wing.accountingclient.EventLog.Type;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.R.integer;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.Spinner;

public class MainActivity extends Activity {

	//public static final String FUN_CSFB = "csfb";
	public static final String FUN_SPAM = "spam";
	public static final String FUN_CONN = "conn";
	public static final String FUN_CALL = "call";
	public static final String FUN_TURN_DATA = "turn on/off data";
	

	private static final String[] FUNS = { FUN_SPAM, FUN_CONN, FUN_CALL, FUN_TURN_DATA };

	private MenuItem itemSetting;

	private Spinner spinnerFunction;
	private CheckBox checkBoxAvoidOverchargingCsfb;
	private EditText editTextDebug;

	SharedPreferences prefs;

	private static Handler _handler;

	public static Handler getHandler() {
		return _handler;
	}

	private Handler handler = new Handler() {
		@Override
		public void handleMessage(Message message) {
			switch (message.what) {
			case Msg.MSG_TRAFFIC_INFO:
				String data = (String) message.obj;
				editTextDebug.setText(data);
				break;

			default:
				break;
			}
		}

	};

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		prefs = PreferenceManager.getDefaultSharedPreferences(this);

		spinnerFunction = (Spinner) findViewById(R.id.spinner_function);
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
				android.R.layout.simple_spinner_item, FUNS);
		adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
		spinnerFunction.setAdapter(adapter);
		spinnerFunction.setSelection(prefs.getInt("function", 0));

		checkBoxAvoidOverchargingCsfb = (CheckBox) findViewById(R.id.checkBoxAvoidOverchargingCsfb);
		checkBoxAvoidOverchargingCsfb.setChecked(false);

		editTextDebug = (EditText) findViewById(R.id.editTextDebug);

		_handler = this.handler;

		

		startService(new Intent(this, BackgroundService.class));

	}

	@Override
	public void onDestroy() {
		super.onDestroy();

		_handler = null;
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.main, menu);
		this.itemSetting = menu.findItem(R.id.action_settings);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.action_settings:
			this.startActivity(new Intent(MainActivity.this,
					SettingsActivity.class));
			break;

		default:
			break;
		}
		return true;
	}

	public void onClickDataFlowStart(View view) {
		int dataRate = Integer.parseInt(prefs.getString("data_rate",
				getString(R.string.pref_default_data_rate)));
		int probingRate = Integer.parseInt(prefs.getString("probing_rate",
				getString(R.string.pref_default_probing_rate)));
		int probingThresRate = Integer.parseInt(prefs.getString(
				"probing_thres_rate",
				getString(R.string.pref_default_probing_thres_rate)));

		EventLog.newLogFile(EventLog.genLogFileName(new String[] { "data",
				String.valueOf(dataRate), String.valueOf(probingRate),
				String.valueOf(probingThresRate) }));

		BackgroundService.getCommander().startData();

	}

	public void onClickDataFlowStop(View view) {
		EventLog.close();

		BackgroundService.getCommander().stopData();

	}

	public void onClickSpamFlowStart(View view) {
		BackgroundService.getCommander().startSpam();

	}

	public void onClickSpamFlowStop(View view) {
		BackgroundService.getCommander().stopSpam();
		;
	}

	public void onClickDebug(View view) {
		MobileInfo.getInstance().setMobileDataEnabled(false);
	

	}

	public void onClickCsfbCall(View view) {
		BackgroundService.getCommander().csfbCall(
				checkBoxAvoidOverchargingCsfb.isChecked());
	}

	public void onClickRunAutoTest(View view) {
		String function = spinnerFunction.getSelectedItem().toString();
		
		Editor editor = prefs.edit();
		editor.putInt("function", spinnerFunction.getSelectedItemPosition());
		editor.commit();

		if (function.equals(FUN_SPAM)) {
			BackgroundService.getCommander().runAutoTestSpam();
		} else if (function.equals(FUN_CONN)) {
			BackgroundService.getCommander().runAutoConn();
		} else if (function.equals(FUN_CALL)) {
			BackgroundService.getCommander().runAutoCall();
		} else if (function.equals(FUN_TURN_DATA)) {
			BackgroundService.getCommander().runAutoTurnOnOffData();
		}

	}

	public void onClickConn(View view) {
		BackgroundService.getCommander().setupConn();
	}

	public void onClickConnAuth(View view) {
		BackgroundService.getCommander().setupConnAuth();
	}

	public void onClickStartAlarm(View view) {
		BackgroundService.getCommander().startAlarm(
				spinnerFunction.getSelectedItem().toString());

	}

	public void onClickStopAlarm(View view) {
		BackgroundService.getCommander().stopAlarm();
	}

	public void onClickProbingFlowStart(View view) {
		BackgroundService.getCommander().startProbing();
	}

	public void onClickProbingFlowStop(View view) {
		BackgroundService.getCommander().stopProbing();
	}

	public void onClickLogStart(View view) {
		EventLog.newLogFile(EventLog.genLogFileName(new String[] { "log", 
				String.valueOf(System.currentTimeMillis()) }));

	}

	public void onClickLogStop(View view) {
		EventLog.close();

	}
	
	public void onClickDummyWeb(View view) {
		BackgroundService.getCommander().connectDummyWeb();
	}
	
	public void onClickLocationUpdateStart(View view) {
		BackgroundService.getCommander().startLocationUpdate();
		
	}
	
	public void onClickLocationUpdateStop(View view) {
		BackgroundService.getCommander().stopLocationUpdate();
		
	}
	
	public void onClickLocationUpdateProbe(View view) {
		BackgroundService.getCommander().sendLocationUpdateProbe();
	}

}
