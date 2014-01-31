package edu.ucla.cs.wing.accountingclient;

import edu.ucla.cs.wing.accountingclient.EventLog.Type;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class AlarmReceiver extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		EventLog.write(Type.DEBUG, "AlarmReceiver.onReceive()");
		BackgroundService.getCommander().onAlarm();

	}

}
