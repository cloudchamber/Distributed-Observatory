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

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FileWriter;
import java.util.Calendar;
import java.util.List;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.BitmapFactory.Options;
import android.hardware.Camera;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.Camera.PictureCallback;
import android.location.Location;
import android.location.LocationManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;


/**
 * 
 * @author kenny
 *
 */
public class DistObsCamera extends Activity {
    private Preview mPreview;
    private TextView mTextView;
    private LinearLayout ll;
    private static final String TAG = "DistObsCamera";
    private TestThread thread;
    private boolean isRunning;


    /**
     * 
     */
	BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
		private static final String TAG = "BroadcastReceiver";	
		public void onReceive(Context context, Intent intent) {
			Log.v(TAG, "Broadcast receiver intent = "+intent.getAction());				
			finish();
		}
	};
	
	
	/**
	 * 
	 * @author kenny
	 *
	 */
    public class TestThread extends Thread {
    	public TestThread() {    		
    	}
    	
    	public void run() {    		
            Log.v(TAG, "running!");

            isRunning = true;
    		mPreview.initCamera();
    		while (isRunning) {
    			Log.v(TAG, "Start loop"); 

    			try {
	        		mPreview.setUpPicture();
	        		mPreview.gatherData();
	        		
	        		mTextView.setText("numEvents=" + mPreview.numEvents + "\n" + mPreview.lastEventData.toString("\n"));
	        		mTextView.postInvalidate();
	        		mPreview.postInvalidate();
		    		ll.postInvalidate();
	        		
	        		mPreview.takePicture();
	                mPreview.analyzePicture();
		    		while (!mPreview.done);
    			}
    			catch (Exception e) {
    				
    			}
    		}
    	}
    	
    	public void finish() {
    		Log.v(TAG, "finishing");
    		
    		isRunning = false;    		
    		mPreview.finish();
    	}
    }
	
	@Override
	protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);        
        Log.v(TAG, "DistObsCamera created");
        
		registerReceiver(broadcastReceiver, new IntentFilter("STOP"));
        
		
		ll = new LinearLayout(this);

		mTextView = new TextView(this);
		//mTextView.setMinLines(19);
		mTextView.setText("TextView123456789012345678901234567890\nTextView123456789012345678901234567890\nTextView123456789012345678901234567890\nTextView123456789012345678901234567890\nTextView123456789012345678901234567890\nTextView123456789012345678901234567890\nTextView123456789012345678901234567890\nTextView123456789012345678901234567890\nTextView123456789012345678901234567890\nTextView123456789012345678901234567890\nTextView123456789012345678901234567890\nTextView123456789012345678901234567890\nTextView123456789012345678901234567890\nTextView123456789012345678901234567890\nTextView123456789012345678901234567890\nTextView123456789012345678901234567890\nTextView123456789012345678901234567890\n");
		
        mPreview = new Preview(this);
        
        ll.addView(mTextView);
        ll.addView(mPreview);
        
        setContentView(ll);
        
        thread = new TestThread();
    }	

	@Override
	protected void onStart() {
        super.onStart();
        Log.v(TAG, "DistObsCamera started");
        
        thread.start();
    }
	    
	@Override
	protected void onStop() {
        super.onStop();
        Log.v(TAG, "DistObsCamera stopped");
        
        try {
        	thread.finish();
        }
        catch (Exception e) {        	
        }
    }

	@Override
	protected void onDestroy() {
        super.onDestroy();
        Log.v(TAG, "DistObsCamera destroyed");
        unregisterReceiver(broadcastReceiver);
        mPreview = null;
        thread = null;
    }

	@Override
	protected void onPause() {
        super.onPause();
        Log.v(TAG, "DistObsCamera pauseed");
    }

	@Override
	protected void onRestart() {
        super.onRestart();
        Log.v(TAG, "DistObsCamera restarted");
    }
}




/**
 * 	
 * @author kenny
 *
 */
class Preview extends SurfaceView {
	
    SurfaceHolder mHolder;
    Camera mCamera;    
    AudioManager am;

    private static final String TAG = "DistObsCamera";	

