package org.distobs.distobs;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
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
		//c = context;
	}

	
	/**
	 * 
	 * TODO: implement
	 */
	public void run() {
		registerAndTransfer();
	}
	
	
	/**
	 * 
	 * TODO: implement
	 */
	public void finish() {
		
	}
	
	
	/**
	 * 
	 */
	public void registerAndTransfer() {
		boolean registered = false;
		
		if (DistObs.getID(c)<0) {
			registered = registerID();
			Log.v(TAG, "registered="+registered);
		}
		
		if (registered) {
			Log.v(TAG, "stoping data acq");
			
			if (d.isActivityRunning) {
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
     * 
     * TODO: cleanup
     */
    private boolean registerID() {
    	BufferedReader rd;
    	String line;
		HttpURLConnection conn = null;
    	TelephonyManager tm = (TelephonyManager)c.getSystemService(Context.TELEPHONY_SERVICE);
    	String imsiStr = tm.getSubscriberId();
    	String imeiStr = tm.getDeviceId();
    	int id = 0;
    	
    	String urlString = "http://www.distobs.org/register";
        URL url;
		try {
			url = new URL(urlString);
            conn = (HttpURLConnection)url.openConnection();

            // set-up connection 
			conn.setRequestMethod("GET");
            conn.setRequestProperty("imei", imeiStr);
            conn.setRequestProperty("imsi", imsiStr);
            Log.v(TAG, "imei="+imeiStr+" imsi="+imsiStr);
            
            rd = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            while ((line = rd.readLine()) != null) {
            	id = Integer.parseInt(line);
            	Log.v(TAG, "reg resp="+line);
            }
            rd.close();
            
		} catch (MalformedURLException e1) {
			e1.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}


        
    	try {
			FileOutputStream fos = c.openFileOutput("config", Context.MODE_PRIVATE);
			OutputStreamWriter osw = new OutputStreamWriter(fos);
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
	 * TODO: setup real server
	 * TODO: reduce memory footprint
	 */
	private boolean transferFile(String filename) {
		HttpURLConnection conn = null;
        DataOutputStream dos = null;
        
        String clientFileName = DistObs.getDataDir(c).toString() + "/" + filename;
        String serverFileName = String.format("%09d-", DistObs.getID(c)) + filename;
        
        String lineEnd = "\r\n";
        String twoHyphens = "--";
        String boundary = "*****";
        
        int bytesRead, bufferSize;
        byte[] buffer;
        int maxBufferSize = 1024*1024;

        String urlString = "http://www.distobs.org/upload";

        // Attempt to upload the file
        try {
            Log.v(TAG, "Start file upload");
            FileInputStream fis = new FileInputStream(new File(clientFileName));
            URL url = new URL(urlString);
            conn = (HttpURLConnection)url.openConnection();

            // set-up connection 
            conn.setDoInput(true);
            conn.setDoOutput(true);
            conn.setUseCaches(false);
            conn.setRequestMethod("POST");
            conn.setRequestProperty("filename", serverFileName);
            conn.setRequestProperty("Connection", "Keep-Alive");
            conn.setRequestProperty("Content-Type", "multipart/form-data;boundary="+boundary);

            dos = new DataOutputStream( conn.getOutputStream() );
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
