package com.deerengine.pokemongomap;

import android.content.Context;
import android.content.SharedPreferences;
import android.preference.PreferenceManager;

import com.google.android.gms.maps.model.LatLng;


public class Settings {
    private final static String KEY_LOGIN = "login";
    private final static String KEY_PASSWORD = "password";

    private final static String KEY_LAST_LAT = "last_location_lat";
    private final static String KEY_LAST_LNG = "last_location_lng";
    private final static String KEY_LAST_ZOOM = "last_location_zoom";


    private final SharedPreferences mPreferences;


    public Settings(Context context){
        mPreferences = PreferenceManager.getDefaultSharedPreferences(context);
    }

    public void setLogin(String login){
        mPreferences.edit().putString(KEY_LOGIN, login).commit();
    }

    public void setPassword(String password){
        mPreferences.edit().putString(KEY_PASSWORD, password).commit();
    }

    public String getLogin(){
        return mPreferences.getString(KEY_LOGIN,"");
    }

    public String getPassword(){
        return mPreferences.getString(KEY_PASSWORD,"");
    }

    public void setLastLocation(LatLng point){
        mPreferences.edit()
                .putFloat(KEY_LAST_LAT, (float)point.latitude)
                .putFloat(KEY_LAST_LNG, (float)point.longitude)
                .apply();
    }

    public LatLng getLastLocation(){
        if (!mPreferences.contains(KEY_LAST_LAT))
            return null;
        return new LatLng(mPreferences.getFloat(KEY_LAST_LAT,0),mPreferences.getFloat(KEY_LAST_LNG,0));
    }

    public void setLastZoom(float zoom_level){
        mPreferences.edit()
                .putFloat(KEY_LAST_ZOOM, zoom_level)
                .apply();
    }

    public Float getLastZoom(){
        if (!mPreferences.contains(KEY_LAST_ZOOM))
            return null;
        return mPreferences.getFloat(KEY_LAST_ZOOM, 10);
    }
}
