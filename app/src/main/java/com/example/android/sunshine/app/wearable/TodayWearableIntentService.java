/*
 * Copyright (C) 2015 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.example.android.sunshine.app.wearable;

import android.app.IntentService;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.util.Log;

import com.example.android.sunshine.app.Utility;
import com.example.android.sunshine.app.data.WeatherContract;
import com.example.android.sunshine.app.sync.SunshineSyncAdapter;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;

/**
 * IntentService which handles updating all Today widgets with the latest data
 */
public class TodayWearableIntentService extends IntentService implements DataApi.DataListener,
        GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG = TodayWearableIntentService.class.getSimpleName();

    private static final String[] FORECAST_COLUMNS = {
            WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
            WeatherContract.WeatherEntry.COLUMN_SHORT_DESC,
            WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
            WeatherContract.WeatherEntry.COLUMN_MIN_TEMP
    };
    // these indices must match the projection
    private static final int INDEX_WEATHER_ID = 0;
    private static final int INDEX_SHORT_DESC = 1;
    private static final int INDEX_MAX_TEMP = 2;
    private static final int INDEX_MIN_TEMP = 3;
    private GoogleApiClient mGoogleApiClient;

    public TodayWearableIntentService() {
        super("TodayWearableIntentService");
    }

    @Override
    protected void onHandleIntent(Intent intent) {

        if (SunshineSyncAdapter.ACTION_DATA_UPDATED.equals(intent.getAction())) {
            Log.d(TAG, "Updating wearable weather...");
            mGoogleApiClient = new GoogleApiClient.Builder(this)
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();

            mGoogleApiClient.connect();

            Log.d(TAG, "Connecting...");

        }
    }


    private void sendWeatherUpdate() {
        // Get today's data from the ContentProvider
        String location = Utility.getPreferredLocation(this);
        Uri weatherForLocationUri = WeatherContract.WeatherEntry.buildWeatherLocationWithStartDate(
                location, System.currentTimeMillis());
        Cursor data = getContentResolver().query(weatherForLocationUri, FORECAST_COLUMNS, null,
                null, WeatherContract.WeatherEntry.COLUMN_DATE + " ASC");
        if (data == null) {
            return;
        }
        if (!data.moveToFirst()) {
            data.close();
            return;
        }

        // Extract the weather data from the Cursor
        int weatherId = data.getInt(INDEX_WEATHER_ID);
        int weatherArtResourceId = Utility.getArtResourceForWeatherCondition(weatherId);
        Log.d(TAG, " Art=" + weatherId);
        String description = data.getString(INDEX_SHORT_DESC);
        double maxTemp = data.getDouble(INDEX_MAX_TEMP);
        double minTemp = data.getDouble(INDEX_MIN_TEMP);
        String formattedMaxTemperature = Utility.formatTemperature(this, maxTemp);
        String formattedMinTemperature = Utility.formatTemperature(this, minTemp);
        data.close();

        Log.d(TAG, "Sending weather report data... high=" + formattedMaxTemperature + " low=" + formattedMinTemperature + " art=" + weatherId);
        sendWeatherData(formattedMaxTemperature, formattedMinTemperature, weatherId);

    }

    private void sendWeatherData(String high, String low, int weatherId) {
        PutDataMapRequest putDataMapRequest = PutDataMapRequest.create("/weather-update");
        putDataMapRequest.getDataMap().putString("high", high);
        putDataMapRequest.getDataMap().putString("low", low);
        putDataMapRequest.getDataMap().putInt("art", weatherId);
        PutDataRequest putDataRequest = putDataMapRequest.asPutDataRequest();


        Log.d(TAG, "Sending data item..." + mGoogleApiClient.isConnected());
        Wearable.DataApi.putDataItem(mGoogleApiClient,putDataRequest).setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
            @Override
            public void onResult(@NonNull DataApi.DataItemResult dataItemResult) {
                if (dataItemResult.getStatus().isSuccess()) {
                    Log.d(TAG, "Message sent successfully to wearable");
                } else {
                    Log.e(TAG, "Message transfer failed.");
                }
                mGoogleApiClient.disconnect();
            }
        });

    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {

        Log.d(TAG, "Connected to Google api...");
        Wearable.DataApi.addListener(mGoogleApiClient, this);
        sendWeatherUpdate();
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.d(TAG, "Connection to Google api suspended...");

    }

    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {
        Log.d(TAG, "Connection to Google api failed:" + connectionResult);
    }
}
