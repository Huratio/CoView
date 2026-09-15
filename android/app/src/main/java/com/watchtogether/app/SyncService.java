package com.watchtogether.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.os.Handler;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import android.util.Base64;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;

import okhttp3.*;
import org.json.JSONArray;
import org.json.JSONObject;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Iterator;
import java.util.ArrayDeque;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

public class SyncService extends Service {
    public static final String ACTION_CONNECT = "com.watchtogether.CONNECT";
    public static final String ACTION_CHAT = "com.watchtogether.CHAT";
    public static final String ACTION_TYPING = "com.watchtogether.TYPING";
    public static final String ACTION_REACTION = "com.watchtogether.REACTION";
    public static final String ACTION_SYNC = "com.watchtogether.SYNC";
    public static final String ACTION_WEB_SYNC = "com.watchtogether.WEB_SYNC";
    public static final String ACTION_MEDIA_SOURCE = "com.watchtogether.MEDIA_SOURCE";
    public static final String ACTION_STATE_REQUEST = "com.watchtogether.STATE_REQUEST";
    public static final String ACTION_DISCONNECT = "com.watchtogether.DISCONNECT";
    public static final String ACTION_STATUS_REQUEST = "com.watchtogether.STATUS_REQUEST";
    public static final String ACTION_PRESENCE_ACK = "com.watchtogether.PRESENCE_ACK";
    public static final String ACTION_PRESENCE_UPDATE = "com.watchtogether.PRESENCE_UPDATE";
    public static final String ACTION_EVENT = "com.watchtogether.EVENT";
    public static final String EXTRA_EVENT = "event";
    public static final String EXTRA_DATA = "data";

    private static final String CHANNEL = "watchtogether_sync";
    public static final String EVENT_PERMISSION = "com.watchtogether.app.permission.SYNC_EVENT";
    private final OkHttpClient client = new OkHttpClient.Builder().pingInterval(20, TimeUnit.SECONDS).build();
    private WebSocket socket;
    private String serverUrl = "", anonKey = "", username = "", roomName = "", roomPassword = "", topic = "", profilePicture = "";
    private String mediaSource = "", mediaSourceType = "local", mediaSessionId = "";
    private long mediaRevision=0;
    private String mediaRevisionOwner="";
    private long observedMediaRevision=0;
    private String observedMediaRevisionOwner="";
    private String presenceStatus = "Online";
    private long ref = 1;
    private long reconnectDelay = 1000;
    private long syncSequence = 0;
    private boolean intentionalStop = false;
    private boolean joined = false;
    private String joinRef = null;
    private String presenceKey = "";
    private Handler handler;
    private android.content.SharedPreferences servicePrefs;
    private final Runnable heartbeatRunnable = this::heartbeat;
    private Runnable reconnectRunnable;
    private Runnable presenceAnnounceRunnable;
    private Runnable disconnectStopRunnable;
    private static final int MAX_PENDING_ACTIONS = 32;
    private static final long CHAT_ACTION_MAX_AGE_MS = 30_000L;
    private static final String PASSWORD_KEY_ALIAS = "synka_room_password";
    private final ArrayDeque<QueuedBroadcast> pendingActions = new ArrayDeque<>();
    private final java.util.LinkedHashSet<String> retiredMediaSessionIds = new java.util.LinkedHashSet<>();

    @Override public void onCreate() {
        super.onCreate();
        handler = new Handler(getMainLooper());
        servicePrefs = getSharedPreferences("synka_service_session", MODE_PRIVATE);
        servicePrefs.edit().remove("media_session_id").apply();
        mediaSessionId = "";
        createChannel();
        startForeground(12, notification("Connected room service"));
    }

