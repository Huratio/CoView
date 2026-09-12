package com.watchtogether.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PictureInPictureParams;
import android.content.*;
import android.content.pm.ActivityInfo;
import android.graphics.*;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.BitmapDrawable;
import android.animation.ValueAnimator;
import android.view.animation.LinearInterpolator;
import android.util.Base64;
import android.util.Rational;
import android.text.SpannableString;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ImageSpan;
import android.media.MediaPlayer;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.os.Build;
import android.content.res.Configuration;
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
import androidx.media3.common.C;
import androidx.media3.common.MediaItem;
import androidx.media3.common.MimeTypes;
import androidx.media3.common.Player;
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

import java.util.*;
import java.nio.charset.StandardCharsets;

public class MainActivity extends AppCompatActivity {
    private static final int PICK_VIDEO = 77, PICK_SUBTITLE = 78, PICK_PFP = 79;
    private static final String APP_NAME = "Synka";
    private int clientSlot = 1;
    private Class<?> syncServiceClass = SyncService.class;
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
    private String webUrl="";
    private long webRemoteGuardUntil=0, lastWebSyncSentAt=0, lastWebPositionMs=-1;
    private boolean lastWebPlaying=false, webObservedInitial=false;
    private int unreadMessages=0;
    private TextView chatBadge, quickChatBadge;
    private View appSplash, webLoadingOverlay, playerEmptyOverlay;
    private FrameLayout mediaChooserOverlay;
    private TextView webLoadingTitle, webLoadingDetail;
    private int webCommandSequence=0;
    private long roomSessionGeneration=0;
    private int customThemeBg=Color.rgb(12,18,30);
    private int customThemeBg2=Color.rgb(24,34,52);
    private String localStatus="Online";
    private String pendingRemoteWebSource="";
    private Runnable playerControlsHideRunnable;
    private Runnable webLoadTimeoutRunnable;
    private int webLoadAttempt=0;
    private String pendingWebStateRequestId="";
    private ExoPlayer player;
    // Online playback state. Local playback synchronization remains the v1.7.1 path.
    private boolean onlineMode=false;
    private String onlineUrl="";
    private String onlineResolvedUrl="";
    private boolean onlineReady=false;
    private boolean onlineResolving=false;
    private DefaultTrackSelector trackSelector;
    private RecyclerView chatList;
    private EditText chatInput;
    private TextView typingLabel;
    private TextView participantCount, connectionLabel, mediaLabel, syncStatusLabel, participantSummary;
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
    private boolean playerControlsLocked=false;
    private final HashSet<String> typingUsers = new HashSet<>();
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
    private CueGroup lastCueGroup;
    private Runnable pendingSubtitleRunnable;
    private String activeStateRequestId = "";
    private String localPresenceKey = "";
    private final LinkedHashSet<String> seenEventIds = new LinkedHashSet<>();
    private final Handler uiHandler = new Handler();
    private final HashMap<String,Long> presenceNoticeTimes = new HashMap<>();

    private final BroadcastReceiver receiver = new BroadcastReceiver() {
        @Override public void onReceive(Context context, Intent intent) {
            if (!SyncService.ACTION_EVENT.equals(intent.getAction())) return;
            try {
                String ev=intent.getStringExtra(SyncService.EXTRA_EVENT), raw=intent.getStringExtra(SyncService.EXTRA_DATA);
                JSONObject d=new JSONObject(raw==null?"{}":raw);
                if(intent.hasExtra("client_slot") && intent.getIntExtra("client_slot",1)!=clientSlot) return;
                if ("connected".equals(ev)) {
                    localPresenceKey = d.optString("presenceKey", ""); localStatus="Online";
                    if(!localPresenceKey.isEmpty()){participants.remove("self");participantPfps.remove("self");participants.put(localPresenceKey,username);participantPfps.put(localPresenceKey,profilePicture);}
                    runOnUiThread(() -> {
                        if (!isFinishing()) {
                            if (player == null) {
                                showRoom();
                                if(!pendingSharedUrl.isEmpty()) uiHandler.postDelayed(()->{ if(!isFinishing() && player!=null && !pendingSharedUrl.isEmpty()) showLinkDialog(); },180);
                            } else { if (connectionLabel != null) connectionLabel.setText("●  Connected"); sendStateRequest(); }
                            refreshParticipants();
                        }
                    });
                } else if ("error".equals(ev)) {
                    runOnUiThread(() -> toast(d.optString("message","Connection failed")));
                } else if ("disconnected".equals(ev)) {
                    runOnUiThread(() -> { if(connectionLabel!=null) connectionLabel.setText("●  Reconnecting…"); if(syncStatusLabel!=null) syncStatusLabel.setText("Connection interrupted"); });
                } else if ("sync".equals(ev)) {
                    String syncUser=d.optString("user","");
                    String key=d.optString("senderId",syncUser);
                    if(!key.isEmpty()) participantStatuses.put(key, d.optBoolean("playing",false)?"Playing":"Paused");
                    applySync(d);
                    runOnUiThread(()->{if(syncStatusLabel!=null)syncStatusLabel.setText("✓  Synced");refreshParticipants();});
                } else if ("web_sync".equals(ev)) {
                    String syncUser=d.optString("user","");
                    String key=d.optString("senderId",syncUser);
                    if(!key.isEmpty()) participantStatuses.put(key, d.optBoolean("playing",false)?"Playing":"Paused");
                    applyWebSync(d);
                    runOnUiThread(()->{if(syncStatusLabel!=null)syncStatusLabel.setText("✓  Synced");refreshParticipants();});
                }
                else if ("media_source".equals(ev) || "media_source_state".equals(ev)) MainActivity.this.applyMediaSource(d);
                else if ("chat".equals(ev)) { if(!localPresenceKey.equals(d.optString("senderId",""))) { addChat(d.optString("user","Unknown"), d.optString("text",""), d.optString("pfp",""), d.optLong("sentAt",System.currentTimeMillis()), d.optString("messageId","")); if(!chatOpen){ unreadMessages=Math.min(99,unreadMessages+1); runOnUiThread(MainActivity.this::updateChatBadges); } playChatSound(); } }
                else if ("chat_reaction".equals(ev)) handleChatReaction(d);
                else if ("typing".equals(ev)) handleTyping(d);
                else if ("presence_state".equals(ev)) updatePresenceState(d);
                else if ("presence_diff".equals(ev)) updatePresenceDiff(d);
                else if ("room_presence_join".equals(ev)) handleRoomPresenceJoin(d);
                else if ("room_presence_update".equals(ev)) handleRoomPresenceUpdate(d);
                else if ("room_presence_ack".equals(ev)) handleRoomPresenceAck(d);
                else if ("room_presence_leave".equals(ev)) handleRoomPresenceLeave(d);
                else if ("state_request".equals(ev)) { String rid=d.optString("requestId",""); if(webMode){ activeStateRequestId=rid; pendingWebStateRequestId=rid; if(webView!=null)webView.evaluateJavascript("javascript:(function(){if(window.coviewState)window.coviewState();})()",null); } else sendSync(true,rid); }
            } catch(Exception ignored){}
        }
    };

