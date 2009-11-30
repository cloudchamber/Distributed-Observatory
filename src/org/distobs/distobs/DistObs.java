/**
 * Copyright (c) 2009 Kenneth Jensen
 * 
 * This file is part of Distributed-Observatory.
 *
 * Distributed-Observatory is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Distributed-Observatory is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Distributed-Observatory.  If not, see <http://www.gnu.org/licenses/>.
 */


package org.distobs.distobs;

import java.io.File;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.BatteryManager;
import android.os.Environment;
import android.os.IBinder;
import android.os.SystemClock;
import android.util.Log;


/**
 * Distributed Observatory (distobs.org)
 * @version 0.1
 * @author kenny
 *
 * Description:
 * The Distributed Observatory service monitors various aspects on the phone's state (network connectivity, 
 * battery charging, etc...), and performs appropriate actions (data acquistion, data transfer, etc...).
 *
 * TODO:
 * 1) Generalize some of the hard-coded parameters (i.e. pic size) so it works on more cameras
 * 2) Make display show tick marks for events. 
 * 3) figure out security/anti-cheating measures
 */

public class DistObs extends Service {

	private AlarmManager am;
	private static final String TAG = "DistObsService";	
	private static final String CONFIG_FILENAME = "configFile";	

	public static final int SCHEDULE_ALWAYSON = 0;
	public static final int SCHEDULE_CHARGINGON = 1;
	public static final int SCHEDULE_ACCHARGINGON = 2;
	public static final int SCHEDULE_RUNON = 3;	
	public static final int DISPLAY_DATA = 4;
	public static final int DISPLAY_NONE = 5;
	
	private static long waitTimeAfterStart = 60000; // ms
	private static long waitTimeAfterPlugged = 2000; // ms
	
	public boolean isActivityRunning = false;
	public boolean hourlyAlarm = false;
	private long serviceStartTime = 0;
	private DataSend ds;

	
	/**
	 * Persistently stores total number of events
	 *  
	 * @param c
	 * @param opts
	 */
	public static void setNumEventsAndSamples(Context c, int numEvents, int numSamples) {
		SharedPreferences.Editor editor = c.getSharedPreferences(CONFIG_FILENAME, 0).edit();
		editor.putInt("NumEvents", numEvents);
		editor.putInt("NumSamples", numSamples);
		editor.commit();
	}

	/**
	 * Gets total number of cosmic ray events
	 * @param c
	 * @return
	 */
	public static int getNumEvents(Context c) {
		SharedPreferences settings = c.getSharedPreferences(CONFIG_FILENAME, 0);
		return settings.getInt("NumEvents", 0);
	}

	/**
	 * Gets total number of pictures taken
	 * @param c
	 * @return
	 */
	public static int getNumSamples(Context c) {
		SharedPreferences settings = c.getSharedPreferences(CONFIG_FILENAME, 0);
		return settings.getInt("NumSamples", 0);
	}
	

	/**
	 * Stores the option for when to start the application in persistent storage.
	 *  
	 * @param c
	 * @param opts
	 */
	public static void setScheduleOptions(Context c, int opts) {
		SharedPreferences.Editor editor = c.getSharedPreferences(CONFIG_FILENAME, 0).edit();
		editor.putInt("ScheduleOption", opts);
		editor.commit();
	}
	
	
	/**
	 * Gets the option for when to start from persistent storage (assumes "On When Charging" by default)
	 * 
	 * @param c
	 * @return
	 */
	public static int getScheduleOptions(Context c) {
		SharedPreferences settings = c.getSharedPreferences(CONFIG_FILENAME, 0);
		return settings.getInt("ScheduleOption", SCHEDULE_CHARGINGON);
	}
	
	public int getScheduleOptions() {
		return getScheduleOptions(this);
	}
	
	
	/**
	 * Stores the option for what to display in persistent storage.
	 * 
	 * @param c
	 * @param opts
	 */
	public static void setDisplayOptions(Context c, int opts) {
		SharedPreferences.Editor editor = c.getSharedPreferences(CONFIG_FILENAME, 0).edit();
		editor.putInt("DisplayOption", opts);
		editor.commit();		
	}
	
	
	/**
	 * Gets the option for what to display from persistent storage (assumes "Display nothing" by default)
	 * @param c
	 * @return
	 */
	public static int getDisplayOptions(Context c) {
		SharedPreferences settings = c.getSharedPreferences(CONFIG_FILENAME, 0);
		return settings.getInt("DisplayOption", DISPLAY_DATA);
	}
	
