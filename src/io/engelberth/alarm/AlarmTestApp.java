package io.engelberth.alarm;


public class AlarmTestApp {
	public static void main(String []args) {
		// Get an instance of AlarmManager
		AlarmManager manager = AlarmManager.getInstance();
		
		// Set an alarm of type TestAlarm to fire every 1000 milliseconds.  Also set the extra persistent data to String "First"
		manager.setAlarm(TestAlarm.class, 1000, "First");
		
		// Set an alarm of type TestAlarm2 to fire every 5 seconds
		manager.setAlarm(TestAlarm2.class, 5000, new Integer(0));
		
		// add a broadcast listener to the alarm manager.
		// this listener will be called every time TestAlarm broadcasts 
		manager.addBroadcastListener(TestAlarm.class, new AlarmManager.BroadcastListener() {
			@Override
			public void onBroadcast(Alarm alarm, int action, Object data) {
				AlarmTestApp.log(this, "onBroadcast: " + (String)data);
				// Display the current Extra persistent data
				AlarmTestApp.log(this, "onBroadcast: " + (String)AlarmManager.getInstance().getExtraData(TestAlarm.class));
				
			}
		});
	}
	
	// Helper function to print to the screen.
	public static void log(Object o, String s) {
		System.out.println(o.getClass().getName() + ": " + s);
	}
	
	// TestAlarm
	public static class TestAlarm implements Alarm {
		private static int count = 0;
		
		// Called by AlarmManager when TestAlarm is removed
		@Override
		public void onRemove() {
			AlarmTestApp.log(this, "onRemove: " + AlarmManager.getInstance().interruptAlarm(TestAlarm.class));
			
		}
		
		// Called by AlarmManager when TestAlarm is fired.
		@Override
		public void onFire() {
			// Broadcast a message to listeners registered with AlarmManager for this Alarm
			AlarmManager.getInstance().broadcast(this, AlarmManager.BROADCAST_ACTION_MESSAGE, "hi Dude");
			AlarmTestApp.log(this, "onFire: " + ++count);
			// Change the extra persistent data with this alarm.
			AlarmManager.getInstance().setExtraData(TestAlarm.class, "" + count);
			
		}
	}
	public static class TestAlarm2 implements Alarm {		
		@Override
		public void onRemove() {
			AlarmTestApp.log(this, "onRemove");
		}
		@Override
		public void onFire() {
			Integer count = (Integer)AlarmManager.getInstance().getExtraData(TestAlarm2.class);
			AlarmTestApp.log(this, "onFire: " + ++count);
			if (count == 3) {
				AlarmManager.getInstance().removeAlarm(TestAlarm2.class);
				AlarmManager.getInstance().removeAlarm(TestAlarm.class);
			}
			AlarmManager.getInstance().setExtraData(TestAlarm2.class, count);
		}
	}
}