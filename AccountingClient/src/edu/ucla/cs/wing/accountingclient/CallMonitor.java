package edu.ucla.cs.wing.accountingclient;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class CallMonitor extends BroadcastReceiver {
	
	private static ICommander commander;
	
	public static void init(ICommander commander) {
		CallMonitor.commander = commander;
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		
		if (intent.getAction().equals("android.intent.action.NEW_OUTGOING_CALL")) {
			commander.onOutgoingCall("");
		}


	}

}
