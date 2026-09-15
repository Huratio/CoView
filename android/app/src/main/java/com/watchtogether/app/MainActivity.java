package com.watchtogether.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.animation.ValueAnimator;
import android.view.animation.LinearInterpolator;
import android.util.Base64;
import android.text.SpannableStringBuilder;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.os.Build;
import android.view.*;
import android.webkit.CookieManager;
import android.webkit.JavascriptInterface;
import android.webkit.WebChromeClient;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.view.inputmethod.EditorInfo;
import android.widget.*;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Player;
import androidx.media3.common.Tracks;
import androidx.media3.common.text.Cue;
import androidx.media3.common.text.CueGroup;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector;
import androidx.media3.ui.PlayerView;
import androidx.media3.ui.SubtitleView;
import androidx.media3.ui.TrackSelectionDialogBuilder;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;

import java.util.*;
import java.nio.charset.StandardCharsets;

public class MainActivity extends AppCompatActivity {
    private static final int PICK_VIDEO = 77, PICK_SUBTITLE = 78, PICK_PFP = 79;
    private static final String APP_NAME = "Synka";
    private static final int[] PFP_RES = {R.drawable.pfp01,R.drawable.pfp02,R.drawable.pfp03,R.drawable.pfp04,R.drawable.pfp05,R.drawable.pfp06,R.drawable.pfp07,R.drawable.pfp08,R.drawable.pfp09,R.drawable.pfp10,R.drawable.pfp11,R.drawable.pfp12,R.drawable.pfp13,R.drawable.pfp14,R.drawable.pfp15,R.drawable.pfp16,R.drawable.pfp17,R.drawable.pfp18,R.drawable.pfp19,R.drawable.pfp20};

