/**
 *  Copyright (c) 2009 Kenneth Jensen
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
import java.io.IOException;
import java.util.Calendar;
import java.util.List;

import android.content.Context;
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
import android.location.LocationListener;
import android.location.LocationManager;
import android.media.AudioManager;
import android.os.Bundle;
import android.util.Log;
import android.view.KeyEvent;
import android.view.SurfaceHolder;
import android.view.SurfaceView;
import android.view.View;




/**
 * SensorView is the view that gathers all the data.  It has to be a View because
 * the camera needs a SurfaceHolder from the SurfaceView class to operate.
 * 	
 * @author kenny
 *
 */
public class SensorView extends SurfaceView {
	
    SurfaceHolder h;
    Camera c;    
    AudioManager am;
    SensorManager sm;

    private static final String TAG = "DistObsCamera";	
    
    private static int fastFilterThreshold = 4;
    private static int fastFilterMinPix = 0;
    private static int fastFilterMaxPix = 20;
    private static int slowFilterThreshold = 16;
    
    
    

    public boolean cameraError = false;
    public boolean analysisDone = true;   
    public boolean created = false;
    public boolean done = true;
    public int numEvents = 0;

    public byte[] lastPic = null;
	private Options bmopt = new Options();

	private long lastStartTime, startTime = 0, finishTime = 0;
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
		 * Makes a comma deliminated string of all the data 
		 * @return	The comma delimiated string.
		 */
		public String toString() {
			return toString(", ");
		}		
		
