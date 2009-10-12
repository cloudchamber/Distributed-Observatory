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

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.os.IBinder;
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
 *
 * Data formating:
 * It is expected that the data format will change with new versions of the client 
 * software.  Thus, during data upload, the client transmits the version of the 
 * software.
 * 
 * Format v0.1
 * seqIDstr, 		picNum,		picStartTime,	picFinishTime, 	gpsFixTime,		gpsLatitude,	gpsLongitude,	gpsAltitude,	networkFixTime,		networkLatitude, 	networkLongitude, 	accx, 	accy,	accz, 	magx,	magy,	magz,	temp
 * 20090921T002100, 23, 		12341983274, 	12341983999, 	12341000000, 	37.1324, 		-142.1412, 		0,				12341000000, 		37.2,				-142.2,				9.81, 	0,		0,		1, 		0,		0,		37
 *
 *
 *
 *
 * TODO: 
 * 0) Take data!
 * 1) Check incoming call -- caused an uncaught exception 
 * 3) figure out security/privacy/anti-cheating measures
 * 4) make options menu (allows control over when it turns on, what data it sends, etc...)
 * 5) spruce up data display
 * 6) Disable camera button.
 * 
 */

public class DistObs extends Service {

	private static final String TAG = "DistObsService";	
	
	private static long waitTimeAfterStart = 6000; // ms
	private static long waitTimeAfterPlugged = 2000; // ms
	
	private boolean alwaysOn = true;
	public boolean isActivityRunning = false;
	private long serviceStartTime = 0;
	private DataSend ds;
	
	
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
    	try {
			Log.v(TAG, "trying to open config");
			FileInputStream fis = c.openFileInput("config");
			InputStreamReader isr = new InputStreamReader(fis);
			BufferedReader br = new BufferedReader(isr);
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
		
    	return -1;
    }
    
    public int getID() {
    	return getID(this);
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
			if ( intent.getIntExtra("plugged", -1) > 0 ) {
				Log.v(TAG, "Plugged");
				// wait at least a minute after startup before a "plugged" signal
				// starts data acq.
				if (System.currentTimeMillis()-serviceStartTime > waitTimeAfterStart) {
					// Wait a fixed amount of time before starting data acquisition.
					try {
						Thread.sleep(waitTimeAfterPlugged);
					} catch (InterruptedException e) {
						e.printStackTrace();
					}				
					
					startDataAcq();
				}
			}
			else {
				Log.v(TAG, "Unplugged");
				if (!alwaysOn)
					stopDataAcq();				
			}			 
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
					ds.start();
				}
			}
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
		
		registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
		registerReceiver(networkReceiver, new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION));
		
		ds = new DataSend(this);
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