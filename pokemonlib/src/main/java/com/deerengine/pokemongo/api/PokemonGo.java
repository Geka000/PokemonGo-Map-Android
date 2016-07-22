package com.deerengine.pokemongo.api;

import com.deerengine.pokemongo.exceptions.LoginException;
import com.deerengine.pokemongo.exceptions.PokemonException;
import com.deerengine.pokemongo.protocol.PokemonProto;
import com.google.common.geometry.S2Cell;
import com.google.common.geometry.S2CellId;
import com.google.common.geometry.S2LatLng;
import com.google.protobuf.InvalidProtocolBufferException;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import okhttp3.Cookie;
import okhttp3.CookieJar;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;


public class PokemonGo {
    private final OkHttpClient mClient;
    private String mToken;
    private String mEndpoint;
    private S2LatLng mLocation;


    private String mLogin;
    private String mPassword;
    private PokemonProto.ResponseEnvelop mLastProfileResponse;

    public PokemonGo() {
        mClient = new OkHttpClient.Builder()
                //.followRedirects(false).followSslRedirects(false).build();
                .cookieJar(new CookieJar() {
                    private final HashMap<HttpUrl, List<Cookie>> cookieStore = new HashMap<>();

                    @Override
                    public void saveFromResponse(HttpUrl url, List<Cookie> cookies) {
                        cookieStore.put(url, cookies);
                    }

                    @Override
                    public List<Cookie> loadForRequest(HttpUrl url) {
                        List<Cookie> cookies = cookieStore.get(url);
                        return cookies != null ? cookies : new ArrayList<Cookie>();
                    }
                })
                .build();
        mToken = null;
    }

    public boolean signInIfNeed(String login, String password, S2LatLng location) throws PokemonException{
        boolean needLogin = !login.equals(mLogin) || !password.equals(mPassword) || mToken == null || mEndpoint == null;
        if (!needLogin){
            mLastProfileResponse = PokemonGoStatic.getProfileRetry(mClient, mEndpoint, mToken, location, null, null);
            needLogin = mLastProfileResponse == null;
        }
        if (needLogin){
            return signIn(login, password, location);
        }
        return true;
    }

    public boolean signIn(String login, String password, S2LatLng location) throws PokemonException{
        mLocation = location;
        mLogin = login;
        mPassword = password;
        mToken = null;
        try {
            getToken();
        } catch (IOException e){

        }
        if (mToken == null)
            return false;

        mEndpoint = PokemonGoStatic.getEndpoint(mClient, null, mToken, location);
        mLastProfileResponse = PokemonGoStatic.getProfileRetry(mClient, mEndpoint, mToken, location, null, null);
        if (mLastProfileResponse == null) {
            throw new LoginException();
        }
        System.out.println("login success");
        PokemonProto.ResponseEnvelop.ProfilePayload profile = null;
        try {
            profile = PokemonProto.ResponseEnvelop.ProfilePayload.parseFrom(mLastProfileResponse.getPayload(0).toByteArray());
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }
        if (profile == null){
            System.err.println("Can't parse payload");
            return false;
        }
        System.out.println("username: "+profile.getProfile().getUsername());
        return true;
    }

    public String getToken() throws LoginException, IOException {
        if (mToken == null){
            mToken = PokemonGoStatic.getToken(mClient, mLogin, mPassword);
        }
        return mToken;
    }

    public void setLocation(S2LatLng location){
        mLocation = location;
    }

    public Set<PokemonInfo> getPokemonInfo(S2LatLng location){
        PokemonProto.ResponseEnvelop.HeartbeatPayload h = PokemonGoStatic.getHeartbeat(mClient, mEndpoint, mToken, mLastProfileResponse, location);
        HashMap<String, PokemonProto.ResponseEnvelop.WildPokemonProto> pokemons = new HashMap<>();

        if (h != null) {
            for (PokemonProto.ResponseEnvelop.ClientMapCell cell : h.getCellsList()) {
                for (PokemonProto.ResponseEnvelop.WildPokemonProto wild : cell.getWildPokemonList()) {
                    String hash = wild.getSpawnPointId() + ":" + wild.getPokemon().getId();
                    if (!pokemons.containsKey(hash)) {
                        pokemons.put(hash, wild);
                    }
                }
            }
        }

        HashSet<PokemonInfo> set = new HashSet<>();

        for (PokemonProto.ResponseEnvelop.WildPokemonProto pokemon:pokemons.values()){
            PokemonInfo info = PokemonInfoFacory.create(pokemon.getSpawnPointId(), pokemon.getPokemon().getPokemonId());
            info.disappear_timestamp =  System.currentTimeMillis() + pokemon.getTimeTillHiddenMs();
            info.lat = pokemon.getLatitude();
            info.lng = pokemon.getLongitude();
            set.add(info);
        }

        return set;
    }

    public Set<PokemonInfo> getNearPokemonInfo(S2LatLng location){
        S2CellId parent = S2CellId.fromLatLng(location).parent(15);

        ArrayList<S2CellId> children = new ArrayList<>();
        S2CellId child_begin = parent.childBegin();
        S2CellId child_end = parent.childEnd();
        S2CellId current = child_begin;
        while (!current.equals(child_end)){
            children.add(current);
            current = current.next();
        }

        HashSet<PokemonInfo> result = new HashSet<>();
        for (S2CellId child:children){
            S2LatLng latLng = new S2LatLng(new S2Cell(child).getCenter());
            result.addAll(getPokemonInfo(latLng));
        }

        return result;
    }



}
