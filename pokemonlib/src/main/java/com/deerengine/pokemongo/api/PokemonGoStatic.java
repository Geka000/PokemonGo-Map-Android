package com.deerengine.pokemongo.api;


import com.deerengine.pokemongo.exceptions.LoginException;
import com.deerengine.pokemongo.protocol.PokemonProto;
import com.google.common.geometry.S2CellId;
import com.google.common.geometry.S2LatLng;
import com.google.protobuf.ByteString;
import com.google.protobuf.InvalidProtocolBufferException;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Formatter;

import okhttp3.Call;
import okhttp3.FormBody;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;


public class PokemonGoStatic {
    private static final String TAG = "PokemonGoApi";

    private static final String API_URL = "https://pgorelease.nianticlabs.com/plfe/rpc";
    private static final String LOGIN_URL = "https://sso.pokemon.com/sso/login?service=https%3A%2F%2Fsso.pokemon.com%2Fsso%2Foauth2.0%2FcallbackAuthorize";
    private static final String LOGIN_OAUTH = "https://sso.pokemon.com/sso/oauth2.0/accessToken";
    private static final String PTC_CLIENT_SECRET = "w8ScCUXJQc6kXKw8FiOhd8Fixzht18Dq3PEVkUCP5ZPxtgyWsbTvWHFLm2wNY0JR";

    public static String getToken(OkHttpClient client, String login, String password) throws LoginException, IOException{
        return login(client, login, password);
    }