    public boolean analysisDone = true;   
    public boolean created = false;
    public boolean done = true;
    public int numEvents = 0;

    public byte[] lastPic = null;
	private Options bmopt = new Options();

	private long lastStartTime, lastFinishTime, startTime = 0, finishTime = 0;
	private float accx, accy, accz, magx, magy, magz, temp;

	private LocationManager lm = null; 
	private boolean hasGPSLocation = false;
	private boolean hasNetworkLocation = false;
	
	public EventData lastEventData = new EventData();
	public EventData eventData = new EventData();



	/**
	 * 
	 * @author kenny
	 *
	 */
	public class EventData {
		// event ID
		public String seqIDstr;
		public int picNum;
		
		// time/place
		public long startTime, finishTime;
		public Location gpsLocation, networkLocation;
		
		// other sensors
		public float accx, accy, accz, magx, magy, magz, temp;
		
		/**
		 * 
		 */
		public String toString() {
			return toString(", ");
		}
		
		
		/**
		 * 
		 * @return
		 */
		public String toString(String delim) {
			String str;
			
			str = seqIDstr + delim + startTime + delim + finishTime;
			
			if (gpsLocation != null)
				str += delim + gpsLocation.getTime() + delim + gpsLocation.getLongitude() + delim + gpsLocation.getLatitude() + delim + gpsLocation.getAltitude();	
			else
				str += delim + 0 + delim + 0 + delim + 0 + delim + 0;
			
			if (networkLocation != null)
				str +=  delim + networkLocation.getTime() + delim + networkLocation.getLongitude() + delim + networkLocation.getLatitude();
			else
				str += delim + 0 + delim + 0 + delim + 0;
			
			str += delim + accx + delim + accy + delim + accz + delim + magx + delim + magy + delim + magz + delim + temp;     
			return str;
		}
	}
	
	
	/**
	 * 
	 */
    SensorEventListener accEventListener = new SensorEventListener() {
		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) {
		}
		
