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

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

/**
 * Starts Distributed Observatory on boot.
 * @author kenny
 */
public class BootReceiver extends BroadcastReceiver {
	private static final String TAG = "DistObsService";	
	
	@Override
	public void onReceive(Context context, Intent intent) {
		
		Log.v(TAG, "Action="+intent.getAction());
		Intent distObsIntent = new Intent(context, DistObs.class);
		context.startService(distObsIntent);
	}
}
