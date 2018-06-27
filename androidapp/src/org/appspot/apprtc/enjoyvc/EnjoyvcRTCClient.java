package org.appspot.apprtc.enjoyvc;

import android.util.Log;

import org.appspot.apprtc.AppRTCClient;
import org.appspot.apprtc.DirectRTCClient;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import org.webrtc.IceCandidate;
import org.webrtc.SessionDescription;

import java.util.ArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class EnjoyvcRTCClient implements org.appspot.apprtc.AppRTCClient, EnjoyvcRTC.EnjoyvcNotify {
    private final static String TAG = "EnjoyvcRTCClient";

    private final ExecutorService executor;
    private final SignalingEvents events;
    EnjoyvcRTC rtc;

    public EnjoyvcRTCClient(SignalingEvents events) {
        rtc = new EnjoyvcRTC();
        this.events = events;
        executor = Executors.newSingleThreadExecutor();
    }

    @Override
    public void connectToRoom(RoomConnectionParameters connectionParameters) {
        EnjoyvcRTC.EnjoyvcNotify thiz = this;
        new Thread(new Runnable() {
            @Override
            public void run() {
                if(rtc.createToken())
                    rtc.connect(thiz);
            }
        }).start();

    }

    @Override
    public void sendOfferSdp(long streamId, SessionDescription sdp) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                rtc.sendOffer(streamId, sdp.description);
            }
        });
    }

    @Override
    public void sendAnswerSdp(SessionDescription sdp) {

    }

    @Override
    public void sendLocalIceCandidate(long streamId, IceCandidate candidate) {
        executor.execute(new Runnable() {
            @Override
            public void run() {
                rtc.sendCandidate(streamId, candidate);
            }
        });

    }

    @Override
    public void sendLocalIceCandidateRemovals(IceCandidate[] candidates) {

    }

    @Override
    public void disconnectFromRoom() {

    }

    @Override
    public void connected() {
        Log.i(TAG, "websocket connected");
    }

    @Override
    public void disconnected() {

    }

    @Override
    public void checked(JSONArray streams) {
        Log.i(TAG, "token checked");
        rtc.sendPublish();
        int len = streams.length();
        for(int i = 0; i < len; i++) {
            try {
                JSONObject stream = streams.getJSONObject(i);
                long id = stream.getLong("id");
                rtc.sendSubscribe(id);
                return;
            } catch (JSONException e) {
                e.printStackTrace();
            }
        }

    }

    @Override
    public void published(long streamId) {
        Log.i(TAG, "published" + String.valueOf(streamId));
        SignalingParameters parameters = new SignalingParameters(
                // Ice servers are not needed for direct connections.
                new ArrayList<>(),
                true, // Server side acts as the initiator on direct connections.
                null, // clientId
                null, // wssUrl
                null, // wwsPostUrl
                null, // offerSdp
                null, // iceCandidates
                true,
                streamId
        );
        events.onConnectedToRoom(parameters);
    }

    @Override
    public void subscribed(long streamId) {
        Log.i(TAG, "subscribed" + String.valueOf(streamId));
        SignalingParameters parameters = new SignalingParameters(
                // Ice servers are not needed for direct connections.
                new ArrayList<>(),
                true, // Server side acts as the initiator on direct connections.
                null, // clientId
                null, // wssUrl
                null, // wwsPostUrl
                null, // offerSdp
                null, // iceCandidates
                false,
                streamId
        );
        events.onConnectedToRoom(parameters);
    }

    @Override
    public void answer(long streamId, SessionDescription sdp) {
        Log.i(TAG, "answer" + sdp.description);
        events.onRemoteDescription(streamId, sdp);
    }

    @Override
    public void ready(boolean publish, long streamId) {
        //if(!publish)
        //      rtc.sendPublish();
    }
}
