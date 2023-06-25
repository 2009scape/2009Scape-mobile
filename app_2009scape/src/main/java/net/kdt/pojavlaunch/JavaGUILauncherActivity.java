package net.kdt.pojavlaunch;

import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.*;
import android.os.Vibrator;
import android.util.Log;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;

import java.io.*;
import java.util.*;

import net.kdt.pojavlaunch.sound.JAudioManager;
import net.kdt.pojavlaunch.sound.SoundService;
import net.kdt.pojavlaunch.multirt.MultiRTUtils;
import net.kdt.pojavlaunch.prefs.*;
import net.kdt.pojavlaunch.utils.*;
import org.lwjgl.glfw.*;

import static net.kdt.pojavlaunch.utils.Tools.currentDisplayMetrics;
import static net.kdt.pojavlaunch.utils.MathUtils.map;

import androidx.preference.PreferenceManager;

public class JavaGUILauncherActivity extends  BaseActivity implements View.OnTouchListener {
    
    private AWTCanvasView mTextureView;
    private int totalMovement;


    private boolean mouseState = false;
    public static Context conT;
    public static boolean isFocused = true;

    private LinearLayout touchPad;
    private ImageView mousePointer;
    private GestureDetector gestureDetector;
    private long lastPress = 0;
    ScaleGestureDetector scaleGestureDetector;
    private long touchStart = 0;
    boolean longPressTriggered = false;

    private boolean rcState = false;

    private boolean isVirtualMouseEnabled;

    private float scaleFactor;
    public float[] scaleFactors = initScaleFactors();

    public static Context getContext() {
        return conT;
    }

    public static class ScaleListener extends ScaleGestureDetector.SimpleOnScaleGestureListener {

        @Override
        public boolean onScale(ScaleGestureDetector detector) {
            float scaleFactor = detector.getScaleFactor();
            if (scaleFactor > 1) { //Send F4 To Zoom Out
                AWTInputBridge.sendKey((char)AWTInputEvent.VK_F3, AWTInputEvent.VK_F3);
            } else { //116 F3 To Zoom In
                AWTInputBridge.sendKey((char)AWTInputEvent.VK_F4,AWTInputEvent.VK_F4);
            }
            return true;
        }

        @Override
        public boolean onScaleBegin(ScaleGestureDetector detector) {
            return true;
        }

        @Override
        public void onScaleEnd(ScaleGestureDetector detector) {

        }
    }

