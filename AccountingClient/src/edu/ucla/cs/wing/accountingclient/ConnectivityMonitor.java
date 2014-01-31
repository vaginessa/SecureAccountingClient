package edu.ucla.cs.wing.accountingclient;

import edu.ucla.cs.wing.accountingclient.EventLog.Type;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Bundle;

public class ConnectivityMonitor extends BroadcastReceiver {

	@Override
	public void onReceive(Context context, Intent intent) {
		
		EventLog.write(Type.DEBUG, "ConnectivityMonitor: recv broadcast");
		NetworkInfo networkInfo = intent.getParcelableExtra(ConnectivityManager.EXTRA_NETWORK_INFO);
		
		if (networkInfo != null) {
			EventLog.write(Type.DEBUG, "ConnectivityMonitor: " + networkInfo.isConnectedOrConnecting());
			BackgroundService.getCommander().onNetworkConnectionChange(networkInfo.isConnectedOrConnecting());
		}
		
		/*Bundle bundle = intent.getExtras();
		for (String key : bundle.keySet()) {
			EventLog.write(Type.DEBUG, key + " : " + bundle.get(key).toString());
		
		}*/

	}

}