    private static String login(OkHttpClient client, String username, String password) throws LoginException, IOException{

        if (password.length() > 15)
            password = password.substring(0,15);

        try {
            Request request = new Request.Builder().addHeader("User-Agent", "Niantic App").url(LOGIN_URL).get().build();
            Call call = client.newCall(request);
            Response response = call.execute();
            String json_body =response.body().string() ;
            response.close();
            JSONObject jdata = new JSONObject(json_body);

            RequestBody login_body = new FormBody.Builder()
                    .add("lt", jdata.getString("lt"))
                    .add("execution", jdata.getString("execution"))
                    .add("_eventId","submit")
                    .add("username",username)
                    .add("password",password)
                    .build();

            Request login_request = new Request.Builder().addHeader("User-Agent", "Niantic App").url(LOGIN_URL).post(login_body).build();
            Call login_call = client.newCall(login_request);
            response = login_call.execute();

            LoginException exception = null;
            try {
                JSONObject jsonResult = new JSONObject(response.body().string());
                if (jsonResult.has("errors")){
                    int length = jsonResult.getJSONArray("errors").length();
                    if (length > 0) {
                        System.err.println(jsonResult.getJSONArray("errors").toString());
                        exception = new LoginException(jsonResult.getJSONArray("errors").get(0).toString());
                    }
                }
            } catch (JSONException e){
                e.printStackTrace();
            }
            String ticket = response.request().url().toString();
            try {
                ticket = ticket.split("ticket=")[1].split("=")[0];
            } catch (IndexOutOfBoundsException e){
                if (exception == null)
                    exception = new LoginException();
                throw exception;
            }
            response.close();

            RequestBody oauth_body = new FormBody.Builder()
                    .add("client_id", "mobile-app_pokemon-go")
                    .add("redirect_uri", "https://www.nianticlabs.com/pokemongo/error")
                    .add("client_secret", PTC_CLIENT_SECRET)
                    .add("grant_type", "refresh_token")
                    .add("code", ticket)
                    .build();

            response = client.newCall(new Request.Builder().url(LOGIN_OAUTH).post(oauth_body).build()).execute();
            String oauth_result = response.body().string();
            String token = oauth_result.split("&expires")[0].split("=")[1];
            response.close();
            return token;
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return null;
    }

    private static PokemonProto.ResponseEnvelop apiRequestRetry(OkHttpClient client,String end_point, String token, S2LatLng point, PokemonProto.UnknownAuth auth, PokemonProto.RequestEnvelop.Builder req) {
        int count = 10;
        while (count>0) {
            try {
                PokemonProto.ResponseEnvelop result = apiRequest(client, end_point, token, point, auth, req);
                if (result != null) {
                    System.err.println("[+]("+count+") request success");
                    return result;
                } else {
                    count--;
                }
            } catch (IOException e) {
                System.err.println("[-]("+count+") "+e.getLocalizedMessage());
                count--;
            }
        }
        return null;
    }

    private static PokemonProto.ResponseEnvelop apiRequest(OkHttpClient client, String end_point, String token, S2LatLng point, PokemonProto.UnknownAuth auth, PokemonProto.RequestEnvelop.Builder req) throws IOException {
        PokemonProto.RequestEnvelop.Builder builder = PokemonProto.RequestEnvelop.newBuilder()
                .setRpcId(1469378659230941192l)
                .setUnknown1(2)
                .setLatitude(f2i(point.latDegrees()))
                .setLongitude(f2i(point.lngDegrees()))
                .setAltitude(0)
                .setUnknown12(989);
        if (auth == null){
                builder.setAuth(PokemonProto.RequestEnvelop.AuthInfo.newBuilder()
                .setProvider("ptc")
                .setToken(
                        PokemonProto.RequestEnvelop.AuthInfo.JWT.newBuilder()
                                .setContents(token)
                                .setUnknown13(14)
                                .build()
                ).build());
        } else {
            builder.setUnknown11(PokemonProto.UnknownAuth.newBuilder()
                    .setUnknown71(auth.getUnknown71())
                    .setUnknown72(auth.getUnknown72())
                    .setUnknown73(auth.getUnknown73())
                    .build());
        }

        builder.mergeFrom(req.clone().buildPartial());

        byte[] request = builder.build().toByteArray();

        RequestBody body = RequestBody.create(MediaType.parse("image/jpeg"),request);
        Response result = client.newCall(new Request.Builder().url(end_point).post(body).build()).execute();

        PokemonProto.ResponseEnvelop resp = PokemonProto.ResponseEnvelop.parseFrom(result.body().bytes());
        result.close();
        return resp;
    }

    public static PokemonProto.ResponseEnvelop getProfile(OkHttpClient client, String endpoint, String token, S2LatLng point, PokemonProto.UnknownAuth auth, ArrayList<PokemonProto.RequestEnvelop.Requests> requests){
        PokemonProto.RequestEnvelop.Requests.Builder req1 = PokemonProto.RequestEnvelop.Requests.newBuilder();
        req1.setType(2);
        if (requests != null && requests.size() >= 1)
            req1.mergeFrom(requests.get(0));
        PokemonProto.RequestEnvelop.Requests.Builder req2 = PokemonProto.RequestEnvelop.Requests.newBuilder();
        req2.setType(126);
        if (requests != null && requests.size() >= 2)
            req2.mergeFrom(requests.get(1));
        PokemonProto.RequestEnvelop.Requests.Builder req3 = PokemonProto.RequestEnvelop.Requests.newBuilder();
        req3.setType(4);
        if (requests != null && requests.size() >= 3)
            req3.mergeFrom(requests.get(2));
        PokemonProto.RequestEnvelop.Requests.Builder req4 = PokemonProto.RequestEnvelop.Requests.newBuilder();
        req4.setType(129);
        if (requests != null && requests.size() >= 4)
            req4.mergeFrom(requests.get(3));
        PokemonProto.RequestEnvelop.Requests.Builder req5 = PokemonProto.RequestEnvelop.Requests.newBuilder();
        req5.setType(5);
        if (requests != null && requests.size() >= 5)
            req5.mergeFrom(requests.get(4));

        PokemonProto.RequestEnvelop.Builder request = PokemonProto.RequestEnvelop.newBuilder()
                .addRequests(req1)
                .addRequests(req2)
                .addRequests(req3)
                .addRequests(req4)
                .addRequests(req5);

        return apiRequestRetry(client, endpoint, token, point, auth, request);
    }

    public static PokemonProto.ResponseEnvelop getProfileRetry(OkHttpClient client, String endpoint, String token, S2LatLng point, PokemonProto.UnknownAuth auth, ArrayList<PokemonProto.RequestEnvelop.Requests> requests){
        int count = 10;
        while (count>0) {
            PokemonProto.ResponseEnvelop result = getProfile(client, endpoint, token, point, auth, requests);
            if (result != null && result.getPayloadCount() > 0)
                return  result;
            count--;
        }
        return null;
    }


    public static String getEndpoint(OkHttpClient client, String endpoint, String token, S2LatLng point){
        if (endpoint == null)
            endpoint = API_URL;
        PokemonProto.ResponseEnvelop profile_response = getProfileRetry(client, endpoint, token, point, null, null);
        return String.format("https://%s/rpc",profile_response.getApiUrl());
    }

    public static PokemonProto.ResponseEnvelop.HeartbeatPayload getHeartbeat(OkHttpClient client, String endpoint, String token, PokemonProto.ResponseEnvelop profileResponse, S2LatLng point){
        //m4 время
        PokemonProto.RequestEnvelop.Requests.Builder m4 = PokemonProto.RequestEnvelop.Requests.newBuilder();
        PokemonProto.RequestEnvelop.MessageSingleInt.Builder m_int = PokemonProto.RequestEnvelop.MessageSingleInt.newBuilder();
        m_int.setF1(System.currentTimeMillis());
        m4.setMessage(m_int.build().toByteString());
        //m5 хз
        PokemonProto.RequestEnvelop.Requests.Builder m5 = PokemonProto.RequestEnvelop.Requests.newBuilder();
        PokemonProto.RequestEnvelop.MessageSingleString.Builder m_str = PokemonProto.RequestEnvelop.MessageSingleString.newBuilder();
        m_str.setBytes(ByteString.copyFrom("05daf51635c82611d1aac95c0b051d3ec088a930".getBytes()));
        m5.setMessage(m_str.build().toByteString());

        S2CellId cellid = S2CellId.fromLatLng(point).parent(15);
        ArrayList<Long> walkArray = new ArrayList<>();
        walkArray.add(cellid.id());
        S2CellId prev = cellid.prev();
        S2CellId next = cellid.next();
        for (int i =0;i< 10; i++){
            walkArray.add(prev.id());
            walkArray.add(next.id());
            prev = prev.prev();
            next = next.next();
        }
        Collections.sort(walkArray);

        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        for (long walk: walkArray) {
            try {
                stream.write(convert(walk));
            } catch (IOException e1) {
                e1.printStackTrace();
            }
        }

        PokemonProto.RequestEnvelop.MessageQuad.Builder m = PokemonProto.RequestEnvelop.MessageQuad.newBuilder()
                .setF1(ByteString.copyFrom(stream.toByteArray()))
                .setF2(ByteString.copyFrom(new byte[] {
                        0,0,0,0,0,0,0,
                        0,0,0,0,0,0,0,
                        0,0,0,0,0,0,0,
                }))
                .setLat(f2i(point.latDegrees()))
                .setLong(f2i(point.lngDegrees()));


        PokemonProto.RequestEnvelop.Requests.Builder m1 = PokemonProto.RequestEnvelop.Requests.newBuilder()
                .setType(106)
                .setMessage(m.build().toByteString());

        ArrayList<PokemonProto.RequestEnvelop.Requests> array = new ArrayList<>();
        array.add(m1.buildPartial());
        array.add(PokemonProto.RequestEnvelop.Requests.newBuilder().buildPartial());
        array.add(m4.buildPartial());
        array.add(PokemonProto.RequestEnvelop.Requests.newBuilder().buildPartial());
        array.add(m5.buildPartial());

        PokemonProto.ResponseEnvelop response = getProfileRetry(client, endpoint, token, point, profileResponse.getUnknown7(), array);
        ByteString payload = response.getPayload(0);
        PokemonProto.ResponseEnvelop.HeartbeatPayload heartbeat = null ;
        try {
            heartbeat = PokemonProto.ResponseEnvelop.HeartbeatPayload.parseFrom(payload);
        } catch (InvalidProtocolBufferException e) {
            e.printStackTrace();
        }
        return heartbeat;
    }

    static byte[] convert(long value) {
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        int position = 0;
        while (true) {
            if ((value & ~0x7FL) == 0) {
                //buffer[position++] = (byte) value;
                stream.write((byte) value);
                break;
            } else {
                //buffer[position++] = (byte) (((int) value & 0x7F) | 0x80);
                stream.write((byte) (((int) value & 0x7F) | 0x80));
                value >>>= 7;
            }
        }
        return stream.toByteArray();
    }

    static String byteToHex(final byte[] hash)
    {
        Formatter formatter = new Formatter();
        for (byte b : hash)
        {
            formatter.format("x%02x ", b);
        }
        String result = formatter.toString();
        formatter.close();
        return result;
    }

    public static long f2i(double value){
        try {
            final ByteArrayOutputStream output = new ByteArrayOutputStream();
            final DataOutputStream outputStream = new DataOutputStream(output);
            outputStream.writeDouble(value);
            final ByteArrayInputStream input = new ByteArrayInputStream(output.toByteArray());
            final DataInputStream inputStream = new DataInputStream(input);
            long long_lat = inputStream.readLong();
            return long_lat;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public static double i2f(long value){
        try {
            final ByteArrayOutputStream output = new ByteArrayOutputStream();
            final DataOutputStream outputStream = new DataOutputStream(output);
            outputStream.writeLong(value);
            final ByteArrayInputStream input = new ByteArrayInputStream(output.toByteArray());
            final DataInputStream inputStream = new DataInputStream(input);
            double double_lat = inputStream.readDouble();
            return double_lat;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return 0;
    }

    public static final double EARTH_RADIUS = 6372795.0;
    public static final double RADPI = 3.1415925358979323846 / 180;

    public static double distance(S2LatLng p1, S2LatLng p2){
        //double lat1  = p1.latDegrees()  *  RADPI;
        //double lat2  =  p2.latDegrees()  *  RADPI;
        //double long1 = p1.lngDegrees() *  RADPI;
        //double long2 = p2.lngDegrees() *  RADPI;

        double lat1  = p1.latRadians();
        double lat2  =  p2.latRadians();
        double long1 = p1.lngRadians();
        double long2 = p2.lngRadians();

        //вычисление косинусов и синусов широт и разницы долгот
        double cl1 = Math.cos(lat1);
        double cl2 = Math.cos(lat2);
        double sl1 = Math.sin(lat1);
        double sl2 = Math.sin(lat2);
        double delta = long2 - long1;
        double cdelta = Math.cos(delta);
        double sdelta = Math.sin(delta);

        //вычисления длины большого круга
        double y = Math.sqrt(Math.pow(cl2 * sdelta, 2) + Math.pow(cl1 * sl2 - sl1 * cl2 * cdelta, 2));
        double x = sl1 * sl2 + cl1 * cl2 * cdelta;
        double ad = Math.atan2(y, x);
        //расстояние между двумя координатами в метрах
        double dist = ad * EARTH_RADIUS;
        return dist;
    }

}
