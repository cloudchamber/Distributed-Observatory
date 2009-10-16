package org.distobs.distobs;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class CameraButtonIntentReceiver extends BroadcastReceiver {
	private static final String TAG = "DistObsService";
    @Override
    public void onReceive(Context context, Intent intent) {
    	Log.v(TAG, "camera button pressed!");
    	abortBroadcast();
    }
}
