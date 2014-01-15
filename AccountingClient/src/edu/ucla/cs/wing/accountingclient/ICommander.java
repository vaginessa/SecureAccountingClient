package edu.ucla.cs.wing.accountingclient;

public interface ICommander {
	
	public void onOutgoingCall(String phoneNum);
	
	public void onHandover4Gto3G();
	
	public void onhandover3Gto4G();
	
	public void csfbCall(boolean avoidOvercharging);
	
	public void startData();
	
	public void stopData();
	
	public void pauseData();
	
	public void resumeData();
	
	public void startProbing();
	
	public void stopProbing();
	

}
