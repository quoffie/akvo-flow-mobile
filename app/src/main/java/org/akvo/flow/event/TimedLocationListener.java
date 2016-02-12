/*
 *  Copyright (C) 2016 Stichting Akvo (Akvo Foundation)
 *
 *  This file is part of Akvo FLOW.
 *
 *  Akvo FLOW is free software: you can redistribute it and modify it under the terms of
 *  the GNU Affero General Public License (AGPL) as published by the Free Software Foundation,
 *  either version 3 of the License or any later version.
 *
 *  Akvo FLOW is distributed in the hope that it will be useful, but WITHOUT ANY WARRANTY;
 *  without even the implied warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 *  See the GNU Affero General Public License included below for more details.
 *
 *  The full license text can also be seen at <http://www.gnu.org/licenses/agpl.html>.
 */

package org.akvo.flow.event;

import android.content.Context;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;

import java.util.Timer;
import java.util.TimerTask;

/**
 * TimedLocationListener is a reusable helper class to get GPS locations.
 * If geolocation is unknown after TIMEOUT milliseconds, it will time out,
 * and the caller will receive such event.
 */
public class TimedLocationListener implements LocationListener {
    private static final long TIMEOUT   = 1000 * 30; // 1 minute
    private static final float ACCURACY = 20f;       // 20 meters

    public interface Listener {
        void onLocationReady(double lat, double lon);
        void onTimeout();
        void onGPSDisabled();
    }

    private Handler mHandler = new Handler();
    private Listener mListener;
    private LocationManager mLocationManager;
    private Timer mTimer;
    private boolean mListeningLocation = false;

    public TimedLocationListener(Context context, Listener listener) {
        mLocationManager = (LocationManager)context.getSystemService(Context.LOCATION_SERVICE);
        mListener = listener;
    }

    public void start() {
        if (!mLocationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            mListener.onGPSDisabled();
            return;
        }

        mLocationManager.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, this);
        mListeningLocation = true;

        // Ensure no pending tasks are running
        if (mTimer != null) {
            mTimer.cancel();
        }
        mTimer = new Timer();
        mTimer.schedule(new TimerTask() {
            @Override
            public void run() {
                // Ensure it runs on the UI thread!
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        if (mListeningLocation) {
                            stop();
                            mListener.onTimeout();
                        }
                    }
                });
            }
        }, TIMEOUT);
    }

    public void stop() {
        if (mTimer != null) {
            mTimer.cancel();
        }
        mLocationManager.removeUpdates(this);
        mListeningLocation = false;
    }

    public boolean isListening() {
        return mListeningLocation;
    }

    @Override
    public void onLocationChanged(Location location) {
        float currentAccuracy = location.getAccuracy();
        // if accuracy is 0 then the gps has no idea where we're at
        if (currentAccuracy > 0 && currentAccuracy <= ACCURACY && mListeningLocation) {
            mListener.onLocationReady(location.getLatitude(), location.getLongitude());
            stop();
        }
    }

    public void onProviderDisabled(String provider) {
        if (LocationManager.GPS_PROVIDER.equals(provider) && mListeningLocation) {
            stop();// Cancel task and ensure state is updated before passing on the event
            mListener.onGPSDisabled();
        }
    }

    public void onProviderEnabled(String provider) {
    }

    public void onStatusChanged(String provider, int status, Bundle extras) {
    }
}