    @Override protected void onCreate(Bundle state){
        clientSlot=getIntent().getIntExtra("client_slot",1);
        if(clientSlot!=2)clientSlot=1;
        syncServiceClass=clientSlot==2?SyncServiceTest2.class:SyncService.class;
        prefs=getSharedPreferences(clientSlot==2?"watchtogether_test2":"watchtogether",MODE_PRIVATE);
        if(clientSlot==2 && !prefs.contains("server")){
            SharedPreferences primary=getSharedPreferences("watchtogether",MODE_PRIVATE);
            prefs.edit().putString("server",primary.getString("server",""))
                    .putString("key",primary.getString("key",""))
                    .putString("room",primary.getString("room","Movie Night"))
                    .putString("user",primary.getString("user","").trim().isEmpty()?"Test User 2":primary.getString("user","")+" [Test 2]")
                    .putInt("pfp_index",(primary.getInt("pfp_index",0)+1)%PFP_RES.length)
                    .apply();
        }
        subtitleDelayMs=prefs.getInt("subtitle_delay_ms",0);
        lightMode=prefs.getBoolean("light_mode",false);
        customThemeBg=prefs.getInt("custom_theme_bg",Color.rgb(12,18,30));
        customThemeBg2=prefs.getInt("custom_theme_bg2",Color.rgb(24,34,52));
        AppCompatDelegate.setDefaultNightMode(lightMode?AppCompatDelegate.MODE_NIGHT_NO:AppCompatDelegate.MODE_NIGHT_YES);
        super.onCreate(state);
        applyThemeColors();
        setTitle(APP_NAME);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG); updateSystemBars();
        setContentView(R.layout.activity_main); root=findViewById(R.id.root); registerReceiverCompat(); initSounds(); ensureProfile();
        handleIncomingIntent(getIntent());
        showAppSplash();
        uiHandler.postDelayed(() -> { if (!isFinishing()) showConnect(); }, 180);
    }

    @Override protected void onNewIntent(Intent intent){
        super.onNewIntent(intent);
        setIntent(intent);
        handleIncomingIntent(intent);
        if(player==null) showConnect(); else if(!pendingSharedUrl.isEmpty()) showLinkDialog();
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
    private void registerReceiverCompat(){ IntentFilter f=new IntentFilter(SyncService.ACTION_EVENT); if(Build.VERSION.SDK_INT>=33) registerReceiver(receiver,f,RECEIVER_NOT_EXPORTED); else registerReceiver(receiver,f); }
    private void applyThemeColors(){
        int storedAccent=prefs.getInt("accent_color",Color.rgb(216,216,218));
        if(storedAccent==Color.rgb(20,184,166)||storedAccent==Color.rgb(37,211,102)||storedAccent==Color.rgb(139,92,246)) storedAccent=Color.rgb(216,216,218);
        if(lightMode){
            BG=Color.rgb(246,246,247); SURFACE=Color.rgb(255,255,255); SURFACE2=Color.rgb(242,242,244);
            PRIMARY=storedAccent; PRIMARY2=lighten(storedAccent,0.20f); TEXT=Color.rgb(20,20,22);
            MUTED=Color.rgb(105,105,110); SUCCESS=Color.rgb(90,90,96); LINE=Color.rgb(218,218,222);
        }else{
            BG=Color.rgb(5,5,5); SURFACE=Color.rgb(13,13,13); SURFACE2=Color.rgb(40,40,42);
            PRIMARY=storedAccent; PRIMARY2=lighten(storedAccent,0.20f); TEXT=Color.rgb(255,255,255);
            MUTED=Color.rgb(138,138,143); SUCCESS=Color.rgb(216,216,218); LINE=Color.rgb(29,29,29);
        }
        updateActiveBackgrounds();
    }
    private int lighten(int color,float amount){float[] hsv=new float[3];Color.colorToHSV(color,hsv);hsv[2]=Math.min(1f,hsv[2]+amount);hsv[1]=Math.max(0.15f,hsv[1]*0.85f);return Color.HSVToColor(hsv);}
    private android.graphics.drawable.Drawable createRoomBackground(){return round(BG,Color.TRANSPARENT,0);}
    private GradientDrawable gradientBackground(int a,int b){
        GradientDrawable g=new GradientDrawable(GradientDrawable.Orientation.TL_BR,new int[]{a,b});
        g.setCornerRadius(0);
        return g;
    }
    private void updateActiveBackgrounds(){
        if(roomRoot!=null){roomRoot.setBackground(createRoomBackground());}
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
            if(token!=null&&token.startsWith("data:")){int comma=token.indexOf(',');if(comma>0)return BitmapFactory.decodeByteArray(Base64.decode(token.substring(comma+1),Base64.DEFAULT),0,Base64.decode(token.substring(comma+1),Base64.DEFAULT).length);}
            if(token!=null&&token.startsWith("asset:")){int i=Integer.parseInt(token.substring(6));return BitmapFactory.decodeResource(getResources(),PFP_RES[Math.max(0,Math.min(PFP_RES.length-1,i))]);}
        }catch(Exception ignored){}
        return null;
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
    private Button iconOnlyButton(int iconRes,boolean primary){Button b=button("",primary);b.setBackground(round(primary?PRIMARY:SURFACE2,primary?Color.rgb(55,55,58):Color.TRANSPARENT,50));b.setMinHeight(0);b.setMinWidth(0);try{android.graphics.drawable.Drawable d=getDrawable(iconRes);if(d!=null){d.setTint(TEXT);b.setCompoundDrawablesWithIntrinsicBounds(d,null,null,null);}}catch(Exception ignored){}return b;}
    private LinearLayout col(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l;}
    private void add(LinearLayout p,View v,int w,int h,int top,int bottom){LinearLayout.LayoutParams q=new LinearLayout.LayoutParams(w,h);q.topMargin=dp(top);q.bottomMargin=dp(bottom);p.addView(v,q);}
    private ScrollView scroll(LinearLayout c){ScrollView s=new ScrollView(this);s.setFillViewport(true);s.setClipToPadding(false);s.addView(c);return s;}
    private void styleDialog(AlertDialog dialog){if(dialog==null)return;Window w=dialog.getWindow();if(w!=null){w.setBackgroundDrawable(round(SURFACE,LINE,24));w.setDimAmount(.72f);w.setWindowAnimations(0);}Button pos=dialog.getButton(AlertDialog.BUTTON_POSITIVE),neg=dialog.getButton(AlertDialog.BUTTON_NEGATIVE);if(pos!=null)pos.setTextColor(TEXT);if(neg!=null)neg.setTextColor(MUTED);}
    private void finishDialogStyle(AlertDialog dialog){styleDialog(dialog);}
    private void screen(View v){root.removeAllViews();root.addView(v,new FrameLayout.LayoutParams(-1,-1));}
    private void section(LinearLayout c,String s){TextView t=text(s,14,MUTED);t.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));t.setLetterSpacing(.035f);add(c,t,-1,dp(22),15,7);}
    private ImageView iconView(int res,int size){ImageView i=new ImageView(this);i.setImageResource(res);i.setColorFilter(TEXT);i.setScaleType(ImageView.ScaleType.CENTER);i.setPadding(0,0,0,0);return i;}
    private Button sourceCard(String label,String detail,int iconRes,boolean enabled){Button b=new Button(this);b.setAllCaps(false);b.setTextColor(enabled?TEXT:MUTED);b.setTextSize(13);b.setTypeface(Typeface.DEFAULT,Typeface.BOLD);b.setGravity(Gravity.CENTER_VERTICAL|Gravity.LEFT);b.setIncludeFontPadding(false);b.setPadding(dp(12),0,dp(8),0);b.setMinHeight(0);b.setStateListAnimator(null);try{android.graphics.drawable.Drawable d=getDrawable(iconRes);if(d!=null){d.setTint(enabled?TEXT:MUTED);b.setCompoundDrawablesWithIntrinsicBounds(d,null,null,null);b.setCompoundDrawablePadding(dp(12));}}catch(Exception ignored){}SpannableString ss=new SpannableString(label+"\n"+detail);ss.setSpan(new android.text.style.ForegroundColorSpan(enabled?TEXT:MUTED),0,label.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);ss.setSpan(new android.text.style.ForegroundColorSpan(MUTED),label.length()+1,ss.length(),Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);b.setText(ss);b.setBackground(round(SURFACE,LINE,16));b.setAlpha(enabled?1f:.48f);return b;}
    private Button quickCard(String label,int iconRes,boolean enabled){Button b=button(label,false);b.setTextSize(11);b.setMinHeight(0);b.setPadding(dp(3),0,dp(3),0);b.setAlpha(enabled?1f:.42f);try{android.graphics.drawable.Drawable d=getDrawable(iconRes);if(d!=null){d.setTint(TEXT);b.setCompoundDrawablesWithIntrinsicBounds(d,null,null,null);b.setCompoundDrawablePadding(dp(5));}}catch(Exception ignored){}return b;}
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
        LinearLayout.LayoutParams logoLp=new LinearLayout.LayoutParams(dp(48),dp(48));
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
        top.addView(brand,new LinearLayout.LayoutParams(0,dp(54),1));

        ImageButton settings=new ImageButton(this);
        settings.setImageResource(R.drawable.ic_settings_small);
        settings.setColorFilter(TEXT);
        settings.setScaleType(ImageView.ScaleType.CENTER);
        settings.setBackground(round(Color.rgb(34,34,36),Color.TRANSPARENT,50));
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
        profile.addView(ptext,new LinearLayout.LayoutParams(0,dp(52),1));
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
        ImageView sIcon=iconView(R.drawable.ic_link,24); sIcon.setLayoutParams(new LinearLayout.LayoutParams(dp(32),dp(32)));
        sRow.addView(sIcon);
        EditText s=input("SuperBase Server Address");
        s.setText(prefs.getString("server",""));
        s.setTextSize(12);
        s.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
        s.setBackgroundColor(Color.TRANSPARENT);
        sRow.addView(s,new LinearLayout.LayoutParams(0,dp(46),1));
        add(content,sRow,-1,dp(46),0,8);

        LinearLayout kRow=row();
        kRow.setPadding(dp(8),0,dp(8),0);
        kRow.setBackground(round(SURFACE,LINE,15));
        ImageView kIcon=iconView(R.drawable.ic_key,24); kIcon.setLayoutParams(new LinearLayout.LayoutParams(dp(32),dp(32)));
        kRow.addView(kIcon);
        EditText k=input("Superbase Public Key");
        k.setText(prefs.getString("key",""));
        k.setInputType(129);
        k.setTextSize(12);
        k.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
        k.setBackgroundColor(Color.TRANSPARENT);
        kRow.addView(k,new LinearLayout.LayoutParams(0,dp(46),1));
        add(content,kRow,-1,dp(46),0,8);

        LinearLayout uRow=row();
        uRow.setPadding(dp(8),0,dp(8),0);
        uRow.setBackground(round(SURFACE,LINE,15));
        ImageView uIcon=iconView(R.drawable.ic_person,24); uIcon.setLayoutParams(new LinearLayout.LayoutParams(dp(32),dp(32)));
        uRow.addView(uIcon);
        EditText u=input("Username");
        u.setText(prefs.getString("user",""));
        u.setTextSize(12);
        u.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
        u.setBackgroundColor(Color.TRANSPARENT);
        uRow.addView(u,new LinearLayout.LayoutParams(0,dp(46),1));
        add(content,uRow,-1,dp(46),0,28);

        section(content,"ROOM");

        LinearLayout rRow=row();
        rRow.setPadding(dp(8),0,dp(8),0);
        rRow.setBackground(round(SURFACE,LINE,15));
        TextView hash=text("#",25,TEXT); hash.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL)); hash.setGravity(Gravity.CENTER); hash.setIncludeFontPadding(false);
        rRow.addView(hash,new LinearLayout.LayoutParams(dp(32),dp(32)));
        EditText r=input("Room name");
        r.setText(prefs.getString("room","Movie Night"));
        r.setTextSize(12);
        r.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
        r.setBackgroundColor(Color.TRANSPARENT);
        rRow.addView(r,new LinearLayout.LayoutParams(0,dp(46),1));
        add(content,rRow,-1,dp(46),0,8);

        LinearLayout pwRow=row();
        pwRow.setPadding(dp(8),0,dp(8),0);
        pwRow.setBackground(round(SURFACE,LINE,15));
        ImageView pwIcon=iconView(R.drawable.ic_lock,24); pwIcon.setLayoutParams(new LinearLayout.LayoutParams(dp(32),dp(32)));
        pwRow.addView(pwIcon);
        EditText pw=input("Room password  •  optional");
        pw.setInputType(129);
        pw.setTextSize(12);
        pw.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));
        pw.setBackgroundColor(Color.TRANSPARENT);
        pwRow.addView(pw,new LinearLayout.LayoutParams(0,dp(46),1));
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
        wrapper.addView(grid,new LinearLayout.LayoutParams(-1,-2));Button custom=iconButton("Choose from device",R.drawable.ic_image,false);wrapper.addView(custom,new LinearLayout.LayoutParams(-1,dp(58)));holder[0]=new AlertDialog.Builder(this).setTitle("Profile picture").setView(wrapper).setNegativeButton("Cancel",null).create();custom.setOnClickListener(v->{Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("image/*");i.addCategory(Intent.CATEGORY_OPENABLE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,PICK_PFP);if(holder[0]!=null)holder[0].dismiss();});holder[0].show();styleDialog(holder[0]);
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
        modes.addView(dark,new LinearLayout.LayoutParams(0,dp(48),1));
        modes.addView(new Space(this),new LinearLayout.LayoutParams(dp(8),1));
        modes.addView(light,new LinearLayout.LayoutParams(0,dp(48),1));
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
        dialogTitle.addView(dialogTitleText,new LinearLayout.LayoutParams(0,dp(48),1));

        ImageButton close=new ImageButton(this);
        close.setImageResource(R.drawable.ic_close);
        close.setColorFilter(TEXT);
        close.setScaleType(ImageView.ScaleType.CENTER);
        close.setBackgroundColor(Color.TRANSPARENT);
        close.setPadding(0,0,0,0);
        close.setMinimumWidth(0);
        close.setMinimumHeight(0);
        close.setContentDescription("Close settings");
        dialogTitle.addView(close,new LinearLayout.LayoutParams(dp(36),dp(36)));

        AlertDialog dialog=new AlertDialog.Builder(this).setCustomTitle(dialogTitle).setView(box).create();
        dark.setOnClickListener(v->{prefs.edit().putBoolean("light_mode",false).apply();lightMode=false;AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);dialog.dismiss();applyThemeColors();showConnect();});
        light.setOnClickListener(v->{prefs.edit().putBoolean("light_mode",true).apply();lightMode=true;AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);dialog.dismiss();applyThemeColors();showConnect();});
        done.setOnClickListener(v->dialog.dismiss());
        close.setOnClickListener(v->dialog.dismiss());
        dialog.show();
        styleDialog(dialog);
    }

    private void dialogThemeSafe(AlertDialog parent){
        final String[] names={"Default","Messenger-style","WhatsApp-style","Custom"};
        final String[] keys={"default","messenger","whatsapp","custom"};
        String current=prefs.getString("theme_preset","default");int checked=0;for(int i=0;i<keys.length;i++)if(keys[i].equals(current))checked=i;
        new AlertDialog.Builder(this).setTitle("Room & chat theme").setSingleChoiceItems(names,checked,(d,w)->{
            if(w==3){d.dismiss();if(parent!=null)parent.dismiss();showCustomThemeEditor();return;}
            prefs.edit().putString("theme_preset",keys[w]).apply();applyThemeColors();d.dismiss();if(parent!=null)parent.dismiss();if(roomRoot!=null){updateActiveBackgrounds();}else showConnect();
        }).setNegativeButton("Cancel",null).show();
    }
    private void showCustomThemeEditor(){
        LinearLayout box=col();box.setPadding(dp(18),dp(8),dp(18),dp(8));
        TextView preview=text("Background preview",14,TEXT);preview.setGravity(Gravity.CENTER);
        FrameLayout previewBox=new FrameLayout(this);previewBox.setBackground(gradientBackground(customThemeBg,customThemeBg2));previewBox.addView(preview,new FrameLayout.LayoutParams(-1,dp(110),Gravity.CENTER));add(box,previewBox,-1,dp(110),0,10);
        TextView hint=text("Choose the room/chat background. Your accent colour remains separate.",12,MUTED);hint.setLineSpacing(dp(2),1f);add(box,hint,-1,-2,0,10);
        Button bg=button("Background colour",false);add(box,bg,-1,dp(48),0,7);
        Button bg2=button("Gradient second colour",false);add(box,bg2,-1,dp(48),0,7);
        Button save=button("Use custom theme",true);add(box,save,-1,dp(52),8,0);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Custom theme").setView(box).setNegativeButton("Cancel",null).create();
        bg.setOnClickListener(v->{ColorWheelView wheel=new ColorWheelView(this,customThemeBg);LinearLayout w=col();w.setPadding(dp(12),dp(6),dp(12),dp(6));w.addView(wheel,new LinearLayout.LayoutParams(-1,dp(240)));new AlertDialog.Builder(this).setTitle("Background colour").setView(w).setNegativeButton("Cancel",null).setPositiveButton("Use",(x,which)->{customThemeBg=wheel.selectedColor;previewBox.setBackground(gradientBackground(customThemeBg,customThemeBg2));}).show();});
        bg2.setOnClickListener(v->{ColorWheelView wheel=new ColorWheelView(this,customThemeBg2);LinearLayout w=col();w.setPadding(dp(12),dp(6),dp(12),dp(6));w.addView(wheel,new LinearLayout.LayoutParams(-1,dp(240)));new AlertDialog.Builder(this).setTitle("Gradient second colour").setView(w).setNegativeButton("Cancel",null).setPositiveButton("Use",(x,which)->{customThemeBg2=wheel.selectedColor;previewBox.setBackground(gradientBackground(customThemeBg,customThemeBg2));}).show();});
        save.setOnClickListener(v->{prefs.edit().putString("theme_preset","custom").putInt("custom_theme_bg",customThemeBg).putInt("custom_theme_bg2",customThemeBg2).apply();applyThemeColors();dialog.dismiss();if(roomRoot!=null)updateActiveBackgrounds();else showConnect();});
        dialog.show();
    }

    private void showColorWheel(){
        ColorWheelView wheel=new ColorWheelView(this,PRIMARY);
        LinearLayout box=col();box.setPadding(dp(18),dp(8),dp(18),dp(8));box.addView(wheel,new LinearLayout.LayoutParams(-1,dp(250)));
        TextView preview=text("Accent preview",14,TEXT);preview.setGravity(Gravity.CENTER);box.addView(preview,new LinearLayout.LayoutParams(-1,dp(36)));
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Accent colour").setView(box).setNegativeButton("Cancel",null).setPositiveButton("Use colour",null).create();
        wheel.setListener(color->{preview.setText(String.format(Locale.US,"#%06X",0xFFFFFF&color));preview.setTextColor(color);});
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x->{prefs.edit().putInt("accent_color",wheel.selectedColor).apply();applyThemeColors();dialog.dismiss();showConnect();}));dialog.show();
    }

    private void showDeveloperSettings(){
        final LinearLayout box=col();box.setPadding(dp(20),dp(8),dp(20),dp(10));
        TextView warning=text("Developer tools are temporary testing controls. They do not change the core room synchronization engine.",13,MUTED);warning.setLineSpacing(dp(2),1f);add(box,warning,-1,-2,0,12);
        Switch enabled=new Switch(this);enabled.setText("Developer mode");enabled.setTextColor(TEXT);enabled.setChecked(prefs.getBoolean("dev_mode",false));add(box,enabled,-1,dp(50),0,6);
        Switch verbose=new Switch(this);verbose.setText("Verbose resolver errors");verbose.setTextColor(TEXT);verbose.setChecked(prefs.getBoolean("dev_verbose_errors",true));add(box,verbose,-1,dp(50),0,8);
        Button testClient=button("Open Second Test Client (same app)",false);add(box,testClient,-1,dp(50),0,8);
        TextView testHint=text("Opens a second Synka client with its own username/profile and its own realtime connection. No Dual Space or second APK is required.",11,MUTED);testHint.setLineSpacing(dp(2),1f);add(box,testHint,-1,-2,0,8);
        testClient.setOnClickListener(v->{ Intent i=new Intent(this,MainActivity.class);i.putExtra("client_slot",2);i.addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT|Intent.FLAG_ACTIVITY_MULTIPLE_TASK);startActivity(i); });
        TextView label=text("Legacy Dual Space label (optional)",13,MUTED);add(box,label,-1,dp(25),0,4);
        EditText instance=input("Example: Clone A");instance.setText(prefs.getString("dev_instance_label",""));add(box,instance,-1,dp(54),0,5);
        TextView hint=text("The old label feature is kept for compatibility. For testing two users inside Synka itself, use “Open Second Test Client” above.",11,MUTED);hint.setLineSpacing(dp(2),1f);add(box,hint,-1,-2,0,10);
        Button reset=button("Reset developer settings",false);add(box,reset,-1,dp(50),0,6);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Developer Settings").setView(scroll(box)).setNegativeButton("Cancel",null).setPositiveButton("Save",null).create();
        reset.setOnClickListener(v->{enabled.setChecked(false);verbose.setChecked(true);instance.setText("");});
        dialog.setOnShowListener(v->dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(x->{
            prefs.edit().putBoolean("dev_mode",enabled.isChecked()).putBoolean("dev_verbose_errors",verbose.isChecked()).putString("dev_instance_label",instance.getText().toString().trim()).apply();
            dialog.dismiss();showConnect();
        }));
        dialog.show();
    }

    private Intent serviceIntent(String action){
        return new Intent(this,syncServiceClass).setAction(action).putExtra("client_slot",clientSlot);
    }

    private void sendPresenceUpdate(){
        Intent i=serviceIntent(SyncService.ACTION_PRESENCE_UPDATE).putExtra("pfp",profilePicture).putExtra("status",currentPresenceStatus());
        startService(i);
    }

    private void connect(String server,String key,String user,String room,String pass){
        if(server.isEmpty()||key.isEmpty()||user.isEmpty()||room.isEmpty()){toast("Server, public key, username and room are required.");return;}
        String finalUser=user;String instanceLabel=prefs.getString("dev_instance_label","").trim();if(prefs.getBoolean("dev_mode",false)&&!instanceLabel.isEmpty()){String suffix=" ["+instanceLabel+"]";if(!finalUser.endsWith(suffix))finalUser+=suffix;}serverUrl=server;anonKey=key;username=finalUser;roomName=room;roomPassword=pass;ensureProfile();
        prefs.edit().putString("server",server).putString("key",key).putString("user",user).putString("room",room).apply();
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

    private void showWatchModeMenu(){
        if(player==null) showRoom();
        showMediaChooser();
    }

    private Button compactSourceButton(String label,int iconRes){
        Button b=iconButton(label,iconRes,false);
        b.setTextSize(11);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(0); b.setMinWidth(0);
        b.setPadding(dp(4),0,dp(4),0);
        b.setCompoundDrawablePadding(dp(6));
        b.setIncludeFontPadding(false);
        return b;
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
        mediaChooserOverlay.setBackgroundColor(Color.argb(238,7,11,20));
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
        roomSessionGeneration++;roomRoot=new FrameLayout(this);roomRoot.setBackground(createRoomBackground());ScrollView sc=scroll(col());roomContent=(LinearLayout)sc.getChildAt(0);roomContent.setPadding(dp(16),dp(10),dp(16),dp(22));roomRoot.addView(sc,new FrameLayout.LayoutParams(-1,-1));screen(roomRoot);
        roomHeader=row();ImageButton back=new ImageButton(this);back.setImageResource(R.drawable.ic_back);back.setColorFilter(TEXT);back.setBackgroundColor(Color.TRANSPARENT);back.setPadding(0,0,0,0);back.setScaleType(ImageView.ScaleType.CENTER);roomHeader.addView(back,new LinearLayout.LayoutParams(dp(48),dp(56)));TextView h=title(roomName,20);h.setGravity(Gravity.CENTER_VERTICAL);roomHeader.addView(h,new LinearLayout.LayoutParams(0,dp(52),1));syncStatusLabel=text("✓  Synced",11,TEXT);syncStatusLabel.setGravity(Gravity.CENTER);roomHeader.addView(syncStatusLabel,new LinearLayout.LayoutParams(dp(82),dp(56)));Button peopleTop=iconButton(String.valueOf(Math.max(1,participants.size())),R.drawable.ic_people,false);participantCount=peopleTop;peopleTop.setTextSize(12);roomHeader.addView(peopleTop,new LinearLayout.LayoutParams(dp(58),dp(48)));Button settingsTop=iconOnlyButton(R.drawable.ic_settings,false);roomHeader.addView(settingsTop,new LinearLayout.LayoutParams(dp(32),dp(32)));add(roomContent,roomHeader,-1,dp(56),0,7);back.setOnClickListener(v->disconnect());peopleTop.setOnClickListener(v->showParticipantSheet());settingsTop.setOnClickListener(v->showSettingsDialog());
        connectionLabel=text("●  Connected",11,TEXT);connectionLabel.setGravity(Gravity.CENTER_VERTICAL);add(roomContent,connectionLabel,-1,dp(24),0,8);
        roomPeople=col();roomPeople.setPadding(dp(14),dp(10),dp(14),dp(10));roomPeople.setBackground(round(SURFACE,LINE,18));LinearLayout peopleTitle=row();peopleTitle.addView(iconView(R.drawable.ic_people,24),new LinearLayout.LayoutParams(dp(30),dp(24)));participantSummary=text(formatPeopleWatching(Math.max(1,participants.size())),13,TEXT);participantSummary.setTypeface(Typeface.DEFAULT,Typeface.BOLD);peopleTitle.addView(participantSummary,new LinearLayout.LayoutParams(0,dp(24),1));roomPeople.addView(peopleTitle,new LinearLayout.LayoutParams(-1,dp(26)));HorizontalScrollView hsv=new HorizontalScrollView(this);hsv.setHorizontalScrollBarEnabled(false);participantStrip=row();hsv.addView(participantStrip,new HorizontalScrollView.LayoutParams(-2,dp(58)));roomPeople.addView(hsv,new LinearLayout.LayoutParams(-1,dp(58)));participantList=text("",1,TEXT);participantList.setVisibility(View.GONE);roomPeople.addView(participantList,new LinearLayout.LayoutParams(1,1));add(roomContent,roomPeople,-1,dp(94),0,10);roomPeople.setOnClickListener(v->showParticipantSheet());
        roomPlayerBox=new FrameLayout(this);roomPlayerBox.setBackground(round(Color.BLACK,LINE,16));View pv=getLayoutInflater().inflate(R.layout.view_player,roomPlayerBox,false);roomPlayerBox.addView(pv,new FrameLayout.LayoutParams(-1,-1));playerView=(PlayerView)pv;adjustedSubtitleView=new SubtitleView(this);adjustedSubtitleView.setVisibility(ccEnabled?View.VISIBLE:View.GONE);adjustedSubtitleView.setClickable(false);adjustedSubtitleView.setFocusable(false);roomPlayerBox.addView(adjustedSubtitleView,new FrameLayout.LayoutParams(-1,-1));playerEmptyOverlay=buildEmptyPlayerOverlay();roomPlayerBox.addView(playerEmptyOverlay,new FrameLayout.LayoutParams(-1,-1));setPlayerEmptyVisible(true);add(roomContent,roomPlayerBox,-1,dp(245),0,7);
        mediaInfo=row();mediaLabel=text("No media selected",10,MUTED);mediaInfo.addView(mediaLabel,new LinearLayout.LayoutParams(-1,dp(1)));mediaInfo.setVisibility(View.GONE);add(roomContent,mediaInfo,-1,dp(1),0,0);syncStatusLabel.setContentDescription("Synchronization status");
        TextView watchTitle=text("WATCH TOGETHER",12,MUTED);watchTitle.setTypeface(Typeface.DEFAULT,Typeface.BOLD);watchTitle.setLetterSpacing(.06f);add(roomContent,watchTitle,-1,dp(24),15,7);roomMediaRow=col();LinearLayout r1=row();Button localSource=sourceCard("Local Video","From this device",R.drawable.ic_folder,true);Button linkSource=sourceCard("Watch From Link","YouTube & public links",R.drawable.ic_link,true);r1.addView(localSource,new LinearLayout.LayoutParams(0,dp(62),1));r1.addView(new Space(this),new LinearLayout.LayoutParams(dp(8),1));r1.addView(linkSource,new LinearLayout.LayoutParams(0,dp(62),1));roomMediaRow.addView(r1,new LinearLayout.LayoutParams(-1,dp(62)));LinearLayout r2=row();Button upload=sourceCard("Upload to Server","Share and watch together",R.drawable.ic_upload,true);Button movies=sourceCard("Watch Movies","Pre-uploaded videos",R.drawable.ic_movie,false);r2.addView(upload,new LinearLayout.LayoutParams(0,dp(62),1));r2.addView(new Space(this),new LinearLayout.LayoutParams(dp(8),1));r2.addView(movies,new LinearLayout.LayoutParams(0,dp(62),1));roomMediaRow.addView(r2,new LinearLayout.LayoutParams(-1,dp(62)));add(roomContent,roomMediaRow,-1,dp(132),0,12);localSource.setOnClickListener(v->pickVideo());linkSource.setOnClickListener(v->showLinkDialog());upload.setOnClickListener(v->toast("Server upload is coming soon."));
        roomQuick=row();Button cb=quickCard("Chat",R.drawable.ic_chat,true);Button pb=quickCard("People",R.drawable.ic_people,true);Button audio=quickCard("Audio Call",R.drawable.ic_audio,false);Button more=quickCard("More",R.drawable.ic_more,true);roomQuick.addView(cb,new LinearLayout.LayoutParams(0,dp(60),1));roomQuick.addView(new Space(this),new LinearLayout.LayoutParams(dp(7),1));roomQuick.addView(pb,new LinearLayout.LayoutParams(0,dp(60),1));roomQuick.addView(new Space(this),new LinearLayout.LayoutParams(dp(7),1));roomQuick.addView(audio,new LinearLayout.LayoutParams(0,dp(60),1));roomQuick.addView(new Space(this),new LinearLayout.LayoutParams(dp(7),1));roomQuick.addView(more,new LinearLayout.LayoutParams(0,dp(60),1));add(roomContent,roomQuick,-1,dp(60),0,14);cb.setOnClickListener(v->openChat());pb.setOnClickListener(v->showParticipantSheet());more.setOnClickListener(v->showMoreRoomMenu());
        Button leave=button("Leave room",false);leave.setTextSize(14);add(roomContent,leave,-1,dp(58),0,0);leave.setOnClickListener(v->disconnect());
        initPlayer();updateActiveBackgrounds();refreshParticipants();updateChatBadges();if(!pendingRemoteWebSource.isEmpty()){String queued=pendingRemoteWebSource;pendingRemoteWebSource="";uiHandler.postDelayed(()->loadOnlineSource(queued,false),80);}sendStateRequest();
    }
    private View buildEmptyPlayerOverlay(){FrameLayout f=new FrameLayout(this);LinearLayout center=col();center.setGravity(Gravity.CENTER);ImageView play=new ImageView(this);play.setImageResource(R.drawable.ic_play);play.setColorFilter(TEXT);play.setBackground(round(SURFACE2,Color.TRANSPARENT,50));center.addView(play,new LinearLayout.LayoutParams(dp(72),dp(72)));TextView t=title("No media selected",15);t.setGravity(Gravity.CENTER);add(center,t,-1,dp(30),8,0);TextView d=text("Choose a mode below to start watching together",11,MUTED);d.setGravity(Gravity.CENTER);add(center,d,-1,dp(28),0,0);FrameLayout.LayoutParams cp=new FrameLayout.LayoutParams(-1,dp(150),Gravity.CENTER);f.addView(center,cp);return f;}
    private void showMoreRoomMenu(){LinearLayout box=col();box.setPadding(dp(14),dp(8),dp(14),dp(10));Button ps=iconButton("Player settings",R.drawable.ic_settings,false);Button people=iconButton("People in room",R.drawable.ic_people,false);add(box,ps,-1,dp(56),0,8);add(box,people,-1,dp(56),0,0);AlertDialog dialog=new AlertDialog.Builder(this).setTitle("More").setView(box).setNegativeButton("Cancel",null).create();ps.setOnClickListener(v->{dialog.dismiss();showPlayerSettings();});people.setOnClickListener(v->{dialog.dismiss();showParticipantSheet();});dialog.show();styleDialog(dialog);}

    private void showParticipantSheet(){
        ScrollView scrollBox=new ScrollView(this); scrollBox.setFillViewport(true); scrollBox.setVerticalScrollBarEnabled(true); scrollBox.setScrollbarFadingEnabled(false);
        LinearLayout box=col();box.setPadding(dp(10),dp(6),dp(10),dp(8));
        for(Map.Entry<String,String> e:participants.entrySet()){
            String key=e.getKey(),name=e.getValue(); LinearLayout line=row(); line.setPadding(dp(8),dp(7),dp(8),dp(7)); line.setGravity(Gravity.CENTER_VERTICAL);
            ImageView a=new ImageView(this);a.setImageBitmap(loadPfpBitmap(participantPfps.getOrDefault(key,"asset:0")));a.setScaleType(ImageView.ScaleType.CENTER_CROP);a.setBackground(round(SURFACE2,LINE,50));line.addView(a,new LinearLayout.LayoutParams(dp(32),dp(32)));
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
        LinearLayout speeds=row();float[] speedValues={0.5f,0.75f,1f,1.25f,1.5f,2f};for(float spd:speedValues){Button b=button(spd==1f?"1×":String.format(Locale.US,"%.2g×",spd),false);b.setTextSize(12);if(player!=null&&Math.abs(player.getPlaybackParameters().speed-spd)<.01f)b.setBackground(round(SURFACE2,Color.WHITE,14));speeds.addView(b,new LinearLayout.LayoutParams(0,dp(46),1));if(spd!=2f)speeds.addView(new Space(this),new LinearLayout.LayoutParams(dp(5),1));b.setOnClickListener(v->{if(player!=null)player.setPlaybackSpeed(spd);});}add(box,speeds,-1,dp(46),0,10);
        LinearLayout vol=col();vol.setPadding(dp(12),dp(10),dp(12),dp(8));vol.setBackground(round(SURFACE,LINE,16));LinearLayout vh=row();vh.addView(iconView(R.drawable.ic_audio,28),new LinearLayout.LayoutParams(dp(38),dp(36)));LinearLayout vw=col();add(vw,title("Volume",14),-1,dp(22),0,1);add(vw,text("Adjust or boost the player volume (up to 200%)",10,MUTED),-1,dp(20),0,0);vh.addView(vw,new LinearLayout.LayoutParams(0,dp(44),1));TextView volValue=title("100%",13);volValue.setGravity(Gravity.CENTER);volValue.setBackground(round(SURFACE2,LINE,12));vh.addView(volValue,new LinearLayout.LayoutParams(dp(66),dp(38)));vol.addView(vh,new LinearLayout.LayoutParams(-1,dp(44)));SeekBar seek=new SeekBar(this);seek.setMax(200);seek.setProgress(player==null?100:Math.round(player.getVolume()*100));vol.addView(seek,new LinearLayout.LayoutParams(-1,dp(36)));LinearLayout vl=row();TextView v0=text("0%",10,MUTED);TextView v100=text("100%",10,MUTED);v100.setGravity(Gravity.CENTER);TextView v200=text("200%",10,MUTED);v200.setGravity(Gravity.RIGHT);vl.addView(v0,new LinearLayout.LayoutParams(0,dp(22),1));vl.addView(v100,new LinearLayout.LayoutParams(0,dp(22),1));vl.addView(v200,new LinearLayout.LayoutParams(0,dp(22),1));vol.addView(vl,new LinearLayout.LayoutParams(-1,dp(22)));add(box,vol,-1,dp(126),0,10);seek.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar s,int p,boolean from){volValue.setText(p+"%");if(from&&player!=null)player.setVolume(p/100f);}public void onStartTrackingTouch(SeekBar s){}public void onStopTrackingTouch(SeekBar s){}});
        LinearLayout audio=row();audio.setPadding(dp(12),dp(8),dp(10),dp(8));audio.setBackground(round(SURFACE,LINE,16));audio.addView(iconView(R.drawable.ic_audio,28),new LinearLayout.LayoutParams(dp(42),dp(44)));LinearLayout aw=col();add(aw,title("Audio Track",14),-1,dp(22),0,1);add(aw,text("Select audio track (e.g. different languages)",10,MUTED),-1,dp(20),0,0);audio.addView(aw,new LinearLayout.LayoutParams(0,dp(44),1));TextView ar=text("›",28,MUTED);ar.setGravity(Gravity.CENTER);audio.addView(ar,new LinearLayout.LayoutParams(dp(34),dp(44)));add(box,audio,-1,dp(62),0,8);audio.setOnClickListener(v->{if((player==null||player.getMediaItemCount()==0)&&!(webMode&&webPageReady)){toast("Load a video first.");return;}try{new TrackSelectionDialogBuilder(this,"Audio track",player,C.TRACK_TYPE_AUDIO).setAllowAdaptiveSelections(false).setShowDisableOption(false).build().show();}catch(Exception e){toast("No selectable audio tracks available.");}});
        LinearLayout subtitle=row();subtitle.setPadding(dp(12),dp(8),dp(10),dp(8));subtitle.setBackground(round(SURFACE,LINE,16));subtitle.addView(iconView(R.drawable.ic_cc,28),new LinearLayout.LayoutParams(dp(42),dp(44)));LinearLayout sw=col();add(sw,title("Subtitle Track",14),-1,dp(22),0,1);add(sw,text("Select subtitle track",10,MUTED),-1,dp(20),0,0);subtitle.addView(sw,new LinearLayout.LayoutParams(0,dp(44),1));TextView sr=text("›",28,MUTED);sr.setGravity(Gravity.CENTER);subtitle.addView(sr,new LinearLayout.LayoutParams(dp(34),dp(44)));add(box,subtitle,-1,dp(62),0,8);subtitle.setOnClickListener(v->{if(player==null||player.getMediaItemCount()==0){toast("Load a video first.");return;}try{new TrackSelectionDialogBuilder(this,"Subtitle track",player,C.TRACK_TYPE_TEXT).setAllowAdaptiveSelections(false).setShowDisableOption(true).build().show();}catch(Exception e){toast("No subtitle tracks available.");}});
        LinearLayout delay=row();delay.setPadding(dp(12),dp(8),dp(10),dp(8));delay.setBackground(round(SURFACE,LINE,16));delay.addView(iconView(R.drawable.ic_history,28),new LinearLayout.LayoutParams(dp(42),dp(44)));LinearLayout dw=col();add(dw,title("Subtitle Delay",14),-1,dp(22),0,1);add(dw,text("Adjust subtitle timing",10,MUTED),-1,dp(20),0,0);delay.addView(dw,new LinearLayout.LayoutParams(0,dp(44),1));Button minus=button("−",false);Button plus=button("+",false);TextView delayText=text(formatDelay(subtitleDelayMs),12,TEXT);delayText.setGravity(Gravity.CENTER);delay.addView(minus,new LinearLayout.LayoutParams(dp(46),dp(42)));delay.addView(delayText,new LinearLayout.LayoutParams(dp(78),dp(42)));delay.addView(plus,new LinearLayout.LayoutParams(dp(46),dp(42)));add(box,delay,-1,dp(62),0,8);minus.setOnClickListener(v->{subtitleDelayMs=Math.max(-10000,subtitleDelayMs-250);prefs.edit().putInt("subtitle_delay_ms",subtitleDelayMs).apply();delayText.setText(formatDelay(subtitleDelayMs));if(subtitleUri!=null)applySubtitleWithoutSync();else if(lastCueGroup!=null)applyCueGroupWithDelay(lastCueGroup);});plus.setOnClickListener(v->{subtitleDelayMs=Math.min(10000,subtitleDelayMs+250);prefs.edit().putInt("subtitle_delay_ms",subtitleDelayMs).apply();delayText.setText(formatDelay(subtitleDelayMs));if(subtitleUri!=null)applySubtitleWithoutSync();else if(lastCueGroup!=null)applyCueGroupWithDelay(lastCueGroup);});
        Button upload=iconButton("Upload Custom Subtitle",R.drawable.ic_upload,false);add(box,upload,-1,dp(56),0,8);upload.setOnClickListener(v->{if(videoUri==null){toast("Load a video first.");return;}Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT);i.setType("text/*");i.putExtra(Intent.EXTRA_MIME_TYPES,new String[]{"text/plain","text/vtt","application/x-subrip","text/ssa","text/x-ssa"});i.addCategory(Intent.CATEGORY_OPENABLE);i.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION|Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION);startActivityForResult(i,PICK_SUBTITLE);});
        Button clear=iconButton("Clear Custom Subtitle",R.drawable.ic_close,false);add(box,clear,-1,dp(56),0,4);clear.setOnClickListener(v->{subtitleUri=null;applySubtitleWithoutSync();});TextView status=text(subtitleUri==null?"No custom subtitle loaded":"Custom: "+displayName(subtitleUri),10,MUTED);add(box,status,-1,dp(24),0,4);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Player Settings").setView(scroll(box)).setPositiveButton("Done",null).create();dialog.show();styleDialog(dialog);finishDialogStyle(dialog);
    }

    private String formatDelay(int ms){return (ms>=0?"+":"")+(ms/1000.0f)+" s";}

    private void setCaptionEnabled(boolean enabled){
        ccEnabled=enabled;
        if(trackSelector!=null){
            try{trackSelector.setParameters(trackSelector.buildUponParameters().setTrackTypeDisabled(C.TRACK_TYPE_TEXT,!enabled).build());}catch(Exception ignored){}
        }
        if(adjustedSubtitleView!=null){
            adjustedSubtitleView.setVisibility(enabled?View.VISIBLE:View.GONE);
            if(enabled && lastCueGroup!=null) applyCueGroupWithDelay(lastCueGroup);
        }
    }

    private void applyCueGroupWithDelay(CueGroup group){
        lastCueGroup=group;
        if(adjustedSubtitleView==null)return;
        if(pendingSubtitleRunnable!=null)uiHandler.removeCallbacks(pendingSubtitleRunnable);
        if(!ccEnabled){adjustedSubtitleView.setVisibility(View.GONE);return;}
        adjustedSubtitleView.setVisibility(View.VISIBLE);
        long targetMs;
        if(group.presentationTimeUs!=C.TIME_UNSET){
            targetMs=(group.presentationTimeUs/1000L)+subtitleDelayMs;
        }else{
            targetMs=player==null?0:player.getCurrentPosition()+subtitleDelayMs;
        }
        long current=player==null?0:player.getCurrentPosition();
        long delay=Math.max(0L,targetMs-current);
        pendingSubtitleRunnable=()->{
            if(adjustedSubtitleView!=null && ccEnabled) adjustedSubtitleView.setCues(group.cues);
        };
        if(delay==0)pendingSubtitleRunnable.run();else uiHandler.postDelayed(pendingSubtitleRunnable,Math.min(delay,15000L));
    }

    private MediaItem buildMediaItem(){
        if(videoUri==null)return null;
        MediaItem.Builder b=new MediaItem.Builder().setUri(videoUri);
        if(subtitleUri!=null){String mime=subtitleMime(subtitleUri);if(mime!=null){
            MediaItem.SubtitleConfiguration cfg=new MediaItem.SubtitleConfiguration.Builder(subtitleUri).setMimeType(mime).setLanguage("en").setSelectionFlags(C.SELECTION_FLAG_DEFAULT).build();
            b.setSubtitleConfigurations(Collections.singletonList(cfg));}}
        return b.build();
    }
    private String subtitleMime(Uri uri){String n=displayName(uri).toLowerCase(Locale.ROOT);if(n.endsWith(".vtt"))return MimeTypes.TEXT_VTT;if(n.endsWith(".srt"))return MimeTypes.APPLICATION_SUBRIP;if(n.endsWith(".ass")||n.endsWith(".ssa"))return MimeTypes.TEXT_SSA;return MimeTypes.APPLICATION_SUBRIP;}
    private Uri createShiftedSubtitle(Uri source,int offsetMs){
        try{java.io.InputStream in=getContentResolver().openInputStream(source);if(in==null)return null;java.io.BufferedReader br=new java.io.BufferedReader(new java.io.InputStreamReader(in,StandardCharsets.UTF_8));StringBuilder all=new StringBuilder();String line;while((line=br.readLine())!=null){all.append(line).append('\n');}br.close();String n=displayName(source).toLowerCase(Locale.ROOT);String outText;if(n.endsWith(".ass")||n.endsWith(".ssa"))outText=shiftAss(all.toString(),offsetMs);else outText=shiftSrtVtt(all.toString(),offsetMs);java.io.File f=new java.io.File(getCacheDir(),"subtitle_shifted_"+Math.abs(offsetMs)+"_"+SystemClock.uptimeMillis()+n.substring(n.lastIndexOf('.')));java.io.FileOutputStream fos=new java.io.FileOutputStream(f);fos.write(outText.getBytes(StandardCharsets.UTF_8));fos.close();return Uri.fromFile(f);}catch(Exception e){return null;}}
    private String shiftSrtVtt(String text,int offset){java.util.regex.Pattern p=java.util.regex.Pattern.compile("(\\d{2}:\\d{2}:\\d{2}\\.\\d{3})\\s*-->\\s*(\\d{2}:\\d{2}:\\d{2}\\.\\d{3})");java.util.regex.Matcher m=p.matcher(text);StringBuffer out=new StringBuffer();while(m.find()){String a=formatTimestamp(parseTimestamp(m.group(1))+offset);String b=formatTimestamp(parseTimestamp(m.group(2))+offset);m.appendReplacement(out,java.util.regex.Matcher.quoteReplacement(a+" --> "+b));}m.appendTail(out);return out.toString();}
    private String shiftAss(String text,int offset){java.util.regex.Pattern p=java.util.regex.Pattern.compile("(Dialogue:.*?,)(\\d+:\\d{2}:\\d{2}\\.\\d{2})(,)(\\d+:\\d{2}:\\d{2}\\.\\d{2})(,.*)");java.util.regex.Matcher m=p.matcher(text);StringBuffer out=new StringBuffer();while(m.find()){String a=formatAss(parseAss(m.group(2))+offset);String b=formatAss(parseAss(m.group(4))+offset);m.appendReplacement(out,java.util.regex.Matcher.quoteReplacement(m.group(1)+a+m.group(3)+b+m.group(5)));}m.appendTail(out);return out.toString();}
    private int parseTimestamp(String s){String[] a=s.split("[:.]",-1);return Integer.parseInt(a[0])*3600000+Integer.parseInt(a[1])*60000+Integer.parseInt(a[2])*1000+Integer.parseInt(a[3]);}
    private String formatTimestamp(int ms){ms=Math.max(0,ms);int h=ms/3600000;ms%=3600000;int m=ms/60000;ms%=60000;int sec=ms/1000;int x=ms%1000;return String.format(Locale.US,"%02d:%02d:%02d.%03d",h,m,sec,x);}
    private int parseAss(String s){String[] a=s.split("[:.]",-1);return Integer.parseInt(a[0])*3600000+Integer.parseInt(a[1])*60000+Integer.parseInt(a[2])*1000+(a.length>3?Integer.parseInt(a[3])*10:0);}
    private String formatAss(int ms){ms=Math.max(0,ms);int h=ms/3600000;ms%=3600000;int m=ms/60000;ms%=60000;int sec=ms/1000;int cs=(ms%1000)/10;return String.format(Locale.US,"%d:%02d:%02d.%02d",h,m,sec,cs);}
    private void applySubtitleWithoutSync(){if(player==null||videoUri==null)return;long pos=player.getCurrentPosition();boolean playing=player.getPlayWhenReady();MediaItem item=buildMediaItem();if(item==null)return;applyingRemote=true;remoteGuardUntil=SystemClock.uptimeMillis()+1800;try{player.setMediaItem(item,pos);player.prepare();player.setPlayWhenReady(playing);}finally{applyingRemote=false;}toast(subtitleUri==null?"Custom subtitle cleared":"Custom subtitle loaded");}

    private void installPlayerUtilityControls(){
        playerView.post(()->{
            View controllerView=playerView.findViewById(androidx.media3.ui.R.id.exo_controller);
            if(!(controllerView instanceof ViewGroup)) return;
            ViewGroup controller=(ViewGroup)controllerView;
            try{controllerView.getClass().getMethod("setAnimationEnabled",boolean.class).invoke(controllerView,false);}catch(Exception ignored){}

            View existing=controller.findViewWithTag("coview_utility_group");
            if(existing!=null)return;

            View settings=controller.findViewById(androidx.media3.ui.R.id.exo_settings);
            ViewGroup settingsParent=settings==null?null:(settings.getParent() instanceof ViewGroup?(ViewGroup)settings.getParent():null);
            if(settingsParent instanceof LinearLayout){
                LinearLayout parent=(LinearLayout)settingsParent;
                int index=parent.indexOfChild(settings);
                ViewGroup.LayoutParams settingsParams=settings.getLayoutParams();
                parent.removeView(settings);

                LinearLayout utilityBar=new LinearLayout(MainActivity.this);
                utilityBar.setTag("coview_utility_group");
                utilityBar.setOrientation(LinearLayout.HORIZONTAL);
                utilityBar.setGravity(Gravity.CENTER_VERTICAL);
                utilityBar.setPadding(0,0,0,0);
                utilityBar.setClipChildren(false);

                ImageButton lock=iconControl(R.drawable.ic_lock,"coview_lock");
                ImageButton pip=iconControl(R.drawable.ic_pip,"coview_pip");
                ImageButton cc=iconControl(R.drawable.ic_cc,"coview_cc");
                ImageButton full=iconControl(R.drawable.ic_fullscreen,"coview_full");
                ImageButton[] buttons={lock,pip,cc,full};
                for(ImageButton b:buttons) utilityBar.addView(b,new LinearLayout.LayoutParams(dp(34),dp(48)));

                LinearLayout.LayoutParams utilityParams=new LinearLayout.LayoutParams(dp(136),dp(48));
                parent.addView(utilityBar,index,utilityParams);
                parent.addView(settings,index+1,settingsParams);

                cc.setAlpha(ccEnabled?1f:.55f);
                cc.setOnClickListener(v->{ccEnabled=!ccEnabled;setCaptionEnabled(ccEnabled);cc.setAlpha(ccEnabled?1f:.55f);playerView.showController();schedulePlayerControlsAutoHide();});
                pip.setOnClickListener(v->enterPictureInPicture());
                lock.setOnClickListener(v->setPlayerLocked(true));
                full.setOnClickListener(v->setFullscreen(!fullscreen));

                settings.setOnClickListener(v->showPlayerSettings());
                if(settings instanceof ImageButton){
                    ImageButton settingsButton=(ImageButton)settings;
                    settingsButton.setPadding(0,0,0,0);
                    settingsButton.setScaleType(ImageView.ScaleType.CENTER);
                    settingsButton.setMinimumWidth(0);settingsButton.setMinimumHeight(0);
                }
                controller.requestLayout();
                controller.invalidate();
            }else{
                // Fallback for a Media3 controller layout that does not expose a horizontal
                // settings parent. Keep the utility controls compact and aligned as one unit.
                LinearLayout utilityBar=new LinearLayout(MainActivity.this);
                utilityBar.setTag("coview_utility_group");
                utilityBar.setOrientation(LinearLayout.HORIZONTAL);
                utilityBar.setGravity(Gravity.CENTER_VERTICAL);
                ImageButton lock=iconControl(R.drawable.ic_lock,"coview_lock");
                ImageButton pip=iconControl(R.drawable.ic_pip,"coview_pip");
                ImageButton cc=iconControl(R.drawable.ic_cc,"coview_cc");
                ImageButton full=iconControl(R.drawable.ic_fullscreen,"coview_full");
                for(ImageButton b:new ImageButton[]{lock,pip,cc,full}) utilityBar.addView(b,new LinearLayout.LayoutParams(dp(34),dp(48)));
                FrameLayout.LayoutParams gp=new FrameLayout.LayoutParams(dp(136),dp(48),Gravity.BOTTOM|Gravity.END);
                gp.setMargins(0,0,dp(48),0);
                controller.addView(utilityBar,gp);
                cc.setAlpha(ccEnabled?1f:.55f);
                cc.setOnClickListener(v->{ccEnabled=!ccEnabled;setCaptionEnabled(ccEnabled);cc.setAlpha(ccEnabled?1f:.55f);playerView.showController();schedulePlayerControlsAutoHide();});
                pip.setOnClickListener(v->enterPictureInPicture());
                lock.setOnClickListener(v->setPlayerLocked(true));
                full.setOnClickListener(v->setFullscreen(!fullscreen));
            }
            playerView.setOnTouchListener((v,event)->{if(event.getAction()==MotionEvent.ACTION_UP && !playerControlsLocked){schedulePlayerControlsAutoHide();}return false;});
        });
    }
    private ImageButton iconControl(int res,String tag){
        ImageButton v=new ImageButton(this);
        v.setTag(tag);v.setBackgroundColor(Color.TRANSPARENT);v.setPadding(dp(2),dp(2),dp(2),dp(2));v.setScaleType(ImageView.ScaleType.CENTER);v.setContentDescription(tag);
        try{android.graphics.drawable.Drawable d=getDrawable(res);if(d!=null){d.setTint(TEXT);v.setImageDrawable(d);}}catch(Exception ignored){}
        return v;
    }
    private void schedulePlayerControlsAutoHide(){if(playerControlsHideRunnable!=null)uiHandler.removeCallbacks(playerControlsHideRunnable);playerControlsHideRunnable=()->{if(playerView!=null&&!playerControlsLocked)playerView.hideController();};uiHandler.postDelayed(playerControlsHideRunnable,2800);}

    private void enterPictureInPicture(){
        if(Build.VERSION.SDK_INT<26){toast("Picture-in-picture needs Android 8 or newer.");return;}
        boolean hasVideo=(player!=null&&player.getMediaItemCount()>0)||(webMode&&webView!=null&&webPageReady);
        if(!hasVideo){toast("Load a video first.");return;}
        try{
            if(youtubeFullscreen)exitYouTubeFullscreen();
            if(fullscreen)setFullscreen(false);
            Rational ratio=new Rational(16,9);
            PictureInPictureParams.Builder pipBuilder=new PictureInPictureParams.Builder().setAspectRatio(ratio);
            View pipTarget=webMode&&webView!=null&&webView.getVisibility()==View.VISIBLE?webView:playerView;
            if(Build.VERSION.SDK_INT>=26 && pipTarget!=null){int[] loc=new int[2];pipTarget.getLocationOnScreen(loc);int w=Math.max(1,pipTarget.getWidth()),h=Math.max(1,pipTarget.getHeight());pipBuilder.setSourceRectHint(new Rect(loc[0],loc[1],loc[0]+w,loc[1]+h));}
            if(Build.VERSION.SDK_INT>=31){try{pipBuilder.setSeamlessResizeEnabled(true);}catch(Exception ignored){}}
            if(Build.VERSION.SDK_INT>=26)setPictureInPictureParams(pipBuilder.build());
            enterPictureInPictureMode();
        }catch(Exception e){toast("Picture-in-picture is unavailable on this device.");}
    }
    private void setPlayerLocked(boolean locked){
        playerControlsLocked=locked;
        if(playerView!=null) playerView.setUseController(!locked);
        if(roomPlayerBox!=null){
            ImageView unlock=(ImageView)roomPlayerBox.findViewWithTag("coview_unlock");
            if(unlock==null){unlock=new ImageView(this);unlock.setTag("coview_unlock");unlock.setBackground(round(Color.argb(210,0,0,0),Color.TRANSPARENT,16));try{android.graphics.drawable.Drawable d=getDrawable(R.drawable.ic_lock);if(d!=null){d.setTint(TEXT);unlock.setImageDrawable(d);}}catch(Exception ignored){}FrameLayout.LayoutParams q=new FrameLayout.LayoutParams(dp(46),dp(46),Gravity.TOP|Gravity.END);q.setMargins(0,dp(10),dp(10),0);roomPlayerBox.addView(unlock,q);unlock.setOnClickListener(v->setPlayerLocked(false));}
            unlock.setVisibility(locked?View.VISIBLE:View.GONE);
            final ImageView unlockView=unlock;
            if(locked){
                unlockView.animate().cancel(); unlockView.setAlpha(1f);
                uiHandler.postDelayed(()->{if(playerControlsLocked&&unlockView.getParent()!=null)unlockView.animate().alpha(0f).setDuration(220).start();},2600);
                unlockView.setOnClickListener(v->{unlockView.animate().alpha(1f).setDuration(100).start();setPlayerLocked(false);});
                roomPlayerBox.setOnTouchListener((v,event)->{if(playerControlsLocked&&event.getAction()==MotionEvent.ACTION_UP){unlockView.setAlpha(1f);unlockView.setVisibility(View.VISIBLE);uiHandler.postDelayed(()->{if(playerControlsLocked)unlockView.animate().alpha(0f).setDuration(220).start();},2600);}return false;});
            }else{unlockView.setAlpha(1f);roomPlayerBox.setOnTouchListener(null);}

        }
    }
    private void restoreRoomLayoutAfterPip(){
        if(roomRoot==null||roomContent==null||(Build.VERSION.SDK_INT>=26 && isInPictureInPictureMode()))return;
        if(roomHeader!=null)roomHeader.setVisibility(View.VISIBLE);
        if(connectionLabel!=null)connectionLabel.setVisibility(View.VISIBLE);
        if(roomPeople!=null)roomPeople.setVisibility(View.VISIBLE);
        if(mediaLabel!=null)mediaLabel.setVisibility(View.VISIBLE);
        if(roomQuick!=null)roomQuick.setVisibility(View.VISIBLE);
        roomContent.setGravity(Gravity.TOP|Gravity.START);
        roomContent.setPadding(dp(14),dp(8),dp(14),dp(14));
        if(roomPlayerBox!=null){
            LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)roomPlayerBox.getLayoutParams();
            lp.width=-1;lp.height=dp(245);lp.topMargin=0;lp.bottomMargin=dp(7);
            roomPlayerBox.setLayoutParams(lp);
        }
        roomContent.requestLayout();
        roomRoot.requestLayout();
        roomContent.post(()->{
            if(!isInPictureInPictureMode()){
                roomContent.setGravity(Gravity.TOP|Gravity.START);
                roomContent.setY(0f);
                roomContent.requestLayout();
            }
        });
        updateSystemBars();
    }

    @Override public void onPictureInPictureModeChanged(boolean isInPictureInPictureMode, Configuration newConfig){
        super.onPictureInPictureModeChanged(isInPictureInPictureMode,newConfig);
        if(roomRoot==null||roomContent==null)return;
        if(isInPictureInPictureMode){
            if(youtubeFullscreen)exitYouTubeFullscreen();
            if(roomHeader!=null)roomHeader.setVisibility(View.GONE);
            if(connectionLabel!=null)connectionLabel.setVisibility(View.GONE);
            if(roomPeople!=null)roomPeople.setVisibility(View.GONE);
            if(mediaLabel!=null)mediaLabel.setVisibility(View.GONE);
            if(roomQuick!=null)roomQuick.setVisibility(View.GONE);
            roomContent.setGravity(Gravity.TOP|Gravity.START);
            roomContent.setPadding(0,0,0,0);
            if(roomPlayerBox!=null){
                LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)roomPlayerBox.getLayoutParams();
                lp.width=-1;lp.height=-1;lp.topMargin=0;lp.bottomMargin=0;
                roomPlayerBox.setLayoutParams(lp);
            }
        }else{
            restoreRoomLayoutAfterPip();
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
        webView=null;webMode=false;webPageReady=false;webObservedInitial=false;pendingWebStateRequestId="";onlineReady=false;onlineResolving=false;onlineResolvedUrl="";webUrl="";
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
        videoUri=null;subtitleUri=null;onlineMode=false;onlineUrl="";onlineResolvedUrl="";onlineReady=false;onlineResolving=false;
        webMode=false;webPageReady=false;webObservedInitial=false;lastWebPositionMs=-1;lastWebPlaying=false;pendingWebStateRequestId="";
        if(pendingSubtitleRunnable!=null)uiHandler.removeCallbacks(pendingSubtitleRunnable);
        lastCueGroup=null;
        if(adjustedSubtitleView!=null)adjustedSubtitleView.setVisibility(ccEnabled?View.VISIBLE:View.GONE);
        if(playerView!=null){playerView.setVisibility(View.VISIBLE);initPlayer();}
    }
    private void prepareForOnlineMedia(){
        if(youtubeFullscreen)exitYouTubeFullscreen();
        releaseWebViewForMediaSwitch();
        resetPlayerMediaState();
        if(roomPlayerBox!=null){setupWebView();createWebLoadingOverlay();}
        if(webLoadingOverlay!=null)webLoadingOverlay.bringToFront();
    }
    private void prepareForLocalMedia(){
        if(youtubeFullscreen)exitYouTubeFullscreen();
        releaseWebViewForMediaSwitch();
        resetPlayerMediaState();
        if(roomPlayerBox!=null&&webLoadingOverlay!=null)webLoadingOverlay.setVisibility(View.GONE);
    }

    private void initPlayer(){
        trackSelector=new DefaultTrackSelector(this);
        player=new ExoPlayer.Builder(this).setTrackSelector(trackSelector).build();
        playerView.setPlayer(player);
        playerView.setUseController(true);
        playerView.setControllerShowTimeoutMs(2600);
        playerView.setControllerHideOnTouch(true);
        playerView.setControllerAutoShow(false);
        View builtInSubtitles=playerView.findViewById(androidx.media3.ui.R.id.exo_subtitles);
        if(builtInSubtitles!=null) builtInSubtitles.setVisibility(View.GONE);
        installPlayerUtilityControls();
        View playerSettings=playerView.findViewById(androidx.media3.ui.R.id.exo_settings);
        if(playerSettings!=null){
            playerSettings.setOnClickListener(v->showPlayerSettings());
            if(playerSettings instanceof ImageButton){
                ImageButton settingsButton=(ImageButton)playerSettings;
                settingsButton.setPadding(0,0,0,0);
                settingsButton.setScaleType(ImageView.ScaleType.CENTER);
            }
        }
        player.addListener(new Player.Listener(){
            @Override public void onCues(CueGroup cueGroup){
                runOnUiThread(()->applyCueGroupWithDelay(cueGroup));
            }
            @Override public void onPlayWhenReadyChanged(boolean playWhenReady,int reason){
                if(!applyingRemote && SystemClock.uptimeMillis()>=remoteGuardUntil){
                    sendSync(true); setLocalPlaybackStatus(playWhenReady?"Playing":"Paused");
                }
            }
            @Override public void onPlaybackStateChanged(int state){
                if(state==Player.STATE_ENDED && !applyingRemote){setLocalPlaybackStatus("Paused");}
            }
            @Override public void onPositionDiscontinuity(Player.PositionInfo oldP,Player.PositionInfo newP,int reason){
                if(!applyingRemote && SystemClock.uptimeMillis()>=remoteGuardUntil &&
                        reason==Player.DISCONTINUITY_REASON_SEEK){
                    sendSync(false);
                }
            }
        });
    }
    private void showLinkDialog(){
        LinearLayout box=col(); box.setPadding(dp(18),dp(8),dp(18),0);
        EditText input=input("YouTube or any public video/page URL");
        if(!pendingSharedUrl.isEmpty()) input.setText(pendingSharedUrl); input.setSingleLine(true); input.setImeOptions(EditorInfo.IME_ACTION_DONE); add(box,input,-1,dp(54),0,8);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle("Watch From Link").setView(box).setNegativeButton("Cancel",null).setPositiveButton("Open Together",null).create();
        styleDialog(dialog);
        dialog.setOnShowListener(v->{
            finishDialogStyle(dialog);
            Button positive=dialog.getButton(AlertDialog.BUTTON_POSITIVE), negative=dialog.getButton(AlertDialog.BUTTON_NEGATIVE);
            if(positive!=null){
                positive.setTextColor(PRIMARY);
                positive.setOnClickListener(x->{
                    String url=input.getText().toString().trim();
                    if(!isSupportedLink(url)){toast("Please enter a valid http(s) URL.");return;}
                    dialog.dismiss(); pendingSharedUrl=""; loadOnlineSource(url,true);
                });
            }
            if(negative!=null)negative.setTextColor(MUTED);
        });
        dialog.show();
    }
    private boolean isSupportedLink(String url){
        if(url==null)return false;
        try{Uri u=Uri.parse(url.trim());String scheme=u.getScheme();String host=u.getHost();return ("https".equalsIgnoreCase(scheme)||"http".equalsIgnoreCase(scheme)) && host!=null && !host.trim().isEmpty();}
        catch(Exception e){return false;}
    }
    private void loadOnlineSource(String url,boolean announce){
        if(url==null||url.trim().isEmpty()||roomPlayerBox==null)return;
        final String requested=url.trim();
        hideMediaChooser();
        if(player==null){
            if(playerView!=null)playerView.setVisibility(View.VISIBLE);
            initPlayer();
        }
        if(player==null)return;
        // Restore the known-good v2.1.5 online-player path.
        // Do not destroy/recreate the WebView for the initial link load.
        if(player!=null){
            applyingRemote=true;
            remoteGuardUntil=SystemClock.uptimeMillis()+1200;
            try{player.stop();player.clearMediaItems();}catch(Exception ignored){}
            finally{applyingRemote=false;}
        }
        if(webView==null){
            setupWebView();
            createWebLoadingOverlay();
        }
        onlineMode=true; webMode=true; onlineUrl=requested; webUrl=requested; onlineResolvedUrl=requested; onlineReady=false; onlineResolving=false;
        videoUri=null; subtitleUri=null;
        setPlayerEmptyVisible(false);
        if(playerView!=null)playerView.setVisibility(View.GONE);
        if(adjustedSubtitleView!=null)adjustedSubtitleView.setVisibility(View.GONE);
        if(webView!=null){
            webView.setVisibility(View.VISIBLE);
            webView.bringToFront();
            if(webLoadingOverlay!=null)webLoadingOverlay.bringToFront();
        }
        setLocalPlaybackStatus("Online");
        showWebLoading("Preparing YouTube…","Sending the link to everyone in the room");
        if(mediaLabel!=null)mediaLabel.setText("Preparing YouTube…");
        if(announce){
            try{startService(serviceIntent(SyncService.ACTION_MEDIA_SOURCE).putExtra("source",requested).putExtra("type","web"));}catch(Exception e){toast("Could not share the link with the room.");}
        }
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
        String id=youtubeId(url);
        if(id.isEmpty()){
            if(mediaLabel!=null)mediaLabel.setText("Loading • Website");
            webView.loadUrl(url); return;
        }
        try{
            java.io.InputStream in=getAssets().open("youtube_player.html");
            java.io.ByteArrayOutputStream out=new java.io.ByteArrayOutputStream(); byte[] buf=new byte[4096]; int n;
            while((n=in.read(buf))>0)out.write(buf,0,n); in.close();
            String html=new String(out.toByteArray(),StandardCharsets.UTF_8).replace("__VIDEO_ID__",escapeHtml(id));
            if(mediaLabel!=null)mediaLabel.setText("Loading • YouTube");
            webView.loadDataWithBaseURL("https://coview.local/",html,"text/html","UTF-8",null);
        }catch(Exception e){
            hideWebLoading(); if(mediaLabel!=null)mediaLabel.setText("Could not open YouTube"); toast("Could not open the YouTube player.");
        }
    }
    
    private String displayUrl(String url){try{Uri u=Uri.parse(url);return u.getHost()==null?url:u.getHost();}catch(Exception e){return url;}}
    private String youtubeId(String url){
        try{Uri u=Uri.parse(url);String h=u.getHost()==null?"":u.getHost().toLowerCase(Locale.ROOT);if(h.equals("youtu.be"))return u.getPath()==null?"":u.getPath().replace("/","").trim();String v=u.getQueryParameter("v");if(v!=null&&!v.isEmpty())return v;String p=u.getPath()==null?"":u.getPath();int i=p.indexOf("/shorts/");if(i>=0)return p.substring(i+8).split("/",2)[0];i=p.indexOf("/embed/");if(i>=0)return p.substring(i+7).split("/",2)[0];}catch(Exception ignored){}return "";
    }
    private String escapeHtml(String value){return value==null?"":value.replace("&","&amp;").replace("\"","&quot;").replace("<","&lt;").replace(">","&gt;");}
    private void setupWebView(){
        webView=new WebView(this); webView.setVisibility(View.GONE); webView.setBackgroundColor(Color.BLACK); webView.setLayerType(View.LAYER_TYPE_HARDWARE,null);
        WebSettings ws=webView.getSettings(); ws.setJavaScriptEnabled(true); ws.setDomStorageEnabled(true); ws.setDatabaseEnabled(true); ws.setMediaPlaybackRequiresUserGesture(false); ws.setSupportZoom(false); ws.setBuiltInZoomControls(false); ws.setDisplayZoomControls(false); ws.setLoadWithOverviewMode(false); ws.setUseWideViewPort(false); ws.setJavaScriptCanOpenWindowsAutomatically(false); ws.setSupportMultipleWindows(false); ws.setUserAgentString("Mozilla/5.0 (Linux; Android 11) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/131.0.0.0 Mobile Safari/537.36");
        if(Build.VERSION.SDK_INT>=21)ws.setMixedContentMode(WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE);
        CookieManager.getInstance().setAcceptCookie(true); CookieManager.getInstance().setAcceptThirdPartyCookies(webView,true);
        webView.setWebViewClient(new WebViewClient(){
            @Override public boolean shouldOverrideUrlLoading(WebView v,String url){return false;}
            @Override public void onPageStarted(WebView v,String url,android.graphics.Bitmap favicon){if(v!=webView)return;showWebLoading("Loading YouTube…","Starting the video player");}
            @Override public void onPageFinished(WebView v,String url){if(v!=webView)return;webUrl=url;injectWebController();}
        });
        webView.setWebChromeClient(new WebChromeClient(){
            @Override public void onShowCustomView(View view, CustomViewCallback callback){ if(webView==null||webView.getVisibility()!=View.VISIBLE){if(callback!=null)try{callback.onCustomViewHidden();}catch(Exception ignored){}return;} enterYouTubeFullscreen(view,callback); }
            @Override public void onHideCustomView(){ if(youtubeFullscreen)exitYouTubeFullscreen(); }
        }); webView.addJavascriptInterface(new WebBridge(),"CoView");
        roomPlayerBox.addView(webView,new FrameLayout.LayoutParams(-1,-1));
    }
    private void injectWebController(){
        if(webView==null)return;
        // The YouTube HTML page owns all player events. Android only receives explicit
        // commands, preventing the old timeupdate -> broadcast -> seek feedback loop.
        String js="javascript:(function(){if(window.__coviewAndroidHook)return;window.__coviewAndroidHook=true;if(window.coviewSetReady)window.coviewSetReady();})();";
        webView.evaluateJavascript(js,null);
    }
    private class WebBridge{
        @JavascriptInterface public void ready(){
            runOnUiThread(()->{
                webPageReady=true; onlineReady=true; if(webLoadTimeoutRunnable!=null){uiHandler.removeCallbacks(webLoadTimeoutRunnable);webLoadTimeoutRunnable=null;} hideWebLoading();
                if(mediaLabel!=null)mediaLabel.setText("YouTube • Ready");
                if(initialStateWaiting){uiHandler.postDelayed(()->{if(webMode&&webPageReady)sendStateRequest();},250);}
            });
        }
        @JavascriptInterface public void error(String message){
            runOnUiThread(()->{hideWebLoading(); if(mediaLabel!=null)mediaLabel.setText("YouTube could not play this video"); toast(message==null||message.isEmpty()?"YouTube could not play this video.":message);});
        }
        @JavascriptInterface public void state(String json){
            try{
                JSONObject d=new JSONObject(json); String kind=d.optString("kind","heartbeat");
                boolean playing=d.optBoolean("playing",false); long pos=Math.max(0,(long)(d.optDouble("pos",0)*1000));
                boolean remote=d.optBoolean("remote",false); if(remote||webApplyingRemote)return;
                lastWebPlaying=playing; lastWebPositionMs=pos; webObservedInitial=true;
                boolean request=!pendingWebStateRequestId.isEmpty();
                if(request || "play".equals(kind)||"pause".equals(kind)||"seek".equals(kind)){
                    sendWebSync(true,pos,playing,request,kind);
                    if(request)pendingWebStateRequestId="";
                    setLocalPlaybackStatus(playing?"Playing":"Paused");
                }
            }catch(Exception ignored){}
        }
    }
    private void sendWebSync(boolean force,long positionMs,boolean playing,boolean request,String kind){
        if(!webMode||!webPageReady)return;
        long now=SystemClock.uptimeMillis(); if(!force&&!request&&now-lastWebSyncSentAt<1800)return; lastWebSyncSentAt=now;
        Intent i=serviceIntent(SyncService.ACTION_WEB_SYNC)
                .putExtra("position",positionMs).putExtra("playing",playing)
                .putExtra("kind",kind==null?"heartbeat":kind).putExtra("commandSeq",++webCommandSequence)
                .putExtra("sentAt",System.currentTimeMillis());
        if(request)i.putExtra("requestId",activeStateRequestId); startService(i);
    }
    private void applyWebSync(JSONObject d){
        if(!webMode||webView==null)return;
        String requestId=d.optString("requestId",""); if(!requestId.isEmpty()&&(!initialStateWaiting||!requestId.equals(activeStateRequestId)))return;
        String sender=d.optString("senderId",""); if(!sender.isEmpty()&&sender.equals(localPresenceKey))return;
        long pos=d.optLong("position",0); boolean playing=d.optBoolean("playing",false); String kind=d.optString("kind","heartbeat");
        long sent=d.optLong("sentAt",0); if(playing&&sent>0)pos+=Math.max(0,Math.min(1000,System.currentTimeMillis()-sent)); final long target=Math.max(0,pos);
        final String cmd=kind==null?"heartbeat":kind;
        if(!webMode||webView==null||!webPageReady)return;
        webView.post(()->{
            if(!webMode||webView==null||!webPageReady)return;
            webApplyingRemote=true;
            String js="javascript:(function(){if(window.coviewApply)return window.coviewApply("+(target/1000.0)+","+(playing?"true":"false")+",'"+cmd.replace("'","")+"');return false})()";
            webView.evaluateJavascript(js,v->{webApplyingRemote=false;});
            lastWebPlaying=playing; lastWebPositionMs=target;
            if(!requestId.isEmpty()){initialStateWaiting=false;activeStateRequestId="";}
        });
    }
    private void applyMediaSource(JSONObject d){
        String source=d.optString("source","").trim();
        String type=d.optString("type","local");
        String sender=d.optString("user",d.optString("senderId",""));
        if(source.isEmpty() || !"web".equalsIgnoreCase(type)) return;
        if(!sender.isEmpty() && sender.equals(localPresenceKey)) return;
        if(source.equals(onlineUrl) && webMode) return;
        runOnUiThread(()->{
            pendingRemoteWebSource=source;
            if(player==null || roomPlayerBox==null)return;
            loadOnlineSource(source,false);
            pendingRemoteWebSource="";
            initialStateWaiting=true;
            activeStateRequestId="";
            pendingWebStateRequestId="";
            uiHandler.postDelayed(()->{if(webMode&&webPageReady)sendStateRequest();},180);
        });
    }
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
                onlineMode=false; webMode=false; webPageReady=false;
                if(playerView!=null)playerView.setVisibility(View.VISIBLE); if(adjustedSubtitleView!=null)adjustedSubtitleView.setVisibility(ccEnabled?View.VISIBLE:View.GONE);
                videoUri=uri;
                subtitleUri=null;
                setPlayerEmptyVisible(false);
                hideMediaChooser();
                player.setMediaItem(buildMediaItem());
                player.prepare();
                String name=displayName(uri);
                mediaLabel.setText("✓  "+name+"  •  local file");
                setLocalPlaybackStatus("Paused");
                initialStateWaiting=true;
                Intent src=serviceIntent(SyncService.ACTION_MEDIA_SOURCE).putExtra("source","").putExtra("type","local");
                startService(src);
                sendStateRequest();
            }
        } else if(req==PICK_PFP&&result==Activity.RESULT_OK&&data!=null&&data.getData()!=null){
            Uri uri=data.getData();try{getContentResolver().takePersistableUriPermission(uri,data.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
            try{java.io.InputStream in=getContentResolver().openInputStream(uri);Bitmap b=BitmapFactory.decodeStream(in);if(in!=null)in.close();String encoded=bitmapToData(b);if(!encoded.isEmpty()){profilePicture=encoded;prefs.edit().putString("custom_pfp",encoded).apply();if(!localPresenceKey.isEmpty())participantPfps.put(localPresenceKey,profilePicture);sendPresenceUpdate();if(player==null)showConnect();else refreshParticipants();}}catch(Exception ignored){}
        } else if(req==PICK_SUBTITLE&&result==Activity.RESULT_OK&&data!=null&&data.getData()!=null){
            Uri uri=data.getData();
            try{getContentResolver().takePersistableUriPermission(uri,data.getFlags()&Intent.FLAG_GRANT_READ_URI_PERMISSION);}catch(Exception ignored){}
            subtitleUri=uri;
            applySubtitleWithoutSync();
        }
    }

    private String formatPeopleWatching(int count){return "● "+count+(count==1?" person":" people")+" watching";}

    private void setLocalPlaybackStatus(String status){
        if(status==null||status.isEmpty())status="Online";
        boolean changed=!status.equals(localStatus); localStatus=status;
        if(!localPresenceKey.isEmpty())participantStatuses.put(localPresenceKey,status);
        if(changed&&!localPresenceKey.isEmpty())sendPresenceUpdate();
        runOnUiThread(MainActivity.this::refreshParticipants);
    }
    private String currentPresenceStatus(){
        if(webMode)return webPageReady?(lastWebPlaying?"Playing":"Paused"):"Online";
        if(player==null||player.getMediaItemCount()==0)return "Online";
        return player.getPlayWhenReady()?"Playing":"Paused";
    }

    private void sendSync(){ sendSync(false, ""); }
    private void sendSync(boolean force){ sendSync(force, ""); }
    private void sendSync(boolean force,String requestId){
        if(player==null || applyingRemote)return;
        if(!force && SystemClock.uptimeMillis()<remoteGuardUntil)return;
        long now=System.currentTimeMillis();
        if(!force && now-lastLocalSync<120)return;
        lastLocalSync=now;
        Intent i=serviceIntent(SyncService.ACTION_SYNC)
                .putExtra("position",player.getCurrentPosition())
                .putExtra("playing",player.getPlayWhenReady());
        if(requestId!=null && !requestId.isEmpty()) i.putExtra("requestId",requestId);
        startService(i);
    }
    private void sendStateRequest(){
        initialStateWaiting=true;
        activeStateRequestId=UUID.randomUUID().toString();
        Intent i=serviceIntent(SyncService.ACTION_STATE_REQUEST)
                .putExtra("requestId",activeStateRequestId);
        startService(i);
    }
    private void applySync(JSONObject d){
        final String user=d.optString("user","");
        final String eventId=d.optString("eventId","");
        final String requestId=d.optString("requestId","");
        final long seq=d.optLong("seq",-1);
        String senderId=d.optString("senderId","");
        if(!senderId.isEmpty() && senderId.equals(localPresenceKey))return;
        if(senderId.isEmpty() && !user.isEmpty() && user.equals(username))return;
        if(!eventId.isEmpty()){
            if(seenEventIds.contains(eventId))return;
            seenEventIds.add(eventId);
            if(seenEventIds.size()>512) seenEventIds.remove(seenEventIds.iterator().next());
        }
        if(!requestId.isEmpty() && (!initialStateWaiting || !requestId.equals(activeStateRequestId)))return;
        String sequenceKey=!senderId.isEmpty()?senderId:user;
        if(!sequenceKey.isEmpty() && seq>=0){
            Long previous=lastSequenceByUser.get(sequenceKey);
            if(previous!=null && seq<=previous)return;
            lastSequenceByUser.put(sequenceKey,seq);
        }

        long pos=d.optLong("position",0);
        boolean playing=d.optBoolean("playing",false);
        long sentAt=d.optLong("sentAt",0);
        long now=System.currentTimeMillis();
        if(playing && sentAt>0){
            long elapsed=Math.max(0,Math.min(1500,now-sentAt));
            pos+=elapsed;
        }
        final long target=Math.max(0,pos);

        runOnUiThread(()->{
            if(player==null || player.getMediaItemCount()==0)return;
            applyingRemote=true;
            remoteGuardUntil=SystemClock.uptimeMillis()+1800;
            try{
                long diff=Math.abs(player.getCurrentPosition()-target);
                if(diff>350)player.seekTo(target);
                if(player.getPlayWhenReady()!=playing)player.setPlayWhenReady(playing);
                if(!requestId.isEmpty()) {
                    initialStateWaiting=false;
                    activeStateRequestId="";
                }
            }finally{
                applyingRemote=false;
            }
        });
    }

    private void updatePresenceState(JSONObject state){
        if(state==null)return;
        if(localPresenceKey.isEmpty()){participants.put("self",username);participantPfps.put("self",profilePicture);}
        else {participants.put(localPresenceKey,username);participantPfps.put(localPresenceKey,profilePicture);}
        try{Iterator<String> it=state.keys();while(it.hasNext()){String key=it.next();JSONObject entry=state.optJSONObject(key);if(entry==null)continue;JSONArray metas=entry.optJSONArray("metas");String name=key,pfp="";if(metas!=null&&metas.length()>0){JSONObject m=metas.optJSONObject(0);if(m!=null){name=m.optString("username",key);pfp=m.optString("pfp","");participantStatuses.put(key,m.optString("status","Online"));}}participants.put(key,name);if(!pfp.isEmpty())participantPfps.put(key,pfp);}}catch(Exception ignored){}
        refreshParticipants();
    }
    private void updatePresenceDiff(JSONObject diff){
        try{
            JSONObject joins=diff.optJSONObject("joins");
            if(joins!=null){Iterator<String> it=joins.keys();while(it.hasNext()){String key=it.next();JSONObject entry=joins.optJSONObject(key);String name=key,pfp="";JSONArray metas=entry==null?null:entry.optJSONArray("metas");if(metas!=null&&metas.length()>0){JSONObject m=metas.optJSONObject(0);if(m!=null){name=m.optString("username",key);pfp=m.optString("pfp","");}}boolean wasPresent=participants.containsKey(key);participants.put(key,name);if(!pfp.isEmpty())participantPfps.put(key,pfp);participantStatuses.put(key,"Online");if(!wasPresent&&shouldShowPresenceNotice(key,true))showPresenceNotice(name+" joined the room",true);}}
            JSONObject leaves=diff.optJSONObject("leaves");
            if(leaves!=null){Iterator<String> it=leaves.keys();while(it.hasNext()){String key=it.next();String name=participants.get(key);boolean existed=participants.remove(key)!=null;participantPfps.remove(key);participantStatuses.remove(key);typingUsers.remove(name);if(name==null)name="Someone";if(existed&&shouldShowPresenceNotice(key,false))showPresenceNotice(name+" left the room",false);}}
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
        boolean existed=participants.remove(key)!=null;participantPfps.remove(key);refreshParticipants();
        if(existed&&shouldShowPresenceNotice(key,false))showPresenceNotice(name+" left the room",false);
    }

    private void refreshParticipants(){
        int count=Math.max(1,participants.size());
        if(participantCount!=null)participantCount.setText(String.valueOf(count));
        if(participantSummary!=null)participantSummary.setText(formatPeopleWatching(count));
        if(participantStrip!=null){
            participantStrip.removeAllViews();
            for(Map.Entry<String,String> e:participants.entrySet()){
                String key=e.getKey(),name=e.getValue();
                LinearLayout pill=row();pill.setPadding(dp(5),dp(3),dp(9),dp(3));pill.setBackground(round(SURFACE2,LINE,22));
                ImageView a=new ImageView(this);a.setImageBitmap(loadPfpBitmap(participantPfps.getOrDefault(key,"asset:0")));a.setScaleType(ImageView.ScaleType.CENTER_CROP);a.setBackground(round(SURFACE,LINE,50));pill.addView(a,new LinearLayout.LayoutParams(dp(30),dp(30)));
                TextView n=text(name,10,TEXT);n.setSingleLine(true);n.setEllipsize(android.text.TextUtils.TruncateAt.END);n.setIncludeFontPadding(false);n.setGravity(Gravity.CENTER_VERTICAL);n.setMaxEms(14);n.setPadding(dp(6),0,0,0);pill.addView(n,new LinearLayout.LayoutParams(-2,dp(30)));
                participantStrip.addView(pill,new LinearLayout.LayoutParams(-2,dp(38)));
                Space sp=new Space(this);participantStrip.addView(sp,new LinearLayout.LayoutParams(dp(6),1));
            }
        }
        if(participantList!=null){SpannableStringBuilder b=new SpannableStringBuilder();for(Map.Entry<String,String> e:participants.entrySet()){b.append(e.getValue()).append(" • ").append(participantStatuses.getOrDefault(e.getKey(),"Online")).append('\n');}participantList.setText(b,TextView.BufferType.SPANNABLE);}
    }

    private void appendAvatar(SpannableStringBuilder b,String token){Bitmap bmp=loadPfpBitmap(token);if(bmp==null)return;Bitmap small=Bitmap.createScaledBitmap(bmp,dp(30),dp(30),true);BitmapDrawable bd=new BitmapDrawable(getResources(),small);bd.setBounds(0,0,dp(30),dp(30));int start=b.length();b.append("  ");b.setSpan(new ImageSpan(bd,ImageSpan.ALIGN_BOTTOM),start,start+1,Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);}
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
        LinearLayout panel=col();panel.setPadding(dp(18),dp(6),dp(18),dp(14));panel.setBackground(round(SURFACE,LINE,24));FrameLayout.LayoutParams pp=new FrameLayout.LayoutParams(-1,dp(510),Gravity.BOTTOM);chatOverlay.addView(panel,pp);
        View handle=new View(this);handle.setBackground(round(MUTED,Color.TRANSPARENT,5));FrameLayout.LayoutParams hp=new FrameLayout.LayoutParams(dp(42),dp(5),Gravity.TOP|Gravity.CENTER_HORIZONTAL);hp.topMargin=dp(6);panel.addView(handle,hp);
        LinearLayout head=col();head.setPadding(0,dp(14),0,dp(4));LinearLayout titleRow=row();LinearLayout words=col();add(words,title("Room Chat",20),-1,dp(28),0,2);add(words,text("Chat with people in this room",12,MUTED),-1,dp(22),0,0);titleRow.addView(words,new LinearLayout.LayoutParams(0,dp(58),1));Button close=iconOnlyButton(R.drawable.ic_close,false);titleRow.addView(close,new LinearLayout.LayoutParams(dp(32),dp(32)));head.addView(titleRow,new LinearLayout.LayoutParams(-1,dp(64)));panel.addView(head,new LinearLayout.LayoutParams(-1,dp(72)));close.setOnClickListener(v->closeChat());
        chatList=new RecyclerView(this);chatList.setLayoutManager(new LinearLayoutManager(this));chatList.setAdapter(new ChatAdapter());chatList.setClipToPadding(false);panel.addView(chatList,new LinearLayout.LayoutParams(-1,0,1));
        typingLabel=text("",11,MUTED);typingLabel.setPadding(dp(46),0,dp(8),dp(4));typingLabel.setVisibility(View.GONE);panel.addView(typingLabel,new LinearLayout.LayoutParams(-1,dp(24)));
        LinearLayout in=row();Button emoji=iconButton("",R.drawable.ic_kaomoji,false);in.addView(emoji,new LinearLayout.LayoutParams(dp(52),dp(54)));in.addView(new Space(this),new LinearLayout.LayoutParams(dp(7),1));chatInput=input("Message…");in.addView(chatInput,new LinearLayout.LayoutParams(0,dp(54),1));in.addView(new Space(this),new LinearLayout.LayoutParams(dp(7),1));Button send=iconOnlyButton(R.drawable.ic_send,false);in.addView(send,new LinearLayout.LayoutParams(dp(62),dp(54)));add(panel,in,-1,dp(54),8,0);
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
        String who=joinStrings(typingUsers, ", ");typingLabel.setText(who+(typingUsers.size()==1?" is typing…":" are typing…"));typingLabel.setVisibility(View.VISIBLE);
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
        sendTyping(false);
        String messageId=UUID.randomUUID().toString();
        Intent i=serviceIntent(SyncService.ACTION_CHAT).putExtra("text",s).putExtra("pfp",profilePicture).putExtra("messageId",messageId);startService(i);
        addChat(username,s,profilePicture,System.currentTimeMillis(),messageId);playChatSound();chatInput.setText("");
    }
    private void addChat(String user,String msg,String pfp){addChat(user,msg,pfp,System.currentTimeMillis(),UUID.randomUUID().toString());}
    private void addChat(String user,String msg,String pfp,long sentAt){addChat(user,msg,pfp,sentAt,UUID.randomUUID().toString());}
    private void addChat(String user,String msg,String pfp,long sentAt,String id){
        runOnUiThread(()->{chats.add(new ChatMessage(user,msg,pfp,sentAt,id));if(chatList!=null){chatList.getAdapter().notifyItemInserted(chats.size()-1);chatList.scrollToPosition(chats.size()-1);}});
    }
    private void handleTyping(JSONObject d){
        String user=d.optString("user","");if(user.isEmpty()||user.equals(username))return;
        boolean active=d.optBoolean("typing",false);if(active)typingUsers.add(user);else typingUsers.remove(user);
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
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
        getWindow().setStatusBarColor(Color.BLACK); getWindow().setNavigationBarColor(Color.BLACK);
        View decor=getWindow().getDecorView();
        if(Build.VERSION.SDK_INT>=30){android.view.WindowInsetsController c=decor.getWindowInsetsController();if(c!=null){c.hide(android.view.WindowInsets.Type.statusBars()|android.view.WindowInsets.Type.navigationBars());c.setSystemBarsBehavior(android.view.WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE);}}
        else decor.setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
    }
    private void exitYouTubeFullscreen(){
        if(youtubeCustomView==null)return;
        View view=youtubeCustomView; ViewParent parent=view.getParent(); if(parent instanceof ViewGroup)((ViewGroup)parent).removeView(view);
        if(youtubeFullscreenContainer!=null){ViewParent cp=youtubeFullscreenContainer.getParent();if(cp instanceof ViewGroup)((ViewGroup)cp).removeView(youtubeFullscreenContainer);}
        WebChromeClient.CustomViewCallback cb=youtubeCustomViewCallback;
        youtubeCustomView=null; youtubeCustomViewCallback=null; youtubeFullscreenContainer=null; youtubeFullscreen=false;
        if(cb!=null)try{cb.onCustomViewHidden();}catch(Exception ignored){}
        if(webView!=null)webView.setVisibility(webMode?View.VISIBLE:View.GONE);
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT); getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(BG); updateSystemBars();
    }

    private void setFullscreen(boolean on){
        fullscreen=on;
        if(on){setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);if(roomHeader!=null)roomHeader.setVisibility(View.GONE);if(connectionLabel!=null)connectionLabel.setVisibility(View.GONE);if(roomPeople!=null)roomPeople.setVisibility(View.GONE);if(roomMediaRow!=null)roomMediaRow.setVisibility(View.GONE);if(mediaLabel!=null)mediaLabel.setVisibility(View.GONE);if(roomQuick!=null)roomQuick.setVisibility(View.GONE);if(roomContent!=null){roomContent.setPadding(0,0,0,0);roomContent.setGravity(Gravity.CENTER);}if(roomPlayerBox!=null){LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)roomPlayerBox.getLayoutParams();lp.height=-1;lp.width=-1;lp.topMargin=0;lp.bottomMargin=0;roomPlayerBox.setLayoutParams(lp);}getWindow().setStatusBarColor(Color.BLACK);getWindow().setNavigationBarColor(Color.BLACK);getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN|View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_LAYOUT_STABLE);}
        else{setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);if(roomHeader!=null)roomHeader.setVisibility(View.VISIBLE);if(connectionLabel!=null)connectionLabel.setVisibility(View.VISIBLE);if(roomPeople!=null)roomPeople.setVisibility(View.VISIBLE);if(roomMediaRow!=null)roomMediaRow.setVisibility(View.VISIBLE);if(mediaLabel!=null)mediaLabel.setVisibility(View.VISIBLE);if(roomQuick!=null)roomQuick.setVisibility(View.VISIBLE);if(roomContent!=null){roomContent.setPadding(dp(14),dp(8),dp(14),dp(14));roomContent.setGravity(Gravity.NO_GRAVITY);}if(roomPlayerBox!=null){LinearLayout.LayoutParams lp=(LinearLayout.LayoutParams)roomPlayerBox.getLayoutParams();lp.height=dp(245);lp.width=-1;lp.topMargin=0;lp.bottomMargin=dp(8);roomPlayerBox.setLayoutParams(lp);}getWindow().setStatusBarColor(BG);getWindow().setNavigationBarColor(BG);updateSystemBars();}
    }

    private void disconnect(){
        if(youtubeFullscreen)exitYouTubeFullscreen();
        closeChat();
        if(fullscreen)setFullscreen(false);
        startService(serviceIntent(SyncService.ACTION_DISCONNECT));
        releaseWebViewForMediaSwitch();
        if(pendingSubtitleRunnable!=null){uiHandler.removeCallbacks(pendingSubtitleRunnable);pendingSubtitleRunnable=null;}
        lastCueGroup=null; videoUri=null; subtitleUri=null;
        setPlayerEmptyVisible(false);
        if(playerView!=null){try{playerView.setPlayer(null);}catch(Exception ignored){}}
        if(player!=null){try{player.stop();}catch(Exception ignored){}try{player.clearMediaItems();}catch(Exception ignored){}try{player.release();}catch(Exception ignored){}player=null;}
        webMode=false;webPageReady=false;webObservedInitial=false;pendingWebStateRequestId="";pendingRemoteWebSource="";
        onlineMode=false;onlineUrl="";onlineResolvedUrl="";onlineReady=false;onlineResolving=false;
        typingUsers.clear();participantStatuses.clear();participants.clear();participantPfps.clear();
        playerView=null;adjustedSubtitleView=null;roomRoot=null;roomContent=null;roomPlayerBox=null;mediaChooserOverlay=null;playerEmptyOverlay=null;
        showConnect();
    }
    @Override public void onBackPressed(){if(Build.VERSION.SDK_INT>=26 && isInPictureInPictureMode())return;if(youtubeFullscreen){exitYouTubeFullscreen();return;}if(chatOpen){closeChat();return;}if(fullscreen){setFullscreen(false);return;}if(player!=null){disconnect();return;}super.onBackPressed();}
    @Override public void onUserLeaveHint(){
        super.onUserLeaveHint();
        // Explicit PIP is entered from the player control. Leaving the activity must
        // never be interpreted as a room leave; SyncService owns the room connection.
    }
    @Override public void onConfigurationChanged(Configuration newConfig){
        super.onConfigurationChanged(newConfig);
        if(fullscreen&&newConfig.orientation==Configuration.ORIENTATION_LANDSCAPE){/* Keep the existing player; do not recreate it. */}
        else if((Build.VERSION.SDK_INT<26 || !isInPictureInPictureMode()) && !fullscreen && newConfig.orientation==Configuration.ORIENTATION_PORTRAIT){
            uiHandler.post(this::restoreRoomLayoutAfterPip);
        }
    }
    private void toast(String s){Toast.makeText(this,s,Toast.LENGTH_LONG).show();}
    @Override protected void onDestroy(){if(pendingSubtitleRunnable!=null)uiHandler.removeCallbacks(pendingSubtitleRunnable);if(playerControlsHideRunnable!=null)uiHandler.removeCallbacks(playerControlsHideRunnable);if(webLoadTimeoutRunnable!=null)uiHandler.removeCallbacks(webLoadTimeoutRunnable);uiHandler.removeCallbacksAndMessages(null);try{unregisterReceiver(receiver);}catch(Exception ignored){}if(youtubeFullscreen)exitYouTubeFullscreen();releaseWebViewForMediaSwitch();if(playerView!=null){try{playerView.setPlayer(null);}catch(Exception ignored){}}if(player!=null){try{player.release();}catch(Exception ignored){}player=null;}super.onDestroy();}

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
            LinearLayout.LayoutParams bp=new LinearLayout.LayoutParams(dp(270),-2);bp.gravity=mine?Gravity.RIGHT:Gravity.LEFT;h.row.addView(bubble,bp);
            if(mine){Space sp=new Space(MainActivity.this);h.row.addView(sp,new LinearLayout.LayoutParams(dp(7),1));ImageView a=new ImageView(MainActivity.this);a.setImageBitmap(loadPfpBitmap(m.pfp));a.setScaleType(ImageView.ScaleType.CENTER_CROP);a.setBackground(round(SURFACE2,LINE,50));h.row.addView(a,new LinearLayout.LayoutParams(dp(38),dp(38)));}
            if(pos==chats.size()-1&&!typingUsers.isEmpty()){TextView typing=text(joinStrings(typingUsers, ", ")+" is typing…",11,MUTED);typing.setPadding(dp(48),dp(2),0,dp(5));h.row.addView(typing,new LinearLayout.LayoutParams(-1,dp(24)));}
        }
        @Override public int getItemCount(){return chats.size();}
    }

    private class ColorWheelView extends View {
        private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);private final Paint marker=new Paint(Paint.ANTI_ALIAS_FLAG);private int selectedColor;private OnColorChanged listener;
        ColorWheelView(Context c,int initial){super(c);selectedColor=initial;setLayerType(View.LAYER_TYPE_SOFTWARE,null);marker.setStyle(Paint.Style.STROKE);marker.setStrokeWidth(dp(3));marker.setColor(Color.WHITE);}
        void setListener(OnColorChanged l){listener=l;}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float cx=getWidth()/2f,cy=getHeight()/2f,r=Math.min(cx,cy)-dp(14);int[] colors=new int[]{Color.RED,Color.YELLOW,Color.GREEN,Color.CYAN,Color.BLUE,Color.MAGENTA,Color.RED};float[] stops=new float[]{0f,1f/6f,2f/6f,3f/6f,4f/6f,5f/6f,1f};paint.setShader(new SweepGradient(cx,cy,colors,stops));canvas.drawCircle(cx,cy,r,paint);paint.setShader(new RadialGradient(cx,cy, r,new int[]{Color.WHITE,Color.TRANSPARENT},null,Shader.TileMode.CLAMP));canvas.drawCircle(cx,cy,r,paint);paint.setShader(null);float[] hsv=new float[3];Color.colorToHSV(selectedColor,hsv);double angle=Math.toRadians(hsv[0]);float rr=r*hsv[1];float mx=cx+(float)Math.cos(angle)*rr,my=cy+(float)Math.sin(angle)*rr;canvas.drawCircle(mx,my,dp(8),marker);}
        @Override public boolean onTouchEvent(android.view.MotionEvent e){if(e.getAction()!=MotionEvent.ACTION_DOWN&&e.getAction()!=MotionEvent.ACTION_MOVE)return true;float cx=getWidth()/2f,cy=getHeight()/2f;float dx=e.getX()-cx,dy=e.getY()-cy;float r=Math.min(cx,cy)-dp(14);float dist=(float)Math.sqrt(dx*dx+dy*dy);float sat=Math.min(1f,dist/r);float hue=(float)Math.toDegrees(Math.atan2(dy,dx));if(hue<0)hue+=360f;if(hue>=360)hue-=360f;selectedColor=Color.HSVToColor(new float[]{hue,sat,1f});if(listener!=null)listener.onColorChanged(selectedColor);invalidate();return true;}
        interface OnColorChanged{void onColorChanged(int color);}
    }
}