		@Override
		public void onSensorChanged(SensorEvent event) {
			accx = event.values[0];
			accy = event.values[1];
			accz = event.values[2];			
		}
    };
    
    
    /**
     * 
     */
    SensorEventListener magEventListener = new SensorEventListener() {
		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) {
		}
		
		@Override
		public void onSensorChanged(SensorEvent event) {
			magx = event.values[0];
			magy = event.values[1];
			magz = event.values[2];			
		}
    };
    
    
    /**
     * 
     */
    SensorEventListener tempEventListener = new SensorEventListener() {
		@Override
		public void onAccuracyChanged(Sensor sensor, int accuracy) {
		}
		
		@Override
		public void onSensorChanged(SensorEvent event) {
			temp = event.values[0];
		}
    };

	
    /**
     * 
     * @param context
     */
    Preview(Context context) {
        super(context);
        
        // Set up audio manager.  Turn camera sounds off.
        am = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);        
        am.setStreamMute(AudioManager.STREAM_SYSTEM, true);

        // Set up location manager.  Check for GPS/Network location methods
        lm = (LocationManager)context.getSystemService(Context.LOCATION_SERVICE); 
        List<String> pl = lm.getProviders(true);
        Log.v(TAG, "Num providers = "+pl.size());
        for (int i=0; i<pl.size(); i++) {
        	Log.v(TAG, "Location provider="+pl.get(i));
        	if (pl.get(i).compareTo("gps")==0)
        		hasGPSLocation = true;
        	else if (pl.get(i).compareTo("network")==0)
        		hasNetworkLocation = true;
        }
                
        // Set up sensor manager.
        SensorManager sm = (SensorManager)context.getSystemService(Context.SENSOR_SERVICE);		
		List<Sensor> sl = sm.getSensorList(Sensor.TYPE_ALL);
		for (int i=0; i<sl.size(); i++) {
			if (sl.get(i).getType() == Sensor.TYPE_ACCELEROMETER)
				sm.registerListener(accEventListener, sl.get(i), SensorManager.SENSOR_DELAY_UI);
			else if (sl.get(i).getType() == Sensor.TYPE_MAGNETIC_FIELD)
				sm.registerListener(magEventListener, sl.get(i), SensorManager.SENSOR_DELAY_UI);
			else if (sl.get(i).getType() == Sensor.TYPE_TEMPERATURE)
				sm.registerListener(tempEventListener, sl.get(i), SensorManager.SENSOR_DELAY_UI);
		}
		
        mHolder = getHolder();

        Calendar c = Calendar.getInstance();
        lastEventData.seqIDstr = String.format("%04d%02d%02dT%02d%02d%02d", c.get(Calendar.YEAR), c.get(Calendar.MONTH), 
        		c.get(Calendar.DAY_OF_MONTH), c.get(Calendar.HOUR), 
        		c.get(Calendar.MINUTE), c.get(Calendar.SECOND));
        eventData.picNum = 0;
    }

    
 
    
    /**
     * Functions for taking and analyzing pictures.
     */    
    
    /**
     * Initializes the camera.  Must be called once before any picture taking.
     */
    public void initCamera() {
    	if ( mCamera != null )
    		mCamera.release();
    	mCamera = Camera.open();
        try {
        	Log.v(TAG, "Init. camera");

            Camera.Parameters p;
            p = mCamera.getParameters();
            p.set("antibanding", "off");           
            p.set("effect", "mono");
            p.set("jpeg-quality", 100);
            p.set("luma-adjust", 1);
            p.set("nightshot-mode", 1);
            p.set("rotation", 0);
            p.set("whitebalance", "twilight");
            p.set("picture-size", "2048x1536");
            p.set("picture-format", "jpeg");
            mCamera.setParameters(p);
            mCamera.setPreviewDisplay(mHolder);
        } catch (Exception exception) {
            mCamera.release();
            mCamera = null;
        }    	    	
    }
    
    
    /**
     * 
     */
    public void releaseCamera() {
    	if ( mCamera != null )
    		mCamera.release();
    	mCamera = null;
    }
    
    
    /**
     * 
     */
    public void finish() {
    	releaseCamera();
    	am.setStreamMute(AudioManager.STREAM_SYSTEM, false);
    }
    
    
    /**
     * Starts taking a picture.  (Integration of light starts here).
     */
    public void setUpPicture() {
       	done = false;
        try {
        	Log.v(TAG, "Start preview");
            mCamera.startPreview();
            
            lastStartTime = startTime;
            startTime = System.currentTimeMillis();
            
            // Should do half of analysis here, rather than sleep
            try {
				Thread.sleep(500);
				Log.v(TAG, "Sleeping");
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
            
        } catch (Exception exception) {
            mCamera.release();
            mCamera = null;
        }    	
    }
         
    
    /**
     * Finishes taking a picture.
     */
    public void takePicture() {
		Log.v(TAG, "Take picture");
    	try {
	    	mCamera.takePicture(null, null, jpegCallback);
	    		    	
	    	lastFinishTime = finishTime;
	    	finishTime = System.currentTimeMillis();
	    } catch (Exception exception) {
	        mCamera.release();
	        mCamera = null;
	    }    	
    }

    
    /**
     * The fastFilter is used to weed out most of the images, either because there is nothing there
     * or because the picture was not dark enough.
     * 
     * @param pic
     * @return
     */
    private boolean fastFilter(byte[] pic) {
    	int numNonZero = 0;
		bmopt.inSampleSize = 8;
		
		Bitmap bm = BitmapFactory.decodeByteArray(pic, 0, pic.length, bmopt);
		
		for (int x=0; x<bm.getWidth(); x++) {
			for (int y=0; y<bm.getHeight(); y++) {
				if (Color.blue(bm.getPixel(x, y))>0) {
					numNonZero++;
				}
			}
		}
		
		bm.recycle();
		
		return (numNonZero>0 && numNonZero<20);    	
    }

    
    /**
     * The slowFilter is used to do a more in-depth analysis of the image.  It is only run after 
     * an image passes the fastFilter.
     * 
     * @param pic
     * @return
     */
    private boolean slowFilter(byte[] pic) {
		bmopt.inSampleSize = 1;
		Bitmap bm = BitmapFactory.decodeByteArray(pic, 0, pic.length, bmopt);

		for (int x=0; x<bm.getWidth(); x++) {
			for (int y=0; y<bm.getHeight(); y++) {
				if (Color.blue(bm.getPixel(x, y))>8) {
					bm.recycle();
					return true;
				}        				
			}
		}    	
		
		bm.recycle();		
		
		return false;
    }
    
    
    /**
     * 
     */
    public void analyzePicture() {
    	analysisDone = false;
    	
    	if (lastPic != null) {
	    	if (fastFilter(lastPic)) {
	    		if (slowFilter(lastPic)) {
	    			savePicture(lastPic, lastEventData);
	    			saveData(lastEventData);
	    			numEvents++;
	    		}
	    	}
    	}
    	
    	analysisDone = true;
    }
    
    
    /**
     * Reads all the sensors.
     */
    public void gatherData() {

    	lastEventData.picNum = eventData.picNum;
    	
    	lastEventData.startTime = lastStartTime;
    	lastEventData.finishTime = finishTime;
    	
    	lastEventData.accx = eventData.accx;
    	lastEventData.accy = eventData.accy;
    	lastEventData.accz = eventData.accz;

    	lastEventData.magx = eventData.magx;
    	lastEventData.magy = eventData.magy;
    	lastEventData.magz = eventData.magz;
    	
    	lastEventData.temp = eventData.temp;
    	
    	lastEventData.gpsLocation = eventData.gpsLocation;
    	lastEventData.networkLocation = eventData.networkLocation;

    	
    	eventData.picNum++;
    	
    	eventData.accx = accx;
    	eventData.accy = accy;
    	eventData.accz = accz;

    	eventData.magx = magx;
    	eventData.magy = magy;
    	eventData.magz = magz;
    	
    	eventData.temp = temp;
    	
    	try {
    		if (hasGPSLocation) {
    			Log.v(TAG, "Trying to get GPS location.");
    			eventData.gpsLocation = new Location(lm.getLastKnownLocation("gps"));
    		}
    		else
    			eventData.gpsLocation = null;
    		
    		if (hasNetworkLocation) {
    			Log.v(TAG, "Trying to get network location.");
    			eventData.networkLocation = new Location(lm.getLastKnownLocation("network"));
    		}
    		else 
    			eventData.networkLocation = null;
	    } catch (Exception e) {
	    	e.printStackTrace();
	    }    	
    		   
    }


    /**
     * 
     * @param eventData
     */
    public void saveData(EventData eventData) {
    	try {
    		File dataDir = getDataDir();
    		if (!dataDir.exists()) {
    			dataDir.mkdir();
    		}
    		BufferedWriter bw = new BufferedWriter(new FileWriter(dataDir.toString() + "/distobs.dat", true));
    		bw.write(eventData.toString()+"\n");
    		bw.close();
		} catch (Exception e) {
			e.printStackTrace();
		}	    		
    }

    
    /**
     * Saves a potential cosmic ray picture.
     * 
     * @param pic
     */
	public void savePicture(byte[] pic, EventData eventData) {
        try {
    		File dataDir = getDataDir();
    		if (!dataDir.exists()) {
    			dataDir.mkdir();
    		}
        	FileOutputStream fos = new FileOutputStream(dataDir.toString() + "/pic"+eventData.seqIDstr+"-"+eventData.picNum+".jpg");
    		fos.write(pic, 0, pic.length);
    		fos.flush();
    		fos.close();
		} catch (Exception e) {
			e.printStackTrace();
		}	    		
	}

	
    /**
     * 
     * @return	The directory to store the data in.
     */
    public File getDataDir() {
   		Log.v(TAG, Environment.getExternalStorageDirectory().getPath() + "/" + getResources().getText(R.string.data_dir));
    	return new File(Environment.getExternalStorageDirectory().getPath() + "/" + getResources().getText(R.string.data_dir));
    }
	
	/**
	 * 
	 */
    PictureCallback jpegCallback = new PictureCallback() {
    	public void onPictureTaken(byte[] _data, Camera _camera) {
    		Log.v(TAG, "Start callback");
    		while (!analysisDone);
    		lastPic = _data;
			done = true;
    	}
    };
}
