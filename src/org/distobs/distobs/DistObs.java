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
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Resources;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.wifi.WifiManager;
import android.os.Environment;
import android.os.Handler;
import android.os.IBinder;
import android.util.Log;


/**
 * 
 * @author kenny
 *
 *
 *
 *
 *
 * It is expected that the data format will change with new versions of the client 
 * software.  Thus, during data upload, the client transmits the version of the 
 * software.
 * 
 * Other sensors (temp, acc, mag, sound level, etc..) 
 * 
 * Format v0.1
 * seqIDstr			, picNum, picStartTime	, picFinishTime	, latitude	, longitude	, locationMethod	, temp	, accx	, accy	, accz	, magx	, magy	, magz
 * 20090921T002100  , 23	, 12341983274	, 12341983999	, 37.1324	, 142.1412	, GPS				, 25	, 0		, 1		, 0		, 0		, 1		, 0
 *
 *
 *
 *
 * TODO: 
 * 0) Take data!
 * 1) Fix bugs!
 * 3) Check incoming call -- caused an uncaught exception 
 * 5) figure out gps on my phone
 * 6) make webpage (reg. distobs.org com net)
 * 8) figure out security/privacy/anti-cheating measures
 */

public class DistObs extends Service {

	private static final String TAG = "DistObsService";	
    private boolean hasData = false;

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
	BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
		private static final String TAG = "BatteryReceiver";	
		public void onReceive(Context context, Intent intent) {
			Log.v(TAG, "Battery receiver intent = "+intent.getExtras().describeContents());						
			if ( intent.getIntExtra("plugged", -1) > 0 ) {
				Log.v(TAG, "Plugged");
				Intent in = new Intent(context, DistObsCamera.class);
				in.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
				startActivity(in);
			}
			else {
				Log.v(TAG, "Unplugged");								
				sendBroadcast(new Intent("STOP"));				
			}			 
		}
	};
	
	
	
	/**
	 * 
	 */
	BroadcastReceiver networkReceiver = new BroadcastReceiver() {
		private static final String TAG = "NetworkReceiver";	
		private ConnectivityManager cm;
		public void onReceive(Context context, Intent intent) {			
			Log.v(TAG, "Network receiver intent = "+intent .getExtras().getParcelable(WifiManager.EXTRA_NETWORK_INFO));
			cm = (ConnectivityManager)context.getSystemService(Context.CONNECTIVITY_SERVICE);

			if ( cm.getActiveNetworkInfo().getState() == NetworkInfo.State.CONNECTED ) { 
				Log.v(TAG, "network connected");
								
				transferData();
				if (hasData) {
					//stopDataAcq();
					//transferData();
					//deleteData();
					//startDataAcq();
				}
			}
			else {
				Log.v(TAG, "network disconnected");
			}
		}
		
		private void transferData() {
			boolean success;
			
			if (getDataDir().exists()) {
				String[] fileList = getDataDir().list();
				for (int i=0; i<fileList.length; i++) {
					Log.v(TAG, "Trying to transfer " + fileList[i]);
					success = transferFile(fileList[i]);
					
					if (success) {
						Log.v(TAG, "Succeeded.  Trying to delete local copy");
						File transferedFile = new File(getDataDir() + "/" + fileList[i]);
						transferedFile.delete();
					}
					else
						break;
				}
			}
		}
		
		private boolean transferFile(String filename) {
			HttpURLConnection conn = null;
            DataOutputStream dos = null;
            DataInputStream inStream = null;
            
            String exsistingFileName = getDataDir().toString() + "/" + filename;
            String serverFileName = "ID000-" + filename;
            String lineEnd = "\r\n";
            String twoHyphens = "--";
            String boundary = "*****";
            int bytesRead, bytesAvailable, bufferSize;
            byte[] buffer;
            int maxBufferSize = 1*1024*1024;
            String responseFromServer = "";
            String urlString = "http://192.168.0.2/upload.php";


            // client request
            try {
	            Log.v(TAG, "Start file upload");
	            FileInputStream fileInputStream = new FileInputStream(new File(exsistingFileName));
	            URL url = new URL(urlString);

	            conn = (HttpURLConnection) url.openConnection();
	            Log.v(TAG, "conn="+conn.toString());
	
	            // Set-up connection 
	            conn.setDoInput(true);
	            conn.setDoOutput(true);
	            conn.setUseCaches(false);
	            conn.setRequestMethod("POST");
	            conn.setRequestProperty("Connection", "Keep-Alive");
	            conn.setRequestProperty("Content-Type", "multipart/form-data;boundary="+boundary);
	
	            dos = new DataOutputStream( conn.getOutputStream() );
	            dos.writeBytes(twoHyphens + boundary + lineEnd);
	            dos.writeBytes("Content-Disposition: form-data; name=\"uploadedfile\";filename=\"" + serverFileName +"\"" + lineEnd);
	            dos.writeBytes(lineEnd);
	
	            // create a buffer of maximum size
	            bytesAvailable = fileInputStream.available();
	            bufferSize = Math.min(bytesAvailable, maxBufferSize);
	            buffer = new byte[bufferSize];
	
	            // read file and write it into form...
	            bytesRead = fileInputStream.read(buffer, 0, bufferSize);
	            while (bytesRead > 0) {
	            	Log.v(TAG, "here");
		            dos.write(buffer, 0, bufferSize);
		            bytesAvailable = fileInputStream.available();
		            bufferSize = Math.min(bytesAvailable, maxBufferSize);
		            bytesRead = fileInputStream.read(buffer, 0, bufferSize);
	            }
	
	            // send multipart form data necesssary after file data...
	            dos.writeBytes(lineEnd);
	            dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);
	            Log.v(TAG, "dos="+dos.toString());
	            
	            // close streams
	            fileInputStream.close();
	            dos.flush();
	            dos.close();	

	            Log.v(TAG, "File was uploaded");
            } catch (MalformedURLException ex) {
            } catch (IOException ioe) {
            }

            //------------------ read the SERVER RESPONSE
            try {
	            Log.v(TAG, "Reading response");            	
            	BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
                String line;
                while ((line = rd.readLine()) != null) {
    	            Log.v(TAG, "resp="+line);
                }
                rd.close();
	            Log.v(TAG, "Done reading response");            	
            } catch (Exception e) {
            }	
            
            return true;
			
		}
		
		
	};


	/**
	 * 
	 */
	@Override
	public IBinder onBind(Intent intent) {
		// TODO Auto-generated method stub
		return null;
	}
	
	
	/**
	 * 
	 */
	@Override
	public void onStart(Intent intent, int startId) {
		super.onStart(intent, startId);
		
		Log.v(TAG, "Distributed observatory service started");	
	}

	
	/**
	 * 
	 */
	@Override
	public void onCreate() {
		super.onCreate();		
		Log.v(TAG, "Distributed observatory service created");	
		
		registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
		registerReceiver(networkReceiver, new IntentFilter(WifiManager.NETWORK_STATE_CHANGED_ACTION));

	}

	
	/**
	 * 
	 */
	@Override
	public void onDestroy() {
		super.onDestroy();
		Log.v(TAG, "Distributed observatory service destroyed");
		
		unregisterReceiver(batteryReceiver);
		unregisterReceiver(networkReceiver);		
	}

	
	/**
	 * 
	 */
	@Override
	public void onLowMemory() {
		super.onLowMemory();
	}
}






