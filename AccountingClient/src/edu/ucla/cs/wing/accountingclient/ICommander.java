package edu.ucla.cs.wing.accountingclient;

public interface ICommander {
	
	public void onOutgoingCall(String phoneNum);
	
	public void onHandover4Gto3G();
	
	public void onhandover3Gto4G();
	
	public void csfbCall(boolean avoidOvercharging);
	
	public void startData();
	
	public void stopData();
	
	public void startSpam();
	
	public void stopSpam();
	
	public void pauseData();
	
	public void resumeData();
	
	public void startProbing();
	
	public void stopProbing();
	
	public void runAutoTestSpam();
	
	public void setupConn();
	
	public void setupConnAuth();
	
	public void runAutoConn();
	
	public void startAlarm(String function);
	
	public void stopAlarm();
	
	public void onAlarm();
	
	public void connectDummyWeb();
	
	public void runAutoCall();
	
	public void runAutoTurnOnOffData();
	
	public void startLocationUpdate();
	
	public void stopLocationUpdate();
	
	public void sendLocationUpdateProbe();
	
	public void onNetworkConnectionChange(boolean connected);
	
	

}