	public int getDisplayOptions() {
		return getDisplayOptions(this);
	}
	
	
    /**
     * Gets the directory to store all the data in.  This is usually /sdcard/distobs/.
     * @return	The directory to store the data in.
     */
    public static File getDataDir(Context c) {
    	return new File(Environment.getExternalStorageDirectory().getPath() + "/" + c.getResources().getText(R.string.data_dir));
    }
    
    public File getDataDir() {
    	return getDataDir(this);
    }
    
    
    /**
     * Each client has a unique ID assigned to it.  This ID is assigned the first time the server is contacted.
     * @return	The integer ID, -1 on failure.
     */
    public static int getID(Context c) {
		SharedPreferences settings = c.getSharedPreferences(CONFIG_FILENAME, 0);
		return settings.getInt("ID", -1);
/*    	try {
			Log.v(TAG, "trying to open config");
			BufferedReader br = new BufferedReader(new InputStreamReader(c.openFileInput("config")));
			String idStr = br.readLine();
			Log.v(TAG, "buf="+idStr);
			return Integer.parseInt(idStr);
		} catch (NumberFormatException e) {
			e.printStackTrace();
		} catch (FileNotFoundException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}    		
		
    	return -1;*/
    }
    
    public int getID() {
    	return getID(this);
    }
    
    
    public static void setID(Context c, int id) {
		SharedPreferences.Editor editor = c.getSharedPreferences(CONFIG_FILENAME, 0).edit();
		editor.putInt("ID", id);
		editor.commit();		    	
    }
    
    public void setID(int id) {
    	setID(this, id);
    }

    
	/**
	 * Starts the camera/data acquisition. (Non-blocking)
	 * Does nothing if it is already started (FLAG_ACTIVITY_NEW_TASK)
	 */
	public void startDataAcq() {
		Intent in = new  Intent(this, DataAcq.class);
		in.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
		startActivity(in);
		isActivityRunning = true;
	}
	
	
	/**
	 * Starts the camera/data acquisition only after a fixed time past startup and a fixed time past the command.
	 */
	public void startDataAcqWithWait() {
		// wait at least a minute after startup before starting data acq.
		// Wait a fixed amount of time before starting data acquisition.
		try {
			Thread.sleep(Math.max(waitTimeAfterPlugged, waitTimeAfterStart-(System.currentTimeMillis()-serviceStartTime)));
		} catch (InterruptedException e) {
			e.printStackTrace();
		}				
		
		startDataAcq();
	}

	
	/**
	 * Stops the camera from taking pictures. (Non-blocking)
	 */
	public void stopDataAcq() {
		sendBroadcast(new Intent("STOP"));
		isActivityRunning = false;
	}
	
	
    /**
     * Data acquisition starts a short period after the battery is plugged in.
     * This method listens for changes in the battery charging state and starts or
     * stops data acquisition.
     */
	BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
		private static final String TAG = "BatteryReceiver";
		