    @Override
    protected void onPause()
    {
        super.onPause();
        isFocused = false;
        JAudioManager.muteSound();
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.install_mod);
        Tools.updateWindowSize(this);
        Vibrator vb = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);

        try {
            MultiRTUtils.setRuntimeNamed(this,LauncherPreferences.PREF_DEFAULT_RUNTIME);
            gestureDetector = new GestureDetector(this, new SingleTapConfirm());
            scaleGestureDetector = new ScaleGestureDetector(this, new ScaleListener());

            findViewById(R.id.keyboard).setOnTouchListener(this);
            findViewById(R.id.camera).setOnTouchListener(this);
            findViewById(R.id.mb2).setOnTouchListener(this);

            // Righthanded Flip UI
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
            if(Boolean.parseBoolean(preferences.getString("righthanded",""))){
                RelativeLayout.LayoutParams lp = (RelativeLayout.LayoutParams) findViewById(R.id.keyboard).getLayoutParams();
                lp.addRule(RelativeLayout.ALIGN_PARENT_END);
                RelativeLayout.LayoutParams lp1 = (RelativeLayout.LayoutParams) findViewById(R.id.mb2).getLayoutParams();
                lp1.addRule(RelativeLayout.LEFT_OF,R.id.keyboard);
                lp1.addRule(RelativeLayout.RIGHT_OF,0); // Clear right_of prop
                RelativeLayout.LayoutParams lp2 = (RelativeLayout.LayoutParams) findViewById(R.id.main_toggle_mouse).getLayoutParams();
                lp2.addRule(RelativeLayout.LEFT_OF,R.id.keyboard);
                RelativeLayout.LayoutParams lp3 = (RelativeLayout.LayoutParams) findViewById(R.id.installmod_scale_down).getLayoutParams();
                lp3.addRule(RelativeLayout.LEFT_OF,R.id.keyboard);
                RelativeLayout.LayoutParams lp4 = (RelativeLayout.LayoutParams) findViewById(R.id.menu).getLayoutParams();
                lp4.addRule(RelativeLayout.ALIGN_PARENT_LEFT);
            } else {
                // Just a hack to get the settings icon to align to right by default.
                RelativeLayout.LayoutParams lp4 = (RelativeLayout.LayoutParams) findViewById(R.id.menu).getLayoutParams();
                lp4.addRule(RelativeLayout.ALIGN_PARENT_RIGHT);
            }


            // Launch Settings Popup
            findViewById(R.id.menu).setOnTouchListener((view, motionEvent) -> {
                if(motionEvent.getAction() == MotionEvent.ACTION_DOWN){
                    startActivity(new Intent(JavaGUILauncherActivity.this, SettingsActivity.class));
                }
                return false;
            });

            this.touchPad = findViewById(R.id.main_touchpad);
            touchPad.setVisibility(View.VISIBLE);

            this.mousePointer = findViewById(R.id.main_mouse_pointer);
            this.mousePointer.post(() -> {
                ViewGroup.LayoutParams params = mousePointer.getLayoutParams();
                params.width = (int) (36 / 100f * LauncherPreferences.PREF_MOUSESCALE);
                params.height = (int) (54 / 100f * LauncherPreferences.PREF_MOUSESCALE);
            });

            // Just to get the scaled mouse on startup.
            ViewGroup.LayoutParams params = mousePointer.getLayoutParams();
            params.width = (int) (36 / 100f * LauncherPreferences.PREF_MOUSESCALE);
            params.height = (int) (54 / 100f * LauncherPreferences.PREF_MOUSESCALE);

            touchPad.setOnTouchListener((v, event) -> {
                    int action = event.getActionMasked();

                    if(action == KeyEvent.ACTION_DOWN){
                        // Reset checks because this is a new tap
                        longPressTriggered = false;
                        totalMovement = 0;
                        touchStart = System.currentTimeMillis();
                    }

                    float x = event.getX();
                    float y = event.getY();
                    float mouseX = mousePointer.getX();
                    float mouseY = mousePointer.getY();

                    float prevX;
                float prevY;
                if(event.getHistorySize() > 0) {
                        prevX = event.getHistoricalX(0);
                        prevY = event.getHistoricalY(0);
                    }else{
                        prevX = x;
                        prevY = y;
                    }

                    // Long press
                    totalMovement += Math.abs(x - prevX) + Math.abs(y - prevY);
                    if(!longPressTriggered &&
                        System.currentTimeMillis() - touchStart > 1500 &&
                        totalMovement < 5
                    ){
                        longPressTriggered = true;
                        Log.i("Longpress: ",""+totalMovement);

                        AWTInputBridge.sendKey((char)AWTInputEvent.VK_F5,AWTInputEvent.VK_F5);
                        AWTInputBridge.sendMousePress(AWTInputEvent.BUTTON1_DOWN_MASK);
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            vb.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE));
                        } else {
                            //deprecated in API 26
                            vb.vibrate(100);
                        }
                        return true;
                    }

                    if (gestureDetector.onTouchEvent(event)) {
                        sendScaledMousePosition(mouseX,mouseY);
                        AWTInputBridge.sendMousePress(AWTInputEvent.BUTTON1_DOWN_MASK);
                        clearRC();
                    } else {
                        switch (action) {
                            case MotionEvent.ACTION_POINTER_DOWN: //Second finger right clicking
                                AWTInputBridge.sendKey((char)AWTInputEvent.VK_F11,AWTInputEvent.VK_F11);
                                AWTInputBridge.sendMousePress(AWTInputEvent.BUTTON1_DOWN_MASK);
                            case MotionEvent.ACTION_UP: // 1
                                if(longPressTriggered){
                                    // End clickdrag
                                    AWTInputBridge.sendKey((char)AWTInputEvent.VK_F5,AWTInputEvent.VK_F5);
                                }
                                break;
                            case MotionEvent.ACTION_CANCEL: // 3
                            case MotionEvent.ACTION_POINTER_UP: // 6
                                break;
                            case MotionEvent.ACTION_MOVE: // 2
                                // System.out.println("DEBUG: MOUSESPEED"+LauncherPreferences.PREF_MOUSESPEED);
                                mouseX = Math.max(0, Math.min(currentDisplayMetrics.widthPixels, mouseX + (x - prevX) * LauncherPreferences.PREF_MOUSESPEED));
                                mouseY = Math.max(0, Math.min(currentDisplayMetrics.heightPixels, mouseY + (y - prevY) * LauncherPreferences.PREF_MOUSESPEED));
                                placeMouseAt(mouseX, mouseY);
                                sendScaledMousePosition(mouseX,mouseY);
                                break;
                        }
                    }
                    // debugText.setText(CallbackBridge.DEBUG_STRING.toString());
                    //CallbackBridge.DEBUG_STRING.setLength(0);
                    return true;
                });
                
            placeMouseAt(CallbackBridge.physicalWidth / 2f, CallbackBridge.physicalHeight / 2f);

            mTextureView = findViewById(R.id.installmod_surfaceview);
            mTextureView.setOnTouchListener((v, event) -> {
                scaleGestureDetector.onTouchEvent(event);
                float x = event.getX();
                float y = event.getY();
                if (gestureDetector.onTouchEvent(event)) {
                    sendScaledMousePosition(x, y);
                    AWTInputBridge.sendMousePress(AWTInputEvent.BUTTON1_DOWN_MASK);
                    return true;
                }
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_UP: // 1
                    case MotionEvent.ACTION_CANCEL: // 3
                    case MotionEvent.ACTION_POINTER_UP: // 6
                        break;
                    case MotionEvent.ACTION_MOVE: // 2
                        sendScaledMousePosition(x, y);
                        break;
                }
                return true;
            });

            final File miniclient = new File(Tools.DIR_DATA, "rt4.jar");
            final File config = new File(Tools.DIR_DATA, "config.json");
            final String javaArgs = getIntent().getExtras().getString("javaArgs");

            new Thread(() -> {
                try {
                    launchJavaRuntime(miniclient, javaArgs, config);
                } catch (Throwable e) {
                    Tools.showError(JavaGUILauncherActivity.this, e);
                }
            }, "2009Scape").start();
        } catch (Throwable th) {
            Tools.showError(this, th, true);
        }

        // Start the audio service is it's not already running, otherwise it crashes.
        if(!isSoundServiceRunning()){
            JAudioManager.init();
        }
    }

    private boolean isSoundServiceRunning() {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (ActivityManager.RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE)) {
            if (SoundService.class.getName().equals(service.service.getClassName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean onTouch(View v, MotionEvent e) {
        long time = System.currentTimeMillis();
        if (time > lastPress + 500) {
            switch (v.getId()) {
                case R.id.keyboard:
                    InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
                    imm.toggleSoftInput(InputMethodManager.SHOW_FORCED,0);
                    break;
                case R.id.mb2:
                    if (!rcState) {
                        // Send F11 to activate RightClick
                        activateRC();
                    } else {
                        // Send F10 to clear RightClick
                        clearRC();
                    }
                    System.out.println("Time:" + time + " Last " + lastPress);
                    break;
                case R.id.camera:
                    System.out.println("Hello from the App Camera Button");
                    if(!mouseState){ // Camera Mode On
                        AWTInputBridge.sendKey((char)AWTInputEvent.VK_F9,(char)AWTInputEvent.VK_F9); // Send F9
                        v.setBackground(getResources().getDrawable( R.drawable.control_button_pressed ));
                        mouseState = true;
                    }
                    else{ // Camera Mode off
                        AWTInputBridge.sendKey((char)AWTInputEvent.VK_F8,(char)AWTInputEvent.VK_F8);
                        v.setBackground(getResources().getDrawable( R.drawable.control_button_normal ));
                        mouseState = false;
                    }
                    break;
            }
            lastPress = time;
        }
        return true;
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if(event.getAction() == KeyEvent.ACTION_DOWN){
            KeyEncoder.sendEncodedChar(event.getKeyCode(),(char)event.getUnicodeChar());
        }
        return true;
    }

    private void clearRC(){
        rcState = false;
        AWTInputBridge.sendKey((char)AWTInputEvent.VK_F10,AWTInputEvent.VK_F10);
        findViewById(R.id.mb2).setBackground(getResources().getDrawable( R.drawable.control_button ));
    }

    private void activateRC(){
        rcState = true;
        AWTInputBridge.sendKey((char)AWTInputEvent.VK_F11,AWTInputEvent.VK_F11);
        findViewById(R.id.mb2).setBackground(getResources().getDrawable( R.drawable.control_button_pressed ));
    }

    public void placeMouseAt(float x, float y) {
        this.mousePointer.setX(x);
        this.mousePointer.setY(y);
    }

    void sendScaledMousePosition(float x, float y){
        AWTInputBridge.sendMousePos(
                (int) map(x,0,CallbackBridge.physicalWidth, scaleFactors[0], scaleFactors[2]),
                (int) map(y,0,CallbackBridge.physicalHeight, scaleFactors[1], scaleFactors[3])
        );
    }

    public void toggleVirtualMouse(View v) {
        isVirtualMouseEnabled = !isVirtualMouseEnabled;
        touchPad.setVisibility(isVirtualMouseEnabled ? View.GONE : View.VISIBLE);
        ImageView view = findViewById(R.id.mouse_button_img);
        if(isVirtualMouseEnabled){
            view.setImageResource(R.drawable.touch);
        } else{
            view.setImageResource(R.drawable.ic_mouse3);
        }
        touchPad.setVisibility(isVirtualMouseEnabled ? View.GONE : View.VISIBLE);
    }

    public void launchJavaRuntime(File miniclient, String javaArgs, File config) {
        conT = this;
        JREUtils.redirectAndPrintJRELog(this);

        try {
            JREUtils.jreReleaseList = JREUtils.readJREReleaseProperties();
            
            List<String> javaArgList = new ArrayList<>();

            // Load saved username and password
            SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);

            // Enable Caciocavallo
            Tools.getCacioJavaArgs(javaArgList,false);
            
            if (javaArgs != null) {
                javaArgList.addAll(Arrays.asList(javaArgs.split(" ")));
            } else {
                javaArgList.add("-Dusername=" + preferences.getString("username",""));
                javaArgList.add("-Dpassword=" + preferences.getString("password",""));
                javaArgList.add("-DpluginDir="+Tools.DIR_DATA + "/plugins/");
                javaArgList.add("-DclientHomeOverride="+Tools.DIR_DATA+"/");
                javaArgList.add("-jar");
                javaArgList.add(miniclient.getAbsolutePath());
                javaArgList.add(config.getAbsolutePath()); // Pass client config as arg0
                javaArgList.add(Tools.DIR_DATA + "/plugins/");
            }

            Logger.getInstance().appendToLog("Info: Java arguments: " + Arrays.toString(javaArgList.toArray(new String[0])));
            Log.i("Info: Java arguments: ",Arrays.toString(javaArgList.toArray(new String[0])));
            
            // Run java on sandbox, non-overrideable.

            JREUtils.launchJavaVM(this, javaArgList);
        } catch (Throwable th) {
            Tools.showError(this, th, true);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        isFocused = true;
        if(isSoundServiceRunning())
            JAudioManager.resumeSound();
        final int uiOptions = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION;
        final View decorView = getWindow().getDecorView();
        decorView.setSystemUiVisibility(uiOptions);
    }

    float[] initScaleFactors(){
        return initScaleFactors(true);
    }

    float[] initScaleFactors(boolean autoScale){
        //Could be optimized

        if(autoScale) { //Auto scale
            scaleFactor = 2.166F;
        }

        float[] scales = new float[4]; //Left, Top, Right, Bottom

        scales[0] = (CallbackBridge.physicalWidth/2f);
        scales[0] -= scales[0]/scaleFactor;

        scales[1] = (CallbackBridge.physicalHeight/2f);
        scales[1] -= scales[1]/scaleFactor;

        scales[2] = (CallbackBridge.physicalWidth/2f);
        scales[2] += scales[2]/scaleFactor;

        scales[3] = (CallbackBridge.physicalHeight/2f);
        scales[3] += scales[3]/scaleFactor;

        return scales;
    }

    public void scaleDown(View view) {
        scaleFactor = Math.max(scaleFactor - .2F, 1);
        scaleFactors = initScaleFactors(false);
        mTextureView.initScaleFactors(scaleFactor);
        sendScaledMousePosition(mousePointer.getX(),mousePointer.getY());
    }

    public void scaleUp(View view) {
        scaleFactor = Math.min(scaleFactor + .2F, 6);
        scaleFactors = initScaleFactors(false);
        mTextureView.initScaleFactors(scaleFactor);
        sendScaledMousePosition(mousePointer.getX(),mousePointer.getY());
    }
}
