package io.engelberth.alarm;

/**
 * 
 * @author Brandon Engelberth
 * @version 1
 */
public interface Alarm {
	
	/**
	 * Blocks on the calling thread.
	 * The object created from Alarm.class to call onSet()
	 * is not used again after this call.
	 * @param interval
	 */
	//public void onSet(int interval);
	/**
	 * Blocks on the calling thread.
	 * If an alarm for Alarm.class is currently running then
	 * onRemove is called from the existing object.  If Alarm.class
	 * onFire is not running then a new object is created and called.
	 */
	public void onRemove();
	
	/**
	 * This is called when the alarm is fired.  A new object is created 
	 * every time it fires.  This runs on its on thread and does not 
	 * block the main thread.
	 */
	public void onFire();
	
}