		public void onReceive(Context context, Intent intent) {
			int scheduleOpt = getScheduleOptions();
			int level = intent.getIntExtra("level", -1);
			int plugged = intent.getIntExtra("plugged", -1);
			
			Log.v(TAG, "scheduleOpt = "+scheduleOpt + " status="+intent.getIntExtra("status", -1));
			Log.v(TAG, "level="+level);
			Log.v(TAG, "plugged="+plugged);
			Log.v(TAG, "activityRunning="+isActivityRunning+" hourly alarm="+hourlyAlarm);
			
			
			if ( isActivityRunning && level<30 ) {
				// stop acquisition if it was started by service and battery is low
				Log.v(TAG, "battery level low, stopping acq.");
				stopDataAcq();
			}
			else if ( isActivityRunning && plugged<1 
					&& (scheduleOpt == SCHEDULE_CHARGINGON || scheduleOpt == SCHEDULE_ACCHARGINGON) ) {
				// stop acquisition if it was started by service, phone is unplugged, and configured for charging 
				Log.v(TAG, "phone unplugged, stopping acq.");
				stopDataAcq();
			}
			else if ( isActivityRunning && plugged!=BatteryManager.BATTERY_PLUGGED_AC
					&& scheduleOpt==SCHEDULE_ACCHARGINGON) {
				// stop acquisition if it was started by service, phone is unplugged from AC, and configured for AC charging
				Log.v(TAG, "phone unplugged from AC, stopping acq.");
				stopDataAcq();
			}
			else if ( (hourlyAlarm || !isActivityRunning) && level>40 
					&& scheduleOpt==SCHEDULE_ALWAYSON ) {
				// start acquisition if it was stopped by service (or never started or hourly alarm triggered), phone is >40%
				Log.v(TAG, "always on, starting acq.");
				startDataAcqWithWait();				
			}
			else if ( (hourlyAlarm || !isActivityRunning) && level>40 && plugged>=1 
					&& scheduleOpt==SCHEDULE_CHARGINGON ) {
				// start acquisition if it was stopped by service (or never started or hourly alarm triggered), phone is charging (and >40%), and configured for charging
				Log.v(TAG, "charging, starting acq.");
				startDataAcqWithWait();
			}
			else if ( (hourlyAlarm || !isActivityRunning) && level>40 && plugged==BatteryManager.BATTERY_PLUGGED_AC
					&& scheduleOpt==SCHEDULE_ACCHARGINGON ) {
				// start acquisition if it was stopped by service (or never started or hourly alarm triggered), phone is AC charging (and >40%), and configured for AC charging
				Log.v(TAG, "AC charging, starting acq.");
				startDataAcqWithWait();
			}
			
			hourlyAlarm = false;
		}
	};

	
	/**
	 * Data transfer occurs whenever there is data and an Internet connection.  This
	 * method listens for changes in network connectivity.
	 */
	BroadcastReceiver networkReceiver = new BroadcastReceiver() {
		private static final String TAG = "NetworkReceiver";	
		private ConnectivityManager cm;
		
		public void onReceive(Context context, Intent intent) {
			cm = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);
			if (cm != null) {
				NetworkInfo ni = cm.getActiveNetworkInfo();
	
				if ( ni != null && ni.getState() == NetworkInfo.State.CONNECTED ) { 
					Log.v(TAG, "network connected");
					try {
						ds.start();
					}
					catch (IllegalThreadStateException e) {
						e.printStackTrace();
					}
				}
			}
		}
	};

	
	/**
	 * Listens for hourly alarm.
	 */
	BroadcastReceiver alarmReceiver = new BroadcastReceiver() {
		private static final String TAG = "AlarmReceiver";	
		
		public void onReceive(Context context, Intent intent) {
			Log.v(TAG, "hourly ALARM");
			hourlyAlarm = true;
		}
	};

		
	/**
	 * At the moment, nothing binds to this service.
	 */
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}
	
	
	/**
	 * Nothing yet.
	 */
	@Override
	public void onStart(Intent intent, int startId) {
		super.onStart(intent, startId);		
		Log.v(TAG, "Distributed observatory service started");
	}

	
	/**
	 * Registers the various Receivers.
	 */
	@Override
	public void onCreate() {
		super.onCreate();		
		Log.v(TAG, "Distributed observatory service created");	
		
		serviceStartTime = System.currentTimeMillis();
		
		am = (AlarmManager)this.getSystemService(Context.ALARM_SERVICE);
		am.setInexactRepeating(AlarmManager.ELAPSED_REALTIME_WAKEUP, SystemClock.elapsedRealtime(), 
				AlarmManager.INTERVAL_HOUR, 
				PendingIntent.getBroadcast(DistObs.this, 0, new Intent("HOURLY_ALARM"), 0));
		
		registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
		registerReceiver(networkReceiver, new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION));
		registerReceiver(alarmReceiver, new IntentFilter("HOURLY_ALARM"));
		
		ds = new DataSend(this);		

		if (getScheduleOptions() == SCHEDULE_ALWAYSON)
			startDataAcq();		
	}

	
	/**
	 * Unregisters the various Receivers.
	 */
	@Override
	public void onDestroy() {
		super.onDestroy();
		Log.v(TAG, "Distributed observatory service destroyed");
		
		unregisterReceiver(batteryReceiver);
		unregisterReceiver(networkReceiver);
		unregisterReceiver(alarmReceiver);
		
		ds = null;
	}

	
	/**
	 * Nothing yet.
	 */
	@Override
	public void onLowMemory() {
		super.onLowMemory();
	}
}