    private void createChannel() {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel c = new NotificationChannel(CHANNEL, "Synka", NotificationManager.IMPORTANCE_LOW);
            c.setDescription("Keeps room synchronization connected while Synka is in the background.");
            ((NotificationManager)getSystemService(NOTIFICATION_SERVICE)).createNotificationChannel(c);
        }
    }

    private Notification notification(String text) {
        return new NotificationCompat.Builder(this, CHANNEL)
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("Synka")
                .setContentText(text)
                .setOngoing(true)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
                .build();
    }

    @Override public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_CONNECT.equals(action)) {
                intentionalStop = false;
                if(handler!=null && disconnectStopRunnable!=null) handler.removeCallbacks(disconnectStopRunnable);
                disconnectStopRunnable=null;
                serverUrl = safe(intent.getStringExtra("server")).trim(); if(serverUrl.length()>2048) serverUrl=serverUrl.substring(0,2048);
                anonKey = safe(intent.getStringExtra("key")).trim(); if(anonKey.length()>4096) anonKey=anonKey.substring(0,4096);
                username = safe(intent.getStringExtra("user")); if(username.length()>48) username=username.substring(0,48);
                roomName = safe(intent.getStringExtra("room")); if(roomName.length()>120) roomName=roomName.substring(0,120);
                roomPassword = safe(intent.getStringExtra("password")); if(roomPassword.length()>128) roomPassword=roomPassword.substring(0,128);
                profilePicture = intent.getStringExtra("pfp");
                String previousRoom=servicePrefs==null?"":servicePrefs.getString("room","");
                String previousServer=servicePrefs==null?"":servicePrefs.getString("server","");
                String previousKey=servicePrefs==null?"":servicePrefs.getString("key","");
                String previousUser=servicePrefs==null?"":servicePrefs.getString("user","");
                String previousPassword=servicePrefs==null?"":getStoredPassword();
                boolean newContext=!roomName.equals(previousRoom)||!serverUrl.equals(previousServer)||!anonKey.equals(previousKey)||!username.equals(previousUser)||!roomPassword.equals(previousPassword);
                if(newContext){
                    mediaSource="";
                    mediaSourceType="local";
                    mediaSessionId="";
                    pendingActions.clear();
                    retiredMediaSessionIds.clear();
                }
                if(servicePrefs!=null){
                    android.content.SharedPreferences.Editor e=servicePrefs.edit()
                            .putString("server",serverUrl).putString("key",anonKey).putString("user",username)
                            .putString("room",roomName).putString("pfp",profilePicture==null?"":profilePicture);
                    if(roomPassword==null)roomPassword="";
                    String enc=encryptPassword(roomPassword);
                    if(enc!=null)e.putString("password_enc",enc).remove("password");
                    else { e.remove("password").remove("password_enc"); e.apply(); emit("error",new JSONObjectSafe().put("message","Secure room-password storage is unavailable on this device.").obj); intentionalStop=true; return START_NOT_STICKY; }
                    e.apply();
                }
                if (profilePicture == null) profilePicture = "";
                presenceStatus = "Online";
                connect();
            } else if (ACTION_CHAT.equals(action)) {
                if (intentionalStop) return START_STICKY;
                try { broadcast("chat", new JSONObject().put("user", username).put("senderId", presenceKey).put("messageId", intent.getStringExtra("messageId")).put("text", intent.getStringExtra("text")).put("pfp", intent.getStringExtra("pfp") == null ? profilePicture : intent.getStringExtra("pfp")).put("sentAt", System.currentTimeMillis())); } catch (Exception ignored) {}
            } else if (ACTION_TYPING.equals(action)) {
                if (intentionalStop) return START_STICKY;
                try { broadcast("typing", new JSONObject().put("user", username).put("senderId", presenceKey).put("typing", intent.getBooleanExtra("typing", false)).put("sentAt",System.currentTimeMillis())); } catch (Exception ignored) {}
            } else if (ACTION_REACTION.equals(action)) {
                if (intentionalStop) return START_STICKY;
                try { broadcast("chat_reaction", new JSONObject().put("user", username).put("messageId", intent.getStringExtra("messageId")).put("reaction", intent.getStringExtra("reaction"))); } catch (Exception ignored) {}
            } else if (ACTION_SYNC.equals(action)) {
                if (intentionalStop) return START_STICKY;
                try {
                    String suppliedSession=safe(intent.getStringExtra("mediaSessionId"));
                    long incomingRevision=intent.getLongExtra("mediaRevision",mediaRevision);
                    String incomingOwner=safe(intent.getStringExtra("mediaRevisionOwner")); if(incomingOwner.isEmpty())incomingOwner=presenceKey;
                    if(suppliedSession.isEmpty() || mediaSessionId.isEmpty() || !suppliedSession.equals(mediaSessionId)
                            || incomingRevision!=mediaRevision
                            || (!mediaRevisionOwner.isEmpty() && !incomingOwner.equals(mediaRevisionOwner))) return START_STICKY;
                    JSONObject d = new JSONObject();
                    d.put("user", username);
                    d.put("senderId", presenceKey);
                    d.put("position", intent.getLongExtra("position", 0));
                    boolean playing=intent.getBooleanExtra("playing", false); boolean buffering=intent.getBooleanExtra("buffering",false);
                    String suppliedState=intent.getStringExtra("state");
                    presenceStatus=(suppliedState!=null&&!suppliedState.isEmpty())?suppliedState:(buffering?"Buffering":(playing?"Playing":"Paused"));
                    d.put("playing", playing).put("buffering", buffering).put("state",intent.getStringExtra("state")==null?(buffering?"Buffering":(playing?"Playing":"Paused")):intent.getStringExtra("state"));
                    d.put("speed", intent.getFloatExtra("speed",1f));
                    d.put("kind", intent.getStringExtra("kind")==null?"heartbeat":intent.getStringExtra("kind"));
                    d.put("playbackRevision", intent.getLongExtra("playbackRevision",0));
                    d.put("playbackStateId", intent.getStringExtra("playbackStateId")==null?"":intent.getStringExtra("playbackStateId"));
                    d.put("playbackRevisionOwner", intent.getStringExtra("playbackRevisionOwner")==null?presenceKey:intent.getStringExtra("playbackRevisionOwner"));
                    if(intent.hasExtra("desiredPlaying"))d.put("desiredPlaying",intent.getBooleanExtra("desiredPlaying",playing));
                    d.put("mediaSessionId", mediaSessionId).put("mediaRevision",mediaRevision).put("mediaRevisionOwner",mediaRevisionOwner).put("mediaType",mediaSourceType);
                    String targetKey=intent.getStringExtra("targetKey"); if(targetKey!=null&&!targetKey.isEmpty())d.put("targetKey",targetKey);
                    d.put("sentAt", intent.getLongExtra("sentAt",System.currentTimeMillis()));
                    d.put("seq", ++syncSequence);
                    d.put("eventId", UUID.randomUUID().toString());
                    String requestId = intent.getStringExtra("requestId");
                    if (requestId != null && !requestId.isEmpty()) d.put("requestId", requestId);
                    broadcast("sync", d); updateNativePresence();
                } catch (Exception ignored) {}
            } else if (ACTION_WEB_SYNC.equals(action)) {
                if (intentionalStop) return START_STICKY;
                try {
                    if(!"web".equalsIgnoreCase(mediaSourceType)) return START_STICKY;
                    String suppliedSession=safe(intent.getStringExtra("mediaSessionId"));
                    long incomingRevision=intent.getLongExtra("mediaRevision",mediaRevision);
                    String incomingOwner=safe(intent.getStringExtra("mediaRevisionOwner")); if(incomingOwner.isEmpty())incomingOwner=presenceKey;
                    if(suppliedSession.isEmpty() || mediaSessionId.isEmpty() || !suppliedSession.equals(mediaSessionId)
                            || incomingRevision!=mediaRevision
                            || (!mediaRevisionOwner.isEmpty() && !incomingOwner.equals(mediaRevisionOwner))) return START_STICKY;
                    boolean playing=intent.getBooleanExtra("playing",false); boolean buffering=intent.getBooleanExtra("buffering",false);
                    String suppliedState=intent.getStringExtra("state");
                    presenceStatus=(suppliedState!=null&&!suppliedState.isEmpty())?suppliedState:(buffering?"Buffering":(playing?"Playing":"Paused"));
                    JSONObject d=new JSONObject();
                    d.put("user",username); d.put("senderId",presenceKey);
                    d.put("position",intent.getLongExtra("position",0)); d.put("playing",playing).put("buffering",buffering);
                    d.put("kind",intent.getStringExtra("kind")==null?"heartbeat":intent.getStringExtra("kind")).put("speed",intent.getFloatExtra("speed",1f)).put("state",intent.getStringExtra("state")==null?(buffering?"Buffering":(playing?"Playing":"Paused")):intent.getStringExtra("state"));
                    d.put("commandSeq",intent.getIntExtra("commandSeq",0));
                    d.put("playbackRevision", intent.getLongExtra("playbackRevision",0));
                    d.put("playbackStateId", intent.getStringExtra("playbackStateId")==null?"":intent.getStringExtra("playbackStateId"));
                    d.put("playbackRevisionOwner", intent.getStringExtra("playbackRevisionOwner")==null?presenceKey:intent.getStringExtra("playbackRevisionOwner"));
                    if(intent.hasExtra("desiredPlaying"))d.put("desiredPlaying",intent.getBooleanExtra("desiredPlaying",playing));
                    long sentAt=intent.getLongExtra("sentAt",System.currentTimeMillis());
                    d.put("mediaSessionId", mediaSessionId).put("mediaRevision",mediaRevision).put("mediaRevisionOwner",mediaRevisionOwner).put("mediaType",mediaSourceType); String targetKey=intent.getStringExtra("targetKey"); if(targetKey!=null&&!targetKey.isEmpty())d.put("targetKey",targetKey); d.put("sentAt",sentAt); d.put("seq",++syncSequence); d.put("eventId",UUID.randomUUID().toString());
                    String requestId=intent.getStringExtra("requestId"); if(requestId!=null&&!requestId.isEmpty())d.put("requestId",requestId);
                    broadcast("web_sync",d); updateNativePresence();
                } catch(Exception ignored) {}
            } else if (ACTION_MEDIA_SOURCE.equals(action)) {
                if (intentionalStop) return START_STICKY;
                String nextSource = safe(intent.getStringExtra("source"));
                String nextType = safe(intent.getStringExtra("type"));
                if(nextType.isEmpty()) nextType="local";
                // Device-local files are intentionally private. Do not let a local-only
                // selection mutate or advertise the room's shared media session.
                if(!"web".equalsIgnoreCase(nextType)){ updateNativePresence(); return START_STICKY; }
                if(nextSource.isEmpty())return START_STICKY;
                String suppliedMediaSessionId = safe(intent.getStringExtra("mediaSessionId"));
                if(suppliedMediaSessionId.isEmpty())return START_STICKY;
                long incomingRevision=intent.getLongExtra("mediaRevision",-1L);
                String incomingOwner=safe(intent.getStringExtra("mediaRevisionOwner")); if(incomingOwner.isEmpty())incomingOwner=presenceKey;
                if(incomingRevision<0 || !adoptMediaSession(suppliedMediaSessionId,incomingRevision,incomingOwner))return START_STICKY;
                mediaSource = nextSource;
                mediaSourceType = "web";
                try {
                    broadcast("media_source", new JSONObject()
                            .put("source", mediaSource)
                            .put("type", mediaSourceType)
                            .put("mediaSessionId", mediaSessionId)
                            .put("mediaRevision",mediaRevision)
                            .put("mediaRevisionOwner",mediaRevisionOwner)
                            .put("user", presenceKey));
                } catch (Exception ignored) {}
            } else if (ACTION_STATE_REQUEST.equals(action)) {
                if (intentionalStop) return START_STICKY;
                try {
                    String requestId = intent.getStringExtra("requestId");
                    if (requestId == null || requestId.isEmpty()) requestId = UUID.randomUUID().toString();
                    broadcast("state_request", new JSONObject().put("user", presenceKey).put("requestId", requestId).put("mediaSessionId", mediaSessionId).put("mediaRevision", mediaRevision).put("mediaRevisionOwner", mediaRevisionOwner));
                } catch (Exception ignored) {}
            } else if (ACTION_STATUS_REQUEST.equals(action)) {
                if (joined) {
                    emit("connected", new JSONObjectSafe()
                            .put("room", roomName)
                            .put("presenceKey", presenceKey)
                            .put("mediaSessionId", mediaSessionId)
                            .put("mediaRevision", mediaRevision)
                            .put("mediaRevisionOwner", mediaRevisionOwner).obj);
                } else if (servicePrefs != null) {
                    serverUrl=servicePrefs.getString("server","");
                    anonKey=servicePrefs.getString("key","");
                    username=servicePrefs.getString("user","");
                    roomName=servicePrefs.getString("room","");
                    roomPassword=getStoredPassword();
                    profilePicture=servicePrefs.getString("pfp","");
                    mediaSessionId="";
                    if(roomPassword==null){emit("error",new JSONObjectSafe().put("message","Saved room password could not be decrypted. Please reconnect and enter it again.").obj);intentionalStop=true;return START_NOT_STICKY;}
                    if(!serverUrl.isEmpty()&&!roomName.isEmpty()&&!intentionalStop) connect();
                }
            } else if (ACTION_PRESENCE_ACK.equals(action)) {
                try {
                    String forKey=intent.getStringExtra("forKey");
                    if(forKey!=null&&!forKey.isEmpty()) broadcast("room_presence_ack",new JSONObject().put("forKey",forKey).put("key",presenceKey).put("username",username).put("pfp",profilePicture).put("status",presenceStatus));
                } catch(Exception ignored){}
            } else if (ACTION_PRESENCE_UPDATE.equals(action)) {
                String pfp=intent.getStringExtra("pfp");if(pfp!=null)profilePicture=pfp;
                String st=intent.getStringExtra("status");if(st!=null&&!st.isEmpty())presenceStatus=st;
                try {
                    JSONObject presence = new JSONObject();
                    presence.put("key", presenceKey);
                    presence.put("username", username);
                    presence.put("pfp", profilePicture).put("status",presenceStatus);
                    updateNativePresence();
                    broadcast("room_presence_update", presence);
                } catch (Exception ignored) {}
            } else if (ACTION_DISCONNECT.equals(action)) {
                intentionalStop = true;
                pendingActions.clear();
                if(joined){
                    try{sendBroadcastNow("room_presence_leave",new JSONObject().put("key",presenceKey).put("username",username));}catch(Exception ignored){}
                    if(handler!=null){
                        if(disconnectStopRunnable!=null)handler.removeCallbacks(disconnectStopRunnable);
                        disconnectStopRunnable=()->{
                            if(!intentionalStop)return;
                            closeSocket();
                            if(servicePrefs!=null)servicePrefs.edit().clear().apply();
                            stopForeground(true);
                            stopSelf();
                        };
                        handler.postDelayed(disconnectStopRunnable,350);
                    }
                } else {
                    pendingActions.clear();
                    closeSocket();
                    if(servicePrefs!=null)servicePrefs.edit().clear().apply();
                    stopForeground(true);
                    stopSelf();
                }
            }
        } else if(servicePrefs!=null && !intentionalStop){
            serverUrl=servicePrefs.getString("server","");
            anonKey=servicePrefs.getString("key","");
            username=servicePrefs.getString("user","");
            roomName=servicePrefs.getString("room","");
            roomPassword=getStoredPassword();
            profilePicture=servicePrefs.getString("pfp","");
            if(roomPassword==null){emit("error",new JSONObjectSafe().put("message","Saved room password could not be decrypted. Please reconnect and enter it again.").obj);intentionalStop=true;return START_NOT_STICKY;}
            if(!serverUrl.isEmpty()&&!roomName.isEmpty()){presenceKey=username+"-"+UUID.randomUUID().toString().substring(0,8);connect();}
        }
        return START_STICKY;
    }

    private void connect() {
        closeSocket();
        joined = false;
        joinRef = null;
        presenceKey = username + "-" + UUID.randomUUID().toString().substring(0, 8);
        if (serverUrl == null || serverUrl.isEmpty() || anonKey == null || anonKey.isEmpty()) return;
        final android.net.Uri parsedServer;
        try{ parsedServer=android.net.Uri.parse(serverUrl); }catch(Exception e){
            emit("error", new JSONObjectSafe().put("message", "The realtime server address is invalid.").obj);
            return;
        }
        if (!"https".equalsIgnoreCase(parsedServer.getScheme()) || parsedServer.getHost()==null || parsedServer.getHost().isEmpty()
                || parsedServer.getUserInfo()!=null || parsedServer.getQuery()!=null || parsedServer.getFragment()!=null
                || (parsedServer.getPath()!=null && !parsedServer.getPath().isEmpty() && !"/".equals(parsedServer.getPath()))) {
            emit("error", new JSONObjectSafe().put("message", "The realtime server must be a valid HTTPS origin.").obj);
            return;
        }
        String base = parsedServer.getEncodedAuthority();
        String scheme = "wss://";
        String ws = scheme + base + "/realtime/v1/websocket?apikey=" + urlEncode(anonKey) + "&vsn=1.0.0";
        Request req = new Request.Builder().url(ws).build();
        socket = client.newWebSocket(req, new WebSocketListener() {
            @Override public void onOpen(WebSocket webSocket, Response response) {
                socket = webSocket; reconnectDelay = 1000; joinRoom();
            }
            @Override public void onMessage(WebSocket webSocket, String text) { handle(text); }
            @Override public void onFailure(WebSocket webSocket, Throwable t, Response response) {
                if (socket != webSocket) return;
                socket = null;
                joined = false;
                joinRef = null;
                emit("error", new JSONObjectSafe().put("message", t.getMessage() == null ? "Connection failed" : t.getMessage()).obj);
                emit("disconnected", new JSONObjectSafe().put("reason", t.getMessage() == null ? "Connection failed" : t.getMessage()).obj);
                scheduleReconnect();
            }
            @Override public void onClosed(WebSocket webSocket, int code, String reason) {
                if (socket != webSocket) return;
                socket = null;
                joined = false;
                joinRef = null;
                emit("disconnected", new JSONObjectSafe().put("reason", reason == null ? "" : reason).obj);
                scheduleReconnect();
            }
        });
    }

    private void scheduleReconnect() {
        if (intentionalStop) return;
        long delay = reconnectDelay;
        reconnectDelay = Math.min(reconnectDelay * 2, 30000);
        if (reconnectRunnable != null) handler.removeCallbacks(reconnectRunnable);
        reconnectRunnable = () -> { if (!intentionalStop) connect(); };
        handler.postDelayed(reconnectRunnable, delay);
    }

    private void joinRoom() {
        try {
            topic = "realtime:room:" + sha256(roomName.trim().toLowerCase(Locale.ROOT) + "\n" + roomPassword);
            JSONObject config = new JSONObject();
            config.put("broadcast", new JSONObject().put("ack", false).put("self", false));
            config.put("presence", new JSONObject().put("key", presenceKey));
            JSONObject payload = new JSONObject().put("config", config);
            joinRef = String.valueOf(ref++);
            send(topic, "phx_join", payload, joinRef);
        } catch (Exception ignored) {}
    }

    private void track() {
        if (joined) return;
        joined = true;
        try {
            JSONObject track = new JSONObject()
                    .put("user_id", presenceKey)
                    .put("username", username)
                    .put("pfp", profilePicture)
                    .put("status", presenceStatus);
            JSONObject payload = new JSONObject()
                    .put("type", "presence")
                    .put("event", "track")
                    .put("payload", track);
            send(topic, "presence", payload, String.valueOf(ref++));
            // Tell the activity its local presence identity BEFORE announcing the user.
            // This prevents the first roster acknowledgement from arriving before the
            // activity knows which presence key belongs to this device.
            emit("connected", new JSONObjectSafe().put("room", roomName).put("presenceKey", presenceKey).put("mediaSessionId", mediaSessionId).put("mediaRevision",mediaRevision).put("mediaRevisionOwner",mediaRevisionOwner).obj);
            broadcast("room_presence_join", new JSONObject().put("key",presenceKey).put("username",username).put("pfp",profilePicture).put("status",presenceStatus));
            if(handler!=null){
                if(presenceAnnounceRunnable!=null) handler.removeCallbacks(presenceAnnounceRunnable);
                final int[] remaining={3};
                presenceAnnounceRunnable=()->{
                    if(!joined||socket==null)return;
                    try{broadcast("room_presence_join",new JSONObject().put("key",presenceKey).put("username",username).put("pfp",profilePicture).put("status",presenceStatus));}catch(Exception ignored){}
                    if(--remaining[0]>0) handler.postDelayed(presenceAnnounceRunnable,900);
                };
                handler.postDelayed(presenceAnnounceRunnable,450);
            }

            flushPendingActions();
            // The activity sends a request after receiving the connected event.
            // This avoids creating an untracked catch-up request inside the service.
            heartbeat();
        } catch (Exception e) {
            emit("error", new JSONObjectSafe()
                    .put("message", e.getMessage() == null ? "Could not join room" : e.getMessage()).obj);
        }
    }

    private void heartbeat() {
        if (socket == null) return;
        try { send("phoenix", "heartbeat", new JSONObject(), String.valueOf(ref++)); } catch (Exception ignored) {}
        handler.removeCallbacks(heartbeatRunnable);
        handler.postDelayed(heartbeatRunnable, 18000);
    }

    private boolean send(String t, String e, JSONObject p, String r) {
        if (socket == null) return false;
        try { return socket.send(new JSONObject().put("topic", t).put("event", e).put("payload", p).put("ref", r).toString()); } catch (Exception ignored) { return false; }
    }

    private void updateNativePresence(){
        if(!joined||socket==null||topic.isEmpty())return;
        try{
            JSONObject track=new JSONObject().put("user_id",presenceKey).put("username",username).put("pfp",profilePicture).put("status",presenceStatus);
            JSONObject payload=new JSONObject().put("type","presence").put("event","track").put("payload",track);
            if(!send(topic,"presence",payload,String.valueOf(ref++)) && handler!=null && !intentionalStop){ handler.postDelayed(this::updateNativePresence,1500); }
        }catch(Exception ignored){}
    }

    private boolean isNewerMediaRevision(long revision,String owner){
        owner=safe(owner);
        if(revision>mediaRevision)return true;
        if(revision<mediaRevision)return false;
        if(mediaRevisionOwner==null||mediaRevisionOwner.isEmpty())return true;
        return owner.compareTo(mediaRevisionOwner)>0;
    }

    private void broadcast(String event, JSONObject data) {
        if (intentionalStop) return;
        if (socket == null || topic.isEmpty() || !joined) {
            if (shouldQueue(event, data)) {
                if ("media_source".equals(event)) {
                    pendingActions.removeIf(action -> "media_source".equals(action.event));
                }
                if (pendingActions.size() >= MAX_PENDING_ACTIONS) pendingActions.removeFirst();
                pendingActions.addLast(new QueuedBroadcast(event, data, System.currentTimeMillis()));
            }
            return;
        }
        if(!sendBroadcastNow(event, data) && shouldQueue(event, data)){
            if ("media_source".equals(event)) pendingActions.removeIf(action -> "media_source".equals(action.event));
            if (pendingActions.size() >= MAX_PENDING_ACTIONS) pendingActions.removeFirst();
            pendingActions.addLast(new QueuedBroadcast(event, data, System.currentTimeMillis()));
        }
    }

    private boolean shouldQueue(String event, JSONObject data) {
        return "chat".equals(event) || "chat_reaction".equals(event) || "media_source".equals(event);
    }

    private boolean sendBroadcastNow(String event, JSONObject data) {
        if (socket == null || topic.isEmpty() || !joined) return false;
        try {
            JSONObject payload = new JSONObject().put("type", "broadcast").put("event", event).put("payload", data);
            return send(topic, "broadcast", payload, "b" + ref++);
        } catch (Exception ignored) { return false; }
    }

    private void flushPendingActions() {
        if (!joined || socket == null) return;
        long now=System.currentTimeMillis();
        while (!pendingActions.isEmpty()) {
            QueuedBroadcast action=pendingActions.removeFirst();
            long maxAge="media_source".equals(action.event)?5*60_000L:CHAT_ACTION_MAX_AGE_MS;
            if(now-action.enqueuedAt>maxAge)continue;
            refreshQueuedIdentity(action.event,action.data);
            if(!sendBroadcastNow(action.event, action.data)){ pendingActions.addFirst(action); break; }
        }
    }

    private void refreshQueuedIdentity(String event, JSONObject data){
        if(data==null)return;
        try{
            if("chat".equals(event)){
                data.put("user",username).put("senderId",presenceKey).put("pfp",profilePicture);
            }else if("media_source".equals(event)){
                data.put("user",presenceKey);
            }
        }catch(Exception ignored){}
    }

    private static class QueuedBroadcast {
        final String event;
        final JSONObject data;
        final long enqueuedAt;
        QueuedBroadcast(String event, JSONObject data, long enqueuedAt) {
            this.event = event;
            this.data = data;
            this.enqueuedAt = enqueuedAt;
        }
    }

        private boolean adoptMediaSession(String incoming,long revision,String owner){
        if(incoming==null||incoming.isEmpty()||retiredMediaSessionIds.contains(incoming))return false;
        owner=safe(owner);
        if(revision<mediaRevision)return false;
        if(revision==mediaRevision&&!mediaRevisionOwner.isEmpty()&&owner.compareTo(mediaRevisionOwner)<0)return false;
        boolean same=incoming.equals(mediaSessionId);
        if(!same&&revision==mediaRevision&&owner.equals(mediaRevisionOwner)&&!mediaSessionId.isEmpty())return false;
        if(!same&&!mediaSessionId.isEmpty()){retiredMediaSessionIds.add(mediaSessionId);if(retiredMediaSessionIds.size()>32)retiredMediaSessionIds.remove(retiredMediaSessionIds.iterator().next());}
        mediaSessionId=incoming;mediaRevision=revision;mediaRevisionOwner=owner;
        observedMediaRevision=mediaRevision;observedMediaRevisionOwner=mediaRevisionOwner;
        return true;
    }


    private boolean adoptIncomingSharedMediaState(JSONObject data){
        if(data==null)return false;
        String type=safe(data.optString("type","")); if(!"web".equalsIgnoreCase(type))return false;
        String incoming=safe(data.optString("mediaSessionId","")); String source=safe(data.optString("source","")); if(incoming.isEmpty()||source.isEmpty())return false;
        long revision=data.optLong("mediaRevision",-1L); if(revision<0)return false;
        String owner=safe(data.optString("mediaRevisionOwner",data.optString("user",""))); if(owner.isEmpty())return false;
        if(mediaSessionId.equals(incoming)&&revision==mediaRevision&&owner.equals(mediaRevisionOwner)){mediaSource=source;mediaSourceType="web";return true;}
        if(revision<mediaRevision)return false;
        if(revision==mediaRevision){int ownerCmp=owner.compareTo(safe(mediaRevisionOwner));if(ownerCmp<0)return false;if(ownerCmp==0&&!incoming.equals(mediaSessionId)&&incoming.compareTo(mediaSessionId)<=0)return false;}
        if(!mediaSessionId.isEmpty()&&!mediaSessionId.equals(incoming)){retiredMediaSessionIds.add(mediaSessionId);if(retiredMediaSessionIds.size()>32)retiredMediaSessionIds.remove(retiredMediaSessionIds.iterator().next());}
        mediaSessionId=incoming; mediaRevision=revision; mediaRevisionOwner=owner; observedMediaRevision=revision; observedMediaRevisionOwner=owner; mediaSource=source; mediaSourceType="web";
        return true;
    }

    private void handle(String raw) {
        try {
            JSONObject o = new JSONObject(raw);
            String event = o.optString("event");
            if ("phx_reply".equals(event)) {
                JSONObject p = o.optJSONObject("payload");
                if (topic.equals(o.optString("topic")) && p != null) {
                    if ("ok".equals(p.optString("status"))) {
                        // Only the acknowledgement for our exact phx_join may establish the room.
                        String replyRef = o.optString("ref", "");
                        if (!joined && joinRef != null && joinRef.equals(replyRef)) track();
                    } else {
                        JSONObject err = p.optJSONObject("response");
                        String reason = err == null ? p.optString("status", "Realtime join failed") : err.toString();
                        joined=false; joinRef=null; emit("error", new JSONObjectSafe().put("message", reason).obj);
                        if(!intentionalStop){ try{if(socket!=null)socket.close(4000,"join failed");}catch(Exception ignored){} scheduleReconnect(); }
                    }
                }
            } else if ("broadcast".equals(event)) {
                JSONObject p = o.optJSONObject("payload");
                if (p == null) return;
                JSONObject data = p.optJSONObject("payload");
                String ev = p.optString("event");
                if (data == null) return;
                if ("sync".equals(ev)) emit("sync", data);
                else if ("web_sync".equals(ev)) emit("web_sync", data);
                else if ("media_source".equals(ev) && !presenceKey.equals(data.optString("user"))) {
                    if(adoptIncomingSharedMediaState(data)) emit("media_source", data);
                }
                else if ("media_source_state".equals(ev) && !presenceKey.equals(data.optString("user"))) {
                    String target=data.optString("targetKey","");
                    if(!target.isEmpty() && !presenceKey.equals(target)) return;
                    if(adoptIncomingSharedMediaState(data)) emit("media_source_state", data);
                }
                else if ("chat".equals(ev)) emit("chat", data);
                else if ("typing".equals(ev) && !presenceKey.equals(data.optString("user"))) emit("typing", data);
                else if ("chat_reaction".equals(ev)) emit("chat_reaction", data);
                else if ("state_request".equals(ev) && !presenceKey.equals(data.optString("user"))) {
                    emit("state_request", data);
                    String requester=data.optString("user",""); String requestId=data.optString("requestId","");
                    if(!requester.isEmpty() && !requestId.isEmpty() && "web".equalsIgnoreCase(mediaSourceType) && !mediaSessionId.isEmpty()){
                        try{broadcast("media_source_state",new JSONObject().put("source",mediaSource).put("type",mediaSourceType).put("mediaSessionId",mediaSessionId).put("mediaRevision",mediaRevision).put("mediaRevisionOwner",mediaRevisionOwner).put("requestId",requestId).put("user",presenceKey).put("targetKey",requester));}catch(Exception ignored){}
                    }
                }
                else if ("room_presence_join".equals(ev) && !presenceKey.equals(data.optString("key"))) {
                    emit("room_presence_join", data);
                    try{broadcast("room_presence_ack",new JSONObject().put("forKey",data.optString("key","")).put("key",presenceKey).put("username",username).put("pfp",profilePicture).put("status",presenceStatus));}catch(Exception ignored){}
                    if("web".equalsIgnoreCase(mediaSourceType) && (!mediaSource.isEmpty() || !mediaSessionId.isEmpty())) try{broadcast("media_source_state",new JSONObject().put("source",mediaSource).put("type",mediaSourceType).put("mediaSessionId",mediaSessionId).put("mediaRevision",mediaRevision).put("mediaRevisionOwner",mediaRevisionOwner).put("user",presenceKey).put("targetKey",data.optString("key","")));}catch(Exception ignored){}
                }
                else if ("room_presence_update".equals(ev) && !presenceKey.equals(data.optString("key"))) emit("room_presence_update", data);
                else if ("room_presence_ack".equals(ev)) emit("room_presence_ack", data);
                else if ("room_presence_leave".equals(ev) && !presenceKey.equals(data.optString("key"))) emit("room_presence_leave", data);
            } else if ("presence_state".equals(event)) {
                JSONObject state = o.optJSONObject("payload");
                if (state != null) emit("presence_state", state);
            } else if ("presence_diff".equals(event)) {
                JSONObject diff = o.optJSONObject("payload");
                if (diff != null) emit("presence_diff", diff);
            }
        } catch (Exception ignored) {}
    }

    private void emit(String event, JSONObject data) {
        Intent i = new Intent(ACTION_EVENT);
        i.setPackage(getPackageName());
        i.putExtra(EXTRA_EVENT, event);
        i.putExtra(EXTRA_DATA, data == null ? "{}" : data.toString());
        sendBroadcast(i, EVENT_PERMISSION);
    }

    private SecretKey getPasswordKey() throws Exception {
        java.security.KeyStore ks=java.security.KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        if(ks.containsAlias(PASSWORD_KEY_ALIAS)){
            return ((java.security.KeyStore.SecretKeyEntry)ks.getEntry(PASSWORD_KEY_ALIAS,null)).getSecretKey();
        }
        KeyGenerator kg=KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore");
        kg.init(new KeyGenParameterSpec.Builder(PASSWORD_KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT|KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return kg.generateKey();
    }

    private String encryptPassword(String value){
        if(value==null)value="";
        try{
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE,getPasswordKey());
            byte[] iv=cipher.getIV();
            byte[] encrypted=cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] packed=new byte[iv.length+encrypted.length];
            System.arraycopy(iv,0,packed,0,iv.length);
            System.arraycopy(encrypted,0,packed,iv.length,encrypted.length);
            return Base64.encodeToString(packed,Base64.NO_WRAP);
        }catch(Exception e){return null;}
    }

    private String decryptPassword(String value){
        if(value==null||value.isEmpty())return "";
        try{
            byte[] packed=Base64.decode(value,Base64.DEFAULT);
            if(packed.length<13)return null;
            byte[] iv=java.util.Arrays.copyOfRange(packed,0,12);
            byte[] ciphertext=java.util.Arrays.copyOfRange(packed,12,packed.length);
            Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE,getPasswordKey(),new GCMParameterSpec(128,iv));
            return new String(cipher.doFinal(ciphertext),StandardCharsets.UTF_8);
        }catch(Exception e){return null;}
    }

    private String getStoredPassword(){
        if(servicePrefs==null)return "";
        String encrypted=servicePrefs.getString("password_enc","");
        if(!encrypted.isEmpty()){
            String decoded=decryptPassword(encrypted);
            if(decoded==null){
                servicePrefs.edit().remove("password_enc").remove("password").apply();
            }
            return decoded;
        }
        String legacy=servicePrefs.getString("password","");
        if(!legacy.isEmpty()){
            String migrated=encryptPassword(legacy);
            if(migrated!=null){
                servicePrefs.edit().putString("password_enc",migrated).remove("password").apply();
                return legacy;
            }
            return null;
        }
        return "";
    }

    private static String safe(String s){return s==null?"":s;}
    private String urlEncode(String s) { try { return URLEncoder.encode(s, StandardCharsets.UTF_8.name()); } catch (Exception e) { return s; } }
    private String sha256(String value) {
        try {
            byte[] b = MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(); for (byte x : b) out.append(String.format(Locale.US, "%02x", x)); return out.toString();
        } catch (Exception e) { throw new IllegalStateException("SHA-256 is unavailable", e); }
    }
    private void closeSocket() {
        if (handler != null) {
            handler.removeCallbacks(heartbeatRunnable);
            if (reconnectRunnable != null) handler.removeCallbacks(reconnectRunnable);
            if (presenceAnnounceRunnable != null) handler.removeCallbacks(presenceAnnounceRunnable);
            if (disconnectStopRunnable != null) handler.removeCallbacks(disconnectStopRunnable);
        }
        joined = false;
        joinRef = null;
        if (presenceAnnounceRunnable != null) { try{handler.removeCallbacks(presenceAnnounceRunnable);}catch(Exception ignored){} }
        presenceKey = "";
        if (socket != null) {
            try { socket.close(1000, "leave"); } catch (Exception ignored) {}
            socket = null;
        }
    }
    @Override public void onDestroy() { intentionalStop = true; closeSocket(); super.onDestroy(); }
    @Nullable @Override public IBinder onBind(Intent intent) { return null; }

    private static class JSONObjectSafe {
        final JSONObject obj = new JSONObject();
        JSONObjectSafe put(String k, String v) { try { obj.put(k, v); } catch (Exception ignored) {} return this; }
        JSONObjectSafe put(String k, long v) { try { obj.put(k, v); } catch (Exception ignored) {} return this; }
    }
}
