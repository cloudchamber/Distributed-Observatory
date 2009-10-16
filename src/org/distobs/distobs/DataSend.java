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
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

import android.content.Context;
import android.telephony.TelephonyManager;
import android.util.Log;


/**
 * 
 * @author kenny
 *
 */
public class DataSend extends Thread {
	private static final String TAG = "NetworkReceiver";	
	private Context c;
	private DistObs d;
	
	
	/**
	 * 
	 * @param distObs
	 */
	public DataSend(DistObs distObs) {
		super();
		c = distObs.getBaseContext();
		d = distObs;
	}

	
	/**
	 * 
	 */
	public void run() {
		registerAndTransfer();
	}
	
	
	/**
	 * 
	 */
	public void finish() {
		
	}
	
	
	/**
	 * Registers a unique ID for the phone.  Transfers data. 
	 */
	public void registerAndTransfer() {
		boolean registered = true;
		
		// attempt to register an ID if not registered
		if (DistObs.getID(c)<0) {
			registered = registerID();
			Log.v(TAG, "registered="+registered);
		}
		
		// once registered, transfer data
		if (registered) {
			if (d.isActivityRunning) {
				Log.v(TAG, "stoping data acq");
				d.stopDataAcq();

				// Wait for it to stop
				try {
					Thread.sleep(5000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}				

				Log.v(TAG, "transferring data");
				transferData();
				
				Log.v(TAG, "starting data acq");
				d.startDataAcq();
			}
			else {
				Log.v(TAG, "transferring data");
				transferData();
			}
		}		
	}
	
	
    /**
     * Gets a unique ID from server
     * @return	Returns true if registration was successful.
     */
    private boolean registerID() {
    	int id = 0;    	
        
    	// registers with the server
		try {
			TelephonyManager tm = (TelephonyManager)c.getSystemService(Context.TELEPHONY_SERVICE);    	

			// set-up connection 
			HttpURLConnection conn = (HttpURLConnection)(new URL("http://www.distobs.org/bin/register")).openConnection();
			conn.setRequestMethod("GET");
            conn.setRequestProperty("imei", tm.getDeviceId());
            conn.setRequestProperty("imsi", tm.getSubscriberId());
            Log.v(TAG, "imei="+tm.getDeviceId()+" imsi="+tm.getSubscriberId());
            
            // get response
        	BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
        	String line = rd.readLine();
        	Log.v(TAG, "id="+line);
            id = Integer.parseInt(line);
            rd.close();            
		} catch (MalformedURLException e) {
			e.printStackTrace();
			return false;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		
		// write registered ID to a config file
    	try {
			OutputStreamWriter osw = new OutputStreamWriter(c.openFileOutput("config", Context.MODE_PRIVATE));
			osw.write(""+id+"\n");
			osw.flush();
			osw.close();
		} catch (FileNotFoundException e) {			
			e.printStackTrace();
			return false;
		} catch (IOException e) {
			e.printStackTrace();
			return false;
		}
		
		return true;
    }
	

	/**
	 * Attempts to transfer all the files in the data directory.  Deletes
	 * successfully transferred files.  Aborts on first failure.
	 */
	private void transferData() {
		boolean success;
		
		if ( DistObs.getDataDir(c).exists() ) {
			String[] fileList = DistObs.getDataDir(c).list();
			for (int i=0; i<fileList.length; i++) {
				success = transferFile(fileList[i]);
				
				// if file transfer succeeded, delete local copy
				// otherwise, stop transferring files
				if (success) {
					File transferedFile = new File(DistObs.getDataDir(c) + "/" + fileList[i]);
					transferedFile.delete();
				}
				else
					break;
			}
		}
	}
	
	
	/**
	 * Attempts to transfer a single file.
	 * 
	 * @param filename	Name (without path) of file to be transferred.
	 * @return 	True on successful transfer.  False otherwise.
	 * 
	 * TODO: clean up
	 * TODO: reduce memory footprint
	 */
	private boolean transferFile(String filename) {
		HttpURLConnection conn = null;
        
        String clientFileName = DistObs.getDataDir(c).toString() + "/" + filename;
        String serverFileName = String.format("%09d-", DistObs.getID(c)) + filename;
        
        String lineEnd = "\r\n";
        String twoHyphens = "--";
        String boundary = "*****";
        
        int bytesRead, bufferSize;
        byte[] buffer;
        int maxBufferSize = 1024*1024;

        // Attempt to upload the file
        try {
            Log.v(TAG, "Start file upload");
            FileInputStream fis = new FileInputStream(new File(clientFileName));
            conn = (HttpURLConnection)(new URL("http://www.distobs.org/bin/upload")).openConnection();

            // set-up connection 
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("filename", serverFileName);
            conn.setRequestProperty("Connection", "Keep-Alive");
            conn.setRequestProperty("Content-Type", "multipart/form-data;boundary="+boundary);

            DataOutputStream dos = new DataOutputStream( conn.getOutputStream() );
            dos.writeBytes(twoHyphens + boundary + lineEnd);
            dos.writeBytes("Content-Disposition: form-data; name=\"uploadedfile\";filename=\"" + serverFileName +"\"" + lineEnd);
            dos.writeBytes(lineEnd);

            // create a buffer of maximum size
            bufferSize = Math.min(fis.available(), maxBufferSize);
            buffer = new byte[bufferSize];	
            // read file and write it into form...
            bytesRead = fis.read(buffer, 0, bufferSize);
            while (bytesRead > 0) {
	            dos.write(buffer, 0, bufferSize);
	            bufferSize = Math.min(fis.available(), maxBufferSize);
	            bytesRead = fis.read(buffer, 0, bufferSize);
            }

            // send multipart form data necesssary after file data...
            dos.writeBytes(lineEnd);
            dos.writeBytes(twoHyphens + boundary + twoHyphens + lineEnd);
            
            // close streams
            fis.close();
            dos.flush();
            dos.close();	
        } catch (MalformedURLException ex) {
        	ex.printStackTrace();
        } catch (IOException ioe) {
        	ioe.printStackTrace();
        }

        // Get response from server
        try {
        	boolean success = false;
            Log.v(TAG, "Reading response (serverFileName="+serverFileName+")");            	
        	BufferedReader rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            String line;
			while ((line = rd.readLine()) != null) {
			    Log.v(TAG, "resp="+line);
			    if (line.compareTo("done")==0) {
			    	success = true;
			    }
			}
            rd.close();                
            return success;
		} catch (IOException e) {
			e.printStackTrace();
		} catch (NullPointerException e) {
			e.printStackTrace();
		}
        
        return false;
	}
}
