package edu.ucla.cs.wing.accountingclient;






import edu.ucla.cs.wing.accountingclient.EventLog.Type;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.CheckBox;

public class MainActivity extends Activity {
	
	//private ICommander commander;
	
	private MenuItem itemSetting;
	private CheckBox checkBoxAvoidOverchargingCsfb;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		
		checkBoxAvoidOverchargingCsfb = (CheckBox) findViewById(R.id.checkBoxAvoidOverchargingCsfb);
		checkBoxAvoidOverchargingCsfb.setChecked(true);
		
		
		startService(new Intent(this, BackgroundService.class));
		
		
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
			this.startActivity(new Intent(MainActivity.this, SettingsActivity.class));
			break;
		

		default:
			break;
		}
		return true;
	}
	
	
	public void onClickDataFlowStart(View view) {
		BackgroundService.getCommander().startData();
		
	}
	
	public void onClickDataFlowStop(View view) {
		BackgroundService.getCommander().stopData();
		
	}
	
	public void onClickDebug(View view) {
		EventLog.write(Type.DEBUG, getString(R.string.pref_default_call_duration));
		
	}
	
	public void onClickCsfbCall(View view) {
		BackgroundService.getCommander().csfbCall(checkBoxAvoidOverchargingCsfb.isChecked());		
	}

}
