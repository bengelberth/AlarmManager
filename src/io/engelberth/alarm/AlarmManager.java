package io.engelberth.alarm;

import java.util.concurrent.ConcurrentHashMap;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;

/**
 * This class is a singleton.  Use it to manage all your alarms.
 * @author Brandon Engelberth
 * @version 3
 *
 */
public class AlarmManager {
	public static int VERSION = 3;
	private static AlarmManager mInstance;
	
	public static final int BROADCAST_ACTION_MESSAGE = 1;
	//public static final int BROADCAST_ACTION_SET = 2;
	//public static final int BROADCAST_ACTION_REMOVE = 2;
	
	private Thread mAlarmThread;
	private ConcurrentHashMap <String, AlarmStruct> mAlarmMap;
	private ConcurrentHashMap <String, ArrayList<BroadcastListener>> mBroadcastMap;
	private boolean mAlarmRunning; // synchronize with mAlarmMap
	
	/**
	 * Use this method to retrieve the instance of AlarmManager.
	 * @return
	 */
	public static synchronized AlarmManager getInstance() {
		if (mInstance == null)
			mInstance = new AlarmManager();
		return mInstance;
	}
	/**
	 * Get a collection of set alarms
	 * @return set alarms.  or an empty collection if none are set.
	 */
	public Collection<Class<?>> getAlarms() {
		Collection <AlarmStruct> alarmStructs = mAlarmMap.values();
		Iterator <AlarmStruct> iterator = alarmStructs.iterator();
		ArrayList<Class <?>> returnCollection = new ArrayList<Class <?>>();
		while (iterator.hasNext()) {
			returnCollection.add(iterator.next().alarm);
		}
		return returnCollection;
	}
	public void setAlarmHours(Class <?>alarm, int hours) {
		setAlarmMinutes(alarm, hours * 60);
	}
	
	public void setAlarmMinutes(Class <?>alarm, int minutes) {
		setAlarmSeconds(alarm, minutes * 60);
	}
	public void setAlarmSeconds(Class <?>alarm, int seconds) {
		setAlarm(alarm, seconds * 1000, null);
	}
	/**
	 * Set an alarm.  Only one alarm will be set per Alarm.class.
	 * Calling this for an Alarm.class that is already set will
	 * only change the interval.
	 * 
	 * The alarm is execute as soon as possible.
	 * @param alarm
	 * @param interval The interval is in milliseconds
	 */
	public void setAlarm(Class <?>alarm, int interval, Object extraData) {
		
		AlarmStruct alarmStruct = mAlarmMap.get(alarm.getName());
		if (alarmStruct != null) {
			alarmStruct.interval = interval;
			alarmStruct.lastAlarm = 0;
			
		} else {
			alarmStruct = new AlarmStruct();
			alarmStruct.key = alarm.getName();
			alarmStruct.alarm = alarm;
			alarmStruct.interval = interval;
			alarmStruct.lastAlarm = 0;
		
			mAlarmMap.put(alarmStruct.key, alarmStruct);
		}
		setExtraData(alarm, extraData);
		wakeAlarm();
	}
	/**
	 * Adds a new listener to an alarm.  Only if it is not already added.
	 * You can have multiple listeners for one alarm, but not two that are Object.equal
	 * The alarm does not have to be set and is not removed if the alarm is removed.
	 * @param alarm
	 * @param listener
	 */
	public void addBroadcastListener(Class <?>alarm, BroadcastListener listener) {
		synchronized (mBroadcastMap) {
			String alarmName = alarm.getName();
			if (!mBroadcastMap.containsKey(alarmName))
				mBroadcastMap.put(alarmName, new ArrayList<BroadcastListener>());
			
			ArrayList<BroadcastListener> listeners = mBroadcastMap.get(alarmName);
			if (listeners.contains(listener));
			listeners.add(listener);
		}
	}
	public void removeBroadcastListener(Class <?>alarm, BroadcastListener listener) {
		synchronized (mBroadcastMap) {
			String alarmName = alarm.getName();
			if (mBroadcastMap.containsKey(alarmName)) {
				ArrayList<BroadcastListener> listeners = mBroadcastMap.get(alarmName);
				listeners.remove(alarmName);
				if (listeners.isEmpty()) mBroadcastMap.remove(alarmName);
			}
		}
	}
	/**
	 * each broadcast listener blocks the next.  And everyone in the system!  Keep them simple or 
	 * spawn a new thread.
	 * @param alarm
	 * @param action
	 * @param data
	 */
	public void broadcast(Alarm alarm, int action, String data) {
		synchronized (mBroadcastMap) {
			String alarmName = alarm.getClass().getName();
			if (mBroadcastMap.containsKey(alarmName)) {
				ArrayList<BroadcastListener> listeners = mBroadcastMap.get(alarmName);
				Iterator<BroadcastListener> i = listeners.iterator();
				while (i.hasNext())
					i.next().onBroadcast(alarm, action, data);							
	
			}
				
		}
	}
	/**
	 * The data is set only if the alarm is set.
	 * @param alarm
	 * @param data
	 * @return returns true if the data was set.
	 */
	public void setExtraData(Class <?> alarm, Object data) {
		AlarmStruct alarmStruct = mAlarmMap.get(alarm.getName());
		if (alarmStruct != null) alarmStruct.extraData = data;
	}
	public Object getExtraData(Class <?> alarm) {
		AlarmStruct alarmStruct = mAlarmMap.get(alarm.getName());
		if (alarmStruct != null) return alarmStruct.extraData;
		return null;
	}
	/**
	 * Remove an alarm from the manager.  If the alarm is in onFire(),
	 * then onRemove() is called on the existing object.  This way you
	 * can gracefully exit onFire().
	 * @param alarm
	 * @see interruptAlarm
	 */
	public void removeAlarm(Class <?>alarm) {
        AlarmStruct alarmStruct = mAlarmMap.get(alarm.getName());
        if (alarmStruct == null) return;
        
		
		try {
			Alarm alarmInstance;
			if (alarmStruct.thread == null)
				alarmInstance = (Alarm)alarm.newInstance();
			else
				alarmInstance = alarmStruct.instance;
			alarmInstance.onRemove();
		} catch (Exception ex) {
			ex.printStackTrace();
		}
		mAlarmMap.remove(alarm.getName());
		wakeAlarm();	// In case there are no more alarms the alarm thread should die.
	}
	/**
	 * Sends a Thread.interrupt() to a running onFire().
	 * @param alarm
	 * @return true if the alarm was in onFire and an interrupt was sent.  False otherwise.
	 */
	public boolean interruptAlarm(Class <?>alarm) {
		AlarmStruct alarmStruct = mAlarmMap.get(alarm.getName());
		if (alarmStruct == null) return false;
		if (alarmStruct.thread == null) return false;
		alarmStruct.thread.interrupt();
		return true;
	}
	private AlarmManager() { 
		mAlarmThread = null;
		mAlarmMap = new ConcurrentHashMap<String, AlarmStruct>();
		mBroadcastMap = new ConcurrentHashMap<String, ArrayList<BroadcastListener>>();
		mAlarmRunning = false;
	}
	private  void wakeAlarm() {
		
		synchronized (mAlarmMap) {
			//if (mAlarmMap.isEmpty()) return;	// No need to wake the alarm if there are no alarms set. WRONG.  wake it so it can die.
			if (mAlarmThread == null && !mAlarmMap.isEmpty()) {
				mAlarmThread = new Thread(new AlarmRunnable(), "AlarmManager");
				mAlarmRunning = true;
				mAlarmThread.start();
			} else if (mAlarmThread != null){
				mAlarmThread.interrupt();
			}
		}
	}
	private class AlarmRunnable implements Runnable {
		
