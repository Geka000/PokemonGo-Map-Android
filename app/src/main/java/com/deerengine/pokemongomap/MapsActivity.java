package com.deerengine.pokemongomap;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.support.v4.app.ActivityCompat;
import android.support.v4.util.Pair;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.Toast;

import com.crashlytics.android.Crashlytics;
import com.deerengine.pokemongo.api.PokemonGo;
import com.deerengine.pokemongo.api.PokemonGoStatic;
import com.deerengine.pokemongo.api.PokemonInfo;
import com.deerengine.pokemongo.exceptions.LoginException;
import com.deerengine.pokemongo.exceptions.PokemonException;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.common.geometry.S2Cell;
import com.google.common.geometry.S2CellId;
import com.google.common.geometry.S2LatLng;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashSet;
import java.util.Set;

import io.fabric.sdk.android.Fabric;

public class MapsActivity extends AppCompatActivity implements OnMapReadyCallback {

    private static final int REQUEST_CODE = 123;

    private GoogleMap mMap;
    PokemonGo mApi;
    private LatLng mCurrentLocation;
    private Marker mCurrentMarker;

    private ArrayList<Marker> mPokemonMarkers = new ArrayList<>();
    private ArrayList<Marker> mTestMarkers = new ArrayList<>();
    private HashSet<PokemonInfo> mPokemonInfoSet = new HashSet<>();
    private Settings mSettings;
    private GetInfoTask mTask;
    private Handler mHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (!BuildConfig.DEBUG) {
            Fabric.with(this, new Crashlytics());
        }
        setContentView(R.layout.activity_maps);
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);
        ActionBar mToolbar = getSupportActionBar();
        mApi = new PokemonGo();
        mSettings = new Settings(this);
        mHandler = new Handler();
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_logout){
            mSettings.setLogin(null);
            mSettings.setPassword(null);
            return true;
        } else
            return super.onOptionsItemSelected(item);
    }


    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        if (ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED && ActivityCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[] {Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION}, REQUEST_CODE);
        } else {
            mMap.setMyLocationEnabled(true);
        }

        setCameraLocation();

        mMap.setOnMapClickListener(new GoogleMap.OnMapClickListener() {
            @Override
            public void onMapClick(LatLng latLng) {
                if (TextUtils.isEmpty(mSettings.getLogin())){
                    showLoginDialog();
                    return;
                }
                mCurrentLocation = latLng;
                if (mCurrentMarker != null){
                    mCurrentMarker.remove();
                    mCurrentMarker = null;
                }
                mCurrentMarker = mMap.addMarker(new MarkerOptions().position(mCurrentLocation));

                for (Marker marker :mTestMarkers){
                    marker.remove();
                }
                mTestMarkers.clear();

                S2LatLng currentLocation = S2LatLng.fromDegrees(mCurrentLocation.latitude, mCurrentLocation.longitude);

                ArrayList<S2LatLng> points = new ArrayList<S2LatLng>();

                final double RADIUS = 500;

                final double EARTH_RADIUS = 6372795.0;
                double meterStep = 70;//140;
                int stepCount = (int) (RADIUS / meterStep);

                double latStep = meterStep/EARTH_RADIUS;
                double lngStep = meterStep/(
                        EARTH_RADIUS*Math.cos(currentLocation.latRadians())
                );

                for (       double latRad = currentLocation.latRadians()-latStep*stepCount; latRad <= currentLocation.latRadians() + latStep*stepCount; latRad += latStep){
                    for (   double lngRad = currentLocation.lngRadians()-lngStep*stepCount; lngRad <= currentLocation.lngRadians() + lngStep*stepCount; lngRad += lngStep) {
                        S2LatLng point = S2LatLng.fromRadians(latRad, lngRad);
                        double distance = PokemonGoStatic.distance(currentLocation, point);
                        if (distance <= 1.1*RADIUS) {
                            points.add(point);
                        }
                    }
                }

                HashSet<S2CellId> cells  = new HashSet<S2CellId>();
                for (S2LatLng point: points){
                    cells.add(S2CellId.fromLatLng(point).parent(16));
                }
                points.clear();
                for (S2CellId cell:cells){
                    points.add(new S2LatLng(new S2Cell(cell).getCenter()));
                }


                for (S2LatLng point: points){
                    mTestMarkers.add(mMap.addMarker(new MarkerOptions().position(new LatLng(point.latDegrees(), point.lngDegrees()))));
                }

                if (mTask != null && !mTask.isCancelled()){
                    mTask.cancel(true);
                }
                mTask = new GetInfoTask();
                mTask.execute(points);


            }
        });
        mMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker) {
                if (mPokemonMarkers.contains(marker)){
                    //marker.showInfoWindow();
                    //return true;
                }
                return false;
            }
        });

    }



    private void addMarker(PokemonInfo info){
        int id = getResources().getIdentifier("pid"+info.id,"drawable",MapsActivity.this.getPackageName());
        long time = (info.disappear_timestamp - System.currentTimeMillis())/1000;
        Marker marker = mMap.addMarker(
                new MarkerOptions()
                        .title(info.name)
                        .snippet("осталось "+time+" сек")
                        .position(new LatLng(info.lat, info.lng))
                        .icon(BitmapDescriptorFactory.fromResource(id)));
        mPokemonMarkers.add(marker);
    }

    private class GetInfoTask extends  AsyncTask<ArrayList<S2LatLng>,Pair<Set<PokemonInfo>,S2LatLng>,Set<PokemonInfo>>{

        @Override
        protected void onPreExecute() {
            for (Marker marker:mPokemonMarkers){
                marker.remove();
            }
            mPokemonMarkers.clear();
            mPokemonInfoSet = new HashSet<>();
            super.onPreExecute();
        }

        @Override
        protected Set<PokemonInfo> doInBackground(ArrayList<S2LatLng>... params) {
            try {
                double minLat = params[0].get(0).latDegrees();
                double maxLat = params[0].get(0).latDegrees();
                double minLng = params[0].get(0).lngDegrees();
                double maxLng = params[0].get(0).lngDegrees();
                
                for (S2LatLng point:params[0]){
                    if (minLat >= point.latDegrees())
                        minLat = point.latDegrees();
                    if (maxLat <= point.latDegrees())
                        maxLat = point.latDegrees();
                    if (minLng >= point.lngDegrees())
                        minLng = point.lngDegrees();
                    if (maxLng <= point.lngDegrees())
                        maxLng = point.lngDegrees();
                }
                S2LatLng startPoint = S2LatLng.fromDegrees(minLat+(maxLat-minLat)/2.0, minLng+(maxLng-minLng)/2.0);
                ArrayList<Pair<Double,S2LatLng>> distPoints = new ArrayList<>();
                for (S2LatLng point: params[0]){
                    double distance = PokemonGoStatic.distance(startPoint, point);
                    distPoints.add(new Pair<>(distance, point));
                    Log.d("pokemon",String.format("%f",distance));

                }

                Collections.sort(distPoints, new Comparator<Pair<Double, S2LatLng>>() {
                    @Override
                    public int compare(Pair<Double, S2LatLng> lhs, Pair<Double, S2LatLng> rhs) {
                        return Double.compare(lhs.first, rhs.first);
                    }
                });

                System.out.println("START");
                if (!mApi.signInIfNeed(mSettings.getLogin(), mSettings.getPassword(), startPoint))
                    return null;

                if (isCancelled())
                    return null;

                Set<PokemonInfo> all = new HashSet<>();

                for(Pair<Double,S2LatLng> point: distPoints){
                    if (isCancelled())
                        return null;
                    final Set<PokemonInfo> set = mApi.getPokemonInfo(point.second);
                    Log.d("pokemon", ""+set.size());
                    if (set == null) {
                        break;
                    }
                    all.addAll(set);
                    publishProgress(new Pair<Set<PokemonInfo>, S2LatLng>(set, new S2LatLng(point.second.lat(),point.second.lng())));
                }
                return all;
            } catch (LoginException e){
                showLoginDialogFromThread(e.getMessage());
                this.cancel(true);
            } catch (PokemonException e){
            }
            return null;
        }

        @Override
        protected void onProgressUpdate(Pair<Set<PokemonInfo>,S2LatLng>... values) {
            for (Pair<Set<PokemonInfo>,S2LatLng> pair: values){
                for (PokemonInfo info: pair.first){
                    if (!mPokemonInfoSet.contains(info)){
                        mPokemonInfoSet.add(info);
                        addMarker(info);
                    }
                }
                int bestIndex = -1;
                double minLat = 100;
                double minLng = 100;
                for (int i = 0;i< mTestMarkers.size();i++){
                    LatLng position = mTestMarkers.get(i).getPosition();
                    double dlat = Math.abs(pair.second.latDegrees()-position.latitude);
                    double dlng = Math.abs(pair.second.lngDegrees()-position.longitude);
                    if ( dlat <= minLat && dlng <= minLng){
                        minLat = dlat;
                        minLng = dlng;
                        bestIndex = i;
                    }
                }
                if (bestIndex != -1) {
                    mTestMarkers.get(bestIndex).remove();
                    mTestMarkers.remove(bestIndex);
                }
            }
        }

        @Override
        protected void onPostExecute(Set<PokemonInfo> pokemonInfos) {
            if (isCancelled())
                return;
            if (pokemonInfos == null) {
                Toast.makeText(MapsActivity.this, getString(R.string.message_network_error), Toast.LENGTH_SHORT).show();
                return;
            }
            mPokemonInfoSet = new HashSet<>(pokemonInfos);
            Toast.makeText(MapsActivity.this, getString(R.string.message_found_count, mPokemonInfoSet.size()), Toast.LENGTH_SHORT).show();
            for (Marker marker:mPokemonMarkers){
                marker.remove();
            }
            mPokemonMarkers.clear();
            for (PokemonInfo info: mPokemonInfoSet){
                addMarker(info);
            }
        }
    }

    @Override
    protected void onPause() {
        if (mMap != null){
            mSettings.setLastLocation(mMap.getCameraPosition().target);
            mSettings.setLastZoom(mMap.getCameraPosition().zoom);
        }
        super.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
    }

    private void setCameraLocation() {
        LatLng cameraLocation = mSettings.getLastLocation();
        Float zoom = mSettings.getLastZoom();
        if (cameraLocation != null && zoom != null){
            mMap.moveCamera(CameraUpdateFactory.newLatLngZoom(cameraLocation, zoom));
        }
    }

    private void showLoginDialog() {
        LoginDialogFragment dialog = LoginDialogFragment.newInstance();
        dialog.show(getSupportFragmentManager(),"login");
    }

    private void showLoginDialogFromThread(){
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                showLoginDialog();
            }
        });
    }
    private void showLoginDialogFromThread(final String text){
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                Toast.makeText(MapsActivity.this, text, Toast.LENGTH_LONG).show();
                showLoginDialog();
            }
        });
    }
}
