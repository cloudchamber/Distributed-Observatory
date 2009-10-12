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

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.LinearLayout;
import android.widget.TextView;


/**
 * 
 * @author kenny
 *
 */
public class DataAcq extends Activity {
    
    private static final String TAG = "DistObsCamera";

	private static final int MENU_ALWAYS_ON = 0;
	private static final int MENU_CHARGING_ON = 1;
    
    private SensorView sv;
    private TextView tv;
    private LinearLayout ll;

    private MainLoop ml;
    private DisplayUpdater du;
    private Activity a;
    

    /**
     * Receives STOP signals from DistObs Service
     */
	BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
		private static final String TAG = "BroadcastReceiver";	
		public void onReceive(Context context, Intent intent) {
			Log.v(TAG, "Broadcast receiver intent = "+intent.getAction());				
			finish();
		}
	};
	

	/**
	 * Updates the display in the thread of the UI (called by runOnUiThread)
	 */
	public class DisplayUpdater implements Runnable {
		@Override
		public void run() {
			if (tv != null) {
	       		tv.setText("Num. Events = " + sv.numEvents + "\n" + sv.lastEventData.toString("\n"));
	       		tv.setWidth(1000);
				tv.postInvalidate();
			}
		}
	}
	
	
	/** 
	 * Thread for main loop
	 * @author kenny
	 */
    public class MainLoop extends Thread {
    	private boolean isRunning;
    	private boolean inLoop;
    	
    	/**
    	 * Main loop
    	 */
    	public void run() {    		
            Log.v(TAG, "running!");

            isRunning = true;
            inLoop = true;
            
    		if (sv.initCamera()) {
	    		while (isRunning) {
	    			Log.v(TAG, "Start loop"); 
	
	        		isRunning = sv.setUpPicture();
	        		if (isRunning)
	        			isRunning = sv.gatherData();
	
	        		runOnUiThread(du);
	        		
	        		if (isRunning)
	        			isRunning = sv.takePicture();
	                if (isRunning)
	                	isRunning = sv.analyzePicture();

	                System.gc();
	                if (isRunning)	             
	                	while (!sv.done);
	    		}
    		}
    		inLoop = false;

    		// Properly close activity once main loop is finished
    		if (!a.isFinishing())
    			a.finish();
    	}
    	
    	
        /**
    	 * 
    	 */
    	public void finish() {
    		Log.v(TAG, "finishing");
    		
    		isRunning = false;    
    		while (inLoop);
    		
    		sv.finish();
    	}
    }


    
    /**
     * Registers the broadcast receiver, which listens for STOP signals.
     * Sets up the view
     * Starts data acquisition thread.
     * 
     * TODO: Move layout into xml file
     */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);        
        Log.v(TAG, "DistObsCamera created");
        
		registerReceiver(broadcastReceiver, new IntentFilter("STOP"));
        		
		a = this;
		
		ll = new LinearLayout(this);
		tv = new TextView(this);
        sv = new SensorView(this);        

        ll.addView(tv);
        ll.addView(sv);
        
        setContentView(ll);

        ml = new MainLoop();    
        du = new DisplayUpdater();             
    }	

	
	/**
	 * 
	 * TODO: Probably not the right way of doing runOnUiThread
	 */
	@Override
	protected void onStart() {
        super.onStart();
        Log.v(TAG, "DistObsCamera started");
        
        ml.start();
    }
	   
	
	/**
	 * Stops MainLoop
	 */
	@Override
	protected void onStop() {
        super.onStop();
        Log.v(TAG, "DistObsCamera stopped");
        
       	ml.finish();
    }

	
	/**
	 * Unregisters the receivers.
	 */
	@Override
	protected void onDestroy() {
        super.onDestroy();
        Log.v(TAG, "DistObsCamera destroyed");

        unregisterReceiver(broadcastReceiver);
        
        sv = null;
        tv = null;
        ll = null;
        ml = null;
        du = null;
    }

	
	/**
	 * Do nothing
	 */
	@Override
	protected void onPause() {
        super.onPause();
        Log.v(TAG, "DistObsCamera pauseed");
    }

	
	/**
	 * Do nothing
	 */
	@Override
	protected void onRestart() {
        super.onRestart();
        Log.v(TAG, "DistObsCamera restarted");
    }
	
	
	/* Creates the menu items */
	public boolean onCreateOptionsMenu(Menu menu) {		
	    menu.add(0, MENU_ALWAYS_ON, 0, "Always on");
	    menu.add(0, MENU_CHARGING_ON, 0, "On when charging");
	    return true;
	}

	/* Handles item selections */
	public boolean onOptionsItemSelected(MenuItem item) {
	    switch (item.getItemId()) {
	    case MENU_ALWAYS_ON:        
	        return true;
	    case MENU_CHARGING_ON:	        
	        return true;
	    }
	    return false;
	}
	
}