		@Override
		public synchronized void run() {
			while (mAlarmRunning) {
				long time = System.currentTimeMillis();
				long shortestWait = 0;
				for (Enumeration<AlarmStruct> e = mAlarmMap.elements(); e.hasMoreElements();) {
					AlarmStruct alarmStruct = e.nextElement();
					int nextFire = alarmStruct.interval;
					if (alarmStruct.lastAlarm == 0 || time - alarmStruct.lastAlarm >= alarmStruct.interval) {
						// Fire this alarm.  If it isn't currently running......
						if (alarmStruct.thread == null || alarmStruct.thread.isAlive() == false) {
							//  Thread is null or not alive.  Fire.
							alarmStruct.thread = new Thread(new AlarmFireRunnable(alarmStruct));
							alarmStruct.thread.start();
						}
					} else {
						// Wasn't fired this time
						nextFire = nextFire - (int)(time - alarmStruct.lastAlarm);
						if (nextFire < 0) nextFire = 0;
					}
					if (shortestWait == 0 || nextFire < shortestWait) shortestWait = nextFire;
				}
				//shortestWait++;	// Don't want it to = 0;
				//System.out.println("Wait time: " + shortestWait);
				synchronized (mAlarmMap) {
					if (mAlarmMap.isEmpty()) {
						System.out.println("No more alarms");
						mAlarmRunning = false;
						mAlarmThread = null;
						break;
					}
				}
				try {
					wait(shortestWait);
				} catch (InterruptedException e) {
				}
			}
		}
	}
	public interface BroadcastListener {
		void onBroadcast(Alarm alarm, int action, Object data);
	}
	private static class AlarmFireRunnable implements Runnable {
		private AlarmStruct mAlarmStruct;
		public AlarmFireRunnable(AlarmStruct alarmStruct) {
			mAlarmStruct = alarmStruct;
		}
		@Override
		public void run() {
			try {
				mAlarmStruct.instance = (Alarm)mAlarmStruct.alarm.newInstance();
				mAlarmStruct.lastAlarm = System.currentTimeMillis();
				mAlarmStruct.instance.onFire();
			} catch (Exception ex) {
				ex.printStackTrace();
			}
			mAlarmStruct.instance = null;
			mAlarmStruct.thread = null;
			AlarmManager.getInstance().wakeAlarm();
		}
	}
	
	private static class AlarmStruct {
		public String key;
		public Class <?>alarm;
		public int interval;
		public long lastAlarm;
		public Thread thread;
		public Alarm instance;
		public Object extraData;
	}
}