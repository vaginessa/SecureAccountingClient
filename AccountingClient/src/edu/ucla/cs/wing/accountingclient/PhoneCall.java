package edu.ucla.cs.wing.accountingclient;

import java.io.IOException;
import java.lang.reflect.Method;
import java.util.Timer;
import java.util.TimerTask;

import com.android.internal.telephony.ITelephony;


import android.R.bool;
import android.R.integer;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.RemoteException;
import android.telephony.TelephonyManager;
import android.text.format.Time;
import android.util.Log;
import android.view.KeyEvent;

public class PhoneCall extends BroadcastReceiver {
	
	private static ITelephony telephonyService;	
	
	private static Context context;	

	public static void init(Context context) {
		TelephonyManager tm = (TelephonyManager) context
				.getSystemService(Context.TELEPHONY_SERVICE);

		try {
			Class<?> c = Class.forName(tm.getClass().getName());
			Method m = c.getDeclaredMethod("getITelephony");
			m.setAccessible(true);
			telephonyService = (ITelephony) m.invoke(tm);
		} catch (Exception e) {
		}
		PhoneCall.context = context;
		
	}

	public static void endCall(String phoneNum) {
		try {
			if (telephonyService != null) {
				telephonyService.endCall();
				
				
			}
		} catch (RemoteException e) {
		}
	}

	public static void call(String phoneNum) {
		Intent phoneIntent = new Intent("android.intent.action.CALL",
				Uri.parse("tel:" + phoneNum));
		phoneIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		context.startActivity(phoneIntent);
		
	}

	private static void answer(String phoneNum) {
		 Intent buttonDown = new Intent(Intent.ACTION_MEDIA_BUTTON);             
         buttonDown.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_HEADSETHOOK));
         context.sendOrderedBroadcast(buttonDown, "android.permission.CALL_PRIVILEGED");

         // froyo and beyond trigger on buttonUp instead of buttonDown
         Intent buttonUp = new Intent(Intent.ACTION_MEDIA_BUTTON);               
         buttonUp.putExtra(Intent.EXTRA_KEY_EVENT, new KeyEvent(KeyEvent.ACTION_UP, KeyEvent.KEYCODE_HEADSETHOOK));
         context.sendOrderedBroadcast(buttonUp, "android.permission.CALL_PRIVILEGED");
	}

	@Override
	public void onReceive(Context context, Intent intent) {
		try {
			if (telephonyService != null && telephonyService.isRinging()) {
				Bundle bundle = intent.getExtras();
				String incomingNum = bundle
						.getString(TelephonyManager.EXTRA_INCOMING_NUMBER);
				

				
			}
		} catch (RemoteException e) {
		}
	}

}
