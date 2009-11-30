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
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.SubMenu;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;


/**
 * 
 * @author kenny
 *
 */
public class DataAcq extends Activity {
    
    private static final String TAG = "DistObsCamera";

    private static final int MENU_QUIT = 6;
    
    private SensorView sv;
    private TextView tv;
    private ImageView iv;
    private LinearLayout ll;

    private MainLoop ml;
    private DisplayUpdater du;
    private Activity a;
    
    private int numPastEvents = 0;
    private int numPastSamples = 0;
    

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
			switch ( DistObs.getDisplayOptions(a) ) {
			case DistObs.DISPLAY_DATA:
				if (iv != null) {					
					//iv.setVisibility(ImageView.INVISIBLE);
					//iv.setMaxWidth(1);
				}
				if (tv != null) {
					tv.setVisibility(TextView.VISIBLE);
		       		tv.setText("Distributed Observatory v0.1" 
		       				+ "\nPoss. Events / Pictures = " + sv.numEvents + "/" + sv.lastEventData.picNum 
		       				+ "  (" + (sv.numEvents+numPastEvents) + "/" + (sv.lastEventData.picNum+numPastSamples) + ")" 
		       				+ "\nID = " + DistObs.getID(a)); //sv.lastEventData.toString("\n"));
		       		tv.setWidth(1000);
					tv.postInvalidate();
				}
				break;				
			case DistObs.DISPLAY_NONE:
				if (iv != null) {
					iv.setVisibility(ImageView.VISIBLE);					
					//iv.setMaxWidth(1000);
					//iv.setMaxHeight(1000);					
				}
				if (tv != null) {
					tv.setVisibility(TextView.INVISIBLE);
					//tv.setWidth(1);
				}
				break;
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
	        		Log.v(TAG, "h1");
	        		if (isRunning)
	        			isRunning = sv.gatherData();
	        		Log.v(TAG, "h2");
	        		runOnUiThread(du);
	        		Log.v(TAG, "h3");
	        		if (isRunning)
	        			isRunning = sv.takePicture();
	        		Log.v(TAG, "h4");
	                if (isRunning)
	                	isRunning = sv.analyzePicture();
	                Log.v(TAG, "h5");
	                while (sv.slowFiltering);
	                Log.v(TAG, "h6");
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

    		try {
    			Log.v(TAG, "finishing surface view");
    			sv.finish();
    		}
    		catch (NullPointerException e) {
    			e.printStackTrace();
    		}
    		Log.v(TAG, "finished");

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
		
        numPastEvents = DistObs.getNumEvents(a);
        numPastSamples = DistObs.getNumSamples(a);

		ll = new LinearLayout(this);
		tv = new TextView(this);
		iv = new ImageView(this);
        sv = new SensorView(this);        

        ll.setOrientation(LinearLayout.VERTICAL);
        ll.addView(iv, 0);
        ll.addView(tv, 1);        
        ll.addView(sv, 2);
        iv.setImageDrawable(a.getResources().getDrawable(R.drawable.distobs_white));
        
        setContentView(ll);

        ml = new MainLoop();    
        du = new DisplayUpdater();             
    }	

	
	/**
	 * 
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
        
       	DistObs.setNumEventsAndSamples(a, sv.numEvents+numPastEvents, sv.lastEventData.picNum+numPastSamples);
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
        
        iv = null;
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
        Log.v(TAG, "DistObsCamera paused");
    }

	
	/**
	 * Do nothing
	 */
	@Override
	protected void onRestart() {
        super.onRestart();
        Log.v(TAG, "DistObsCamera restarted");
    }
	
	
	/**
	 * Do nothing
	 */
	@Override
	public void onConfigurationChanged(Configuration newConfig) { 
		super.onConfigurationChanged(newConfig); 
	}
	
	
	/**
	 *  Creates the menu items
	 *  Schedule
	 *    Always on
	 *    On when charging
	 *    On only when run
	 *  Display
	 *    Display sensor data
	 *    No display
	 *  Quit 
	 *  
	 *  TODO: generate via xml
	 */
	public boolean onCreateOptionsMenu(Menu menu) {		
	    SubMenu sm1 = menu.addSubMenu("Schedule");	    	  
	    MenuItem m1i0 = sm1.add(0, DistObs.SCHEDULE_ALWAYSON, 0, "Always on");
	    MenuItem m1i1 = sm1.add(0, DistObs.SCHEDULE_CHARGINGON, 1, "On when charging");
	    MenuItem m1i2 = sm1.add(0, DistObs.SCHEDULE_ACCHARGINGON, 2, "On when AC charging");
	    MenuItem m1i3 = sm1.add(0, DistObs.SCHEDULE_RUNON, 3, "On only when run");
	    m1i0.setCheckable(true);
	    m1i1.setCheckable(true);
	    m1i2.setCheckable(true);
	    m1i3.setCheckable(true);
	    switch (DistObs.getScheduleOptions(this)) {
	    case DistObs.SCHEDULE_ALWAYSON:
	    	m1i0.setChecked(true);
	    	break;
	    case DistObs.SCHEDULE_CHARGINGON:
	    	m1i1.setChecked(true);
	    	break;
	    case DistObs.SCHEDULE_ACCHARGINGON:
	    	m1i2.setChecked(true);
	    	break;
	    case DistObs.SCHEDULE_RUNON:
	    	m1i3.setChecked(true);
	    	break;
	    }
	    sm1.setGroupCheckable(0, true, true);  // must be placed after setting other stuff
	    
	    SubMenu sm2 = menu.addSubMenu("Display");
	    MenuItem m2i0 = sm2.add(0, DistObs.DISPLAY_DATA, 0, "Display sensors data");
	    MenuItem m2i1 = sm2.add(0, DistObs.DISPLAY_NONE, 1, "No display");
	    m2i0.setCheckable(true);
	    m2i1.setCheckable(true);
	    switch (DistObs.getDisplayOptions(this)) {
	    case DistObs.DISPLAY_DATA:
	    	m2i0.setChecked(true);
	    	break;
	    case DistObs.DISPLAY_NONE:
	    	m2i1.setChecked(true);
	    	break;
	    }
	    sm2.setGroupCheckable(0, true, true);  // must be placed after setting other stuff
	    													
	    menu.add(0, MENU_QUIT, 0, "Quit");
	    return true;
	}

