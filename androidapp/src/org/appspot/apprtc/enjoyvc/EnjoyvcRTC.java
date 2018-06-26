package org.appspot.apprtc.enjoyvc;

import android.util.Base64;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

import java.io.IOException;
import java.net.URISyntaxException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.HashMap;
import java.util.Map;

import io.socket.client.Ack;
import io.socket.emitter.Emitter;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import io.socket.client.IO;
import io.socket.client.Socket;
import android.util.Log;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSession;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

public class EnjoyvcRTC {
    private static final String TAG = "EnjoyvcRTC";

    public static final MediaType JSON
            = MediaType.parse("application/json; charset=utf-8");

    /**
     * Created by Anonymous on 2017/6/13.
     */

        //获取这个SSLSocketFactory
        public static SSLSocketFactory getSSLSocketFactory() {
            try {
                SSLContext sslContext = SSLContext.getInstance("SSL");
                sslContext.init(null, getTrustManager(), new SecureRandom());
                return sslContext.getSocketFactory();
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        //获取TrustManager
        private static TrustManager[] getTrustManager() {
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public void checkClientTrusted(X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public void checkServerTrusted(X509Certificate[] chain, String authType) {
                        }

                        @Override
                        public X509Certificate[] getAcceptedIssuers() {
                            return new X509Certificate[]{};
                        }
                    }
            };
            return trustAllCerts;
        }

        //获取HostnameVerifier
        public static HostnameVerifier getHostnameVerifier() {
            HostnameVerifier hostnameVerifier = new HostnameVerifier() {
                @Override
                public boolean verify(String s, SSLSession sslSession) {
                    return true;
                }
            };
            return hostnameVerifier;
        }

    public EnjoyvcRTC() {
        OkHttpClient.Builder clientBuilder =
                new OkHttpClient().newBuilder().sslSocketFactory(getSSLSocketFactory()).hostnameVerifier(getHostnameVerifier());
        client = clientBuilder.build();// = new OkHttpClient();
    }

    OkHttpClient client;
    JSONObject token;
    JSONObject clientInfo;
    JSONArray iceServers;
    Map sdps = new HashMap();

    public String post(String url, String json) throws IOException {
        RequestBody body = RequestBody.create(JSON, json);
        Request request = new Request.Builder()
                .url(url)
                .post(body)
                .build();
        try (Response response = client.newCall(request).execute()) {
            return response.body().string();
        }
    }

    public static final String enjoyvcUrl = "https://192.168.106.180/cloud/createToken";//"https://vccamp.vccore.com/cloud/createToken";

    public boolean createToken() {
        JSONObject room = new JSONObject();
        String roomStr;
        try {
            room.put("username", "gta");
            room.put("password", "1112");
            roomStr = room.toString();
            String resp = post(enjoyvcUrl, roomStr);
            //String tokenStr = Base64.encodeToString(resp.getBytes(), Base64.DEFAULT);
            String tokenStr = new String(Base64.decode(resp.getBytes(), Base64.DEFAULT));
            token = new JSONObject(tokenStr);
            Log.i(TAG, "get token:" + token);
            return true;
        } catch (JSONException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    Socket socket;
    boolean connected = false;
    EnjoyvcNotify enjoyvcCallback;

    public boolean connected() {
        return connected;
    }

    public interface EnjoyvcNotify {
        void connected();
        void disconnected();
        void checked(JSONArray streams);
        void published(int streamId);
        void subscribed(int streamId);
        void answer(int streamId, SessionDescription sdp);
        void ready(boolean publish, int streamId);
    }

    void sendToken() {
        if(socket == null || token == null)
            return;
        socket.emit("token", token, new Ack() {
            @Override
            public void call(Object... args) {
                String resp = String.valueOf(args[0]);
                if(resp.equals("success")) {
                    try {
                        clientInfo = (JSONObject)args[1];
                        iceServers = clientInfo.getJSONArray("iceServers");
                        if (enjoyvcCallback != null)
                            enjoyvcCallback.checked(clientInfo.getJSONArray("streams"));
                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }

            }
        });
    }

    public void sendPublish() {
        if(socket == null || token == null)
            return;
        JSONObject msg = new JSONObject();
        JSONObject meta = new JSONObject();
        JSONObject mute = new JSONObject();
        try {
            meta.put("type", "publisher");
            meta.put("area", token.get("area"));
            mute.put("audio", false);
            mute.put("video", false);
            msg.put("state", "erizo");
            msg.put("data", false);
            msg.put("audio", false);
            msg.put("video", true);
            msg.put("metadata", meta);
            msg.put("muteStream", mute);
            msg.put("minVideoBW", 0);

        } catch (JSONException e) {
            e.printStackTrace();
        }
        Log.i(TAG, "send publish:");
        Log.i(TAG, String.valueOf(msg));

        socket.emit("publish", msg, "undefined", new Ack() {
            @Override
            public void call(Object... args) {
                Log.i(TAG, "publish callback");
            }
        });
    }

    public void sendOffer(int streamId, String sdp) {
        if(socket == null || token == null)
            return;
        JSONObject signal = new JSONObject();
        JSONObject msg = new JSONObject();
        try {
            msg.put("type", "offer");
            msg.put("sdp", sdp);
            signal.put("msg", msg);
            signal.put("streamId", streamId);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        Log.i(TAG, "send offer:");
        Log.i(TAG, String.valueOf(signal));
        socket.emit("signaling_message", signal);
    }

    public void sendCandidate(int streamId, IceCandidate candidate) {
        if(socket == null || token == null)
            return;
        JSONObject signal = new JSONObject();
        JSONObject msg = new JSONObject();
        JSONObject cand = new JSONObject();
        String candidateStr = "a=" + candidate.sdp;
        try {
            cand.put("sdpMLineIndex", candidate.sdpMLineIndex);
            cand.put("sdpMid", candidate.sdpMid);
            cand.put("candidate", candidateStr);
            msg.put("type", "candidate");
            msg.put("candidate", cand);
            signal.put("msg", msg);
            signal.put("streamId", streamId);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        Log.i(TAG, "send candidate:");
        Log.i(TAG, String.valueOf(signal));
        socket.emit("signaling_message", signal);
    }

    public void sendEndCandidate(int streamId) {
        if (socket == null || token == null)
            return;
        JSONObject signal = new JSONObject();
        JSONObject msg = new JSONObject();
        JSONObject cand = new JSONObject();
        try {
            cand.put("sdpMLineIndex", -1);
            cand.put("sdpMid", "end");
            cand.put("candidate", "end");
            msg.put("type", "candidate");
            msg.put("candidate", cand);
            signal.put("msg", msg);
            signal.put("streamId", streamId);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        Log.i(TAG, "send end candidate:");
        Log.i(TAG, String.valueOf(signal));
        socket.emit("signaling_message", signal);
    }

    public void sendSubscribe(int streamId) {
        if(socket == null || token == null)
            return;
        JSONObject msg = new JSONObject();
        JSONObject meta = new JSONObject();
        JSONObject mute = new JSONObject();
        try {
            meta.put("type", "subscriber");
            meta.put("area", token.get("area"));
            mute.put("audio", false);
            mute.put("video", false);
            msg.put("state", "erizo");
            msg.put("data", false);
            msg.put("audio", false);
            msg.put("video", true);
            msg.put("metadata", meta);
            msg.put("muteStream", mute);
            msg.put("streamId", streamId);

        } catch (JSONException e) {
            e.printStackTrace();
        }
        Log.i(TAG, "send publish:");
        Log.i(TAG, String.valueOf(msg));

        socket.emit("subscribe", msg, "undefined", new Ack() {
            @Override
            public void call(Object... args) {
                Log.i(TAG, "subscribe callback");
            }
        });
    }

    public boolean connect(EnjoyvcNotify callback) {
        if(token == null)
            return false;
        if(socket != null)
            return true;
        enjoyvcCallback = callback;
        try {
            String host = token.getString("host");
            //host = "192.168.106.180";
            IO.Options opts = new IO.Options();

// default settings for all sockets
            IO.setDefaultOkHttpWebSocketFactory(client);
            IO.setDefaultOkHttpCallFactory(client);

// set as an option
            opts.callFactory = client;
            opts.webSocketFactory = client;
            opts.transports = new String[]{"websocket"};
            socket = IO.socket("https://" + host + "/", opts);
            socket.on(Socket.EVENT_CONNECT, new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    connected = true;
                    if(enjoyvcCallback != null)
                        enjoyvcCallback.connected();

                    sendToken();
                }
            }).on("signaling_message_erizo", new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    Log.i(TAG, "signaling message");
                    Log.i(TAG, String.valueOf(args[0]));
                    JSONObject msg = (JSONObject)args[0];
                    try {
                        String type = msg.getJSONObject("mess").getString("type");
                        if(type.equals("started")) {
                            if(msg.has("streamId")) {
                                int streamId = msg.getInt("streamId");
                                if(enjoyvcCallback != null)
                                    enjoyvcCallback.published(streamId);
                            }
                            else {
                                int streamId = msg.getInt("peerId");
                                if(enjoyvcCallback != null)
                                    enjoyvcCallback.subscribed(streamId);
                            }
                        }
                        else if(type.equals("answer")) {
                            int streamId;
                            if(msg.has("streamId")) {
                                streamId = msg.getInt("streamId");
                            }
                            else {
                                streamId = msg.getInt("peerId");
                            }
                            SessionDescription sdp = new SessionDescription(
                                    SessionDescription.Type.fromCanonicalForm(type), msg.getJSONObject("mess").getString("sdp"));
                            if(enjoyvcCallback != null)
                                enjoyvcCallback.answer(streamId, sdp);
                        }
                        else if(type.equals("ready")) {
                            int streamId;
                            boolean publish;
                            if(msg.has("streamId")) {
                                streamId = msg.getInt("streamId");
                                publish = true;
                            }
                            else {
                                streamId = msg.getInt("peerId");
                                publish = false;
                            }
                            if(enjoyvcCallback != null)
                                enjoyvcCallback.ready(publish, streamId);
                        }

                    } catch (JSONException e) {
                        e.printStackTrace();
                    }
                }

            }).on(Socket.EVENT_DISCONNECT, new Emitter.Listener() {
                @Override
                public void call(Object... args) {
                    Log.i(TAG, "websocket disconnected");
                    connected = false;
                    if(enjoyvcCallback != null)
                        enjoyvcCallback.disconnected();

                }
            }).on(Socket.EVENT_CONNECT_ERROR, new Emitter.Listener() {
                        @Override
                        public void call(Object... args) {
                            Log.i(TAG, "websocket connect error");
                            connected = false;
                            if (enjoyvcCallback != null)
                                enjoyvcCallback.disconnected();

                        }
                    });

            socket.connect();
            return true;
        } catch (URISyntaxException e) {
            e.printStackTrace();
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return false;
    }
}