		/**
		 * Makes a string of all the data with an arbitrary deliminator
		 * @return
		 */
		public String toString(String delim) {
			String str;
			
			str = seqIDstr + delim + picNum + delim + startTime + delim + finishTime;
			
			// if no gps location, just returns 0's
			if (gpsLocation != null)
				str += delim + gpsLocation.getTime() + delim + gpsLocation.getLatitude() + delim + gpsLocation.getLongitude() + delim + gpsLocation.getAltitude();	
			else
				str += delim + 0 + delim + 0 + delim + 0 + delim + 0;
			
			// if no network location, just return 0's
			if (networkLocation != null)
				str +=  delim + networkLocation.getTime() + delim + networkLocation.getLatitude() + delim + networkLocation.getLongitude();
			else
				str += delim + 0 + delim + 0 + delim + 0;
			
			str += delim + accx + delim + accy + delim + accz + delim + magx + delim + magy + delim + magz + delim + temp;     
			return str;
		}
	}
	
	
	/**
	 * Listens for changes to the accelerometer.
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
     * Listens for changes to the magnetometer
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
     * Listens for changes to the temperature sensor.
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
    SensorView(Context context) {
        super(context);
       
        // Turn camera button off.
        this.setOnKeyListener(new OnKeyListener(){
        	@Override
            public boolean onKey(View v, int keyCode, KeyEvent event){
                if(event.getAction() == KeyEvent.ACTION_DOWN) {
                    switch(keyCode) {
                        case KeyEvent.KEYCODE_CAMERA:
                            return true;
                    }
                }
                return false;
            }
        });

        
        // Set up audio manager.  Turn camera sounds off.
        am = (AudioManager)context.getSystemService(Context.AUDIO_SERVICE);        
        am.setStreamMute(AudioManager.STREAM_SYSTEM, true);

        // Set up location manager.  Check for GPS/Network location methods
        lm = (LocationManager)context.getSystemService(Context.LOCATION_SERVICE); 
        List<String> pl = lm.getProviders(true);
        
        for (int i=0; i<pl.size(); i++) {
        	if (pl.get(i).compareTo("gps")==0) {
        		hasGPSLocation = true;
        		// having trouble to get this to update, don't know if this helps
                lm.requestLocationUpdates("gps", 1000000, 0, 
                		new LocationListener() {
							@Override
							public void onLocationChanged(Location location) {
							}
							@Override
							public void onProviderDisabled(String provider) {
							}
							@Override
							public void onProviderEnabled(String provider) {
							}
							@Override
							public void onStatusChanged(String provider, int status,
									Bundle extras) {
							}
						});
        	}
        	else if (pl.get(i).compareTo("network")==0) {
        		hasNetworkLocation = true;
        		// having trouble to get this to update, don't know if this helps
                lm.requestLocationUpdates("network", 1000000, 0, 
                		new LocationListener() {
							@Override
							public void onLocationChanged(Location location) {
							}		
							@Override
							public void onProviderDisabled(String provider) {
							}
							@Override
							public void onProviderEnabled(String provider) {
							}
							@Override
							public void onStatusChanged(String provider, int status,
									Bundle extras) {
							}
						});        		
        	}
        }
        
                
        // Set up sensor manager.
        sm = (SensorManager)context.getSystemService(Context.SENSOR_SERVICE);		
		List<Sensor> sl = sm.getSensorList(Sensor.TYPE_ALL);
		for (int i=0; i<sl.size(); i++) {
			if (sl.get(i).getType() == Sensor.TYPE_ACCELEROMETER)
				sm.registerListener(accEventListener, sl.get(i), SensorManager.SENSOR_DELAY_UI);
			else if (sl.get(i).getType() == Sensor.TYPE_MAGNETIC_FIELD)
				sm.registerListener(magEventListener, sl.get(i), SensorManager.SENSOR_DELAY_UI);
			else if (sl.get(i).getType() == Sensor.TYPE_TEMPERATURE)
				sm.registerListener(tempEventListener, sl.get(i), SensorManager.SENSOR_DELAY_UI);
		}
		
		// Gets holder
        h = getHolder();
        h.setType(SurfaceHolder.SURFACE_TYPE_PUSH_BUFFERS);	// required by Android 1.6


        // Gets sequence ID
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
    public boolean initCamera() {
    	releaseCamera();
    	
    	try {
    		c = Camera.open();
        } catch (RuntimeException e) {
			Log.v(TAG, "Error initializing camera (runtime)");
			e.printStackTrace();
			releaseCamera();
			return false;        	
        }
        
    	if (c==null) {
    		Log.v(TAG, "Could not open camera");
    		return false;
    	}
    	
        try {
        	Log.v(TAG, "Init. camera");

            Camera.Parameters p;
            p = c.getParameters();
            p.set("antibanding", "off");           
            //p.set("effect", "mono");
            p.set("jpeg-quality", 100);
            //p.set("luma-adjust", 1);
            p.set("nightshot-mode", 1);
            p.set("rotation", 0);
            //p.set("whitebalance", "twilight");
            p.set("picture-size", "2048x1536");
            p.set("picture-format", "jpeg");
            c.setParameters(p);
			c.setPreviewDisplay(h);
		} catch (IOException e) {
			Log.v(TAG, "Error initializing camera (io)");
			e.printStackTrace();
			releaseCamera();
			return false;
		}    	  
		
		return true;
    }
    
    
    /**
     * 
     */
    public void releaseCamera() {
    	if ( c != null )
    		c.release();
    	c = null;
    }
    
    
    /**
     * 
     */
    public void finish() {
    	releaseCamera();
    	am.setStreamMute(AudioManager.STREAM_SYSTEM, false);
    	am = null;
    	
    	// unregister the sensor listeners
    	sm.unregisterListener(accEventListener);
    	sm.unregisterListener(magEventListener);
    	sm.unregisterListener(tempEventListener);
    	sm = null;
    	
    	h = null;
    }
    
    
    /**
     * Starts taking a picture.  (Integration of light starts here).
     */
    public boolean setUpPicture() {
       	done = false;
       	try {
       		Log.v(TAG, "Start preview");
            c.startPreview();
            
            lastStartTime = startTime;
            startTime = System.currentTimeMillis();
            
            // Should do half of analysis here, rather than sleep
            try {
				Thread.sleep(500);
				Log.v(TAG, "Sleeping");
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
            
        } catch (RuntimeException e) {
        	Log.v(TAG, "Start preview failed");
        	return false;
        }    	
        
        return true;
    }
         
    
    /**
     * Finishes taking a picture.
     */
    public boolean takePicture() {
		Log.v(TAG, "Take picture");
    	c.takePicture(null, null, jpegCallback);
    		    	
    	finishTime = System.currentTimeMillis();

	    return true;
    }
    
    
	/**
	 * Moves old picture data to lastPic.  Gets new picture data. 
	 */
    PictureCallback jpegCallback = new PictureCallback() {
    	public void onPictureTaken(byte[] _data, Camera _camera) {
    		Log.v(TAG, "Start callback");
    		while (!analysisDone);
    		lastPic = _data;
			done = true;
    	}
    };
    

    private int sumColor(int col) {
    	return Color.red(col) + Color.green(col) + Color.blue(col);
    }
    
    /**
     * The fastFilter is used to weed out most of the images, either because there is nothing there
     * or because the picture was not dark enough.
     * 
     * @param pic
     * @return	True if the picture is a potential event.  False otherwise
     */
    private boolean fastFilter(byte[] pic) {
    	Log.v(TAG, "fastFilter");
    	int numNonZero = 0;
		bmopt.inSampleSize = 8;
		bmopt.inPreferredConfig = Bitmap.Config.ARGB_8888;
		
		Bitmap bm = BitmapFactory.decodeByteArray(pic, 0, pic.length, bmopt);
		
		for (int x=0; x<bm.getWidth(); x++) {
			for (int y=0; y<bm.getHeight(); y++) {
				if (sumColor(bm.getPixel(x, y))>fastFilterThreshold) {
					Log.v(TAG, "ff="+bm.getPixel(x,y)+","+Color.alpha(bm.getPixel(x,y))+","+Color.red(bm.getPixel(x,y))+","+Color.green(bm.getPixel(x, y))+","+Color.blue(bm.getPixel(x, y)));
					numNonZero++;
				}
			}
		}
		
		bm.recycle();
		
		return (numNonZero>fastFilterMinPix && numNonZero<fastFilterMaxPix);    	
    }


    /**
     * The slowFilter is used to do a more in-depth analysis of the image.  It is only run after 
     * an image passes the fastFilter.
     * 
     * @param pic
     * @return	True if the picture is a potential event.  False otherwise
     */
    private boolean slowFilter(byte[] pic) {
    	Log.v(TAG, "slowFilter");
		bmopt.inSampleSize = 4;
		bmopt.inPreferredConfig = Bitmap.Config.ARGB_8888;
		
		Bitmap bm = BitmapFactory.decodeByteArray(pic, 0, pic.length, bmopt);

		for (int x=0; x<bm.getWidth(); x++) {
			for (int y=0; y<bm.getHeight(); y++) {
				if (sumColor(bm.getPixel(x, y))>slowFilterThreshold) {
					Log.v(TAG, "color p,r,g,b="+bm.getPixel(x,y)+","+Color.red(bm.getPixel(x,y))+","+Color.green(bm.getPixel(x, y))+","+Color.blue(bm.getPixel(x, y)));
					bm.recycle();
					return true;
				}        				
			}
		}    	
		
		bm.recycle();		
		
		return false;
    }
    
    
    /**
     * Analyzes the picture.  Saves the data if it is a potential cosmic ray event. 
     */
    public boolean analyzePicture() {
    	Log.v(TAG, "analyzing picture");
    	
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
    	
    	return true;
    }
    
    
    /**
     * Reads all the sensors.
     * 
     * TODO: Proper exception handling
     */
    public boolean gatherData() {

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
    	
		if (hasGPSLocation) {
			Location l = lm.getLastKnownLocation("gps");    			
			if (l!=null)
				eventData.gpsLocation = new Location(l);
			else 
				eventData.gpsLocation = null;
		}
		else
			eventData.gpsLocation = null;
		
		if (hasNetworkLocation) {
			Location l = lm.getLastKnownLocation("network");
			if (l!=null)
				eventData.networkLocation = new Location(l);
			else 
				eventData.networkLocation = null;
		}
		else 
			eventData.networkLocation = null;

	   	return true;
    }


    /**
     * 
     * @param eventData
     * 
     */
    public void saveData(EventData eventData) {
    	try {
    		File dataDir = DistObs.getDataDir(this.getContext());
    		if (!dataDir.exists()) {
    			dataDir.mkdir();
    		}
    		BufferedWriter bw = new BufferedWriter(new FileWriter(dataDir.toString() + "/distobs.dat", true));
    		bw.write(eventData.toString()+"\n");
    		bw.flush();
    		bw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}	    		
    }

    
    /**
     * Saves a potential cosmic ray picture.
     * 
     * @param pic
     * 
     */
	public void savePicture(byte[] pic, EventData eventData) {
        try {
    		File dataDir = DistObs.getDataDir(this.getContext());
    		if (!dataDir.exists()) {
    			dataDir.mkdir();
    		}
        	FileOutputStream fos = new FileOutputStream(dataDir.toString() + "/pic"+eventData.seqIDstr+"-"+eventData.picNum+".jpg");
    		fos.write(pic, 0, pic.length);
    		fos.flush();
    		fos.close();
		} catch (IOException e) {
			e.printStackTrace();
		}	    		
	}
}