    private int BG, SURFACE, SURFACE2, PRIMARY, PRIMARY2, TEXT, MUTED, SUCCESS, LINE;
    private boolean lightMode;
    private FrameLayout root, roomRoot, chatOverlay, roomPlayerBox;
    private LinearLayout roomContent, roomHeader, roomPeople, roomMediaRow, roomQuick, participantStrip, mediaInfo;
    private PlayerView playerView;
    private WebView webView;
    private View youtubeCustomView;
    private WebChromeClient.CustomViewCallback youtubeCustomViewCallback;
    private FrameLayout youtubeFullscreenContainer;
    private boolean youtubeFullscreen=false;
    private boolean webMode=false, webPageReady=false, webApplyingRemote=false;
    private int clientSlot = 1;
    private Class<?> syncServiceClass = SyncService.class;
    private String webUrl="";
    private String onlineResolvedUrl="";
    private long lastWebSyncSentAt=0, lastWebPositionMs=-1;
    private boolean lastWebPlaying=false, webObservedInitial=false;
    private boolean onlineResolving=false;
    private int unreadMessages=0;
    private TextView chatBadge, quickChatBadge;
    private View appSplash, webLoadingOverlay, playerEmptyOverlay;
    private FrameLayout mediaChooserOverlay;
    private TextView webLoadingTitle, webLoadingDetail;
    private int webCommandSequence=0;
    private String localStatus="Online";
    private String pendingRemoteWebSource="";
    private JSONObject pendingMediaSourceState;
    private Runnable pendingMediaSourceApplyRunnable;
    private Runnable webLoadTimeoutRunnable;
    private int webLoadAttempt=0;
    private String pendingWebStateRequestId="";
    private ExoPlayer player;
    // Online playback state. Local playback synchronization remains the v1.7.1 path.
    private boolean onlineMode=false;
    private String onlineUrl="";
    private boolean onlineReady=false;
    private DefaultTrackSelector trackSelector;
    private RecyclerView chatList;
    private EditText chatInput;
    private TextView typingLabel;
    private TextView participantCount, connectionLabel, mediaLabel, syncStatusLabel, participantSummary, localRoomStatus;
    private ImageView localRoomAvatar;
    private final LinkedHashMap<String,Bitmap> pfpCache = new LinkedHashMap<>(64,0.75f,true);
    private final ArrayList<ChatMessage> chats = new ArrayList<>();
    private final LinkedHashMap<String,String> participants = new LinkedHashMap<>();
    private final HashMap<String,String> participantPfps = new HashMap<>();
    private SharedPreferences prefs;
    private String serverUrl="", anonKey="", username="", roomName="", roomPassword="", profilePicture="";
    private int pfpIndex=0;
    private int subtitleDelayMs=0;
    private TextView participantList;
    private Uri videoUri, subtitleUri;
    private boolean applyingRemote=false, chatOpen=false, fullscreen=false;
    private final LinkedHashMap<String,String> typingUsers = new LinkedHashMap<>();
    private final HashMap<String,Long> typingExpiry = new HashMap<>();
    private final HashMap<String,String> participantStatuses = new HashMap<>();
    private long lastTypingSentAt=0;
    private boolean typingActive=false;
    private Runnable typingStopRunnable;
    private String pendingSharedUrl="";
    private long remoteGuardUntil=0;
    private long lastLocalSync=0;
    private final HashMap<String,Long> lastSequenceByUser = new HashMap<>();
    private boolean initialStateWaiting = false;
    private boolean ccEnabled = true;
    private SubtitleView adjustedSubtitleView;
    private long subtitleGeneration=0;
    private ImageButton playerVolumeButton;
    private SeekBar playerVolumeSeek;
    private int playerVolumePercent=100;
    private float lastNonZeroVolume=1f;
    private SeekBar settingsVolumeSeek;
    private TextView settingsVolumeValue;
    private int fullscreenOrientation=ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
    private int youtubeFullscreenOrientation=ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
    private final java.util.List<SubtitleCueData> customSubtitleCues = new java.util.ArrayList<>();
    private Runnable customSubtitleTickRunnable;
    private java.util.concurrent.ExecutorService subtitleExecutor;
    private boolean customSubtitleLoading=false;
    private String activeStateRequestId = "";
    private long mediaStateRequestDeadline = 0;
    private String pendingNativeStateRequestId = "";
    private String currentMediaSessionId = "";
    private String currentMediaType = "";
    private boolean lastWebBuffering = false;
    private long mediaRevision = 0;
    private String mediaRevisionOwner = "";
    private final LinkedHashSet<String> retiredMediaSessionIds = new LinkedHashSet<>();
    private String localPresenceKey = "";
    private final LinkedHashSet<String> seenEventIds = new LinkedHashSet<>();
    private final Handler uiHandler = new Handler();
    private final Handler playerSyncHandler = new Handler();
    private final HashMap<String,Long> presenceNoticeTimes = new HashMap<>();
    private final Runnable nativeSyncHeartbeat = new Runnable(){ @Override public void run(){
        if(player!=null && !webMode && player.isPlaying() && !applyingRemote){ sendSync(false); playerSyncHandler.postDelayed(this,2000); }
    }};
    private boolean webBridgeEnabled=false;
    private boolean serviceConnected=false;
    private long webSessionGeneration=0;
    private String webBridgeNonce="";
    private long roomSessionGeneration=0;
    private final HashMap<View,Integer> fullscreenVisibility = new HashMap<>();
    private int playerContentIndex=-1;
    private boolean playerDetachedForFullscreen=false;
    private long currentMediaRevision=0;
    private String currentMediaRevisionOwner="";
    private long playbackRevision=0;
    private String playbackRevisionOwner="";
    private String playbackStateId="";
    private long lastAppliedPlaybackRevision=0;
    private String lastAppliedPlaybackOwner="";
    private String lastAppliedPlaybackStateId="";
    private long lastAppliedPlaybackSeq=-1;
    private JSONObject pendingNativeSync;
    private JSONObject pendingWebSync;
    private Runnable stateRequestTimeoutRunnable;
    private String pendingNativeStateRequestTarget="";

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!SyncService.ACTION_EVENT.equals(intent.getAction())) return;
            try {
                String ev=intent.getStringExtra(SyncService.EXTRA_EVENT), raw=intent.getStringExtra(SyncService.EXTRA_DATA);
                JSONObject d=new JSONObject(raw==null?"{}":raw);
                if ("connected".equals(ev)) {
                    serviceConnected = true;
                    lastSequenceByUser.clear(); seenEventIds.clear(); participantStatuses.clear(); typingUsers.clear();
                    localPresenceKey = d.optString("presenceKey", ""); localStatus="Online";
                    String connectedMediaSession=d.optString("mediaSessionId","");
                    long connectedRevision=d.optLong("mediaRevision",0); String connectedOwner=d.optString("mediaRevisionOwner","");
                    boolean preserveLocalMedia=!currentMediaSessionId.isEmpty()&&"local".equalsIgnoreCase(currentMediaType);
                    if(!preserveLocalMedia){
                        if(!connectedMediaSession.isEmpty())currentMediaSessionId=connectedMediaSession;
                        currentMediaRevision=connectedRevision; currentMediaRevisionOwner=connectedOwner; currentMediaType=connectedMediaSession.isEmpty()?"":"web";
                    }
                    playbackRevision=0; playbackRevisionOwner=""; playbackStateId=""; lastAppliedPlaybackRevision=0; lastAppliedPlaybackOwner=""; lastAppliedPlaybackStateId=""; lastAppliedPlaybackSeq=-1; pendingNativeSync=null; pendingWebSync=null;
                    if(!localPresenceKey.isEmpty()){participants.remove("self");participantPfps.remove("self");participants.put(localPresenceKey,username);participantPfps.put(localPresenceKey,profilePicture);}
                    runOnUiThread(() -> {
                        if (!isFinishing()) {
                            if (player == null) {
                                showRoom();
                                if(!pendingSharedUrl.isEmpty()) uiHandler.postDelayed(()->{ if(!isFinishing() && player!=null && !pendingSharedUrl.isEmpty()) showLinkDialog(); },180);
                            } else { if (connectionLabel != null) { applyStatusIndicator(connectionLabel,"Connected",SUCCESS); } if(!preserveLocalMedia)sendStateRequest(); }
                            refreshParticipants();
                        }
                    });
                } else if ("error".equals(ev)) {
                    runOnUiThread(() -> toast(d.optString("message","Connection failed")));
                } else if ("disconnected".equals(ev)) {
                    serviceConnected = false;
                    runOnUiThread(() -> { if(connectionLabel!=null) { applyStatusIndicator(connectionLabel,"Reconnecting…",MUTED); } if(syncStatusLabel!=null) { syncStatusLabel.setText("Connection interrupted"); syncStatusLabel.setTextColor(MUTED); } });
                } else if ("sync".equals(ev)) {
                    if(applySync(d)) runOnUiThread(()->{if(syncStatusLabel!=null) { syncStatusLabel.setText("✓  Synced"); syncStatusLabel.setTextColor(SUCCESS); syncStatusLabel.setContentDescription("Synchronization status: synced"); }refreshParticipants();});
                } else if ("web_sync".equals(ev)) {
                    if(applyWebSync(d)) runOnUiThread(()->{if(syncStatusLabel!=null) { syncStatusLabel.setText("✓  Synced"); syncStatusLabel.setTextColor(SUCCESS); syncStatusLabel.setContentDescription("Synchronization status: synced"); }refreshParticipants();});
                }
                else if ("media_source".equals(ev) || "media_source_state".equals(ev)) { d.put("_event",ev); MainActivity.this.applyMediaSource(d); }
                else if ("chat".equals(ev)) { if(!localPresenceKey.equals(d.optString("senderId",""))) { addChat(d.optString("user","Unknown"), clampChatText(d.optString("text","")), d.optString("pfp",""), d.optLong("sentAt",System.currentTimeMillis()), d.optString("messageId","")); if(!chatOpen){ unreadMessages=Math.min(99,unreadMessages+1); runOnUiThread(MainActivity.this::updateChatBadges); } playChatSound(); } }
                else if ("chat_reaction".equals(ev)) handleChatReaction(d);
                else if ("typing".equals(ev)) handleTyping(d);
                else if ("presence_state".equals(ev)) updatePresenceState(d);
                else if ("presence_diff".equals(ev)) updatePresenceDiff(d);
                else if ("room_presence_join".equals(ev)) handleRoomPresenceJoin(d);
                else if ("room_presence_update".equals(ev)) handleRoomPresenceUpdate(d);
                else if ("room_presence_ack".equals(ev)) handleRoomPresenceAck(d);
                else if ("room_presence_leave".equals(ev)) handleRoomPresenceLeave(d);
                else if ("state_request".equals(ev)) {
                    String requester=d.optString("user",""); String requestId=d.optString("requestId","");
                    if(!requester.isEmpty() && !requester.equals(localPresenceKey) && !requestId.isEmpty()) {
                        respondToStateRequest(requestId, requester, d.optString("mediaSessionId",""), d.optLong("mediaRevision",0), d.optString("mediaRevisionOwner",""));
                    }
                }

            } catch(Exception ignored){}
        }
    };

    @Override protected void onCreate(Bundle state){
        prefs=getSharedPreferences("watchtogether",MODE_PRIVATE);
        subtitleDelayMs=prefs.getInt("subtitle_delay_ms",0);
        lightMode=prefs.getBoolean("light_mode",false);
        currentMediaSessionId="";
        prefs.edit().remove("media_session_id").apply();
        AppCompatDelegate.setDefaultNightMode(lightMode?AppCompatDelegate.MODE_NIGHT_NO:AppCompatDelegate.MODE_NIGHT_YES);
        super.onCreate(state);
        applyThemeColors();
        setTitle(APP_NAME);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG); updateSystemBars();
        setContentView(R.layout.activity_main); root=findViewById(R.id.root); registerReceiverCompat(); initSounds(); ensureProfile();
        handleIncomingIntent(getIntent());
        showAppSplash();
        uiHandler.postDelayed(() -> { if (!isFinishing() && !serviceConnected && player == null) showConnect(); }, 180);
    }

    @Override protected void onStart(){
        super.onStart();
        try{ startService(serviceIntent(SyncService.ACTION_STATUS_REQUEST)); }catch(Exception ignored){}
    }

    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
        if(player==null && serviceConnected) showRoom(); else if(player==null) showConnect(); else if(!pendingSharedUrl.isEmpty()) showLinkDialog();
    }
    private String extractHttpUrl(String text){
        if(text==null)return "";
        java.util.regex.Matcher m=java.util.regex.Pattern.compile("https?://[^\\s<>\"']+",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(text);
        if(!m.find())return "";
        String u=m.group().trim();
        while(!u.isEmpty() && ".,;:!?)]}>\u201d\u2019".indexOf(u.charAt(u.length()-1))>=0)u=u.substring(0,u.length()-1);
        return u;
    }
    private void handleIncomingIntent(Intent intent){
        if(intent==null)return;
        try{
            String extracted="";
            if(Intent.ACTION_SEND.equals(intent.getAction())){
                extracted=extractHttpUrl(intent.getStringExtra(Intent.EXTRA_TEXT));
                if(extracted.isEmpty() && intent.getClipData()!=null){
                    ClipData clip=intent.getClipData();
                    for(int i=0;i<clip.getItemCount() && extracted.isEmpty();i++){
                        ClipData.Item item=clip.getItemAt(i);
                        CharSequence text=item.getText();
                        if(text!=null) extracted=extractHttpUrl(text.toString());
                        if(extracted.isEmpty() && item.getUri()!=null) extracted=extractHttpUrl(item.getUri().toString());
                    }
                }
                if(extracted.isEmpty() && intent.getData()!=null) extracted=extractHttpUrl(intent.getData().toString());
            } else if(Intent.ACTION_VIEW.equals(intent.getAction()) && intent.getData()!=null){
                extracted=extractHttpUrl(intent.getData().toString());
            }
            if(!extracted.isEmpty()) pendingSharedUrl=extracted;
        }catch(Exception ignored){}
    }
    private void registerReceiverCompat(){ IntentFilter f=new IntentFilter(SyncService.ACTION_EVENT); if(Build.VERSION.SDK_INT>=33) registerReceiver(receiver,f,RECEIVER_NOT_EXPORTED); else registerReceiver(receiver,f,SyncService.EVENT_PERMISSION,null); }
    private void applyThemeColors(){
        PRIMARY=Color.rgb(216,216,218);
        PRIMARY2=Color.rgb(238,238,240);
        if(lightMode){
            BG=Color.rgb(246,246,247); SURFACE=Color.rgb(255,255,255); SURFACE2=Color.rgb(242,242,244);
            TEXT=Color.rgb(20,20,22);
            MUTED=Color.rgb(105,105,110); SUCCESS=Color.rgb(90,90,96); LINE=Color.rgb(218,218,222);
        }else{
            BG=Color.rgb(5,5,5); SURFACE=Color.rgb(13,13,13); SURFACE2=Color.rgb(40,40,42);
            TEXT=Color.rgb(255,255,255);
            MUTED=Color.rgb(138,138,143); SUCCESS=Color.rgb(216,216,218); LINE=Color.rgb(29,29,29);
        }
        updateActiveBackgrounds();
    }
    private void updateActiveBackgrounds(){
        if(roomRoot!=null){roomRoot.setBackground(round(BG,Color.TRANSPARENT,0));}
        if(roomContent!=null)roomContent.setBackgroundColor(Color.TRANSPARENT);
        if(chatOverlay!=null){int alpha=lightMode?185:200;chatOverlay.setBackgroundColor(Color.argb(alpha,Color.red(BG),Color.green(BG),Color.blue(BG)));}
    }
    private void updateSystemBars(){int flags=lightMode?View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR:0;if(lightMode&&Build.VERSION.SDK_INT>=26)flags|=View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;getWindow().getDecorView().setSystemUiVisibility(flags);}
    private void ensureProfile(){
        pfpIndex=Math.max(0,Math.min(PFP_RES.length-1,prefs.getInt("pfp_index",0)));
        String custom=prefs.getString("custom_pfp","");
        profilePicture=custom.isEmpty()?"asset:"+pfpIndex:custom;
    }
    private Bitmap loadPfpBitmap(String token){
        try{
            String key=token==null?"asset:0":token;
            Bitmap cached=pfpCache.get(key);
            if(cached!=null&&!cached.isRecycled())return cached;
            Bitmap result=null;
            if(key.startsWith("data:")){
                int c=key.indexOf(',');
                if(c<0||c>=key.length()-1 || key.length()>3_000_000)return null;
                byte[] raw=Base64.decode(key.substring(c+1),Base64.DEFAULT);
                BitmapFactory.Options o=new BitmapFactory.Options();
                o.inJustDecodeBounds=true; BitmapFactory.decodeByteArray(raw,0,raw.length,o);
                o.inSampleSize=sampleSize(o.outWidth,o.outHeight,192,192); o.inJustDecodeBounds=false;
                result=BitmapFactory.decodeByteArray(raw,0,raw.length,o);
            }else if(key.startsWith("asset:")){
                int idx=0; try{idx=Math.max(0,Math.min(PFP_RES.length-1,Integer.parseInt(key.substring(6))));}catch(Exception ignored){}
                result=BitmapFactory.decodeResource(getResources(),PFP_RES[idx]);
            }
            if(result!=null){
                pfpCache.put(key,result);
                if(pfpCache.size()>64){String first=pfpCache.keySet().iterator().next();pfpCache.remove(first);}
            }
            return result;
        }catch(Exception ignored){return null;}
    }
    private int sampleSize(int w,int h,int tw,int th){
        int s=1; while((w/(s*2))>=tw && (h/(s*2))>=th)s*=2; return Math.max(1,s);
    }

    private String bitmapToData(Bitmap b){
        if(b==null)return "";
        Bitmap scaled=Bitmap.createScaledBitmap(b,96,96,true);java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();scaled.compress(Bitmap.CompressFormat.PNG,88,out);if(scaled!=b)scaled.recycle();return "data:image/png;base64,"+Base64.encodeToString(out.toByteArray(),Base64.NO_WRAP);
    }
    private String displayName(Uri uri){String n=uri==null?null:uri.getLastPathSegment();if(n==null||n.trim().isEmpty())n="Selected video";int slash=n.lastIndexOf('/');if(slash>=0)n=n.substring(slash+1);return n;}
    private int dp(float x){return (int)(x*getResources().getDisplayMetrics().density+.5f);}
    private TextView text(String s,float size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setIncludeFontPadding(false);return t;}
    private TextView title(String s,float size){TextView t=text(s,size,TEXT);t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t;}
    private EditText input(String hint){EditText e=new EditText(this);e.setHint(hint);e.setHintTextColor(MUTED);e.setTextColor(TEXT);e.setTextSize(14);e.setSingleLine(true);e.setPadding(dp(15),0,dp(15),0);e.setBackground(round(SURFACE,LINE,14));e.setIncludeFontPadding(false);return e;}
    private GradientDrawable round(int fill,int stroke,int r){GradientDrawable g=new GradientDrawable();g.setColor(fill);g.setCornerRadius(dp(r));if(stroke!=0)g.setStroke(dp(1),stroke);return g;}
    private Button button(String s,boolean primary){Button b=new Button(this);b.setText(s);b.setTextColor(primary&&lightMode?Color.WHITE:TEXT);b.setTextSize(14);b.setAllCaps(false);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setGravity(Gravity.CENTER);b.setIncludeFontPadding(false);b.setPadding(dp(10),0,dp(10),0);b.setMinHeight(dp(52));b.setMinWidth(0);b.setStateListAnimator(null);b.setBackground(round(primary?PRIMARY:SURFACE2,primary?Color.rgb(55,55,58):LINE,15));b.setOnTouchListener((v,event)->{if(event.getAction()==MotionEvent.ACTION_DOWN){v.animate().scaleX(.985f).scaleY(.985f).setDuration(70).start();}else if(event.getAction()==MotionEvent.ACTION_UP||event.getAction()==MotionEvent.ACTION_CANCEL){v.animate().scaleX(1f).scaleY(1f).setDuration(90).start();}return false;});return b;}
    private Button iconButton(String label,int iconRes,boolean primary){Button b=button(label,primary);try{android.graphics.drawable.Drawable d=getDrawable(iconRes);if(d!=null){d.setTint(TEXT);b.setCompoundDrawablesWithIntrinsicBounds(d,null,null,null);b.setCompoundDrawablePadding(dp(9));}}catch(Exception ignored){}return b;}
    private ImageButton iconOnlyButton(int iconRes,boolean primary){ImageButton b=new ImageButton(this);b.setImageResource(iconRes);b.setColorFilter(TEXT);b.setScaleType(ImageView.ScaleType.CENTER);b.setBackground(round(primary?PRIMARY:SURFACE2,primary?Color.rgb(55,55,58):Color.TRANSPARENT,50));b.setPadding(0,0,0,0);b.setMinimumWidth(0);b.setMinimumHeight(0);b.setStateListAnimator(null);return b;}
    private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private void add(LinearLayout p,View v,int w,int h,int top,int bottom){LinearLayout.LayoutParams q=new LinearLayout.LayoutParams(w,h);q.topMargin=dp(top);q.bottomMargin=dp(bottom);p.addView(v,q);}
    private ScrollView scroll(LinearLayout c){ScrollView s=new ScrollView(this);s.setFillViewport(true);s.setClipToPadding(false);s.addView(c);return s;}
    private void styleDialog(AlertDialog dialog){if(dialog==null)return;Window w=dialog.getWindow();if(w!=null){w.setBackgroundDrawable(round(SURFACE,LINE,24));w.setDimAmount(.72f);w.setWindowAnimations(0);}Button pos=dialog.getButton(AlertDialog.BUTTON_POSITIVE),neg=dialog.getButton(AlertDialog.BUTTON_NEGATIVE);if(pos!=null)pos.setTextColor(TEXT);if(neg!=null)neg.setTextColor(MUTED);}
    private void finishDialogStyle(AlertDialog dialog){styleDialog(dialog);}
    private void screen(View v){root.removeAllViews();root.addView(v,new FrameLayout.LayoutParams(-1,-1));}
    private void section(LinearLayout c,String s){TextView t=text(s,14,MUTED);t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));t.setLetterSpacing(.035f);add(c,t,-1,dp(22),15,7);}
    private ImageView iconView(int res,int size){ImageView i=new ImageView(this);i.setImageResource(res);i.setColorFilter(TEXT);i.setScaleType(ImageView.ScaleType.CENTER);i.setPadding(0,0,0,0);return i;}
    private static class AspectRatioFrameLayout extends FrameLayout {
        private final float ratio;
        private boolean fillParent=false;
        AspectRatioFrameLayout(Context c,float r){super(c);ratio=r;setClipToOutline(true);}
        void setFillParent(boolean fill){
            if(fillParent==fill)return;
            fillParent=fill;
            requestLayout();
        }
        @Override protected void onMeasure(int wSpec,int hSpec){
            if(fillParent){
                super.onMeasure(wSpec,hSpec);
                return;
            }
            int w=MeasureSpec.getSize(wSpec);
            int h=Math.round(w/ratio);
            if(MeasureSpec.getMode(wSpec)==MeasureSpec.UNSPECIFIED && MeasureSpec.getMode(hSpec)!=MeasureSpec.UNSPECIFIED){
                h=MeasureSpec.getSize(hSpec);
                w=Math.round(h*ratio);
            }
            super.onMeasure(MeasureSpec.makeMeasureSpec(Math.max(1,w),MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(Math.max(1,h),MeasureSpec.EXACTLY));
        }
    }
    private LinearLayout sourceCard(String label,String detail,int iconRes,boolean enabled){
        LinearLayout card=row();
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setPadding(dp(9),dp(8),dp(6),dp(8));
        card.setBackground(round(lightMode?SURFACE2:SURFACE,LINE,20));
        card.setAlpha(enabled?1f:.48f);

        ImageView icon=iconView(iconRes,26);
        icon.setColorFilter(enabled?TEXT:MUTED);
        LinearLayout.LayoutParams iconLp=new LinearLayout.LayoutParams(dp(30),dp(30));
        iconLp.gravity=Gravity.CENTER_VERTICAL;
        card.addView(icon,iconLp);

        LinearLayout words=col();
        words.setGravity(Gravity.CENTER_VERTICAL|Gravity.START);
        words.setPadding(dp(5),0,dp(2),0);
        TextView top=title(label,11);
        top.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        top.setIncludeFontPadding(false);
        top.setSingleLine(true);
        top.setEllipsize(android.text.TextUtils.TruncateAt.END);
        TextView sub=text(detail,9,MUTED);
        sub.setGravity(Gravity.START|Gravity.CENTER_VERTICAL);
        sub.setIncludeFontPadding(false);
        sub.setMaxLines(2);
        sub.setEllipsize(null);
        words.addView(top,new LinearLayout.LayoutParams(-1,dp(28)));
        words.addView(sub,new LinearLayout.LayoutParams(-1,dp(30)));
        card.addView(words,new LinearLayout.LayoutParams(0,-1,1));

        ImageView arrow=iconView(R.drawable.ic_chevron_right,24);
        arrow.setColorFilter(MUTED);
        LinearLayout.LayoutParams arrowLp=new LinearLayout.LayoutParams(dp(18),dp(24));
        arrowLp.gravity=Gravity.CENTER_VERTICAL;
        card.addView(arrow,arrowLp);
        return card;
    }
    private void setPlayerEmptyVisible(boolean visible){if(playerEmptyOverlay!=null)playerEmptyOverlay.setVisibility(visible?View.VISIBLE:View.GONE);}

    private void showConnect(){
        final LinearLayout content=col();
        content.setPadding(dp(18),0,dp(18),dp(12));
        ScrollView sc=scroll(content); sc.setBackgroundColor(BG);

        // Exact login-page composition from the approved Synka reference.
        LinearLayout top=row();
        top.setGravity(Gravity.CENTER_VERTICAL);
        ImageView logo=new ImageView(this);
        logo.setImageResource(R.drawable.synka_logo_only);
        logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams logoLp=new LinearLayout.LayoutParams(dp(42),dp(42));
        top.addView(logo,logoLp);

        LinearLayout brand=col();
        brand.setGravity(Gravity.CENTER_VERTICAL);
        brand.setPadding(dp(8),0,0,0);
        TextView brandName=title("Synka",21);
        TextView brandTag=text("Watch. Together.",12,MUTED);
        brandName.setIncludeFontPadding(false);
        brandTag.setIncludeFontPadding(false);
        add(brand,brandName,-1,dp(24),0,0);
        add(brand,brandTag,-1,dp(18),0,0);
        top.addView(brand,new LinearLayout.LayoutParams(0,dp(48),1));

        ImageButton settings=new ImageButton(this);
        settings.setImageResource(R.drawable.ic_settings_small);
        settings.setColorFilter(TEXT);
        settings.setScaleType(ImageView.ScaleType.CENTER);
        settings.setBackground(round(SURFACE2,Color.TRANSPARENT,50));
        settings.setPadding(0,0,0,0);
        settings.setMinimumWidth(0);
        settings.setMinimumHeight(0);
        settings.setStateListAnimator(null);
        settings.setContentDescription("Settings");
        top.addView(settings,new LinearLayout.LayoutParams(dp(44),dp(44)));
        settings.setOnClickListener(v->showSettingsDialog());
        add(content,top,-1,dp(56),0,20);

        LinearLayout profile=row();
        profile.setPadding(dp(9),dp(8),dp(9),dp(8));
        profile.setGravity(Gravity.CENTER_VERTICAL);
        profile.setBackground(round(SURFACE,LINE,16));
        ImageView avatar=new ImageView(this);
        avatar.setImageBitmap(loadPfpBitmap(profilePicture));
        avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);
        profile.addView(avatar,new LinearLayout.LayoutParams(dp(42),dp(42)));
        LinearLayout ptext=col();
        ptext.setGravity(Gravity.CENTER_VERTICAL);
        ptext.setPadding(dp(10),0,dp(4),0);
        add(ptext,title("Your profile",16),-1,dp(22),0,2);
        add(ptext,text("Set how others see you",12,MUTED),-1,dp(18),0,0);
        profile.addView(ptext,new LinearLayout.LayoutParams(0,dp(42),1));
        Button change=button("Change",false);
        change.setTextSize(14);
        change.setMinHeight(0);
        change.setPadding(dp(10),0,dp(10),0);
        profile.addView(change,new LinearLayout.LayoutParams(dp(78),dp(36)));
        change.setOnClickListener(v->showPfpPicker());
        add(content,profile,-1,dp(70),0,28);

        section(content,"JOINING INFO");

        LinearLayout sRow=row();
        sRow.setPadding(dp(4),0,dp(4),0);
        sRow.setBackground(round(SURFACE,LINE,15));
        ImageView sIcon=iconView(R.drawable.ic_link,24); sIcon.setLayoutParams(new LinearLayout.LayoutParams(dp(30),dp(30)));
        sRow.addView(sIcon);
        EditText s=input("Supabase Server Address");
        s.setText(prefs.getString("server",""));
        s.setTextSize(12);
        s.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
        s.setBackgroundColor(Color.TRANSPARENT);
        sRow.addView(s,new LinearLayout.LayoutParams(0,dp(42),1));
        add(content,sRow,-1,dp(46),0,8);

        LinearLayout kRow=row();
        kRow.setPadding(dp(8),0,dp(8),0);
        kRow.setBackground(round(SURFACE,LINE,15));
        ImageView kIcon=iconView(R.drawable.ic_key,24); kIcon.setLayoutParams(new LinearLayout.LayoutParams(dp(30),dp(30)));
        kRow.addView(kIcon);
        EditText k=input("Supabase Public Key");
        k.setText(prefs.getString("key",""));
        k.setInputType(129);
        k.setTextSize(12);
        k.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
        k.setBackgroundColor(Color.TRANSPARENT);
        kRow.addView(k,new LinearLayout.LayoutParams(0,dp(42),1));
        add(content,kRow,-1,dp(46),0,8);

        LinearLayout uRow=row();
        uRow.setPadding(dp(8),0,dp(8),0);
        uRow.setBackground(round(SURFACE,LINE,15));
        ImageView uIcon=iconView(R.drawable.ic_person,24); uIcon.setLayoutParams(new LinearLayout.LayoutParams(dp(30),dp(30)));
        uRow.addView(uIcon);
        EditText u=input("Username");
        u.setText(prefs.getString("user",""));
        u.setTextSize(12);
        u.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
        u.setBackgroundColor(Color.TRANSPARENT);
        uRow.addView(u,new LinearLayout.LayoutParams(0,dp(42),1));
        add(content,uRow,-1,dp(46),0,28);

        section(content,"ROOM");

        LinearLayout rRow=row();
        rRow.setPadding(dp(8),0,dp(8),0);
        rRow.setBackground(round(SURFACE,LINE,15));
        TextView hash=text("#",25,TEXT); hash.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL)); hash.setGravity(Gravity.CENTER); hash.setIncludeFontPadding(false);
        rRow.addView(hash,new LinearLayout.LayoutParams(dp(30),dp(30)));
        EditText r=input("Room name");
        r.setText(prefs.getString("room","Movie Night"));
        r.setTextSize(12);
        r.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
        r.setBackgroundColor(Color.TRANSPARENT);
        rRow.addView(r,new LinearLayout.LayoutParams(0,dp(42),1));
        add(content,rRow,-1,dp(46),0,8);

        LinearLayout pwRow=row();
        pwRow.setPadding(dp(8),0,dp(8),0);
        pwRow.setBackground(round(SURFACE,LINE,15));
        ImageView pwIcon=iconView(R.drawable.ic_key,24); pwIcon.setLayoutParams(new LinearLayout.LayoutParams(dp(30),dp(30)));
        pwRow.addView(pwIcon);
        EditText pw=input("Room password  •  optional");
        pw.setInputType(129);
        pw.setTextSize(12);
        pw.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
        pw.setBackgroundColor(Color.TRANSPARENT);
        pwRow.addView(pw,new LinearLayout.LayoutParams(0,dp(42),1));
        add(content,pwRow,-1,dp(46),0,24);

        Button join=button("Join Room",false);
        join.setTextSize(14);
        join.setMinHeight(0);
        join.setPadding(dp(10),0,dp(10),0);
        add(content,join,-1,dp(46),0,28);
        join.setOnClickListener(v->connect(s.getText().toString().trim(),k.getText().toString().trim(),u.getText().toString().trim(),r.getText().toString().trim(),pw.getText().toString()));

        TextView footer=text("made by Aria Syetan",11,MUTED);
        footer.setGravity(Gravity.CENTER);
        footer.setIncludeFontPadding(false);
        add(content,footer,-1,dp(32),0,0);
        screen(sc);
    }

    private void showPfpPicker(){
        LinearLayout wrapper=col();wrapper.setPadding(dp(18),dp(8),dp(18),dp(12));TextView hint=text("Choose an avatar you like",13,MUTED);add(wrapper,hint,-1,dp(28),0,8);LinearLayout grid=col();final AlertDialog[] holder=new AlertDialog[1];
        for(int r=0;r<4;r++){LinearLayout line=row();for(int c=0;c<5;c++){int index=r*5+c;ImageView a=new ImageView(this);a.setImageResource(PFP_RES[index]);a.setScaleType(ImageView.ScaleType.CENTER_CROP);a.setBackground(round(SURFACE,LINE,14));line.addView(a,new LinearLayout.LayoutParams(0,dp(72),1));if(c<4)line.addView(new Space(this),new LinearLayout.LayoutParams(dp(8),1));final int pick=index;a.setOnClickListener(v->{pfpIndex=pick;profilePicture="asset:"+pick;prefs.edit().putInt("pfp_index",pick).remove("custom_pfp").apply();if(!localPresenceKey.isEmpty())participantPfps.put(localPresenceKey,profilePicture);sendPresenceUpdate();if(holder[0]!=null)holder[0].dismiss();if(player==null)showConnect();else refreshParticipants();});}grid.addView(line,new LinearLayout.LayoutParams(-1,dp(72)));if(r<3)grid.addView(new Space(this),new LinearLayout.LayoutParams(1,dp(8)));}
        wrapper.addView(grid,new LinearLayout.LayoutParams(-1,-2));Button custom=iconButton("Choose from device",R.drawable.ic_image,false);wrapper.addView(custom,new LinearLayout.LayoutParams(-1,dp(58)));holder[0]=new AlertDialog.Builder(this).setTitle("Profile picture").setView(wrapper).setNegativeButton("Cancel",null).create();custom.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,PICK_PFP);if(holder[0]!=null)holder[0].dismiss();});styleDialog(holder[0]);holder[0].show();
    }

    private void showSettingsDialog(){
        LinearLayout box=col();
        box.setPadding(dp(16),dp(0),dp(16),dp(4));

        TextView mode=text("Appearance",13,MUTED);
        mode.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        add(box,mode,-1,dp(22),0,3);

        LinearLayout modes=row();
        Button dark=button("Dark",false);
        Button light=button("Light",false);
        dark.setTextSize(12);light.setTextSize(12);
        dark.setBackground(round(!lightMode?Color.rgb(55,55,58):Color.TRANSPARENT,LINE,15));
        light.setBackground(round(lightMode?Color.rgb(55,55,58):Color.TRANSPARENT,LINE,15));
        dark.setCompoundDrawablesWithIntrinsicBounds(getDrawable(R.drawable.ic_moon),null,null,null);
        light.setCompoundDrawablesWithIntrinsicBounds(getDrawable(R.drawable.ic_sun),null,null,null);
        dark.setCompoundDrawablePadding(dp(8));light.setCompoundDrawablePadding(dp(8));
        modes.addView(dark,new LinearLayout.LayoutParams(0,dp(38),1));
        modes.addView(new Space(this),new LinearLayout.LayoutParams(dp(8),1));
        modes.addView(light,new LinearLayout.LayoutParams(0,dp(38),1));
        add(box,modes,-1,dp(48),0,10);

        View divider=new View(this);
        divider.setBackgroundColor(LINE);
        add(box,divider,-1,dp(1),0,8);

        LinearLayout doneRow=row();
        Space doneSpacer=new Space(this);
        doneRow.addView(doneSpacer,new LinearLayout.LayoutParams(0,1,1));
        Button done=button("Done",false);
        done.setTextSize(14);
        done.setPadding(dp(8),0,dp(8),0);
        doneRow.addView(done,new LinearLayout.LayoutParams(dp(112),dp(46)));
        add(box,doneRow,-1,dp(46),0,0);

        LinearLayout dialogTitle=row();
        dialogTitle.setPadding(dp(16),dp(2),dp(8),dp(0));
        TextView dialogTitleText=title(APP_NAME+" settings",20);
        dialogTitleText.setGravity(Gravity.CENTER_VERTICAL);
        dialogTitle.addView(dialogTitleText,new LinearLayout.LayoutParams(0,dp(38),1));

        ImageButton close=new ImageButton(this);
        close.setImageResource(R.drawable.ic_close);
        close.setColorFilter(TEXT);
        close.setScaleType(ImageView.ScaleType.CENTER);
        close.setBackgroundColor(Color.TRANSPARENT);
        close.setPadding(0,0,0,0);
        close.setMinimumWidth(0);
        close.setMinimumHeight(0);
        close.setContentDescription("Close settings");
        dialogTitle.addView(close,new LinearLayout.LayoutParams(dp(30),dp(30)));

        AlertDialog dialog=new AlertDialog.Builder(this).setCustomTitle(dialogTitle).setView(box).create();
        dark.setOnClickListener(v->{prefs.edit().putBoolean("light_mode",false).apply();lightMode=false;AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);dialog.dismiss();applyThemeColors();if(!serviceConnected)showConnect();});
        light.setOnClickListener(v->{prefs.edit().putBoolean("light_mode",true).apply();lightMode=true;AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);dialog.dismiss();applyThemeColors();if(!serviceConnected)showConnect();});
        done.setOnClickListener(v->dialog.dismiss());
        close.setOnClickListener(v->dialog.dismiss());
        dialog.show();
        styleDialog(dialog);
    }
    private Intent serviceIntent(String action){ return new Intent(this,SyncService.class).setAction(action); }

    private void sendPresenceUpdate(){
        String st=currentPresenceStatus();
        if(!localPresenceKey.isEmpty())participantStatuses.put(localPresenceKey,st);
        if(localRoomAvatar!=null){Bitmap b=loadPfpBitmap(profilePicture);if(b!=null)localRoomAvatar.setImageBitmap(b);}
        if(localRoomStatus!=null){localRoomStatus.setText("●  "+st);localRoomStatus.setTextColor("Playing".equals(st)?SUCCESS:("Paused".equals(st)?MUTED:SUCCESS));}
        Intent i=serviceIntent(SyncService.ACTION_PRESENCE_UPDATE).putExtra("pfp",profilePicture).putExtra("status",st);
        startService(i);
    }

    private void connect(String server,String key,String user,String room,String pass){
        if(server.isEmpty()||key.isEmpty()||user.isEmpty()||room.isEmpty()){toast("Server, public key, username and room are required.");return;}
        String finalUser=user.trim();
        String nextServer=server.trim();
        try{
            Uri serverUri=Uri.parse(nextServer);
            if(!"https".equalsIgnoreCase(serverUri.getScheme())||serverUri.getHost()==null||serverUri.getHost().trim().isEmpty()
                    ||serverUri.getUserInfo()!=null||serverUri.getQuery()!=null||serverUri.getFragment()!=null
                    ||(serverUri.getPath()!=null&&!serverUri.getPath().isEmpty()&&!"/".equals(serverUri.getPath()))){
                toast("Use a valid HTTPS Supabase server origin.");
                return;
            }
            nextServer=nextServer.replaceAll("/+$","");
        }catch(Exception e){toast("Use a valid HTTPS Supabase server origin.");return;}
        String nextKey=key.trim(),nextUser=finalUser,nextRoom=room.trim();
        String oldServer=prefs.getString("server","");
        String oldKey=prefs.getString("key","");
        String oldUser=prefs.getString("user","");
        String oldRoom=prefs.getString("room","");
        boolean newContext=!nextServer.equals(oldServer)||!nextKey.equals(oldKey)||!nextUser.equals(oldUser)||!nextRoom.equals(oldRoom);
        serverUrl=nextServer;anonKey=nextKey;username=nextUser;roomName=nextRoom;roomPassword=pass;ensureProfile();
        if(newContext){currentMediaSessionId="";retiredMediaSessionIds.clear();currentMediaRevision=0;currentMediaRevisionOwner="";prefs.edit().remove("local_video_uri").remove("subtitle_uri").remove("local_video_context").apply();}
        serviceConnected=false;
        prefs.edit().putString("server",serverUrl).putString("key",anonKey).putString("user",username).putString("room",roomName).apply();
        participants.clear();participantPfps.clear();participantStatuses.clear();chats.clear();typingUsers.clear();showConnecting();
        Intent i=serviceIntent(SyncService.ACTION_CONNECT).putExtra("server",server).putExtra("key",key).putExtra("user",finalUser).putExtra("room",room).putExtra("password",pass).putExtra("pfp",profilePicture);
        if(Build.VERSION.SDK_INT>=26) startForegroundService(i); else startService(i);
    }
    private void showAppSplash(){
        LinearLayout splash=col();splash.setGravity(Gravity.CENTER);splash.setPadding(dp(30),dp(30),dp(30),dp(30));splash.setBackgroundColor(BG);
        ImageView logo=new ImageView(this);logo.setImageResource(R.drawable.synka_icon);logo.setScaleType(ImageView.ScaleType.CENTER_INSIDE);splash.addView(logo,new LinearLayout.LayoutParams(dp(150),dp(150)));screen(splash);appSplash=splash;
    }

    private void showConnecting(){
        LinearLayout c=col();
        c.setGravity(Gravity.CENTER_HORIZONTAL|Gravity.CENTER_VERTICAL);
        c.setPadding(dp(28),dp(20),dp(28),dp(20));

        TextView h=title("Joining…",25);
        h.setGravity(Gravity.CENTER);
        h.setIncludeFontPadding(false);
        add(c,h,-1,dp(38),0,0);

        LoadingRingView ring=new LoadingRingView(this);
        add(c,ring,dp(112),dp(112),0,dp(18));

        TextView keep=text("Keeping the room connection alive",14,MUTED);
        keep.setGravity(Gravity.CENTER);
        keep.setIncludeFontPadding(false);
        add(c,keep,-1,dp(28),0,dp(4));

        TextView joining=text("Joining  “"+roomName+"”",14,MUTED);
        joining.setGravity(Gravity.CENTER);
        joining.setIncludeFontPadding(false);
        add(c,joining,-1,dp(28),0,dp(22));

        Button cancel=button("Cancel",false);
        cancel.setTextSize(14);
        cancel.setMinHeight(0); cancel.setMinWidth(0);
        cancel.setPadding(0,0,0,0);
        add(c,cancel,-1,dp(52),0,0);
        cancel.setOnClickListener(v->disconnect());
        screen(c);
    }

    private static class LoadingRingView extends View {
        private final Paint track=new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint arc=new Paint(Paint.ANTI_ALIAS_FLAG);
        private float rotation=0f;
        private final ValueAnimator animator;
        LoadingRingView(Context context){
            super(context);
            track.setStyle(Paint.Style.STROKE); track.setStrokeWidth(10f); track.setStrokeCap(Paint.Cap.BUTT); track.setColor(Color.rgb(28,29,35));
            arc.setStyle(Paint.Style.STROKE); arc.setStrokeWidth(10f); arc.setStrokeCap(Paint.Cap.ROUND); arc.setColor(Color.rgb(220,222,228));
            animator=ValueAnimator.ofFloat(0f,360f); animator.setDuration(1200); animator.setInterpolator(new LinearInterpolator()); animator.setRepeatCount(ValueAnimator.INFINITE);
            animator.addUpdateListener(a->{rotation=(Float)a.getAnimatedValue();invalidate();});
        }
        @Override protected void onAttachedToWindow(){super.onAttachedToWindow();animator.start();}
        @Override protected void onDetachedFromWindow(){animator.cancel();super.onDetachedFromWindow();}
        @Override protected void onDraw(Canvas canvas){
            super.onDraw(canvas); float w=getWidth(),h=getHeight(); float d=Math.min(w,h)-14f; float l=(w-d)/2f+7f,t=(h-d)/2f+7f;
            canvas.drawArc(l,t,l+d,t+d,0,360,false,track); canvas.drawArc(l,t,l+d,t+d,rotation,92,false,arc);
        }
    }

    private LinearLayout mediaCard(String icon,String heading,String detail,boolean enabled){
        LinearLayout card=col();
        card.setPadding(dp(11),dp(8),dp(11),dp(8));
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setBackground(round(enabled?SURFACE2:Color.rgb(lightMode?226:23,lightMode?230:31,lightMode?237:44),LINE,16));
        card.setAlpha(enabled?1f:.48f);
        LinearLayout line=row();
        ImageView i=new ImageView(this);
        i.setTag(icon);
        if("local".equals(icon))i.setImageResource(R.drawable.ic_folder);
        else if("link".equals(icon))i.setImageResource(R.drawable.ic_link);
        else if("movie".equals(icon))i.setImageResource(R.drawable.ic_movie);
        i.setColorFilter(enabled?TEXT:MUTED);
        i.setScaleType(ImageView.ScaleType.CENTER);
        line.addView(i,new LinearLayout.LayoutParams(dp(22),dp(34)));
        Space iconGap=new Space(this);line.addView(iconGap,new LinearLayout.LayoutParams(dp(8),1));
        LinearLayout words=col();words.setGravity(Gravity.CENTER_VERTICAL);
        TextView ht=title(heading,12);ht.setGravity(Gravity.CENTER_VERTICAL);ht.setIncludeFontPadding(false);ht.setSingleLine(true);ht.setEllipsize(android.text.TextUtils.TruncateAt.END);
        add(words,ht,-1,dp(19),0,1);
        TextView d=text(detail,8,enabled?MUTED:MUTED);d.setGravity(Gravity.CENTER_VERTICAL);d.setIncludeFontPadding(false);d.setMaxLines(2);d.setEllipsize(android.text.TextUtils.TruncateAt.END);
        add(words,d,-1,dp(29),0,0);
        line.addView(words,new LinearLayout.LayoutParams(0,dp(49),1));
        card.addView(line,new LinearLayout.LayoutParams(-1,dp(49)));
        return card;
    }

    private void showMediaChooser(){
        if(roomRoot==null||roomPlayerBox==null){if(player==null)showRoom();return;}
        if(mediaChooserOverlay!=null){mediaChooserOverlay.setVisibility(View.VISIBLE);return;}
        createMediaChooserOverlay();
    }

    private void createMediaChooserOverlay(){
        if(roomPlayerBox==null)return;
        mediaChooserOverlay=new FrameLayout(this);
        mediaChooserOverlay.setBackgroundColor(Color.argb(lightMode?235:245,Color.red(BG),Color.green(BG),Color.blue(BG)));
        LinearLayout box=col();
        box.setGravity(Gravity.CENTER);
        box.setPadding(dp(18),dp(16),dp(18),dp(16));

        TextView h=title("Choose something to watch",17);
        h.setGravity(Gravity.CENTER); h.setIncludeFontPadding(false);
        add(box,h,-1,dp(28),0,4);
        TextView sub=text("Pick a source",11,MUTED);
        sub.setGravity(Gravity.CENTER); sub.setIncludeFontPadding(false);
        add(box,sub,-1,dp(24),0,12);

        LinearLayout cards=row(); cards.setGravity(Gravity.CENTER);
        LinearLayout local=mediaCard("local","Local Video","From this device",true);
        LinearLayout link=mediaCard("link","Watch From Link","YouTube & public links",true);
        cards.addView(local,new LinearLayout.LayoutParams(0,dp(68),1));
        Space g1=new Space(this); cards.addView(g1,new LinearLayout.LayoutParams(dp(8),1));
        cards.addView(link,new LinearLayout.LayoutParams(0,dp(68),1));
        add(box,cards,-1,dp(68),0,8);

        LinearLayout movie=mediaCard("movie","Watch Movies","Coming soon",false);
        add(box,movie,-1,dp(54),0,0);

        mediaChooserOverlay.addView(box,new FrameLayout.LayoutParams(-1,-1));
        roomPlayerBox.addView(mediaChooserOverlay,new FrameLayout.LayoutParams(-1,-1));
        local.setOnClickListener(v->{hideMediaChooser();uiHandler.postDelayed(this::pickVideo,60);});
        link.setOnClickListener(v->{hideMediaChooser();uiHandler.postDelayed(this::showLinkDialog,60);});
        movie.setOnClickListener(v->toast("Watch Movies is coming soon."));
        mediaChooserOverlay.setAlpha(1f);
    }
    private void hideMediaChooser(){if(mediaChooserOverlay!=null)mediaChooserOverlay.setVisibility(View.GONE);}

    private void showRoom(){
        if(player!=null && roomRoot!=null){refreshParticipants();return;}
        if(player!=null){try{player.release();}catch(Exception ignored){}player=null;}
        roomSessionGeneration++;roomRoot=new FrameLayout(this);roomRoot.setBackground(round(BG,Color.TRANSPARENT,0));ScrollView sc=scroll(col());roomContent=(LinearLayout)sc.getChildAt(0);roomContent.setPadding(dp(10),dp(10),dp(10),dp(78));FrameLayout.LayoutParams scLp=new FrameLayout.LayoutParams(-1,-1);roomRoot.addView(sc,scLp);screen(roomRoot);
        roomHeader=row();roomHeader.setGravity(Gravity.CENTER_VERTICAL);ImageButton back=new ImageButton(this);back.setImageResource(R.drawable.ic_back);back.setColorFilter(TEXT);back.setBackgroundColor(Color.TRANSPARENT);back.setPadding(0,0,0,0);back.setScaleType(ImageView.ScaleType.CENTER);roomHeader.addView(back,new LinearLayout.LayoutParams(dp(30),dp(36)));TextView h=title(roomName,14);h.setGravity(Gravity.CENTER_VERTICAL);h.setSingleLine(true);roomHeader.addView(h,new LinearLayout.LayoutParams(0,dp(36),1));syncStatusLabel=text("Syncing…",10,MUTED);syncStatusLabel.setTypeface(Typeface.DEFAULT,Typeface.BOLD);syncStatusLabel.setGravity(Gravity.CENTER);roomHeader.addView(syncStatusLabel,new LinearLayout.LayoutParams(dp(58),dp(32)));ImageButton settingsTop=iconOnlyButton(R.drawable.ic_settings,false);settingsTop.setPadding(dp(4),dp(4),dp(4),dp(4));roomHeader.addView(settingsTop,new LinearLayout.LayoutParams(dp(36),dp(36)));add(roomContent,roomHeader,-1,dp(36),0,8);back.setOnClickListener(v->disconnect());settingsTop.setOnClickListener(v->showSettingsDialog());
        LinearLayout statusRow=row();connectionLabel=text("●  Connected",10,SUCCESS);connectionLabel.setTypeface(Typeface.DEFAULT,Typeface.BOLD);connectionLabel.setGravity(Gravity.CENTER_VERTICAL);statusRow.setPadding(dp(2),0,dp(2),0);statusRow.addView(connectionLabel,new LinearLayout.LayoutParams(0,dp(22),1));participantCount=text("●  "+Math.max(1,participants.size())+" watching",10,SUCCESS); TextView watching=participantCount;watching.setTypeface(Typeface.DEFAULT,Typeface.BOLD);watching.setGravity(Gravity.CENTER_VERTICAL|Gravity.RIGHT);statusRow.addView(watching,new LinearLayout.LayoutParams(0,dp(22),1));add(roomContent,statusRow,-1,dp(22),0,8);
        participantStrip=col();
         roomPeople=col();roomPeople.setPadding(dp(12),dp(10),dp(12),dp(10));roomPeople.setBackground(round(SURFACE,LINE,14));LinearLayout person=row();person.setGravity(Gravity.CENTER_VERTICAL);ImageView avatar=new ImageView(this);avatar.setImageBitmap(loadPfpBitmap(participantPfps.getOrDefault(localPresenceKey,"asset:0")));avatar.setScaleType(ImageView.ScaleType.CENTER_CROP);avatar.setBackground(round(SURFACE2,Color.TRANSPARENT,20));localRoomAvatar=avatar;person.addView(avatar,new LinearLayout.LayoutParams(dp(38),dp(38)));LinearLayout words=col();words.setGravity(Gravity.CENTER_VERTICAL);words.setPadding(dp(8),0,0,0);String localName=username==null||username.trim().isEmpty()?"You":username;TextView localNameView=title(localName,12);localNameView.setSingleLine(true);localNameView.setEllipsize(android.text.TextUtils.TruncateAt.END);localNameView.setIncludeFontPadding(true);add(words,localNameView,-1,dp(30),0,1);localRoomStatus=text("●  Online",10,SUCCESS);add(words,localRoomStatus,-1,dp(24),0,0);person.addView(words,new LinearLayout.LayoutParams(0,dp(54),1));roomPeople.addView(person,new LinearLayout.LayoutParams(-1,dp(54)));add(roomContent,roomPeople,-1,dp(74),0,4); roomContent.addView(participantStrip,new LinearLayout.LayoutParams(-1,dp(44))); participantStrip.setOrientation(LinearLayout.HORIZONTAL); participantStrip.setGravity(Gravity.CENTER_VERTICAL); participantStrip.setPadding(dp(2),0,dp(2),0); roomPeople.setOnClickListener(v->showParticipantSheet());
        roomPlayerBox=new AspectRatioFrameLayout(this,16f/9f);roomPlayerBox.setBackground(round(Color.BLACK,LINE,20));View pv=getLayoutInflater().inflate(R.layout.view_player,roomPlayerBox,false);roomPlayerBox.addView(pv,new FrameLayout.LayoutParams(-1,-1));playerView=(PlayerView)pv;adjustedSubtitleView=new SubtitleView(this);adjustedSubtitleView.setVisibility(View.GONE);adjustedSubtitleView.setClickable(false);adjustedSubtitleView.setFocusable(false);adjustedSubtitleView.setBottomPaddingFraction(0.16f);
         View subtitleControls=playerView.findViewById(R.id.coview_control_surface); if(subtitleControls!=null){ subtitleControls.addOnLayoutChangeListener((v,l,t,r,b,ol,ot,or_,ob)->{ if(playerView.getHeight()>0) adjustedSubtitleView.setBottomPaddingFraction(Math.min(.42f,Math.max(.18f,(float)(b-t)/Math.max(1,playerView.getHeight()))+.04f));}); }
        roomPlayerBox.addView(adjustedSubtitleView,new FrameLayout.LayoutParams(-1,-1));playerEmptyOverlay=buildEmptyPlayerOverlay();roomPlayerBox.addView(playerEmptyOverlay,new FrameLayout.LayoutParams(-1,-1));playerEmptyOverlay.setOnClickListener(v->showMediaChooser());setPlayerEmptyVisible(true);add(roomContent,roomPlayerBox,-1,-2,0,6);
        mediaInfo=row();mediaLabel=text("No media selected",10,MUTED);mediaInfo.addView(mediaLabel,new LinearLayout.LayoutParams(-1,dp(20)));mediaInfo.setVisibility(View.VISIBLE);add(roomContent,mediaInfo,-1,dp(20),0,4);syncStatusLabel.setContentDescription("Synchronization status");
        TextView watchTitle=text("WATCH TOGETHER",14,MUTED);watchTitle.setTypeface(Typeface.DEFAULT,Typeface.BOLD);watchTitle.setLetterSpacing(.06f);add(roomContent,watchTitle,-1,dp(20),4,8);roomMediaRow=col();LinearLayout r1=row();LinearLayout localSource=sourceCard("Local Video","From this device",R.drawable.ic_folder,true);LinearLayout linkSource=sourceCard("Watch From Link","YouTube & public links",R.drawable.ic_link,true);r1.addView(localSource,new LinearLayout.LayoutParams(0,dp(76),1));r1.addView(new Space(this),new LinearLayout.LayoutParams(dp(10),1));r1.addView(linkSource,new LinearLayout.LayoutParams(0,dp(76),1));roomMediaRow.addView(r1,new LinearLayout.LayoutParams(-1,dp(76)));LinearLayout r2=row();LinearLayout upload=sourceCard("Upload Video","Coming soon",R.drawable.ic_upload,false);LinearLayout movies=sourceCard("Browse Library","Coming soon",R.drawable.ic_movie,false);r2.addView(upload,new LinearLayout.LayoutParams(0,dp(76),1));r2.addView(new Space(this),new LinearLayout.LayoutParams(dp(10),1));r2.addView(movies,new LinearLayout.LayoutParams(0,dp(76),1));roomMediaRow.addView(r2,new LinearLayout.LayoutParams(-1,dp(76)));add(roomContent,roomMediaRow,-1,dp(163),0,10);localSource.setOnClickListener(v->pickVideo());linkSource.setOnClickListener(v->showLinkDialog());upload.setOnClickListener(v->toast("Server upload is coming soon."));
        roomQuick=row();ImageButton cb=iconOnlyButton(R.drawable.ic_chat,false);ImageButton pb=iconOnlyButton(R.drawable.ic_people,false);ImageButton audio=iconOnlyButton(R.drawable.ic_call,false);ImageButton more=iconOnlyButton(R.drawable.ic_more,false);ImageButton leave=iconOnlyButton(R.drawable.ic_exit,false);leave.setColorFilter(Color.rgb(255,70,95));audio.setAlpha(.5f);audio.setContentDescription("Audio call (coming soon)");ImageButton[] quick={cb,pb,audio,more,leave};for(int qi=0;qi<quick.length;qi++){quick[qi].setPadding(0,0,0,0);quick[qi].setBackground(round(SURFACE,LINE,18));roomQuick.addView(quick[qi],new LinearLayout.LayoutParams(0,dp(42),1));if(qi<quick.length-1)roomQuick.addView(new Space(this),new LinearLayout.LayoutParams(dp(4),1));}FrameLayout.LayoutParams quickLp=new FrameLayout.LayoutParams(-1,dp(46),Gravity.BOTTOM);quickLp.setMargins(dp(10),0,dp(10),dp(12));roomRoot.addView(roomQuick,quickLp);cb.setOnClickListener(v->openChat());pb.setOnClickListener(v->showParticipantSheet());audio.setOnClickListener(v->toast("Audio call is coming soon."));more.setOnClickListener(v->showMoreRoomMenu());leave.setOnClickListener(v->disconnect());
        initPlayer();restorePersistedLocalVideo();updateActiveBackgrounds();refreshParticipants();updateChatBadges();if(!pendingRemoteWebSource.isEmpty()){String queued=pendingRemoteWebSource;pendingRemoteWebSource="";uiHandler.postDelayed(()->loadOnlineSource(queued,false),80);}if(!currentMediaSessionId.isEmpty()&&"web".equalsIgnoreCase(currentMediaType))sendStateRequest();
    }
    private View buildEmptyPlayerOverlay(){FrameLayout f=new FrameLayout(this);LinearLayout center=col();center.setGravity(Gravity.CENTER);ImageView play=new ImageView(this);play.setImageResource(R.drawable.ic_play);play.setColorFilter(TEXT);play.setBackground(round(SURFACE2,Color.TRANSPARENT,50));center.addView(play,new LinearLayout.LayoutParams(dp(72),dp(72)));TextView t=title("No media selected",15);t.setGravity(Gravity.CENTER);add(center,t,-1,dp(30),8,0);TextView d=text("Choose a mode below to start watching together",11,MUTED);d.setGravity(Gravity.CENTER);add(center,d,-1,dp(28),0,0);FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-1,dp(150),Gravity.CENTER);f.addView(center,cp);return f;}
    private void showMoreRoomMenu(){LinearLayout box=col();box.setPadding(dp(14),dp(8),dp(14),dp(10));Button ps=iconButton("Player settings",R.drawable.ic_settings,false);Button people=iconButton("People in room",R.drawable.ic_people,false);add(box,ps,-1,dp(56),0,8);add(box,people,-1,dp(56),0,0);AlertDialog dialog=new AlertDialog.Builder(this).setTitle("More").setView(box).setNegativeButton("Cancel",null).create();ps.setOnClickListener(v->{dialog.dismiss();showPlayerSettings();});people.setOnClickListener(v->{dialog.dismiss();showParticipantSheet();});dialog.show();styleDialog(dialog);}

    private void showParticipantSheet(){
        ScrollView scrollBox=new ScrollView(this); scrollBox.setFillViewport(true); scrollBox.setVerticalScrollBarEnabled(true); scrollBox.setScrollbarFadingEnabled(false);
        LinearLayout box=col();box.setPadding(dp(10),dp(6),dp(10),dp(8));
        for(Map.Entry<String,String> e:participants.entrySet()){
            String key=e.getKey(),name=e.getValue(); LinearLayout line=row(); line.setPadding(dp(8),dp(7),dp(8),dp(7)); line.setGravity(Gravity.CENTER_VERTICAL);
            ImageView a=new ImageView(this);a.setImageBitmap(loadPfpBitmap(participantPfps.getOrDefault(key,"asset:0")));a.setScaleType(ImageView.ScaleType.CENTER_CROP);a.setBackground(round(SURFACE2,LINE,50));line.addView(a,new LinearLayout.LayoutParams(dp(30),dp(30)));
            LinearLayout words=col(); words.setGravity(Gravity.CENTER_VERTICAL); words.setPadding(dp(12),dp(2),dp(4),dp(2));
            String shown=name+((key.equals(localPresenceKey)||(!localPresenceKey.isEmpty()&&name.equals(username)))?"  (You)":"");
            TextView shownName=title(shown,14); shownName.setSingleLine(true); shownName.setEllipsize(android.text.TextUtils.TruncateAt.END); shownName.setIncludeFontPadding(true); shownName.setGravity(Gravity.CENTER_VERTICAL); words.addView(shownName,new LinearLayout.LayoutParams(-1,dp(30)));
            String st=participantStatuses.getOrDefault(key,"Online"); TextView status=text("● "+st,10,st.equals("Playing")?SUCCESS:MUTED); status.setGravity(Gravity.CENTER_VERTICAL); words.addView(status,new LinearLayout.LayoutParams(-1,dp(24)));
            line.addView(words,new LinearLayout.LayoutParams(0,dp(64),1)); box.addView(line,new LinearLayout.LayoutParams(-1,dp(72)));
        }
        scrollBox.addView(box,new ScrollView.LayoutParams(-1,-2));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("People in room").setView(scrollBox).setNegativeButton("Done",null).create();
        styleDialog(dialog); dialog.show(); finishDialogStyle(dialog);
    }

    private void showPlayerSettings(){
        LinearLayout box=col();box.setPadding(dp(18),dp(6),dp(18),dp(8));
        TextView playback=text("Playback",12,MUTED);playback.setTypeface(Typeface.DEFAULT,Typeface.BOLD);add(box,playback,-1,dp(26),0,6);
        LinearLayout speeds=row();float[] speedValues={0.5f,0.75f,1f,1.25f,1.5f,2f};for(float spd:speedValues){Button b=button(spd==1f?"1×":String.format(Locale.US,"%.2g×",spd),false);b.setTextSize(12);if(player!=null&&Math.abs(player.getPlaybackParameters().speed-spd)<.01f)b.setBackground(round(SURFACE2,Color.WHITE,14));speeds.addView(b,new LinearLayout.LayoutParams(0,dp(46),1));if(spd!=2f)speeds.addView(new Space(this),new LinearLayout.LayoutParams(dp(5),1));b.setOnClickListener(v->{if(webMode&&webPageReady)applyWebPlaybackSpeed(spd);else if(player!=null){player.setPlaybackSpeed(spd);sendSync(true,"",null,"speed",true);}});}add(box,speeds,-1,dp(46),0,10);
         LinearLayout vol=col();vol.setPadding(dp(12),dp(10),dp(12),dp(8));vol.setBackground(round(SURFACE,LINE,16));LinearLayout vh=row();vh.addView(iconView(playerVolumePercent==0?R.drawable.ic_audio_muted:R.drawable.ic_audio,28),new LinearLayout.LayoutParams(dp(38),dp(36)));LinearLayout vw=col();add(vw,title("Volume",14),-1,dp(22),0,1);add(vw,text("Adjust the player volume (0–100%)",10,MUTED),-1,dp(20),0,0);vh.addView(vw,new LinearLayout.LayoutParams(0,dp(44),1));TextView volValue=title(playerVolumePercent+"%",13); settingsVolumeValue=volValue;volValue.setGravity(Gravity.CENTER);volValue.setBackground(round(SURFACE2,LINE,12));vh.addView(volValue,new LinearLayout.LayoutParams(dp(66),dp(38)));vol.addView(vh,new LinearLayout.LayoutParams(-1,dp(44)));SeekBar seek=new SeekBar(this);settingsVolumeSeek=seek;seek.setMax(100);seek.setProgress(playerVolumePercent);vol.addView(seek,new LinearLayout.LayoutParams(-1,dp(36)));LinearLayout vl=row();TextView v0=text("0%",10,MUTED);TextView v100=text("100%",10,MUTED);v100.setGravity(Gravity.RIGHT);vl.addView(v0,new LinearLayout.LayoutParams(0,dp(22),1));vl.addView(v100,new LinearLayout.LayoutParams(0,dp(22),1));vol.addView(vl,new LinearLayout.LayoutParams(-1,dp(22)));add(box,vol,-1,dp(126),0,10);seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean from){volValue.setText(p+"%");if(from)setPlayerVolumePercent(p);}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});
        LinearLayout audio=row();audio.setPadding(dp(12),dp(8),dp(10),dp(8));audio.setBackground(round(SURFACE,LINE,16));audio.addView(iconView(R.drawable.ic_audio,28),new LinearLayout.LayoutParams(dp(42),dp(44)));LinearLayout aw=col();add(aw,title("Audio Track",14),-1,dp(22),0,1);add(aw,text("Select audio track (e.g. different languages)",10,MUTED),-1,dp(20),0,0);audio.addView(aw,new LinearLayout.LayoutParams(0,dp(44),1));TextView ar=text("›",28,MUTED);ar.setGravity(Gravity.CENTER);audio.addView(ar,new LinearLayout.LayoutParams(dp(34),dp(44)));add(box,audio,-1,dp(62),0,8);audio.setOnClickListener(v->{if(webMode){toast("Audio tracks are controlled by YouTube.");return;}if(player==null||player.getMediaItemCount()==0){toast("Load a video first.");return;}try{new TrackSelectionDialogBuilder(this,"Audio track",player,C.TRACK_TYPE_AUDIO).setAllowAdaptiveSelections(false).setShowDisableOption(false).build().show();}catch(Exception e){toast("No selectable audio tracks available.");}});
        LinearLayout subtitle=row();subtitle.setPadding(dp(12),dp(8),dp(10),dp(8));subtitle.setBackground(round(SURFACE,LINE,16));subtitle.addView(iconView(R.drawable.ic_cc,28),new LinearLayout.LayoutParams(dp(42),dp(44)));LinearLayout sw=col();add(sw,title("Subtitle Track",14),-1,dp(22),0,1);add(sw,text("Select subtitle track",10,MUTED),-1,dp(20),0,0);subtitle.addView(sw,new LinearLayout.LayoutParams(0,dp(44),1));TextView sr=text("›",28,MUTED);sr.setGravity(Gravity.CENTER);subtitle.addView(sr,new LinearLayout.LayoutParams(dp(34),dp(44)));add(box,subtitle,-1,dp(62),0,8);subtitle.setOnClickListener(v->{if(webMode){toast("Subtitle tracks are controlled by YouTube. Use Custom Subtitle for an external file.");return;}if(player==null||player.getMediaItemCount()==0){toast("Load a video first.");return;}try{new TrackSelectionDialogBuilder(this,"Subtitle track",player,C.TRACK_TYPE_TEXT).setAllowAdaptiveSelections(false).setShowDisableOption(true).build().show();}catch(Exception e){toast("No subtitle tracks available.");}});
        LinearLayout delay=row();delay.setPadding(dp(12),dp(8),dp(10),dp(8));delay.setBackground(round(SURFACE,LINE,16));delay.addView(iconView(R.drawable.ic_history,28),new LinearLayout.LayoutParams(dp(42),dp(44)));LinearLayout dw=col();add(dw,title("Subtitle Delay",14),-1,dp(22),0,1);add(dw,text("Adjust custom subtitle timing",10,MUTED),-1,dp(20),0,0);delay.addView(dw,new LinearLayout.LayoutParams(0,dp(44),1));Button minus=button("−",false);Button plus=button("+",false);TextView delayText=text(formatDelay(subtitleDelayMs),12,TEXT);delayText.setGravity(Gravity.CENTER);delay.addView(minus,new LinearLayout.LayoutParams(dp(46),dp(42)));delay.addView(delayText,new LinearLayout.LayoutParams(dp(78),dp(42)));delay.addView(plus,new LinearLayout.LayoutParams(dp(46),dp(42)));add(box,delay,-1,dp(62),0,8);minus.setOnClickListener(v->{subtitleDelayMs=Math.max(-10000,subtitleDelayMs-250);prefs.edit().putInt("subtitle_delay_ms",subtitleDelayMs).apply();delayText.setText(formatDelay(subtitleDelayMs));if(subtitleUri!=null) scheduleCustomSubtitleTicker();});plus.setOnClickListener(v->{subtitleDelayMs=Math.min(10000,subtitleDelayMs+250);prefs.edit().putInt("subtitle_delay_ms",subtitleDelayMs).apply();delayText.setText(formatDelay(subtitleDelayMs));if(subtitleUri!=null) scheduleCustomSubtitleTicker();});
        Button upload=iconButton("Upload Custom Subtitle",R.drawable.ic_upload,false);add(box,upload,-1,dp(56),0,8);upload.setOnClickListener(v->{if(videoUri==null){toast("Load a video first.");return;}Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("text/*");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"text/plain","text/vtt","application/x-subrip","text/ssa","text/x-ssa"});i.addCategory(Intent.CATEGORY_OPENABLE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,PICK_SUBTITLE);});
        TextView status=text(subtitleUri==null?"No custom subtitle loaded":"Custom: "+displayName(subtitleUri),10,MUTED);add(box,status,-1,dp(24),0,4);Button clear=iconButton("Clear Custom Subtitle",R.drawable.ic_close,false);add(box,clear,-1,dp(56),0,4);clear.setOnClickListener(v->{subtitleUri=null; prefs.edit().remove("subtitle_uri").apply(); subtitleGeneration++; cancelCustomSubtitleTicker(); customSubtitleCues.clear(); customSubtitleLoading=false; if(adjustedSubtitleView!=null){adjustedSubtitleView.setVisibility(View.GONE);adjustedSubtitleView.setCues(Collections.emptyList());} if(trackSelector!=null){try{trackSelector.setParameters(trackSelector.buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_TEXT,!ccEnabled).build());}catch(Exception ignored){}} status.setText("No custom subtitle loaded"); updateSubtitleButtonVisibility();});
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Player Settings").setView(scroll(box)).setPositiveButton("Done",null).create();styleDialog(dialog);dialog.show();finishDialogStyle(dialog);
    }

    private String formatDelay(int ms){
    String sign=ms>=0?"+":"−";
    return sign+String.format(Locale.US,"%.2f s",Math.abs(ms)/1000f);
}

    private void setCaptionEnabled(boolean enabled){
        ccEnabled=enabled;
        if(trackSelector!=null && subtitleUri==null){
            try{
                trackSelector.setParameters(trackSelector.buildUponParameters()
                        .setTrackTypeDisabled(C.TRACK_TYPE_TEXT,!enabled).build());
            }catch(Exception ignored){}
        }
        if(adjustedSubtitleView!=null){
            adjustedSubtitleView.setVisibility((enabled&&subtitleUri!=null)?View.VISIBLE:View.GONE);
            if(enabled&&subtitleUri!=null) renderCustomSubtitleNow();
        }
        updateSubtitleButtonVisibility();
    }

    private void cancelCustomSubtitleTicker(){
        if(customSubtitleTickRunnable!=null){
            uiHandler.removeCallbacks(customSubtitleTickRunnable);
            customSubtitleTickRunnable=null;
        }
        if(adjustedSubtitleView!=null) adjustedSubtitleView.setCues(Collections.emptyList());
    }

    private long shiftedStart(SubtitleCueData c){ return c.startMs + subtitleDelayMs; }
    private long shiftedEnd(SubtitleCueData c){ return c.endMs + subtitleDelayMs; }

    private void renderCustomSubtitleNow(){
        if(adjustedSubtitleView==null)return;
        if(!ccEnabled || subtitleUri==null || customSubtitleCues.isEmpty()){
            adjustedSubtitleView.setVisibility(View.GONE);
            adjustedSubtitleView.setCues(Collections.emptyList());
            return;
        }
        adjustedSubtitleView.setVisibility(View.VISIBLE);
        long position=player==null?0:player.getCurrentPosition();
        ArrayList<Cue> visible=new ArrayList<>();
        long nextBoundary=Long.MAX_VALUE;

        int lo=0,hi=customSubtitleCues.size()-1,lastStart=-1;
        while(lo<=hi){
            int mid=(lo+hi)>>>1;
            long startMs=shiftedStart(customSubtitleCues.get(mid));
            if(startMs<=position){lastStart=mid;lo=mid+1;} else hi=mid-1;
        }
        int from=Math.max(0,lastStart-1);
        for(int i=from;i<customSubtitleCues.size();i++){
            SubtitleCueData c=customSubtitleCues.get(i);
            long startMs=shiftedStart(c),endMs=Math.max(startMs+1,shiftedEnd(c));
            if(startMs>position && startMs<nextBoundary)nextBoundary=startMs;
            if(endMs>position && endMs<nextBoundary)nextBoundary=endMs;
            if(startMs<=position && position<endMs)
                visible.add(new Cue.Builder().setText(c.text).build());
            if(startMs>position && visible.isEmpty())break;
        }
        adjustedSubtitleView.setCues(visible);
        if(customSubtitleTickRunnable!=null)uiHandler.removeCallbacks(customSubtitleTickRunnable);
        if(player!=null && player.isPlaying()){
            customSubtitleTickRunnable=()->renderCustomSubtitleNow();
            float speed=Math.max(0.25f,player.getPlaybackParameters().speed);
            long mediaDelay=nextBoundary==Long.MAX_VALUE?500:Math.max(30,nextBoundary-position+10);
            long realDelay=(long)Math.max(30,Math.min(500,mediaDelay/speed));
            uiHandler.postDelayed(customSubtitleTickRunnable,realDelay);
        }
    }

    private void scheduleCustomSubtitleTicker(){
        if(customSubtitleTickRunnable!=null)uiHandler.removeCallbacks(customSubtitleTickRunnable);
        renderCustomSubtitleNow();
        if(player!=null && player.isPlaying()){
            customSubtitleTickRunnable=()->renderCustomSubtitleNow();
            uiHandler.postDelayed(customSubtitleTickRunnable,100);
        }
    }

    private byte[] readLimited(java.io.InputStream in,int maxBytes)throws java.io.IOException{
        java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream();
        byte[] buf=new byte[8192];int total=0,n;
        while((n=in.read(buf))!=-1){total+=n;if(total>maxBytes)throw new java.io.IOException("File too large");out.write(buf,0,n);}
        return out.toByteArray();
    }

    private void loadCustomSubtitleAsync(Uri uri){
        cancelCustomSubtitleTicker();
        customSubtitleCues.clear();
        subtitleGeneration++;
        customSubtitleLoading=true;
        final long generation=subtitleGeneration;
        if(subtitleExecutor==null || subtitleExecutor.isShutdown())
            subtitleExecutor=java.util.concurrent.Executors.newSingleThreadExecutor();
        subtitleExecutor.execute(()->{
            ArrayList<SubtitleCueData> parsed=new ArrayList<>();
            try{
                byte[] bytes;
                try(java.io.InputStream in=getContentResolver().openInputStream(uri)){
                    if(in==null)throw new IllegalStateException("Unable to open subtitle");
                    bytes=readLimited(in,8*1024*1024);
                }
                java.nio.charset.Charset cs=StandardCharsets.UTF_8;
                int off=0;
                if(bytes.length>=2 && (bytes[0]&255)==0xFF && (bytes[1]&255)==0xFE){cs=StandardCharsets.UTF_16LE;off=2;}
                else if(bytes.length>=2 && (bytes[0]&255)==0xFE && (bytes[1]&255)==0xFF){cs=StandardCharsets.UTF_16BE;off=2;}
                else if(bytes.length>=3 && (bytes[0]&255)==0xEF && (bytes[1]&255)==0xBB && (bytes[2]&255)==0xBF){off=3;}
                String all=new String(bytes,off,bytes.length-off,cs);
                String name=displayName(uri).toLowerCase(Locale.ROOT);
                if(name.endsWith(".ass")||name.endsWith(".ssa")) parsed=parseAssCues(all.toString());
                else parsed=parseSrtVttCues(all.toString());
            }catch(Exception ignored){}
            final ArrayList<SubtitleCueData> parsedCues=new ArrayList<>(parsed);
            runOnUiThread(()->{
                if(generation!=subtitleGeneration || subtitleUri!=uri || isFinishing())return;
                customSubtitleCues.clear(); customSubtitleCues.addAll(parsedCues);
                customSubtitleLoading=false;
                if(trackSelector!=null){
                    try{trackSelector.setParameters(trackSelector.buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_TEXT,true).build());}catch(Exception ignored){}
                }
                if(parsedCues.isEmpty()){
                    subtitleUri=null; prefs.edit().remove("subtitle_uri").apply();
                    cancelCustomSubtitleTicker();
                    updateSubtitleButtonVisibility();
                    toast("Could not read the custom subtitle.");
                }else{
                    updateSubtitleButtonVisibility();
                    if(adjustedSubtitleView!=null){adjustedSubtitleView.bringToFront();adjustedSubtitleView.setVisibility(View.VISIBLE);}
                    scheduleCustomSubtitleTicker();
                    toast("Custom subtitle loaded");
                }
            });
        });
    }

    private ArrayList<SubtitleCueData> parseSrtVttCues(String text){
        ArrayList<SubtitleCueData> out=new ArrayList<>();
        String[] blocks=text.replace("\uFEFF","").replace("\r","").split("\\n\\s*\\n");
        java.util.regex.Pattern timing=java.util.regex.Pattern.compile(
                "(?m)^\\s*(\\d{1,2}:\\d{2}(?::\\d{2})?[\\.,]\\d{3})\\s*-->\\s*(\\d{1,2}:\\d{2}(?::\\d{2})?[\\.,]\\d{3})(?:[^\\r\\n]*)?\\r?$");
        for(String block:blocks){
            java.util.regex.Matcher m=timing.matcher(block);
            if(!m.find())continue;
            long a=parseFlexibleTimestamp(m.group(1)), b=parseFlexibleTimestamp(m.group(2));
            if(b<=a)continue;
            String body=block.substring(m.end()).trim();
            body=body.replaceAll("(?m)^\\s*WEBVTT\\s*$","").trim();
            if(!body.isEmpty()) out.add(new SubtitleCueData(a,b,android.text.Html.fromHtml(body.replace("\\n","<br>"))));
        }
        return out;
    }

    private ArrayList<SubtitleCueData> parseAssCues(String text){
        ArrayList<SubtitleCueData> out=new ArrayList<>();
        for(String line:text.replace("\r","").split("\\n")){
            if(!line.startsWith("Dialogue:"))continue;
            String[] p=line.split(",",10);
            if(p.length<10)continue;
            try{
                long a=parseAssTimestamp(p[1]), b=parseAssTimestamp(p[2]);
                if(b<=a)continue;
                String body=p[9].replaceAll("\\{[^}]*\\}","");
                body=body.replace("\\N","\n").replace("\\n","\n").trim();
                if(!body.isEmpty())out.add(new SubtitleCueData(a,b,body));
            }catch(Exception ignored){}
        }
        return out;
    }

    private long parseFlexibleTimestamp(String s){
        String t=s.trim().replace(',', '.');
        String[] parts=t.split(":",-1);
        try{
            long hours=0,minutes=0;
            double seconds;
            if(parts.length==2){
                minutes=Long.parseLong(parts[0]); seconds=Double.parseDouble(parts[1]);
            }else if(parts.length==3){
                hours=Long.parseLong(parts[0]); minutes=Long.parseLong(parts[1]); seconds=Double.parseDouble(parts[2]);
            }else return 0;
            return hours*3600000L+minutes*60000L+Math.round(seconds*1000.0);
        }catch(Exception e){return 0;}
    }

    private long parseAssTimestamp(String s){
        String[] p=s.trim().split(":",-1);
        if(p.length!=3)return 0;
        String[] sec=p[2].split("\\.",-1);
        int cs=sec.length>1?Integer.parseInt((sec[1]+"00").substring(0,2)):0;
        return Long.parseLong(p[0])*3600000L+Long.parseLong(p[1])*60000L+
                Long.parseLong(sec[0])*1000L+cs*10L;
    }

    private void applyCustomSubtitleFile(){
        if(subtitleUri==null){
            cancelCustomSubtitleTicker(); customSubtitleCues.clear(); customSubtitleLoading=false;
            if(adjustedSubtitleView!=null){adjustedSubtitleView.setVisibility(View.GONE);adjustedSubtitleView.setCues(Collections.emptyList());}
            if(trackSelector!=null){
                try{trackSelector.setParameters(trackSelector.buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_TEXT,!ccEnabled).build());}catch(Exception ignored){}
            }
            updateSubtitleButtonVisibility();
            return;
        }
        if(trackSelector!=null){
            try{trackSelector.setParameters(trackSelector.buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_TEXT,true).build());}catch(Exception ignored){}
        }
        loadCustomSubtitleAsync(subtitleUri);
    }

    private MediaItem buildMediaItem(){
        if(videoUri==null)return null;
        return new MediaItem.Builder().setUri(videoUri).build();
    }
    private void setPlayerVolumePercent(int percent){
        percent=Math.max(0,Math.min(100,percent));
        playerVolumePercent=percent;
        float value=percent/100f;
        if(player!=null) player.setVolume(value);
        if(value>0f) lastNonZeroVolume=value;
        prefs.edit().putInt("player_volume_percent",percent)
                .putFloat("player_last_nonzero",lastNonZeroVolume).apply();
        updatePlayerVolumeUi();
    }

    private void updatePlayerVolumeUi(){
        int p=Math.max(0,Math.min(100,playerVolumePercent));
        boolean muted=p==0;
        if(playerVolumeSeek!=null){
            playerVolumeSeek.setProgress(p);
            playerVolumeSeek.setContentDescription("Volume "+p+" percent");
        }
        if(settingsVolumeSeek!=null && settingsVolumeSeek.getProgress()!=p) settingsVolumeSeek.setProgress(p);
        if(settingsVolumeValue!=null) settingsVolumeValue.setText(p+"%");
        if(playerVolumeButton!=null){
            playerVolumeButton.setImageResource(muted?R.drawable.ic_audio_muted:R.drawable.ic_audio);
            playerVolumeButton.setContentDescription(muted?"Unmute":"Mute");
        }
    }


    private void togglePlayerMute(){
        if(player==null)return;
        if(playerVolumePercent>0){
            lastNonZeroVolume=Math.max(0.01f,playerVolumePercent/100f);
            setPlayerVolumePercent(0);
        }else{
            int restored=Math.max(1,Math.min(100,Math.round(lastNonZeroVolume*100f)));
            setPlayerVolumePercent(restored);
        }
    }
    private void releaseWebViewForMediaSwitch(){
        if(webLoadTimeoutRunnable!=null){uiHandler.removeCallbacks(webLoadTimeoutRunnable);webLoadTimeoutRunnable=null;}
        if(youtubeFullscreen)exitYouTubeFullscreen();
        if(webView!=null){
            try{
                ViewParent parent=webView.getParent();
                if(parent instanceof ViewGroup)((ViewGroup)parent).removeView(webView);
                webView.setWebChromeClient(null);
                webView.setWebViewClient(null);
                webView.stopLoading();
                webView.removeJavascriptInterface("CoView");
                webView.loadUrl("about:blank");
                webView.destroy();
            }catch(Exception ignored){}
        }
        webView=null;webMode=false;webPageReady=false;webObservedInitial=false;pendingWebStateRequestId="";webBridgeEnabled=false;webBridgeNonce="";onlineReady=false;webUrl="";webSessionGeneration++;
        if(webLoadingOverlay!=null){
            try{ViewParent parent=webLoadingOverlay.getParent();if(parent instanceof ViewGroup)((ViewGroup)parent).removeView(webLoadingOverlay);}catch(Exception ignored){}
            webLoadingOverlay=null;
        }
    }
    private void resetPlayerMediaState(){
        applyingRemote=true;remoteGuardUntil=SystemClock.uptimeMillis()+700;
        try{
            if(playerView!=null)playerView.setPlayer(null);
            if(player!=null){
                try{player.stop();}catch(Exception ignored){}
                try{player.clearMediaItems();}catch(Exception ignored){}
                try{player.setPlayWhenReady(false);}catch(Exception ignored){}
                try{player.release();}catch(Exception ignored){}
            }
        }finally{
            player=null;
            applyingRemote=false;
        }
        videoUri=null;subtitleUri=null;customSubtitleCues.clear();cancelCustomSubtitleTicker();customSubtitleLoading=false;onlineMode=false;onlineUrl="";onlineReady=false;
        webMode=false;webPageReady=false;webObservedInitial=false;lastWebPositionMs=-1;lastWebPlaying=false;pendingWebStateRequestId="";
        
        
        if(adjustedSubtitleView!=null){adjustedSubtitleView.setVisibility(View.GONE);adjustedSubtitleView.setCues(Collections.emptyList());}
        if(playerView!=null){playerView.setVisibility(View.VISIBLE);initPlayer();}
    }
    private void prepareForOnlineMedia(){
        if(youtubeFullscreen)exitYouTubeFullscreen();
        releaseWebViewForMediaSwitch();
        resetPlayerMediaState();
    }
    private void prepareForLocalMedia(){
        if(youtubeFullscreen)exitYouTubeFullscreen();
        releaseWebViewForMediaSwitch();
        resetPlayerMediaState();
        if(roomPlayerBox!=null&&webLoadingOverlay!=null)webLoadingOverlay.setVisibility(View.GONE);
    }

    private void updateSubtitleButtonVisibility(){
        if(playerView==null||player==null)return;
        View b=playerView.findViewById(R.id.coview_subtitle);
        if(b==null)return;
        boolean hasText=subtitleUri!=null;
        try{
            for(Tracks.Group group:player.getCurrentTracks().getGroups()){
                if(group.getType()==C.TRACK_TYPE_TEXT){hasText=true;break;}
            }
        }catch(Exception ignored){}
        b.setVisibility(hasText?View.VISIBLE:View.GONE);
        b.setContentDescription(subtitleUri!=null?"Custom captions":"Captions");
    }

    private void initPlayer(){
        trackSelector=new DefaultTrackSelector(this);
        player=new ExoPlayer.Builder(this).setTrackSelector(trackSelector).build();
        playerView.setPlayer(player);
        playerView.setUseController(true);
        playerView.setControllerShowTimeoutMs(3500);
        playerView.setControllerHideOnTouch(true);
        playerView.setControllerAutoShow(true);

        ImageButton volumeButton=playerView.findViewById(R.id.coview_volume);
        SeekBar volumeSeek=playerView.findViewById(R.id.coview_volume_seek);
        playerVolumeButton=volumeButton;
        playerVolumeSeek=volumeSeek;
        ImageButton moreButton=playerView.findViewById(R.id.coview_more);
        ImageButton fullscreenButton=playerView.findViewById(R.id.coview_fullscreen);
        ImageButton subtitleButton=playerView.findViewById(R.id.coview_subtitle);

        playerVolumePercent=Math.max(0,Math.min(100,prefs.getInt("player_volume_percent",100)));
        lastNonZeroVolume=Math.max(0.01f,Math.min(1f,prefs.getFloat("player_last_nonzero",1f)));
        player.setVolume(playerVolumePercent/100f);

        if(volumeSeek!=null){
            volumeSeek.setProgress(playerVolumePercent);
            volumeSeek.setContentDescription("Volume "+playerVolumePercent+" percent");
            volumeSeek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){
                public void onProgressChanged(SeekBar b,int p,boolean fromUser){
                    if(fromUser)setPlayerVolumePercent(p);
                }
                public void onStartTrackingTouch(SeekBar b){}
                public void onStopTrackingTouch(SeekBar b){}
            });
        }
        updatePlayerVolumeUi();

        View controlSurface=playerView.findViewById(R.id.coview_control_surface);
        if(controlSurface!=null){
            ViewCompat.setOnApplyWindowInsetsListener(controlSurface,(v,insets)->{
                int bottom=insets.getInsets(WindowInsetsCompat.Type.systemBars()).bottom;
                int extra=Math.max(0,bottom);
                v.setPadding(dp(8),dp(4),dp(8),dp(4)+extra);
                return insets;
            });
            ViewCompat.requestApplyInsets(controlSurface);
        }

        if(volumeButton!=null)volumeButton.setOnClickListener(v->togglePlayerMute());
        if(moreButton!=null)moreButton.setOnClickListener(v->showPlayerMoreMenu(moreButton));
        if(fullscreenButton!=null)fullscreenButton.setOnClickListener(v->setFullscreen(!fullscreen));
        if(subtitleButton!=null)subtitleButton.setOnClickListener(v->{
            if(subtitleUri!=null){
                setCaptionEnabled(!ccEnabled);
                toast(ccEnabled?"Captions on":"Captions off");
                return;
            }
            if(player==null||player.getMediaItemCount()==0){toast("Load a video first.");return;}
            try{
                new TrackSelectionDialogBuilder(this,"Subtitle track",player,C.TRACK_TYPE_TEXT)
                        .setAllowAdaptiveSelections(false).setShowDisableOption(true).build().show();
            }catch(Exception e){toast("No subtitle tracks available.");}
        });

        player.addListener(new Player.Listener(){
            @Override public void onTracksChanged(Tracks tracks){ updateSubtitleButtonVisibility(); }
            @Override public void onCues(CueGroup cueGroup){
                // Embedded subtitles are rendered by Media3 itself. Custom
                // subtitles are rendered only by adjustedSubtitleView.
            }
            @Override public void onIsPlayingChanged(boolean isPlaying){
                if(subtitleUri!=null)scheduleCustomSubtitleTicker();
                playerSyncHandler.removeCallbacks(nativeSyncHeartbeat);
                if(!applyingRemote && !webMode) setLocalPlaybackStatus(currentPresenceStatus());
                if(isPlaying && !webMode) playerSyncHandler.post(nativeSyncHeartbeat);
            }
            @Override public void onPlayWhenReadyChanged(boolean playWhenReady,int reason){
                if(!applyingRemote && !webMode && reason==Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST){
                    sendSync(true,"",playWhenReady,playWhenReady?"play":"pause",true);
                    setLocalPlaybackStatus(playWhenReady && player!=null && player.isPlaying()?"Playing":(playWhenReady?"Buffering":"Paused"));
                }
                if(subtitleUri!=null)renderCustomSubtitleNow();
            }
            @Override public void onPlayerError(androidx.media3.common.PlaybackException error){
                setLocalPlaybackStatus("Error");
                if(mediaLabel!=null)mediaLabel.setText("Playback error: "+(error==null?"Unknown error":error.getErrorCodeName()));
                if(!applyingRemote) sendSync(true,"",false,"error",true);
            }
            @Override public void onPlaybackStateChanged(int state){
                if(!applyingRemote && state==Player.STATE_BUFFERING) setLocalPlaybackStatus("Buffering");
                if(state==Player.STATE_ENDED && !applyingRemote){ setLocalPlaybackStatus("Paused"); sendSync(true,"",false,"ended",true); }
                if(subtitleUri!=null)renderCustomSubtitleNow();
                if(state==Player.STATE_READY && !pendingNativeStateRequestId.isEmpty()){
                    String rid=pendingNativeStateRequestId; String target=pendingNativeStateRequestTarget;
                    pendingNativeStateRequestId=""; pendingNativeStateRequestTarget="";
                    sendSync(true,rid,null,"heartbeat",false,target);
                }
                if(state==Player.STATE_READY && pendingNativeSync!=null){JSONObject queued=pendingNativeSync;pendingNativeSync=null;applySync(queued);}
            }
            @Override public void onPositionDiscontinuity(Player.PositionInfo oldP,Player.PositionInfo newP,int reason){
                if(!applyingRemote && SystemClock.uptimeMillis()>=remoteGuardUntil &&
                        reason==Player.DISCONTINUITY_REASON_SEEK) sendSync(true,"",null,"seek",true);
                if(subtitleUri!=null)renderCustomSubtitleNow();
            }
        });
        updateSubtitleButtonVisibility();
        playerSyncHandler.removeCallbacks(nativeSyncHeartbeat);
        if(player!=null && player.isPlaying() && !webMode) playerSyncHandler.post(nativeSyncHeartbeat);
    }

    private void showPlayerMoreMenu(View anchor){
        LinearLayout box=col();
        box.setPadding(dp(18),dp(8),dp(18),dp(8));
        Button speed=iconButton("Playback speed",R.drawable.ic_speed,false);
        Button settings=iconButton("Player settings",R.drawable.ic_settings,false);
        add(box,speed,-1,dp(52),0,6);
        add(box,settings,-1,dp(52),0,0);
        PopupWindow popup=new PopupWindow(box,dp(230),WindowManager.LayoutParams.WRAP_CONTENT,true);
        popup.setBackgroundDrawable(round(SURFACE,LINE,18));
        popup.setOutsideTouchable(true);
        popup.setElevation(dp(8));
        speed.setOnClickListener(v->{popup.dismiss();showPlaybackSpeedDialog();});
        settings.setOnClickListener(v->{popup.dismiss();showPlayerSettings();});
        popup.setOnDismissListener(() -> {});
        View root = getWindow().getDecorView();
        int[] location = new int[2];
        anchor.getLocationOnScreen(location);
        popup.setWidth(dp(230));
        popup.setHeight(WindowManager.LayoutParams.WRAP_CONTENT);
        box.measure(View.MeasureSpec.makeMeasureSpec(dp(230), View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED));
        int popupHeight = box.getMeasuredHeight();
        android.util.DisplayMetrics metrics = getResources().getDisplayMetrics();
        int screenWidth = metrics.widthPixels;
        int screenHeight = metrics.heightPixels;
        int x = Math.max(dp(8), Math.min(screenWidth - dp(230) - dp(8),
                location[0] + anchor.getWidth() - dp(230)));
        int yAbove = location[1] - popupHeight - dp(8);
        int yBelow = location[1] + anchor.getHeight() + dp(8);
        int y = yAbove >= dp(8) ? yAbove : Math.min(yBelow, screenHeight - popupHeight - dp(8));
        popup.showAtLocation(root, Gravity.TOP | Gravity.START, x, Math.max(dp(8), y));
    }

    private void showPlaybackSpeedDialog(){
        final String[] values={"0.5x","0.75x","1.0x","1.25x","1.5x","2.0x"};
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Playback speed").setItems(values,(d,which)->{
            float speed=new float[]{0.5f,0.75f,1f,1.25f,1.5f,2f}[which];
            if(webMode&&webPageReady)applyWebPlaybackSpeed(speed);else if(player!=null){player.setPlaybackSpeed(speed);sendSync(true,"",null,"speed",true);}
        }).setNegativeButton("Cancel",null).create();
        styleDialog(dialog); dialog.show(); finishDialogStyle(dialog);
    }

    private void applyWebPlaybackSpeed(float speed){
        if(!webMode||webView==null||!webPageReady)return;
        final float s=Math.max(0.25f,Math.min(2f,speed));
        webView.post(()->{
            if(webView==null||!webPageReady)return;
            String js="javascript:(function(){if(window.coviewSetSpeed)return window.coviewSetSpeed("+s+");return false})()";
            webView.evaluateJavascript(js,null);
        });
    }

    private void showLinkDialog(){
        LinearLayout box=col(); box.setPadding(dp(18),dp(8),dp(18),0);
        EditText input=input("YouTube or direct video URL");
        if(!pendingSharedUrl.isEmpty()) input.setText(pendingSharedUrl); input.setSingleLine(true); input.setImeOptions(EditorInfo.IME_ACTION_DONE); add(box,input,-1,dp(54),0,8);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Watch From Link").setView(box).setNegativeButton("Cancel",null).setPositiveButton("Open Together",null).create();
        styleDialog(dialog);
        dialog.setOnCancelListener(d -> pendingSharedUrl="");
        dialog.setOnShowListener(v->{
            finishDialogStyle(dialog);
            Button positive=dialog.getButton(AlertDialog.BUTTON_POSITIVE), negative=dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if(positive!=null){
                positive.setTextColor(PRIMARY);
                positive.setOnClickListener(x->{
                    String url=input.getText().toString().trim();
                    if(!isSupportedLink(url)){toast("Please enter a valid HTTPS video URL.");return;}
                    dialog.dismiss(); pendingSharedUrl=""; loadOnlineSource(url,true);
                });
            }
            if(negative!=null){
                negative.setTextColor(MUTED);
                negative.setOnClickListener(x->{pendingSharedUrl="";dialog.dismiss();});
            }
        });
        dialog.show();
    }
    private boolean isSupportedLink(String url){
        if(url==null||url.length()>4096)return false;
        try{Uri u=Uri.parse(url.trim());String scheme=u.getScheme();String host=u.getHost();return "https".equalsIgnoreCase(scheme) && host!=null && !host.trim().isEmpty();}
        catch(Exception e){return false;}
    }
    private void loadOnlineSource(String url,boolean announce){
        if(url==null||url.trim().isEmpty()||roomPlayerBox==null)return;
        final String requested=url.trim();
        if(!isSupportedLink(requested)){toast("Please enter a valid HTTPS video URL.");return;}
        if(announce) beginNewMediaSession();
        currentMediaType="web";
        hideMediaChooser();
        if(player==null){
            if(playerView!=null)playerView.setVisibility(View.VISIBLE);
            initPlayer();
        }
        if(player==null)return;
        webSessionGeneration++;
        webPageReady=false; webApplyingRemote=false; initialStateWaiting=false; pendingWebStateRequestId=""; lastWebBuffering=false;
        if(!isYouTubeUrl(requested)){
            prepareForOnlineMedia();
            onlineMode=true; webMode=false; onlineUrl=requested; onlineReady=true;
            videoUri=null; subtitleUri=null; prefs.edit().remove("local_video_uri").remove("subtitle_uri").remove("local_video_context").apply(); customSubtitleCues.clear(); cancelCustomSubtitleTicker(); customSubtitleLoading=false;
            if(adjustedSubtitleView!=null){adjustedSubtitleView.setVisibility(View.GONE);adjustedSubtitleView.setCues(Collections.emptyList());}
            if(webView!=null)webView.setVisibility(View.GONE);
            if(webLoadingOverlay!=null)webLoadingOverlay.setVisibility(View.GONE);
            if(playerView!=null)playerView.setVisibility(View.VISIBLE);
            setPlayerEmptyVisible(false);
            MediaItem item;
            try{
                MediaItem.Builder b=new MediaItem.Builder().setUri(Uri.parse(requested));
                String lower=requested.toLowerCase(Locale.ROOT);
                if(lower.contains(".m3u8"))b.setMimeType(MimeTypes.APPLICATION_M3U8);
                else if(lower.contains(".mpd"))b.setMimeType(MimeTypes.APPLICATION_MPD);
                item=b.build();
                applyingRemote=true; player.setMediaItem(item); player.prepare(); applyingRemote=false;
            }catch(Exception e){applyingRemote=false;toast("Could not play this public video link.");return;}
            if(mediaLabel!=null)mediaLabel.setText("✓  "+displayUrl(requested)+"  •  online media");
            setLocalPlaybackStatus("Paused");
            if(announce)try{startService(serviceIntent(SyncService.ACTION_MEDIA_SOURCE).putExtra("source",requested).putExtra("type","web").putExtra("mediaSessionId",currentMediaSessionId).putExtra("mediaRevision",mediaRevision).putExtra("mediaRevisionOwner",mediaRevisionOwner));}catch(Exception ignored){}
            initialStateWaiting=true; sendStateRequest();
            return;
        }
        if(player!=null){try{player.stop();player.clearMediaItems();}catch(Exception ignored){}}
        if(webView==null){setupWebView();createWebLoadingOverlay();}
        webMode=true; onlineMode=true; onlineUrl=requested; webUrl=requested; onlineReady=false;
        webLoadAttempt=0;
        webBridgeEnabled=true;
        videoUri=null; subtitleUri=null; prefs.edit().remove("local_video_uri").remove("subtitle_uri").remove("local_video_context").apply(); customSubtitleCues.clear(); cancelCustomSubtitleTicker(); customSubtitleLoading=false;
        if(adjustedSubtitleView!=null){adjustedSubtitleView.setVisibility(View.GONE);adjustedSubtitleView.setCues(Collections.emptyList());}
        setPlayerEmptyVisible(false);
        if(playerView!=null)playerView.setVisibility(View.GONE);
        if(webView!=null){webView.setVisibility(View.VISIBLE);webView.bringToFront();if(webLoadingOverlay!=null)webLoadingOverlay.bringToFront();}
        setLocalPlaybackStatus("Online");
        showWebLoading("Preparing YouTube…","Sending the link to everyone in the room");
        if(mediaLabel!=null)mediaLabel.setText("Preparing YouTube…");
        if(announce)try{startService(serviceIntent(SyncService.ACTION_MEDIA_SOURCE).putExtra("source",requested).putExtra("type","web").putExtra("mediaSessionId",currentMediaSessionId).putExtra("mediaRevision",mediaRevision).putExtra("mediaRevisionOwner",mediaRevisionOwner));}catch(Exception ignored){}
        loadWebUrl(requested);
    }
    private void createWebLoadingOverlay(){
        if(roomPlayerBox==null)return;
        FrameLayout overlay=new FrameLayout(this); overlay.setBackgroundColor(Color.argb(242,0,0,0));
        LinearLayout box=col(); box.setGravity(Gravity.CENTER); box.setPadding(dp(24),dp(18),dp(24),dp(18));
        ProgressBar spinner=new ProgressBar(this); spinner.setIndeterminate(true); add(box,spinner,dp(54),dp(54),0,16);
        webLoadingTitle=title("Preparing YouTube…",18); webLoadingTitle.setGravity(Gravity.CENTER); add(box,webLoadingTitle,-1,dp(34),0,4);
        webLoadingDetail=text("Connecting to the YouTube player",12,Color.LTGRAY); webLoadingDetail.setGravity(Gravity.CENTER); add(box,webLoadingDetail,-1,dp(48),0,0);
        overlay.addView(box,new FrameLayout.LayoutParams(-1,-2,Gravity.CENTER));
        roomPlayerBox.addView(overlay,new FrameLayout.LayoutParams(-1,-1)); webLoadingOverlay=overlay; overlay.setVisibility(View.GONE);
    }
    private void showWebLoading(String title,String detail){
        runOnUiThread(()->{if(webLoadingOverlay!=null){webLoadingOverlay.setVisibility(View.VISIBLE);if(webLoadingTitle!=null)webLoadingTitle.setText(title);if(webLoadingDetail!=null)webLoadingDetail.setText(detail);}});
    }
    private void hideWebLoading(){runOnUiThread(()->{if(webLoadingOverlay!=null)webLoadingOverlay.setVisibility(View.GONE);});}
    private void loadWebUrl(String url){
        if(webView==null)return;
        webPageReady=false; lastWebPositionMs=-1; lastWebPlaying=false; webObservedInitial=false; webCommandSequence=0;
        showWebLoading("Loading YouTube…","Opening the video player");
        webBridgeNonce=UUID.randomUUID().toString().replace("-","");
        String id=youtubeId(url);
        if(id.isEmpty()){
            hideWebLoading(); if(mediaLabel!=null)mediaLabel.setText("Unsupported web source");
            toast("Only YouTube links are supported in the web player.");
            return;
        }
        try{
            java.io.InputStream in=getAssets().open("youtube_player.html");
            java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream(); byte[] buf=new byte[4096]; int n;
            while((n=in.read(buf))>0)out.write(buf,0,n); in.close();
            String html=new String(out.toByteArray(),StandardCharsets.UTF_8)
                    .replace("__VIDEO_ID__",escapeJsString(id))
                    .replace("__BRIDGE_NONCE__",escapeJsString(webBridgeNonce));
            if(mediaLabel!=null)mediaLabel.setText("Loading • YouTube");
            webView.loadDataWithBaseURL("https://coview.local/",html,"text/html","UTF-8",null);
            webLoadAttempt++;
            final int attempt=webLoadAttempt; final long generation=webSessionGeneration;
            if(webLoadTimeoutRunnable!=null)uiHandler.removeCallbacks(webLoadTimeoutRunnable);
            webLoadTimeoutRunnable=()->{
                if(generation!=webSessionGeneration||attempt!=webLoadAttempt||webPageReady)return;
                webLoadTimeoutRunnable=null;
                if(webLoadAttempt<2){
                    showWebLoading("Retrying YouTube…","The first connection timed out");
                    uiHandler.postDelayed(()->{
                        if(generation==webSessionGeneration&&!webPageReady)loadWebUrl(url);
                    },250);
                }else{
                    hideWebLoading();
                    if(mediaLabel!=null)mediaLabel.setText("YouTube timed out");
                    toast("YouTube took too long to load.");
                }
            };
            uiHandler.postDelayed(webLoadTimeoutRunnable,15000);
        }catch(Exception e){
            hideWebLoading(); if(mediaLabel!=null)mediaLabel.setText("Could not open YouTube"); toast("Could not open the YouTube player.");
        }
    }
    
    private String displayUrl(String url){try{Uri u=Uri.parse(url);return u.getHost()==null?url:u.getHost();}catch(Exception e){return url;}}
    private String youtubeId(String url){
        try{
            Uri u=Uri.parse(url);
            String h=u.getHost()==null?"":u.getHost().toLowerCase(Locale.ROOT);
            boolean youtubeHost=h.equals("youtu.be")||h.equals("youtube.com")||h.endsWith(".youtube.com")||h.equals("youtube-nocookie.com")||h.endsWith(".youtube-nocookie.com");
            if(!youtubeHost)return "";
            String id="";
            if(h.equals("youtu.be")) id=u.getPath()==null?"":u.getPath().replace("/","").trim();
            else {
                String v=u.getQueryParameter("v");
                if(v!=null&&!v.isEmpty()) id=v.trim();
                if(id.isEmpty()){
                    String path=u.getPath()==null?"":u.getPath();
                    int at=path.indexOf("/shorts/");
                    if(at>=0)id=path.substring(at+8).split("[/#?]",2)[0];
                    at=path.indexOf("/embed/");
                    if(at>=0&&id.isEmpty())id=path.substring(at+7).split("[/#?]",2)[0];
                }
            }
            return id.matches("[A-Za-z0-9_-]{11}")?id:"";
        }catch(Exception ignored){return "";}
    }
    private String escapeJsString(String value){
        if(value==null)return "";
        StringBuilder b=new StringBuilder(value.length()+8);
        for(int i=0;i<value.length();i++){
            char c=value.charAt(i);
            switch(c){
                case '\\': b.append("\\\\"); break;
                case '\'': b.append("\\'"); break;
                case '"': b.append("\\\""); break;
                case '\n': b.append("\\n"); break;
                case '\r': b.append("\\r"); break;
                case '\t': b.append("\\t"); break;
                case '\u2028': b.append("\\u2028"); break;
                case '\u2029': b.append("\\u2029"); break;
                default: b.append(c);
            }
        }
        return b.toString();
    }
    private boolean isYouTubeUrl(String url){return !youtubeId(url).isEmpty();}
    private boolean isNativeOnlineMediaUrl(String url){
        try{
            Uri u=Uri.parse(url);String path=u.getPath()==null?"":u.getPath().toLowerCase(Locale.ROOT);
            String q=url.toLowerCase(Locale.ROOT);
            return path.endsWith(".mp4")||path.endsWith(".webm")||path.endsWith(".mkv")||path.endsWith(".mov")||
                    path.endsWith(".m4v")||path.endsWith(".m3u8")||path.endsWith(".mpd")||q.contains(".m3u8?")||q.contains(".mpd?");
        }catch(Exception e){return false;}
    }

    private void setupWebView(){
        if(webView!=null)return;
        webView=new WebView(this);
        webView.setVisibility(View.GONE);
        webView.setBackgroundColor(Color.BLACK);
        webView.setLayerType(View.LAYER_TYPE_HARDWARE,null);
        WebSettings ws=webView.getSettings();
        ws.setJavaScriptEnabled(true);
        ws.setDomStorageEnabled(true);
        ws.setDatabaseEnabled(true);
         ws.setAllowFileAccess(false); ws.setAllowContentAccess(false);
        ws.setMediaPlaybackRequiresUserGesture(false);
        ws.setSupportZoom(false);
        ws.setBuiltInZoomControls(false);
        ws.setDisplayZoomControls(false);
        ws.setLoadWithOverviewMode(false);
        ws.setUseWideViewPort(false);
        ws.setJavaScriptCanOpenWindowsAutomatically(false);
        ws.setSupportMultipleWindows(false);
        CookieManager.getInstance().setAcceptCookie(true);
        if(Build.VERSION.SDK_INT>=21)CookieManager.getInstance().setAcceptThirdPartyCookies(webView,false);
        webView.setWebViewClient(new WebViewClient(){
            private boolean isTrusted(String url){
                if(url==null)return false;
                try{
                    Uri u=Uri.parse(url);
                    String scheme=u.getScheme()==null?"":u.getScheme().toLowerCase(Locale.ROOT);
                    if("https".equals(scheme) && "coview.local".equalsIgnoreCase(u.getHost()))return true;
                    String host=u.getHost()==null?"":u.getHost().toLowerCase(Locale.ROOT);
                    boolean ytHost=host.equals("youtube.com")||host.endsWith(".youtube.com")||host.equals("youtube-nocookie.com")||host.endsWith(".youtube-nocookie.com")||host.equals("youtu.be");
                    if("https".equals(scheme)&&ytHost)return true;
                }catch(Exception ignored){}
                return false;
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v,String url){
                if(isTrusted(url))return false;
                try{Intent i=new Intent(Intent.ACTION_VIEW,Uri.parse(url));startActivity(i);}catch(Exception ignored){}
                return true;
            }
            @Override public boolean shouldOverrideUrlLoading(WebView v, android.webkit.WebResourceRequest request){
                return shouldOverrideUrlLoading(v, request==null?null:request.getUrl().toString());
            }
            @Override public void onPageStarted(WebView v,String url,android.graphics.Bitmap favicon){
                if(v!=webView)return;
                if(!isTrusted(url)){v.stopLoading();toast("This website is not supported.");return;}
                showWebLoading("Loading YouTube…","Starting the video player");
            }
            @Override public void onPageFinished(WebView v,String url){
                if(v!=webView)return;
                if(!isTrusted(url))return;
                webUrl=url;
                if(webBridgeEnabled){
                    injectWebController();
                    if(!pendingWebStateRequestId.isEmpty() && webView!=null) {
                        uiHandler.postDelayed(()->{
                            if(webMode&&webPageReady&&!pendingWebStateRequestId.isEmpty()&&webView!=null)
                                webView.evaluateJavascript("javascript:(function(){if(window.coviewState)window.coviewState();})()",null);
                        },120);
                    }
                }
            }
        });
        webView.setWebChromeClient(new WebChromeClient(){
            @Override public void onShowCustomView(View view, CustomViewCallback callback){ if(webView==null||webView.getVisibility()!=View.VISIBLE){if(callback!=null)try{callback.onCustomViewHidden();}catch(Exception ignored){}return;} enterYouTubeFullscreen(view,callback); }
            @Override public void onHideCustomView(){ if(youtubeFullscreen)exitYouTubeFullscreen(); }
        });
        webView.addJavascriptInterface(new WebBridge(),"CoView");
        roomPlayerBox.addView(webView,new FrameLayout.LayoutParams(-1,-1));
    }
    private void injectWebController(){
        if(webView==null||!webBridgeEnabled)return;
        String js="javascript:(function(){if(window.__coviewAndroidHook)return;window.__coviewAndroidHook=true;if(window.coviewSetReady)window.coviewSetReady();})();";
        webView.evaluateJavascript(js,null);
    }
    private class WebBridge{
        private boolean valid(String nonce){return webBridgeEnabled && nonce!=null && nonce.equals(webBridgeNonce);}
        @JavascriptInterface public void ready(String nonce){
            if(!valid(nonce))return;
            runOnUiThread(()->{
                webPageReady=true; onlineReady=true;
                if(webLoadTimeoutRunnable!=null){uiHandler.removeCallbacks(webLoadTimeoutRunnable);webLoadTimeoutRunnable=null;}
                hideWebLoading();
                if(mediaLabel!=null)mediaLabel.setText("YouTube • Ready");
                if(pendingWebSync!=null){JSONObject queued=pendingWebSync;pendingWebSync=null;applyWebSync(queued);}
                if(initialStateWaiting){uiHandler.postDelayed(()->{if(webMode&&webPageReady)sendStateRequest();},250);}
            });
        }
        @JavascriptInterface public void error(String nonce,String message){
            if(!valid(nonce))return;
            runOnUiThread(()->{hideWebLoading();lastWebPlaying=false;lastWebBuffering=false;setLocalPlaybackStatus("Error");if(mediaLabel!=null)mediaLabel.setText("YouTube could not play this video");if(webMode&&webPageReady)sendWebSync(true,lastWebPositionMs,false,false,false,"error",1f);toast(message==null||message.isEmpty()?"YouTube could not play this video.":message);});
        }
        @JavascriptInterface public void state(String nonce,String json){
            if(!valid(nonce))return;
            try{
                JSONObject d=new JSONObject(json);String kind=d.optString("kind","heartbeat");
                boolean playing=d.optBoolean("playing",false);boolean buffering=d.optBoolean("buffering",false);long pos=Math.max(0,(long)(d.optDouble("pos",0)*1000));
                float speed=Math.max(0.25f,Math.min(2f,(float)d.optDouble("speed",1.0)));
                boolean remote=d.optBoolean("remote",false);if(remote||webApplyingRemote)return;
                lastWebPlaying=playing;lastWebBuffering=buffering;lastWebPositionMs=pos;webObservedInitial=true;
                boolean request=!pendingWebStateRequestId.isEmpty();
                if(request || "heartbeat".equals(kind)||"play".equals(kind)||"pause".equals(kind)||"seek".equals(kind)||"speed".equals(kind)||"ended".equals(kind)){
                    boolean command=!"heartbeat".equals(kind)&&!"buffering".equals(kind);
                    sendWebSync(command||request,pos,playing,buffering,request,kind,speed);
                    if(request)pendingWebStateRequestId="";
                    setLocalPlaybackStatus(buffering?"Buffering":(playing?"Playing":"Paused"));
                }
            }catch(Exception ignored){}
        }
    }
    private void advancePlaybackRevision(){ playbackRevision=Math.max(playbackRevision,lastAppliedPlaybackRevision)+1; playbackRevisionOwner=localPresenceKey==null?"":localPresenceKey; playbackStateId=UUID.randomUUID().toString(); }
    private void observePlaybackRevision(JSONObject d){
        long r=d.optLong("playbackRevision",0);
        String owner=d.optString("playbackRevisionOwner",d.optString("senderId",d.optString("user","")));
        String stateId=d.optString("playbackStateId","");
        long current=Math.max(playbackRevision,lastAppliedPlaybackRevision);
        if(r>current){ playbackRevision=r; playbackRevisionOwner=owner; playbackStateId=stateId; return; }
        if(r<current)return;
        int ownerCmp=safe(owner).compareTo(safe(playbackRevisionOwner));
        if(ownerCmp>0 || (ownerCmp==0 && !stateId.isEmpty() && stateId.compareTo(safe(playbackStateId))>0)){ playbackRevision=r; playbackRevisionOwner=owner; playbackStateId=stateId; }
    }
    private boolean isNewerPlaybackPacket(JSONObject d){
        long rev=d.optLong("playbackRevision",0);
        String owner=d.optString("playbackRevisionOwner",d.optString("senderId",d.optString("user","")));
        String stateId=d.optString("playbackStateId","");
        String sender=d.optString("senderId",d.optString("user",""));
        long seq=d.optLong("seq",-1);
        if(rev>lastAppliedPlaybackRevision)return true;
        if(rev<lastAppliedPlaybackRevision)return false;
        owner=owner==null?"":owner;
        if(lastAppliedPlaybackOwner==null||lastAppliedPlaybackOwner.isEmpty())return true;
        int cmp=owner.compareTo(lastAppliedPlaybackOwner);
        if(cmp!=0)return cmp>0;
        if(!stateId.isEmpty() && !lastAppliedPlaybackStateId.isEmpty() && !stateId.equals(lastAppliedPlaybackStateId))return stateId.compareTo(lastAppliedPlaybackStateId)>0;
        if(!stateId.equals(lastAppliedPlaybackStateId) && lastAppliedPlaybackStateId.isEmpty())return true;
        if(sender.isEmpty()||seq<0)return false;
        Long previous=lastSequenceByUser.get(sender+"|"+d.optString("mediaSessionId",""));
        return previous==null||seq>previous;
    }
    private boolean isNewerPendingPlayback(JSONObject pending,JSONObject candidate){
        if(pending==null)return true;
        long pr=pending.optLong("playbackRevision",0),cr=candidate.optLong("playbackRevision",0); if(cr!=pr)return cr>pr;
        String po=pending.optString("playbackRevisionOwner",pending.optString("senderId",pending.optString("user","")));
        String co=candidate.optString("playbackRevisionOwner",candidate.optString("senderId",candidate.optString("user","")));
        int c=co.compareTo(po); if(c!=0)return c>0;
        String ps=pending.optString("playbackStateId",""); String cs=candidate.optString("playbackStateId",""); if(!cs.equals(ps))return cs.compareTo(ps)>0;
        String pSender=pending.optString("senderId",pending.optString("user","")); String cSender=candidate.optString("senderId",candidate.optString("user","")); return cSender.compareTo(pSender)>0;
    }
    private void finishStateRequest(String requestId){
        if(requestId==null||requestId.isEmpty())return;
        if(requestId.equals(activeStateRequestId)){
            initialStateWaiting=false;
            mediaStateRequestDeadline=System.currentTimeMillis()+900;
            if(stateRequestTimeoutRunnable!=null)uiHandler.removeCallbacks(stateRequestTimeoutRunnable);
            stateRequestTimeoutRunnable=()->{if(requestId.equals(activeStateRequestId)&&System.currentTimeMillis()>=mediaStateRequestDeadline){activeStateRequestId="";mediaStateRequestDeadline=0;pendingNativeStateRequestId="";pendingNativeStateRequestTarget="";pendingWebStateRequestId="";}};
            uiHandler.postDelayed(stateRequestTimeoutRunnable,950);
        }
    }
    private void scheduleStateRequestTimeout(final String requestId){
        if(stateRequestTimeoutRunnable!=null)uiHandler.removeCallbacks(stateRequestTimeoutRunnable);
        stateRequestTimeoutRunnable=()->{if(requestId.equals(activeStateRequestId)){initialStateWaiting=false;activeStateRequestId="";mediaStateRequestDeadline=0;pendingNativeStateRequestId="";pendingNativeStateRequestTarget="";pendingWebStateRequestId="";}};
        uiHandler.postDelayed(stateRequestTimeoutRunnable,5000);
    }
    private void sendWebSync(boolean force,long positionMs,boolean playing,boolean buffering,boolean request,String kind,float speed){
        if(!webMode||!webPageReady||currentMediaSessionId.isEmpty())return; long now=SystemClock.uptimeMillis(); if(!force&&!request&&now-lastWebSyncSentAt<1800)return; lastWebSyncSentAt=now;
        String k=kind==null?"heartbeat":kind; boolean advance=!request&&!buffering&&(k.equals("play")||k.equals("pause")||k.equals("seek")||k.equals("speed")||k.equals("ended")||k.equals("error")); if(advance)advancePlaybackRevision();
        Intent i=serviceIntent(SyncService.ACTION_WEB_SYNC).putExtra("position",positionMs).putExtra("playing",playing).putExtra("buffering",buffering).putExtra("state",k.equals("error")?"Error":(buffering?"Buffering":(playing?"Playing":"Paused"))).putExtra("kind",k).putExtra("speed",speed).putExtra("commandSeq",++webCommandSequence).putExtra("mediaSessionId",currentMediaSessionId).putExtra("mediaRevision",mediaRevision).putExtra("mediaRevisionOwner",mediaRevisionOwner).putExtra("playbackRevision",playbackRevision).putExtra("playbackRevisionOwner",playbackRevisionOwner).putExtra("playbackStateId",playbackStateId).putExtra("sentAt",System.currentTimeMillis());
        if(k.equals("play"))i.putExtra("desiredPlaying",true); else if(k.equals("pause")||k.equals("ended")||k.equals("error"))i.putExtra("desiredPlaying",false); if(request)i.putExtra("requestId",activeStateRequestId); startService(i);
    }
    private JSONObject copyJsonObject(JSONObject source){
        if(source==null)return null;
        try{return new JSONObject(source.toString());}
        catch(JSONException ignored){return source;}
    }
    private boolean applyWebSync(JSONObject d){
        if(!webMode||webView==null)return false; String sender=d.optString("senderId",""); if(!sender.isEmpty()&&sender.equals(localPresenceKey))return false;
        String session=d.optString("mediaSessionId",""); String type=d.optString("mediaType",""); String targetKey=d.optString("targetKey",""); if(!targetKey.isEmpty()&&!targetKey.equals(localPresenceKey))return false; if(!"web".equalsIgnoreCase(type)||session.isEmpty())return false;
        long revision=d.optLong("mediaRevision",0); String owner=d.optString("mediaRevisionOwner",sender); String requestId=d.optString("requestId","");
        if(currentMediaSessionId.isEmpty()||!session.equals(currentMediaSessionId))return false; if(revision!=currentMediaRevision||(!currentMediaRevisionOwner.isEmpty()&&!owner.equals(currentMediaRevisionOwner)))return false;
        if(!requestId.isEmpty()&&(!requestId.equals(activeStateRequestId)||(mediaStateRequestDeadline>0&&System.currentTimeMillis()>mediaStateRequestDeadline)))return false; if(!isNewerPlaybackPacket(d))return false;
        long seq=d.optLong("seq",-1), commandSeq=d.optLong("commandSeq",-1); String cmdKey=sender+"|"+session+"|web"; if(!sender.isEmpty()&&commandSeq>=0){Long prev=lastSequenceByUser.get(cmdKey);if(prev!=null&&commandSeq<=prev)return false;}
        String eventId=d.optString("eventId",""); if(!eventId.isEmpty()&&seenEventIds.contains(eventId))return false; boolean playing=d.optBoolean("playing",false),buffering=d.optBoolean("buffering",false); String kind=d.optString("kind","heartbeat"); float speed=Math.max(0.25f,Math.min(2f,(float)d.optDouble("speed",1.0))); long sent=d.optLong("sentAt",0),pos=d.optLong("position",0); if(playing&&sent>0)pos+=Math.max(0,Math.min(2000,System.currentTimeMillis()-sent))*speed;
        final long target=Math.max(0,pos); final boolean hasDesired=d.has("desiredPlaying"); final boolean desired=hasDesired&&d.optBoolean("desiredPlaying",playing); final String state=d.optString("state",buffering?"Buffering":(playing?"Playing":"Paused"));
        if(!webPageReady){if(isNewerPendingPlayback(pendingWebSync,d))pendingWebSync=copyJsonObject(d);if(!requestId.isEmpty()){pendingWebStateRequestId=requestId;mediaStateRequestDeadline=System.currentTimeMillis()+5000;scheduleStateRequestTimeout(requestId);}return true;}
        observePlaybackRevision(d); final long generation=webSessionGeneration; final String expectedSession=session;
        webView.post(()->{if(!webMode||webView==null||!webPageReady||generation!=webSessionGeneration||!expectedSession.equals(currentMediaSessionId))return;webApplyingRemote=true;try{String safeKind=JSONObject.quote(kind);String js="javascript:(function(){if(window.coviewApply)return window.coviewApply("+(target/1000.0)+","+(hasDesired?desired:playing)+","+safeKind+","+speed+","+(buffering?"true":"false")+");return false})()";webView.evaluateJavascript(js,v->webApplyingRemote=false);}catch(Exception ignored){webApplyingRemote=false;}lastWebPlaying=playing;lastWebBuffering=buffering;lastWebPositionMs=target;if(!eventId.isEmpty()){seenEventIds.add(eventId);if(seenEventIds.size()>1024)seenEventIds.remove(seenEventIds.iterator().next());}if(!sender.isEmpty()&&seq>=0)lastSequenceByUser.put(sender+"|"+session,seq);if(!sender.isEmpty()&&commandSeq>=0)lastSequenceByUser.put(cmdKey,commandSeq);lastAppliedPlaybackRevision=d.optLong("playbackRevision",0);lastAppliedPlaybackOwner=d.optString("playbackRevisionOwner",sender);lastAppliedPlaybackStateId=d.optString("playbackStateId","");lastAppliedPlaybackSeq=seq;setLocalPlaybackStatus("Error".equalsIgnoreCase(state)?"Error":(buffering?"Buffering":(playing?"Playing":"Paused")));if(!requestId.isEmpty())finishStateRequest(requestId);});
        return true;
    }
    private boolean isNewerMediaRevision(long revision,String owner,String session){owner=owner==null?"":owner;session=session==null?"":session;if(revision>currentMediaRevision)return true;if(revision<currentMediaRevision)return false;String cur=currentMediaRevisionOwner==null?"":currentMediaRevisionOwner;int c=owner.compareTo(cur);if(c!=0)return c>0;return session.compareTo(currentMediaSessionId)>0;}
    private void applyMediaSource(JSONObject d){
        String source=d.optString("source","").trim();String type=d.optString("type","local");String sender=d.optString("user",d.optString("senderId",""));if(!sender.isEmpty()&&sender.equals(localPresenceKey))return;if(!"web".equalsIgnoreCase(type)||source.isEmpty())return;String session=d.optString("mediaSessionId","");String owner=d.optString("mediaRevisionOwner",sender);long revision=d.optLong("mediaRevision",0);String target=d.optString("targetKey","");if(!target.isEmpty()&&!target.equals(localPresenceKey))return;if(session.isEmpty()||retiredMediaSessionIds.contains(session))return;
        final boolean stateResponse="media_source_state".equals(d.optString("_event",""));
        boolean sameCurrentIdentity=session.equals(currentMediaSessionId)&&revision==currentMediaRevision&&owner.equals(currentMediaRevisionOwner);
        boolean recoveringMissingSource=stateResponse&&sameCurrentIdentity&&(onlineUrl==null||onlineUrl.isEmpty());
        if(!recoveringMissingSource&&!isNewerMediaRevision(revision,owner,session))return;if(stateResponse){if(!isNewerPendingMediaSource(pendingMediaSourceState,d))return;pendingMediaSourceState=copyJsonObject(d);if(pendingMediaSourceApplyRunnable!=null)uiHandler.removeCallbacks(pendingMediaSourceApplyRunnable);pendingMediaSourceApplyRunnable=()->{JSONObject chosen=pendingMediaSourceState;pendingMediaSourceState=null;pendingMediaSourceApplyRunnable=null;if(chosen==null)return;chosen.remove("_event");applyMediaSource(chosen);};uiHandler.postDelayed(pendingMediaSourceApplyRunnable,220);return;}
        if(pendingMediaSourceApplyRunnable!=null){uiHandler.removeCallbacks(pendingMediaSourceApplyRunnable);pendingMediaSourceApplyRunnable=null;pendingMediaSourceState=null;}if(!currentMediaSessionId.isEmpty()&&!currentMediaSessionId.equals(session)){retiredMediaSessionIds.add(currentMediaSessionId);if(retiredMediaSessionIds.size()>32)retiredMediaSessionIds.remove(retiredMediaSessionIds.iterator().next());}
        currentMediaSessionId=session;currentMediaRevision=revision;currentMediaRevisionOwner=owner;currentMediaType="web";playbackRevision=0;playbackRevisionOwner="";playbackStateId="";lastAppliedPlaybackRevision=0;lastAppliedPlaybackOwner="";lastAppliedPlaybackStateId="";lastAppliedPlaybackSeq=-1;pendingNativeSync=null;pendingWebSync=null;if(source.equals(onlineUrl)&&onlineMode)return;
        runOnUiThread(()->{pendingRemoteWebSource=source;if(player==null||roomPlayerBox==null)return;loadOnlineSource(source,false);pendingRemoteWebSource="";initialStateWaiting=true;activeStateRequestId="";pendingWebStateRequestId="";uiHandler.postDelayed(()->{if(webMode&&webPageReady)sendStateRequest();},180);});
    }
    private boolean isNewerPendingMediaSource(JSONObject pending,JSONObject candidate){if(pending==null)return true;long pr=pending.optLong("mediaRevision",0),cr=candidate.optLong("mediaRevision",0);if(cr!=pr)return cr>pr;String po=pending.optString("mediaRevisionOwner",pending.optString("user",""));String co=candidate.optString("mediaRevisionOwner",candidate.optString("user",""));int c=co.compareTo(po);if(c!=0)return c>0;return candidate.optString("mediaSessionId","").compareTo(pending.optString("mediaSessionId",""))>0;}

    private void pickVideo(){
        Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"video/*","video/x-matroska","video/mp4","video/webm","video/quicktime","video/x-msvideo","video/3gpp","application/octet-stream"});
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);
        startActivityForResult(i,PICK_VIDEO);
    }
    @Override protected void onActivityResult(int req,int result,@Nullable Intent data){
        super.onActivityResult(req,result,data);
        if(req==PICK_VIDEO&&result==Activity.RESULT_OK&&data!=null&&data.getData()!=null){
            Uri uri=data.getData();
            try{getContentResolver().takePersistableUriPermission(uri,data.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
            if(player!=null){
                prepareForLocalMedia();
                remoteGuardUntil=SystemClock.uptimeMillis()+1200;
                onlineMode=false; webMode=false; webPageReady=false; currentMediaType="local";
                if(playerView!=null)playerView.setVisibility(View.VISIBLE); if(adjustedSubtitleView!=null){adjustedSubtitleView.setVisibility(View.GONE);adjustedSubtitleView.setCues(Collections.emptyList());}
                videoUri=uri;
                prefs.edit().putString("local_video_uri",uri.toString()).putString("local_video_context",serverUrl+"|"+roomName).apply();
                subtitleUri=null;
                setPlayerEmptyVisible(false);
                hideMediaChooser();
                player.setMediaItem(buildMediaItem());
                player.prepare();
                String name=displayName(uri);
                mediaLabel.setText("✓  "+name+"  •  local file");
                setLocalPlaybackStatus("Paused");
                initialStateWaiting=true;
                beginNewMediaSession();
                Intent src=serviceIntent(SyncService.ACTION_MEDIA_SOURCE)
                        .putExtra("source","")
                        .putExtra("type","local")
                        .putExtra("mediaSessionId",currentMediaSessionId).putExtra("mediaRevision",mediaRevision).putExtra("mediaRevisionOwner",mediaRevisionOwner);
                startService(src);
                // Local files are device-local and are not advertised as a shared room source.
            }
        } else if(req==PICK_PFP&&result==Activity.RESULT_OK&&data!=null&&data.getData()!=null){
            Uri uri=data.getData();try{getContentResolver().takePersistableUriPermission(uri,data.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
            try(java.io.InputStream in=getContentResolver().openInputStream(uri)){ if(in==null)throw new IllegalStateException("Unable to open image"); byte[] raw=readLimited(in,6*1024*1024); BitmapFactory.Options o=new BitmapFactory.Options();o.inJustDecodeBounds=true;BitmapFactory.decodeByteArray(raw,0,raw.length,o);o.inSampleSize=sampleSize(o.outWidth,o.outHeight,512,512);o.inJustDecodeBounds=false;Bitmap b=BitmapFactory.decodeByteArray(raw,0,raw.length,o);String encoded=bitmapToData(b);if(b!=null&&!b.isRecycled())b.recycle();if(!encoded.isEmpty()){profilePicture=encoded;prefs.edit().putString("custom_pfp",encoded).apply();if(!localPresenceKey.isEmpty())participantPfps.put(localPresenceKey,profilePicture);sendPresenceUpdate();if(player==null)showConnect();else refreshParticipants();}}catch(Exception ignored){}
        } else if(req==PICK_SUBTITLE&&result==Activity.RESULT_OK&&data!=null&&data.getData()!=null){
            Uri uri=data.getData();
            try{getContentResolver().takePersistableUriPermission(uri,data.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
            subtitleUri=uri;
            prefs.edit().putString("subtitle_uri",uri.toString()).apply();
            applyCustomSubtitleFile();
        }
    }

    private static final class SubtitleCueData{
        final long startMs;
        final long endMs;
        final CharSequence text;
        SubtitleCueData(long startMs,long endMs,CharSequence text){
            this.startMs=startMs;this.endMs=endMs;this.text=text;
        }
    }

    private String formatPeopleWatching(int count){return "● "+count+(count==1?" person":" people")+" watching";}

    private void setLocalPlaybackStatus(String status){
        if(status==null||status.isEmpty())status="Online";
        boolean changed=!status.equals(localStatus); localStatus=status;
        if(!localPresenceKey.isEmpty())participantStatuses.put(localPresenceKey,status);
        if(localRoomStatus!=null){localRoomStatus.setText("●  "+status);localRoomStatus.setTextColor("Playing".equals(status)?SUCCESS:("Paused".equals(status)?MUTED:("Buffering".equals(status)?PRIMARY:("Error".equals(status)?Color.RED:MUTED))));}
        if(localRoomAvatar!=null){Bitmap b=loadPfpBitmap(profilePicture);if(b!=null)localRoomAvatar.setImageBitmap(b);}
        if(changed&&!localPresenceKey.isEmpty())sendPresenceUpdate();
        runOnUiThread(MainActivity.this::refreshParticipants);
    }
    private void beginNewMediaSession(){
        if(!currentMediaSessionId.isEmpty()){retiredMediaSessionIds.add(currentMediaSessionId);if(retiredMediaSessionIds.size()>32)retiredMediaSessionIds.remove(retiredMediaSessionIds.iterator().next());}
        currentMediaSessionId=UUID.randomUUID().toString(); mediaRevision=Math.max(mediaRevision,currentMediaRevision)+1; mediaRevisionOwner=localPresenceKey; currentMediaRevision=mediaRevision; currentMediaRevisionOwner=localPresenceKey;
        playbackRevision=0; playbackRevisionOwner=""; playbackStateId=""; lastAppliedPlaybackRevision=0; lastAppliedPlaybackOwner=""; lastAppliedPlaybackStateId=""; lastAppliedPlaybackSeq=-1; pendingNativeSync=null; pendingWebSync=null;
    }

    private String currentPresenceStatus(){
        if("Error".equals(localStatus))return "Error";
        if(webMode)return webPageReady?(lastWebBuffering?"Buffering":(lastWebPlaying?"Playing":"Paused")):"Online";
        if(player==null||player.getMediaItemCount()==0)return "Online";
        int st=player.getPlaybackState(); if(st==Player.STATE_BUFFERING)return "Buffering"; if(st==Player.STATE_IDLE)return "Online"; return player.isPlaying()?"Playing":"Paused";
    }

    private void respondToStateRequest(String requestId,String requester,String requestedSession,long requestedRevision,String requestedOwner){
        if(requestId==null||requestId.isEmpty()||requester==null||requester.isEmpty()||currentMediaSessionId.isEmpty()||currentMediaType.isEmpty()||"local".equalsIgnoreCase(currentMediaType))return;
        if(requestedRevision>currentMediaRevision)return;
        if(requestedRevision==currentMediaRevision&&!requestedSession.isEmpty()&&!requestedSession.equals(currentMediaSessionId)){int c=currentMediaRevisionOwner.compareTo(requestedOwner==null?"":requestedOwner);if(c<0)return;if(c==0&&currentMediaSessionId.compareTo(requestedSession)<0)return;}
        if(webMode&&webPageReady){Intent i=serviceIntent(SyncService.ACTION_WEB_SYNC).putExtra("position",lastWebPositionMs).putExtra("playing",lastWebPlaying).putExtra("buffering",lastWebBuffering).putExtra("state",lastWebBuffering?"Buffering":(lastWebPlaying?"Playing":"Paused")).putExtra("speed",1f).putExtra("kind","heartbeat").putExtra("commandSeq",++webCommandSequence).putExtra("mediaSessionId",currentMediaSessionId).putExtra("mediaRevision",currentMediaRevision).putExtra("mediaRevisionOwner",currentMediaRevisionOwner).putExtra("playbackRevision",playbackRevision).putExtra("playbackRevisionOwner",playbackRevisionOwner).putExtra("requestId",requestId).putExtra("targetKey",requester).putExtra("sentAt",System.currentTimeMillis());startService(i);}
        else if(onlineMode&&player!=null&&player.getMediaItemCount()>0){if(player.getPlaybackState()!=Player.STATE_READY){pendingNativeStateRequestId=requestId;pendingNativeStateRequestTarget=requester;mediaStateRequestDeadline=System.currentTimeMillis()+5000;scheduleStateRequestTimeout(requestId);return;}sendSync(true,requestId,null,"heartbeat",false,requester);}
    }

    private void sendSync(){sendSync(false,"");}
    private void sendSync(boolean force){sendSync(force,"",null,"heartbeat",false,"");}
    private void sendSync(boolean force,String requestId){sendSync(force,requestId,null,"heartbeat",false,"");}
    private void sendSync(boolean force,String requestId,Boolean desiredPlaying,String kind,boolean advance){sendSync(force,requestId,desiredPlaying,kind,advance,"");}
    private void sendSync(boolean force,String requestId,Boolean desiredPlaying,String kind,boolean advance,String targetKey){
        if(player==null||applyingRemote||currentMediaSessionId.isEmpty()||!"web".equalsIgnoreCase(currentMediaType))return;
        long now=System.currentTimeMillis(); if(!force&&"heartbeat".equals(kind)&&now-lastLocalSync<120)return; lastLocalSync=now; if(advance)advancePlaybackRevision();
        boolean playing=player.isPlaying(), buffering=player.getPlaybackState()==Player.STATE_BUFFERING;
        Intent i=serviceIntent(SyncService.ACTION_SYNC).putExtra("position",player.getCurrentPosition()).putExtra("playing",playing).putExtra("buffering",buffering).putExtra("state",currentPresenceStatus()).putExtra("speed",player.getPlaybackParameters().speed).putExtra("kind",kind==null?"heartbeat":kind).putExtra("mediaSessionId",currentMediaSessionId).putExtra("mediaRevision",mediaRevision).putExtra("mediaRevisionOwner",mediaRevisionOwner).putExtra("playbackRevision",playbackRevision).putExtra("playbackRevisionOwner",playbackRevisionOwner).putExtra("playbackStateId",playbackStateId).putExtra("sentAt",System.currentTimeMillis());
        if(desiredPlaying!=null)i.putExtra("desiredPlaying",desiredPlaying); if(!requestId.isEmpty())i.putExtra("requestId",requestId); if(targetKey!=null&&!targetKey.isEmpty())i.putExtra("targetKey",targetKey); startService(i);
    }

    private void sendStateRequest(){
        initialStateWaiting=true; activeStateRequestId=UUID.randomUUID().toString(); mediaStateRequestDeadline=System.currentTimeMillis()+5000; scheduleStateRequestTimeout(activeStateRequestId);
        Intent i=serviceIntent(SyncService.ACTION_STATE_REQUEST).putExtra("requestId",activeStateRequestId).putExtra("mediaSessionId",currentMediaSessionId).putExtra("mediaRevision",mediaRevision).putExtra("mediaRevisionOwner",mediaRevisionOwner); startService(i);
    }

    private boolean applySync(JSONObject d){
        final String user=d.optString("user",""),eventId=d.optString("eventId",""),requestId=d.optString("requestId",""); final long seq=d.optLong("seq",-1); String senderId=d.optString("senderId","");
        if(!senderId.isEmpty()&&senderId.equals(localPresenceKey))return false; if(senderId.isEmpty()&&!user.isEmpty()&&user.equals(username))return false;
        String session=d.optString("mediaSessionId",""); String type=d.optString("mediaType",""); String targetKey=d.optString("targetKey",""); if(!targetKey.isEmpty()&&!targetKey.equals(localPresenceKey))return false; if(!"web".equalsIgnoreCase(type)||session.isEmpty())return false;
        long revision=d.optLong("mediaRevision",0); String owner=d.optString("mediaRevisionOwner",senderId); if(currentMediaSessionId.isEmpty()||!session.equals(currentMediaSessionId))return false; if(revision!=currentMediaRevision||(!currentMediaRevisionOwner.isEmpty()&&!owner.equals(currentMediaRevisionOwner)))return false;
        if(eventId.length()>0&&seenEventIds.contains(eventId))return false; if(!isNewerPlaybackPacket(d))return false; if(!requestId.isEmpty()&&(!requestId.equals(activeStateRequestId)||(mediaStateRequestDeadline>0&&System.currentTimeMillis()>mediaStateRequestDeadline)))return false;
        if(player==null||player.getMediaItemCount()==0){if(isNewerPendingPlayback(pendingNativeSync,d))pendingNativeSync=copyJsonObject(d);if(!requestId.isEmpty()){initialStateWaiting=true;activeStateRequestId=requestId;mediaStateRequestDeadline=System.currentTimeMillis()+5000;scheduleStateRequestTimeout(requestId);}return true;}
        long incomingPosition=d.optLong("position",0); long sentAt=d.optLong("sentAt",0); boolean playing=d.optBoolean("playing",false); float speed=Math.max(0.25f,Math.min(2f,(float)d.optDouble("speed",1.0))); if(playing&&sentAt>0)incomingPosition+=Math.max(0,Math.min(2000,System.currentTimeMillis()-sentAt))*speed; final long target=Math.max(0,incomingPosition); final boolean buffering=d.optBoolean("buffering",false); final String state=d.optString("state",buffering?"Buffering":(playing?"Playing":"Paused")); final boolean hasDesired=d.has("desiredPlaying"); final boolean desired=hasDesired&&d.optBoolean("desiredPlaying",playing); final String expectedSession=session; observePlaybackRevision(d);
        runOnUiThread(()->{
            if(player==null||player.getMediaItemCount()==0||!expectedSession.equals(currentMediaSessionId))return;
            applyingRemote=true; remoteGuardUntil=SystemClock.uptimeMillis()+75;
            try{long diff=Math.abs(player.getCurrentPosition()-target);if(diff>350)player.seekTo(target);if(Math.abs(player.getPlaybackParameters().speed-speed)>0.01f)player.setPlaybackSpeed(speed);String kind=d.optString("kind","");if(!buffering&&((hasDesired?desired:playing))!=player.getPlayWhenReady())player.setPlayWhenReady(hasDesired?desired:playing);}finally{applyingRemote=false;}
            if(!eventId.isEmpty()){seenEventIds.add(eventId);if(seenEventIds.size()>1024)seenEventIds.remove(seenEventIds.iterator().next());}
            if(!senderId.isEmpty()&&seq>=0)lastSequenceByUser.put(senderId+"|"+session,seq);
            lastAppliedPlaybackRevision=d.optLong("playbackRevision",0); lastAppliedPlaybackOwner=d.optString("playbackRevisionOwner",senderId); lastAppliedPlaybackStateId=d.optString("playbackStateId",""); lastAppliedPlaybackSeq=seq;
            setLocalPlaybackStatus("Error".equalsIgnoreCase(state)?"Error":(buffering?"Buffering":(playing?"Playing":"Paused"))); if(!requestId.isEmpty())finishStateRequest(requestId);
        });
        return true;
    }

    private void updatePresenceState(JSONObject state){
        if(state==null)return;
        LinkedHashMap<String,String> newParticipants=new LinkedHashMap<>();
        HashMap<String,String> newPfps=new HashMap<>();
        HashMap<String,String> newStatuses=new HashMap<>();
        if(!localPresenceKey.isEmpty()){newParticipants.put(localPresenceKey,username);newPfps.put(localPresenceKey,profilePicture);newStatuses.put(localPresenceKey,currentPresenceStatus());}
        try{Iterator<String> it=state.keys();while(it.hasNext()){String key=it.next();JSONObject entry=state.optJSONObject(key);if(entry==null)continue;JSONArray metas=entry.optJSONArray("metas");String name=key,pfp="";if(metas!=null&&metas.length()>0){JSONObject m=metas.optJSONObject(0);if(m!=null){name=m.optString("username",key);pfp=m.optString("pfp","");participantStatuses.put(key,m.optString("status","Online"));}}if(!key.equals(localPresenceKey)){newParticipants.put(key,name);if(!pfp.isEmpty())newPfps.put(key,pfp);String status=(metas!=null&&metas.length()>0&&metas.optJSONObject(0)!=null)?metas.optJSONObject(0).optString("status","Online"):"Online"; newStatuses.put(key,status);}}}catch(Exception ignored){}
        participants.clear();participants.putAll(newParticipants); participantPfps.clear();participantPfps.putAll(newPfps); participantStatuses.clear();participantStatuses.putAll(newStatuses); refreshParticipants();
    }
    private void updatePresenceDiff(JSONObject diff){
        try{
            JSONObject joins=diff.optJSONObject("joins");
            if(joins!=null){Iterator<String> it=joins.keys();while(it.hasNext()){String key=it.next();JSONObject entry=joins.optJSONObject(key);String name=key,pfp="";JSONArray metas=entry==null?null:entry.optJSONArray("metas");if(metas!=null&&metas.length()>0){JSONObject m=metas.optJSONObject(0);if(m!=null){name=m.optString("username",key);pfp=m.optString("pfp","");}}boolean wasPresent=participants.containsKey(key);participants.put(key,name);if(!pfp.isEmpty())participantPfps.put(key,pfp);participantStatuses.put(key,entry==null?"Online":entry.optString("status","Online"));if(!wasPresent&&shouldShowPresenceNotice(key,true))showPresenceNotice(name+" joined the room",true);}}
            JSONObject leaves=diff.optJSONObject("leaves");
            if(leaves!=null){Iterator<String> it=leaves.keys();while(it.hasNext()){String key=it.next();String existingName=participants.get(key);boolean existed=participants.remove(key)!=null;participantPfps.remove(key);participantStatuses.remove(key);final String removedName=existingName;typingUsers.entrySet().removeIf(e->e.getKey().equals(key)||(removedName!=null&&e.getValue().equals(removedName)));String name=removedName;if(name==null)name="Someone";if(existed&&shouldShowPresenceNotice(key,false))showPresenceNotice(name+" left the room",false);}}
        }catch(Exception ignored){}
        runOnUiThread(MainActivity.this::refreshParticipants);
    }
    private void handleRoomPresenceJoin(JSONObject d){
        String key=d.optString("key","");String name=d.optString("username","Someone");String pfp=d.optString("pfp","");
        if(key.isEmpty()||key.equals(localPresenceKey))return;
        boolean wasPresent=participants.containsKey(key);
        participants.put(key,name);if(!pfp.isEmpty())participantPfps.put(key,pfp);participantStatuses.put(key,d.optString("status","Online"));
        refreshParticipants();
        if(!wasPresent&&shouldShowPresenceNotice(key,true))showPresenceNotice(name+" joined the room",true);
    }
    private void handleRoomPresenceUpdate(JSONObject d){
        String key=d.optString("key","");if(key.isEmpty()||key.equals(localPresenceKey))return;
        String name=d.optString("username",participants.getOrDefault(key,"Someone"));String pfp=d.optString("pfp","");
        if(!participants.containsKey(key))participants.put(key,name);if(!pfp.isEmpty())participantPfps.put(key,pfp);participantStatuses.put(key,d.optString("status",participantStatuses.getOrDefault(key,"Online")));refreshParticipants();
    }

    private void handleRoomPresenceAck(JSONObject d){
        String forKey=d.optString("forKey","");if(forKey.isEmpty())return;if(!localPresenceKey.isEmpty()&&!localPresenceKey.equals(forKey))return;
        String key=d.optString("key","");String name=d.optString("username","Someone");String pfp=d.optString("pfp","");
        if(key.isEmpty()||key.equals(localPresenceKey))return;
        participants.put(key,name);if(!pfp.isEmpty())participantPfps.put(key,pfp);participantStatuses.put(key,d.optString("status","Online"));refreshParticipants();
    }
    private void handleRoomPresenceLeave(JSONObject d){
        String key=d.optString("key","");if(key.isEmpty()||key.equals(localPresenceKey))return;
        String name=participants.get(key);if(name==null)name=d.optString("username","Someone");
        boolean existed=participants.remove(key)!=null;participantPfps.remove(key);participantStatuses.remove(key);typingUsers.remove(key);refreshParticipants();
        if(existed&&shouldShowPresenceNotice(key,false))showPresenceNotice(name+" left the room",false);
    }

    private void refreshParticipants(){
        int count=Math.max(1,participants.size());
        if(participantCount!=null)participantCount.setText("●  "+count+" watching");
        if(participantSummary!=null)participantSummary.setText(formatPeopleWatching(count));
        if(participantStrip!=null){
            participantStrip.removeAllViews();
            for(Map.Entry<String,String> e:participants.entrySet()){
                String key=e.getKey(),name=e.getValue();
                LinearLayout pill=row();pill.setPadding(dp(5),dp(3),dp(9),dp(3));pill.setBackground(round(SURFACE2,LINE,22));
                ImageView a=new ImageView(this);a.setImageBitmap(loadPfpBitmap(participantPfps.getOrDefault(key,"asset:0")));a.setScaleType(ImageView.ScaleType.CENTER_CROP);a.setBackground(round(SURFACE,LINE,50));pill.addView(a,new LinearLayout.LayoutParams(dp(30),dp(30)));
                TextView n=text(name,10,TEXT);n.setSingleLine(true);n.setEllipsize(android.text.TextUtils.TruncateAt.END);n.setIncludeFontPadding(false);n.setGravity(Gravity.CENTER_VERTICAL);n.setMaxEms(14);n.setPadding(dp(6),0,0,0);pill.addView(n,new LinearLayout.LayoutParams(-2,dp(30)));
                participantStrip.addView(pill,new LinearLayout.LayoutParams(-2,dp(38)));
                Space sp=new Space(this);participantStrip.addView(sp,new LinearLayout.LayoutParams(dp(4),1));
            }
        }
        if(participantList!=null){SpannableStringBuilder b=new SpannableStringBuilder();for(Map.Entry<String,String> e:participants.entrySet()){b.append(e.getValue()).append(" • ").append(participantStatuses.getOrDefault(e.getKey(),"Online")).append('\n');}participantList.setText(b,TextView.BufferType.SPANNABLE);}
    }
    private void showPresenceNotice(String message,boolean joined){
        runOnUiThread(()->{
            if(roomRoot==null){toast(message);playPresenceSound(joined);return;}
            final TextView notice=text((joined?"＋  ":"−  ")+message,14,TEXT);notice.setGravity(Gravity.CENTER_VERTICAL);notice.setPadding(dp(16),0,dp(16),0);notice.setBackground(round(SURFACE,PRIMARY,18));
            FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-2,dp(52),Gravity.TOP|Gravity.CENTER_HORIZONTAL);p.topMargin=dp(18);roomRoot.addView(notice,p);
            notice.setElevation(dp(12));playPresenceSound(joined);notice.performHapticFeedback(android.view.HapticFeedbackConstants.VIRTUAL_KEY);
            uiHandler.postDelayed(()->{if(notice.getParent()!=null)((ViewGroup)notice.getParent()).removeView(notice);},3200);
        });
    }
    private void initSounds(){
        // Notification audio is played as a short media/sonification clip rather than
        // through SoundPool's notification stream. This makes it reliable while the
        // player is fullscreen and avoids depending on a separately loaded SoundPool id.
    }
    private void playNotificationSound(int resId){
        try{
            // MediaPlayer.create() returns a prepared player. Do not change its audio
            // attributes after preparation (that can fail on some Android builds).
            // Playing on the normal media stream keeps these short sounds audible while
            // the room/player is fullscreen.
            final MediaPlayer mp=MediaPlayer.create(this,resId);
            if(mp==null)return;
            mp.setVolume(0.85f,0.85f);
            mp.setOnCompletionListener(MediaPlayer::release);
            mp.setOnErrorListener((p,what,extra)->{try{p.release();}catch(Exception ignored){}return true;});
            mp.start();
        }catch(Exception ignored){}
    }
    private void playChatSound(){playNotificationSound(R.raw.chat_message);}
    private void playPresenceSound(boolean joined){playNotificationSound(joined?R.raw.room_join:R.raw.room_leave);}
    private boolean shouldShowPresenceNotice(String key,boolean joined){
        String id=(joined?"J:":"L:")+key;long now=SystemClock.uptimeMillis();Long last=presenceNoticeTimes.get(id);if(last!=null&&now-last<5000)return false;presenceNoticeTimes.put(id,now);return true;
    }

    private void updateChatBadges(){
        String value=unreadMessages<=0?"":(unreadMessages>=99?"99+":String.valueOf(unreadMessages));
        if(chatBadge!=null){chatBadge.setText(value);chatBadge.setVisibility(value.isEmpty()?View.GONE:View.VISIBLE);}
        if(quickChatBadge!=null){quickChatBadge.setText(value);quickChatBadge.setVisibility(value.isEmpty()?View.GONE:View.VISIBLE);}
    }
    private void openChat(){
        if(chatOpen)return;chatOpen=true;unreadMessages=0;updateChatBadges();chatOverlay=new FrameLayout(this);chatOverlay.setBackgroundColor(Color.argb(lightMode?205:220,0,0,0));FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(-1,-1);p.gravity=Gravity.BOTTOM;roomRoot.addView(chatOverlay,p);
        LinearLayout panel=col();panel.setPadding(dp(18),dp(6),dp(18),dp(14));panel.setBackground(round(SURFACE,LINE,24));FrameLayout.LayoutParams pp=new FrameLayout.LayoutParams(-1,(int)(getResources().getDisplayMetrics().heightPixels*.62f),Gravity.BOTTOM);chatOverlay.addView(panel,pp);
        View handle=new View(this);handle.setBackground(round(MUTED,Color.TRANSPARENT,5));FrameLayout.LayoutParams hp=new FrameLayout.LayoutParams(dp(42),dp(5),Gravity.TOP|Gravity.CENTER_HORIZONTAL);hp.topMargin=dp(6);panel.addView(handle,hp);
        LinearLayout head=col();head.setPadding(0,dp(14),0,dp(4));LinearLayout titleRow=row();LinearLayout words=col();add(words,title("Room Chat",20),-1,dp(28),0,2);add(words,text("Chat with people in this room",12,MUTED),-1,dp(22),0,0);titleRow.addView(words,new LinearLayout.LayoutParams(0,dp(36),1));ImageButton close=iconOnlyButton(R.drawable.ic_close,false);titleRow.addView(close,new LinearLayout.LayoutParams(dp(30),dp(30)));head.addView(titleRow,new LinearLayout.LayoutParams(-1,dp(64)));panel.addView(head,new LinearLayout.LayoutParams(-1,dp(72)));close.setOnClickListener(v->closeChat());
        chatList=new RecyclerView(this);chatList.setLayoutManager(new LinearLayoutManager(this));chatList.setAdapter(new ChatAdapter());chatList.setClipToPadding(false);panel.addView(chatList,new LinearLayout.LayoutParams(-1,0,1));
        typingLabel=text("",11,MUTED);typingLabel.setPadding(dp(46),0,dp(8),dp(4));typingLabel.setVisibility(View.GONE);panel.addView(typingLabel,new LinearLayout.LayoutParams(-1,dp(24)));
        LinearLayout in=row();Button emoji=iconButton("",R.drawable.ic_kaomoji,false);in.addView(emoji,new LinearLayout.LayoutParams(dp(52),dp(54)));in.addView(new Space(this),new LinearLayout.LayoutParams(dp(7),1));chatInput=input("Message…");in.addView(chatInput,new LinearLayout.LayoutParams(0,dp(48),1));in.addView(new Space(this),new LinearLayout.LayoutParams(dp(7),1));ImageButton send=iconOnlyButton(R.drawable.ic_send,false);in.addView(send,new LinearLayout.LayoutParams(dp(62),dp(54)));add(panel,in,-1,dp(54),8,0);
        chatInput.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int c,int a){}public void onTextChanged(CharSequence s,int st,int b,int c){boolean active=s!=null&&s.length()>0;sendTyping(active);if(typingStopRunnable!=null)uiHandler.removeCallbacks(typingStopRunnable);if(active){typingStopRunnable=()->sendTyping(false);uiHandler.postDelayed(typingStopRunnable,1400);}}public void afterTextChanged(android.text.Editable e){}});emoji.setOnClickListener(v->showKaomojiPicker());send.setOnClickListener(v->sendChat());chatInput.setOnEditorActionListener((v,id,e)->{if(id==EditorInfo.IME_ACTION_SEND){sendChat();return true;}return false;});chatInput.requestFocus();((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).showSoftInput(chatInput,android.view.inputmethod.InputMethodManager.SHOW_IMPLICIT);
    }
    private void showKaomojiPicker(){
        final String[] values={
            "(≧▽≦)","(｡♥‿♥｡)","(◕ᴗ◕✿)","(≧◡≦)","(´꒳`)","(づ｡◕‿‿◕｡)づ","(╥﹏╥)","(¬_¬)","(￣▽￣)","(｡•́︿•̀｡)","(つ≧▽≦)つ","(╯°□°）╯︵ ┻━┻","┬─┬ノ( º _ ºノ)","(•̀ᴗ•́)و","(｡•̀ᴗ-)✧","(づ￣ ³￣)づ","(≧ω≦)","(๑˃ᴗ˂)ﻭ","(｡•ㅅ•｡)♡","(✿◠‿◠)","(˶ᵔ ᵕ ᵔ˶)","(⌒‿⌒)","(❁´◡`❁)","(≧ω≦)/","(っ´▽`)っ","(｡･ω･｡)","(๑•̀ㅂ•́)و✧","(づ￣ ³￣)づ","(っ˘ω˘ς )","(｡•́‿•̀｡)","(≖ ͜ʖ≖)","(ಠ_ಠ)","(ಠ‿ಠ)","(ง'̀-'́)ง","(ノಠ益ಠ)ノ彡┻━┻","┻━┻ ︵ヽ(`Д´)ﾉ︵ ┻━┻","(╬ಠ益ಠ)","(｡•́︿•̀｡)","(ಥ﹏ಥ)","(ಥ_ಥ)","(இ﹏இ)","(つ﹏⊂)","(｡•́︿•̀｡)","(；ω；)","(｡•́‿•̀｡)","(⁄ ⁄•⁄ω⁄•⁄ ⁄)","(〃▽〃)","(*/ω＼*)","(｡♥‿♥｡)","(♡˙︶˙♡)","(づ￣ ³￣)づ♡","(っ˘з(˘⌣˘ )","(｡•̀ᴗ-)✧","(≧∇≦)","(ﾉ◕ヮ◕)ﾉ*:･ﾟ✧","(づ｡◕‿‿◕｡)づ","(つ≧▽≦)つ","(っ・ω・)っ","(っ´ω`)ﾉ(╥ω╥)","(o´ω`o)ﾉ","(｡•̀ᴗ•́｡)","(๑˘︶˘๑)","(⌒▽⌒)☆","(￣ω￣)","(￣︿￣)","(－_ლ)","(¬‿¬)","(¬‿¬ )","(눈_눈)","(•_•)","(•_•) ( •_•)>⌐■-■","(⌐■_■)","(☞ﾟヮﾟ)☞","☜(˚▽˚)☞","(ﾉ≧ڡ≦)","(๑•́ ₃ •̀๑)","(｡•́︿•̀｡)","(ノωヽ)","(つω`｡)","(づ｡◕‿‿◕｡)づ","(っ˘ڡ˘ς)","(っ´ω`)っ","(o_ _)o","m(_ _)m","orz","OTL","_(:3」∠)_","( ͡° ͜ʖ ͡°)","( ͡°╭͜ʖ╮͡° )","(╭ರ_•́)","(ಥ‿ಥ)","(ง •̀_•́)ง","(ง ͠° ͟ل͜ ͡°)ง","(づ￣ ³￣)づ","(ﾉ◕ヮ◕)ﾉ*:･ﾟ✧","(∩^ω^)⊃━☆","☆〜（ゝ。∂）","(ﾉ´ヮ`)ﾉ*:･ﾟ✧","(っ˘з(˘⌣˘ )","(｡•̀ᴗ-)✧","(≧▽≦)ゞ","(￣^￣)ゞ","(｀･ω･´)ゞ","(•̀ᴗ•́)و ̑̑","(｡•̀ᴗ•́｡)و","(๑•̀ㅁ•́๑)✧","(づ￣ ³￣)づ","(っ´ω`)っ","(ノ^_^)ノ","(／≧ω＼)","(≧ڡ≦)","(๑´ڡ`๑)","(｡･∀･)ﾉﾞ","(o´∀`o)ﾉ","( ´ ▽ ` )ﾉ","(｡•̀ᴗ-)✧","(✧ω✧)","(☆▽☆)","(✿◕‿◕)","(❀◦‿◦)","(◕‿◕✿)","(｡♥‿♥｡)","(≧ω≦)","(≧▽≦)","(๑˃̵ᴗ˂̵)و","(๑•̀ㅂ•́)و✧","(っ˘ω˘ς )","( ˘ ³˘)♥","( ˘ ³˘)❤","(づ￣ ³￣)づ❤","(つ≧▽≦)つ","(╥﹏╥)","(ಥ﹏ಥ)","(｡•́︿•̀｡)","(இ﹏இ`｡)","(｡•́‿•̀｡)","(¬_¬)","(ಠ_ಠ)","(눈_눈)","(；一_一)","(╯°□°）╯︵ ┻━┻","┬─┬ノ( º _ ºノ)","┬─┬ノ(ಠ_ಠノ)","(ノಠ益ಠ)ノ彡┻━┻","┻━┻ ︵ヽ(`Д´)ﾉ︵ ┻━┻","(╯°□°)╯︵ ʞooqǝɔɐɟ","(づ｡◕‿‿◕｡)づ","(っ˘з(˘⌣˘ )","(づ￣ ³￣)づ","(っ・ω・)っ","(つω`｡)","(っ´ω`)ﾉ(╥ω╥)","(つ≧▽≦)つ","(づ｡◕‿‿◕｡)づ","(っ˘ڡ˘ς)","(๑´ڡ`๑)","(っ´▽`)っ","(｡･ω･｡)","(o´ω`o)ﾉ","(￣▽￣)ノ","(｀・ω・´)","(｡•̀ᴗ-)✧","(≧∇≦)","(๑˃ᴗ˂)ﻭ","(ﾉ◕ヮ◕)ﾉ*:･ﾟ✧","(∩^ω^)⊃━☆"
        };
        LinearLayout box=col();box.setPadding(dp(10),dp(6),dp(10),dp(8));
        TextView hint=text("Kawaii / Kaomoji",12,MUTED);add(box,hint,-1,dp(24),0,5);
        ScrollView sc=new ScrollView(this);sc.setFillViewport(true);sc.setVerticalScrollBarEnabled(true);sc.setScrollbarFadingEnabled(false);sc.setScrollBarStyle(View.SCROLLBARS_INSIDE_INSET);
        GridLayout grid=new GridLayout(this);grid.setColumnCount(2);grid.setPadding(dp(2),dp(2),dp(2),dp(8));
        for(String value:values){TextView item=text(value,14,TEXT);item.setGravity(Gravity.CENTER);item.setIncludeFontPadding(true);item.setPadding(dp(7),dp(7),dp(7),dp(7));item.setBackground(round(SURFACE2,LINE,12));GridLayout.LayoutParams lp=new GridLayout.LayoutParams();lp.width=0;lp.height=dp(54);lp.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f);lp.setMargins(dp(3),dp(3),dp(3),dp(3));grid.addView(item,lp);item.setOnClickListener(v->{if(chatInput!=null){int st=Math.max(0,chatInput.getSelectionStart());chatInput.getText().insert(st,((TextView)v).getText());chatInput.requestFocus();}});}
        sc.addView(grid,new ScrollView.LayoutParams(-1,-2));box.addView(sc,new LinearLayout.LayoutParams(-1,0,1));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Kawaii / Kaomoji").setView(box).setNegativeButton("Close",null).create();styleDialog(dialog);dialog.show();finishDialogStyle(dialog);
        if(dialog.getWindow()!=null){int h=(int)(getResources().getDisplayMetrics().heightPixels*.72f);dialog.getWindow().setLayout((int)(getResources().getDisplayMetrics().widthPixels*.94f),h);}
    }

    private void showReactionPicker(ChatMessage message){
        final String[] reactions={"❤️","🩷","😂","🤣","😭","🥹","💀","🫠","🙏","🫶","🔥","✨","😑","😮","👏","👍","🦝","🎀","(≧▽≦)","(╥﹏╥)"};
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("React to message").setItems(reactions,(d,which)->{message.reaction=reactions[which];Intent i=serviceIntent(SyncService.ACTION_REACTION).putExtra("messageId",message.id).putExtra("reaction",message.reaction);startService(i);if(chatList!=null)chatList.getAdapter().notifyDataSetChanged();}).create();styleDialog(dialog);dialog.show();finishDialogStyle(dialog);
    }
    private void handleChatReaction(JSONObject d){
        String id=d.optString("messageId","");String reaction=d.optString("reaction","");if(id.isEmpty())return;
        runOnUiThread(()->{for(ChatMessage m:chats){if(id.equals(m.id)){m.reaction=reaction;break;}}if(chatList!=null)chatList.getAdapter().notifyDataSetChanged();});
    }
    private void updateTypingLabel(){
        if(typingLabel==null)return;
        if(typingUsers.isEmpty()){typingLabel.setVisibility(View.GONE);typingLabel.setText("");return;}
        String who=joinStrings(new ArrayList<>(typingUsers.values()), ", ");
        typingLabel.setText(who+(typingUsers.size()==1?" is typing…":" are typing…"));typingLabel.setVisibility(View.VISIBLE);
    }
    private void closeChat(){if(!chatOpen)return;sendTyping(false);chatOpen=false;if(chatOverlay!=null){((android.view.inputmethod.InputMethodManager)getSystemService(INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(chatOverlay.getWindowToken(),0);roomRoot.removeView(chatOverlay);chatOverlay=null;chatList=null;chatInput=null;typingLabel=null;}}
    private void sendTyping(boolean active){
        long now=SystemClock.uptimeMillis();
        if(now-lastTypingSentAt<900 && active==typingActive)return;
        lastTypingSentAt=now;typingActive=active;
        Intent i=serviceIntent(SyncService.ACTION_TYPING).putExtra("typing",active);startService(i);
    }
    private void sendChat(){
        if(chatInput==null)return;String s=chatInput.getText().toString().trim();if(s.isEmpty())return;
        if(s.length()>2000){toast("Message is too long.");return;}
        sendTyping(false);
        String messageId=UUID.randomUUID().toString();
        Intent i=serviceIntent(SyncService.ACTION_CHAT).putExtra("text",s).putExtra("pfp",profilePicture).putExtra("messageId",messageId);startService(i);
        addChat(username,s,profilePicture,System.currentTimeMillis(),messageId);playChatSound();chatInput.setText("");
    }
    private String clampChatText(String s){if(s==null)return "";return s.length()>2000?s.substring(0,2000):s;}
     private void addChat(String user,String msg,String pfp){addChat(user,msg,pfp,System.currentTimeMillis(),UUID.randomUUID().toString());}
    private void addChat(String user,String msg,String pfp,long sentAt){addChat(user,msg,pfp,sentAt,UUID.randomUUID().toString());}
    private void addChat(String user,String msg,String pfp,long sentAt,String id){
        runOnUiThread(()->{
            String chatId=(id==null||id.isEmpty())?UUID.randomUUID().toString():id;
            for(ChatMessage existing:chats) if(chatId.equals(existing.id)) return;
            int index=chats.size();
            while(index>0 && chats.get(index-1).sentAt>sentAt)index--;
            chats.add(index,new ChatMessage(user,msg,pfp,sentAt,chatId));
             if(chats.size()>=500){chats.remove(0); if(chatList!=null&&chatList.getAdapter()!=null)chatList.getAdapter().notifyItemRemoved(0);}
             if(chatList!=null){
                RecyclerView.Adapter<?> adapter=chatList.getAdapter();
                if(adapter!=null){
                    adapter.notifyItemInserted(index);
                    chatList.scrollToPosition(Math.max(0,chats.size()-1));
                }
            }
        });
    }
    private void handleTyping(JSONObject d){
        String user=d.optString("user","");String sender=d.optString("senderId",user);
        if(user.isEmpty()||(!localPresenceKey.isEmpty()&&sender.equals(localPresenceKey))||(sender.isEmpty()&&user.equals(username)))return;
        boolean active=d.optBoolean("typing",false);
        if(active){typingUsers.put(sender,user);typingExpiry.put(sender,System.currentTimeMillis()+5000);uiHandler.postDelayed(()->{Long exp=typingExpiry.get(sender);if(exp!=null&&System.currentTimeMillis()>=exp){typingExpiry.remove(sender);typingUsers.remove(sender);updateTypingLabel();}},5100);}else{typingUsers.remove(sender);typingExpiry.remove(sender);}
        runOnUiThread(()->updateTypingLabel());
    }
    private void enterYouTubeFullscreen(View view, WebChromeClient.CustomViewCallback callback){
        if(view==null||youtubeCustomView!=null){if(callback!=null)try{callback.onCustomViewHidden();}catch(Exception ignored){}return;}
        youtubeCustomView=view; youtubeCustomViewCallback=callback; youtubeFullscreen=true;
        ViewParent parent=view.getParent(); if(parent instanceof ViewGroup)((ViewGroup)parent).removeView(view);
        youtubeFullscreenContainer=new FrameLayout(this); youtubeFullscreenContainer.setBackgroundColor(Color.BLACK); youtubeFullscreenContainer.setKeepScreenOn(true);
        youtubeFullscreenContainer.addView(view,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
        ((ViewGroup)getWindow().getDecorView()).addView(youtubeFullscreenContainer,new ViewGroup.LayoutParams(-1,-1));
        if(webView!=null)webView.setVisibility(View.INVISIBLE);
        youtubeFullscreenOrientation=getSavedPhysicalOrientation();
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        getWindow().setStatusBarColor(Color.BLACK); getWindow().setNavigationBarColor(Color.BLACK);
        View decor=getWindow().getDecorView();
        if(Build.VERSION.SDK_INT>=30){android.view.WindowInsetsController c=decor.getWindowInsetsController();if(c!=null){c.hide(android.view.WindowInsets.Type.statusBars()|android.view.WindowInsets.Type.navigationBars());c.setSystemBarsBehavior(android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);}}
        else decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
    private void restoreFullscreenEnvironment(int orientation){
        if(orientation!=ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED)setRequestedOrientation(orientation);
        getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);updateSystemBars();
    }

    private void exitYouTubeFullscreen(){
        if(youtubeCustomView==null)return;
        View view=youtubeCustomView; ViewParent parent=view.getParent(); if(parent instanceof ViewGroup)((ViewGroup)parent).removeView(view);
        if(youtubeFullscreenContainer!=null){ViewParent cp=youtubeFullscreenContainer.getParent();if(cp instanceof ViewGroup)((ViewGroup)cp).removeView(youtubeFullscreenContainer);}
        WebChromeClient.CustomViewCallback cb=youtubeCustomViewCallback;
        youtubeCustomView=null; youtubeCustomViewCallback=null; youtubeFullscreenContainer=null; youtubeFullscreen=false;
        if(cb!=null)try{cb.onCustomViewHidden();}catch(Exception ignored){}
        if(webView!=null)webView.setVisibility(webMode?View.VISIBLE:View.GONE);
        restoreFullscreenEnvironment(youtubeFullscreenOrientation);
    }

    private void applyImmersiveSystemBars(){
        View decor=getWindow().getDecorView();
        if(Build.VERSION.SDK_INT>=30){
            WindowInsetsController c=decor.getWindowInsetsController();
            if(c!=null){
                c.hide(WindowInsets.Type.statusBars()|WindowInsets.Type.navigationBars());
                c.setSystemBarsBehavior(WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);
            }
        }else{
            decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|
                    View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|
                    View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|
                    View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|
                    View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
        }
    }

    private int getSavedPhysicalOrientation(){
        return getResources().getConfiguration().orientation==Configuration.ORIENTATION_LANDSCAPE
                ? ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                : ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
    }

    private void setFullscreen(boolean on){
        if(fullscreen==on)return;
        fullscreen=on;
        View fullscreenButton=playerView==null?null:playerView.findViewById(R.id.coview_fullscreen);
        if(on){
            fullscreenOrientation=getSavedPhysicalOrientation();
            if(fullscreenButton!=null)fullscreenButton.setContentDescription("Exit fullscreen");
            fullscreenVisibility.clear();
            View[] fsViews={roomHeader,connectionLabel,roomPeople,roomMediaRow,mediaLabel,roomQuick};
            for(View v:fsViews){
                if(v!=null){
                    fullscreenVisibility.put(v,v.getVisibility());
                    v.setVisibility(View.GONE);
                }
            }
            if(roomPlayerBox!=null && roomRoot!=null && roomContent!=null){
                ViewParent parent=roomPlayerBox.getParent();
                if(parent instanceof ViewGroup){
                    ViewGroup oldParent=(ViewGroup)parent;
                    playerContentIndex=Math.max(0,oldParent.indexOfChild(roomPlayerBox));
                    oldParent.removeView(roomPlayerBox);
                }
                roomRoot.addView(roomPlayerBox,new FrameLayout.LayoutParams(-1,-1,Gravity.CENTER));
                playerDetachedForFullscreen=true;
                ViewGroup.LayoutParams flp=roomPlayerBox.getLayoutParams(); if(flp!=null){flp.width=-1;flp.height=-1;roomPlayerBox.setLayoutParams(flp);}
            }
            setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
            if(roomContent!=null){roomContent.setPadding(0,0,0,0);roomContent.setGravity(Gravity.CENTER);roomContent.setVisibility(View.GONE);}
            if(roomQuick!=null)roomQuick.setVisibility(View.GONE);
            getWindow().setStatusBarColor(Color.BLACK);
            getWindow().setNavigationBarColor(Color.BLACK);
            applyImmersiveSystemBars();
            if(roomPlayerBox!=null){roomPlayerBox.bringToFront();roomPlayerBox.requestLayout();}
        }else{
            if(fullscreenButton!=null)fullscreenButton.setContentDescription("Fullscreen");
            restoreFullscreenEnvironment(fullscreenOrientation);
            for(Map.Entry<View,Integer> e:fullscreenVisibility.entrySet()){if(e.getKey()!=null)e.getKey().setVisibility(e.getValue());}
            fullscreenVisibility.clear();
            if(roomPlayerBox!=null && playerDetachedForFullscreen && roomRoot!=null && roomContent!=null){
                ViewParent parent=roomPlayerBox.getParent();
                if(parent instanceof ViewGroup)((ViewGroup)parent).removeView(roomPlayerBox);
                int index=Math.max(0,Math.min(playerContentIndex,roomContent.getChildCount()));
                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);
                lp.topMargin=0;lp.bottomMargin=dp(8);
                roomContent.addView(roomPlayerBox,index,lp);
                playerDetachedForFullscreen=false;
                roomPlayerBox.requestLayout();
            }
            if(roomContent!=null){roomContent.setPadding(dp(14),dp(8),dp(14),dp(14));roomContent.setGravity(Gravity.NO_GRAVITY);roomContent.setVisibility(View.VISIBLE);}
            playerContentIndex=-1;
            if(roomPlayerBox!=null){roomPlayerBox.bringToFront();roomPlayerBox.requestLayout();}
            updateSystemBars();
        }
    }

    private void restorePersistedLocalVideo(){
        if(player==null||!serviceConnected||webMode||onlineMode||videoUri!=null)return;
        String raw=prefs.getString("local_video_uri","");
        String savedContext=prefs.getString("local_video_context","");
        if(raw.isEmpty() || !savedContext.equals(serverUrl+"|"+roomName))return;
        try{
            Uri uri=Uri.parse(raw);
            if(!"content".equalsIgnoreCase(uri.getScheme()) && !"file".equalsIgnoreCase(uri.getScheme()))return;
             if("content".equalsIgnoreCase(uri.getScheme())){try{getContentResolver().takePersistableUriPermission(uri,Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}}
            if(player.getMediaItemCount()>0)return;
            videoUri=uri; String sr=prefs.getString("subtitle_uri",""); subtitleUri=sr.isEmpty()?null:Uri.parse(sr); onlineMode=false; webMode=false; currentMediaType="local";
            playerView.setVisibility(View.VISIBLE); setPlayerEmptyVisible(false);
            player.setMediaItem(buildMediaItem()); player.prepare(); if(subtitleUri!=null) applyCustomSubtitleFile();
            if(mediaLabel!=null)mediaLabel.setText("✓  "+displayName(uri)+"  •  local file");
            setLocalPlaybackStatus("Paused");
        }catch(Exception e){
            prefs.edit().remove("local_video_uri").apply();
        }
    }

    private void disconnect(){
        playerSyncHandler.removeCallbacks(nativeSyncHeartbeat);
        if(youtubeFullscreen)exitYouTubeFullscreen();
        closeChat();
        if(fullscreen)setFullscreen(false);
        startService(serviceIntent(SyncService.ACTION_DISCONNECT));
        releaseWebViewForMediaSwitch();
                 videoUri=null; subtitleUri=null; prefs.edit().remove("local_video_uri").remove("subtitle_uri").remove("local_video_context").apply(); customSubtitleCues.clear(); cancelCustomSubtitleTicker(); customSubtitleLoading=false;
        setPlayerEmptyVisible(false);
        if(playerView!=null){try{playerView.setPlayer(null);}catch(Exception ignored){}}
        if(player!=null){try{player.stop();}catch(Exception ignored){}try{player.clearMediaItems();}catch(Exception ignored){}try{player.release();}catch(Exception ignored){}player=null;}
        webMode=false;webPageReady=false;webObservedInitial=false;pendingWebStateRequestId="";pendingNativeStateRequestId="";pendingRemoteWebSource="";pendingMediaSourceState=null;if(pendingMediaSourceApplyRunnable!=null)uiHandler.removeCallbacks(pendingMediaSourceApplyRunnable);pendingMediaSourceApplyRunnable=null;
        onlineMode=false;onlineUrl="";onlineReady=false;
        currentMediaSessionId="";mediaRevision=0;mediaRevisionOwner="";currentMediaType="";mediaStateRequestDeadline=0;retiredMediaSessionIds.clear();playerContentIndex=-1;playerDetachedForFullscreen=false;
        typingUsers.clear();typingExpiry.clear();participantStatuses.clear();participants.clear();participantPfps.clear();pfpCache.clear();
        playerView=null;adjustedSubtitleView=null;localRoomAvatar=null;localRoomStatus=null;roomRoot=null;roomContent=null;roomPlayerBox=null;mediaChooserOverlay=null;playerEmptyOverlay=null;
        showConnect();
    }
    @Override public void onBackPressed(){if(youtubeFullscreen){exitYouTubeFullscreen();return;}if(chatOpen){closeChat();return;}if(fullscreen){setFullscreen(false);return;}if(player!=null){disconnect();return;}super.onBackPressed();}
    @Override public void onConfigurationChanged(Configuration newConfig){
        super.onConfigurationChanged(newConfig);
        if(fullscreen && newConfig.orientation==Configuration.ORIENTATION_LANDSCAPE){
            // Keep the existing player; do not recreate it.
            updateSystemBars();
        }
    }
    private void applyStatusIndicator(TextView view,String label,int accent){
        if(view==null)return;
        view.setText("●  "+label);
        view.setTextColor(accent);
        view.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        view.setContentDescription(label+" status");
    }

    private static String safe(String s){ return s==null?"":s; }

    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    @Override protected void onDestroy(){pfpCache.clear();playerSyncHandler.removeCallbacks(nativeSyncHeartbeat);if(webLoadTimeoutRunnable!=null)uiHandler.removeCallbacks(webLoadTimeoutRunnable);uiHandler.removeCallbacksAndMessages(null);try{unregisterReceiver(receiver);}catch(Exception ignored){}if(youtubeFullscreen)exitYouTubeFullscreen();if(subtitleExecutor!=null){try{subtitleExecutor.shutdownNow();}catch(Exception ignored){}}releaseWebViewForMediaSwitch();if(playerView!=null){try{playerView.setPlayer(null);}catch(Exception ignored){}}if(player!=null){try{player.release();}catch(Exception ignored){}player=null;}super.onDestroy();}

    private static class ChatMessage{final String user,text,pfp,id;final long sentAt;String reaction="";ChatMessage(String u,String t,String p,long a,String i){user=u;text=t;pfp=p;sentAt=a;id=i;}}
    private String joinStrings(Collection<String> values,String separator){StringBuilder b=new StringBuilder();for(String v:values){if(v==null)continue;if(b.length()>0)b.append(separator);b.append(v);}return b.toString();}
    private String formatChatTime(long t){return new java.text.SimpleDateFormat("HH:mm",Locale.US).format(new java.util.Date(t));}
    private class ChatAdapter extends RecyclerView.Adapter<ChatAdapter.Holder>{
        class Holder extends RecyclerView.ViewHolder{LinearLayout row;Holder(View v){super(v);row=(LinearLayout)v;}}
        @Override public Holder onCreateViewHolder(ViewGroup p,int type){LinearLayout r=row();r.setPadding(dp(4),dp(4),dp(4),dp(4));return new Holder(r);}
        @Override public void onBindViewHolder(Holder h,int pos){
            h.row.removeAllViews();ChatMessage m=chats.get(pos);boolean mine=m.user.equals(username);
            if(!mine){ImageView a=new ImageView(MainActivity.this);a.setImageBitmap(loadPfpBitmap(m.pfp));a.setScaleType(ImageView.ScaleType.CENTER_CROP);a.setBackground(round(SURFACE2,LINE,50));h.row.addView(a,new LinearLayout.LayoutParams(dp(38),dp(38)));Space sp=new Space(MainActivity.this);h.row.addView(sp,new LinearLayout.LayoutParams(dp(7),1));}
            LinearLayout bubble=col();bubble.setPadding(dp(12),dp(8),dp(12),dp(7));bubble.setBackground(round(mine?PRIMARY:SURFACE2,mine?PRIMARY2:LINE,18));
            if(!mine){TextView name=title(m.user,12);name.setTextColor(PRIMARY2);add(bubble,name,-1,dp(20),0,2);}
            TextView body=text(m.text,15,TEXT);body.setLineSpacing(dp(2),1f);add(bubble,body,-1,-2,0,2);
            TextView time=text(formatChatTime(m.sentAt)+(mine?"  ✓✓":""),10,mine?Color.argb(210,255,255,255):MUTED);time.setGravity(Gravity.RIGHT);add(bubble,time,-1,dp(18),2,0);
            if(!m.reaction.isEmpty()){TextView reaction=text(m.reaction,12,TEXT);reaction.setPadding(dp(7),dp(3),dp(7),dp(3));reaction.setBackground(round(SURFACE,LINE,12));add(bubble,reaction,-2,dp(24),2,0);}
            bubble.setOnLongClickListener(v->{showReactionPicker(m);return true;});
            LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(Math.min(dp(270),Math.max(dp(170),getResources().getDisplayMetrics().widthPixels-dp(100))),-2);bp.gravity=mine?Gravity.RIGHT:Gravity.LEFT;h.row.addView(bubble,bp);
            if(mine){Space sp=new Space(MainActivity.this);h.row.addView(sp,new LinearLayout.LayoutParams(dp(7),1));ImageView a=new ImageView(MainActivity.this);a.setImageBitmap(loadPfpBitmap(m.pfp));a.setScaleType(ImageView.ScaleType.CENTER_CROP);a.setBackground(round(SURFACE2,LINE,50));h.row.addView(a,new LinearLayout.LayoutParams(dp(38),dp(38)));}
            if(pos==chats.size()-1&&!typingUsers.isEmpty()){TextView typing=text(joinStrings(new ArrayList<>(typingUsers.values()), ", ")+" is typing…",11,MUTED);typing.setPadding(dp(48),dp(2),0,dp(5));h.row.addView(typing,new LinearLayout.LayoutParams(-1,dp(24)));}
        }
        @Override public int getItemCount(){return chats.size();}
    }
    private String escapeHtml(String value){return value==null?"":value.replace("&","&amp;").replace("\"","&quot;").replace("<","&lt;").replace(">","&gt;");}

}