	/**
	 * Handles item selections 
	 */
	public boolean onOptionsItemSelected(MenuItem item) {
	    switch (item.getItemId()) {
	    case DistObs.SCHEDULE_ALWAYSON:
	    	DistObs.setScheduleOptions(this, DistObs.SCHEDULE_ALWAYSON);
	    	item.setChecked(true);
	        return true;
	    case DistObs.SCHEDULE_CHARGINGON:
	    	DistObs.setScheduleOptions(this, DistObs.SCHEDULE_CHARGINGON);
	    	item.setChecked(true);
	        return true;
	    case DistObs.SCHEDULE_ACCHARGINGON:
	    	DistObs.setScheduleOptions(this, DistObs.SCHEDULE_ACCHARGINGON);
	    	item.setChecked(true);
	        return true;
	    case DistObs.SCHEDULE_RUNON:
	    	DistObs.setScheduleOptions(this, DistObs.SCHEDULE_RUNON);
	    	item.setChecked(true);
	    	return true;
	    case DistObs.DISPLAY_DATA:
	    	DistObs.setDisplayOptions(this, DistObs.DISPLAY_DATA);
	    	item.setChecked(true);
	    	return true;
	    case DistObs.DISPLAY_NONE:
	    	item.setChecked(true);
	    	DistObs.setDisplayOptions(this, DistObs.DISPLAY_NONE);
	    	return true;
	    case MENU_QUIT:
	    	finish();
	        return true;
	    }
	    return false;
	}
	
}



