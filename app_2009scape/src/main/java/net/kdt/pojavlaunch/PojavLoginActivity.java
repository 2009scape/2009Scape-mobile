package net.kdt.pojavlaunch;

import static net.kdt.pojavlaunch.utils.Architecture.archAsString;

import android.Manifest;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.res.AssetManager;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import net.kdt.pojavlaunch.multirt.MultiRTUtils;
import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.Tools;

import org.apache.commons.io.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;

public class PojavLoginActivity extends BaseActivity {
    private final Object mLockStoragePerm = new Object();
    private final Object mLockSelectJRE = new Object();
    
    private EditText edit2, edit3;
    private final int REQUEST_STORAGE_REQUEST_CODE = 1;
    private CheckBox sRemember, sOffline;
    private TextView startupTextView;
    private SharedPreferences firstLaunchPrefs;
    
    private boolean isSkipInit = false;
    private boolean isStarting = false;

    public static final String PREF_IS_INSTALLED_JAVARUNTIME = "isJavaRuntimeInstalled";
    
    @Override
    protected void onCreate(Bundle savedInstanceState){
        super.onCreate(savedInstanceState); // false;
        if(savedInstanceState != null) {
            isStarting = savedInstanceState.getBoolean("isStarting");
            isSkipInit = savedInstanceState.getBoolean("isSkipInit");
        }
        Tools.updateWindowSize(this);
        firstLaunchPrefs = getSharedPreferences("pojav_extract", MODE_PRIVATE);
        new Thread(new InitRunnable()).start();
        // If we get here that's because the client was closed.
        // having this here causes the app to close its activity when installing jdk.
        //finish();
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("isStarting",isStarting);
        outState.putBoolean("isSkipInit",isSkipInit);
    }

    public class InitRunnable implements Runnable{
        private int revokeCount = -1;
        private int proceedState = 0;
        private ProgressBar progress;
        public InitRunnable() {
        }
        public void initLocalUi() {
            LinearLayout startScr = new LinearLayout(PojavLoginActivity.this);
            LayoutInflater.from(PojavLoginActivity.this).inflate(R.layout.start_screen,startScr);
            PojavLoginActivity.this.setContentView(startScr);

            progress = (ProgressBar) findViewById(R.id.startscreenProgress);
            if(isStarting) progress.setVisibility(View.VISIBLE);
            startupTextView = (TextView) findViewById(R.id.startscreen_text);
        }

        public int _start() {
            Log.i("UITest","START initialization");
            if(!isStarting) {
                //try { Thread.sleep(2000); } catch (InterruptedException e) { }
                runOnUiThread(() -> progress.setVisibility(View.VISIBLE));
                while (Build.VERSION.SDK_INT >= 23 && Build.VERSION.SDK_INT < 29 && !isStorageAllowed()) { //Do not ask for storage at all on Android 10+
                    try {
                        revokeCount++;
                        if (revokeCount >= 3) {
                            Toast.makeText(PojavLoginActivity.this, R.string.toast_permission_denied, Toast.LENGTH_LONG).show();
                            return 2;
                        }
                        requestStoragePermission();

                        synchronized (mLockStoragePerm) {
                            mLockStoragePerm.wait();
                        }
                    } catch (InterruptedException e) {
                    }
                }
                isStarting = true;
            }
            try {
                initMain();
            } catch (Throwable th) {
                Tools.showError(PojavLoginActivity.this, th, true);
                return 1;
            }
            return 0;
        }
        public void proceed() {
            isStarting = false;
            switch(proceedState) {
                case 2:
                    finish();
                    break;
                case 0:
                    uiInit();
                    break;
            }
        }
        @Override
        public void run() {
            if(!isSkipInit) {
                PojavLoginActivity.this.runOnUiThread(this::initLocalUi);
                proceedState = _start();
            }
            PojavLoginActivity.this.runOnUiThread(this::proceed);
        }
    }
    private void uiInit() {
        setContentView(R.layout.launcher_main_v4);
        final File miniclient = new File(Tools.DIR_DATA, "rt4.jar");
        Thread t = new Thread(()->{
            PojavLoginActivity.this.runOnUiThread(() -> {
                Intent intent = new Intent(PojavLoginActivity.this, JavaGUILauncherActivity.class);
                intent.putExtra("miniclient", miniclient);
                startActivity(intent);
            });
        });
        t.start();
    }
    
    @Override
    public void onResume() {
        super.onResume();
        Tools.updateWindowSize(this);
    }

   
    private void unpackComponent(AssetManager am, String component) throws IOException {
        File versionFile = new File(Tools.DIR_GAME_HOME + "/" + component + "/version");
        InputStream is = am.open("components/" + component + "/version");
        if(!versionFile.exists()) {
            if (versionFile.getParentFile().exists() && versionFile.getParentFile().isDirectory()) {
                FileUtils.deleteDirectory(versionFile.getParentFile());
            }
            versionFile.getParentFile().mkdir();
            
            Log.i("UnpackPrep", component + ": Pack was installed manually, or does not exist, unpacking new...");
            String[] fileList = am.list("components/" + component);
            for(String s : fileList) {
                Tools.copyAssetFile(this, "components/" + component + "/" + s, Tools.DIR_GAME_HOME + "/" + component, true);
            }
        } else {
            FileInputStream fis = new FileInputStream(versionFile);
            String release1 = Tools.read(is);
            String release2 = Tools.read(fis);
            if (!release1.equals(release2)) {
                if (versionFile.getParentFile().exists() && versionFile.getParentFile().isDirectory()) {
                    FileUtils.deleteDirectory(versionFile.getParentFile());
                }
                versionFile.getParentFile().mkdir();
                
                String[] fileList = am.list("components/" + component);
                for (String s : fileList) {
                    Tools.copyAssetFile(this, "components/" + component + "/" + s, Tools.DIR_GAME_HOME + "/" + component, true);
                }
            } else {
                Log.i("UnpackPrep", component + ": Pack is up-to-date with the launcher, continuing...");
            }
        }
    }
    private void initMain() {
        mkdirs(Tools.DIR_ACCOUNT_NEW);
        mkdirs(Tools.DIR_GAME_HOME);
        mkdirs(Tools.DIR_GAME_HOME + "/lwjgl3");
        mkdirs(Tools.DIR_GAME_HOME + "/config");
        mkdirs(Tools.DIR_DATA + "/music");
        mkdirs(Tools.DIR_DATA + "/effects");
        mkdirs(Tools.DIR_DATA + "/plugins");
        mkdirs(Tools.CTRLMAP_PATH);
        try {
            Tools.copyAssetFile(this, "components/security/pro-grade.jar", Tools.DIR_DATA, true);
            Tools.copyAssetFile(this, "components/security/java_sandbox.policy", Tools.DIR_DATA, true);
            // TODO: Remove after implement.
            Tools.copyAssetFile(this,"resolv.conf",Tools.DIR_DATA, true);
            AssetManager am = this.getAssets();
            
            unpackComponent(am, "caciocavallo");
            unpackComponent(am, "lwjgl3");
            if(!installRuntimeAutomatically(am,MultiRTUtils.getRuntimes().size() > 0)) {
                synchronized (mLockSelectJRE) {
                    mLockSelectJRE.wait();
                }
            }
            LauncherPreferences.loadPreferences(getApplicationContext());
        }
        catch(Throwable e){
            Tools.showError(this, e);
        }
    }
    private boolean installRuntimeAutomatically(AssetManager am, boolean otherRuntimesAvailable) {
        /* Check if JRE is included, This only runs on first startup. */
        String rt_version = null;
        String current_rt_version = MultiRTUtils.__internal__readBinpackVersion("Internal");
        try {
            rt_version = Tools.read(am.open("components/jre/version"));
        } catch (IOException e) {
            Log.e("JREAuto", "JRE was not included on this APK.", e);
        }
        if(current_rt_version == null && otherRuntimesAvailable) return true; //Assume user maintains his own runtime
        if(rt_version == null) return false;
        if(!rt_version.equals(current_rt_version)) { //If we already have an integrated one installed, check if it's up-to-date
            try {
                MultiRTUtils.installRuntimeNamedBinpack(am.open("components/jre/universal.tar.xz"), am.open("components/jre/bin-" + archAsString(Tools.DEVICE_ARCHITECTURE) + ".tar.xz"), "Internal", rt_version,
                        (resid, vararg) -> runOnUiThread(()->{if(startupTextView!=null)startupTextView.setText(getString(resid,vararg));}));
                MultiRTUtils.postPrepare(PojavLoginActivity.this,"Internal");


                Tools.copyAssetFile(this,"rt4.jar",Tools.DIR_DATA,"rt4.jar", true);
                Tools.copyAssetFile(this,"config.json",Tools.DIR_DATA, true);


                // MobileClientBindings
                Tools.copyAssetFile(this, "MobileClientBindings.zip",Tools.DIR_DATA, true);
                Tools.ZipTool.unzip(new File(Tools.DIR_DATA+ "/MobileClientBindings.zip"),
                        new File(Tools.DIR_DATA + "/plugins/"));

                // GroundItemsOverlay
                Tools.copyAssetFile(this, "GroundItems.zip",Tools.DIR_DATA, true);
                Tools.ZipTool.unzip(new File(Tools.DIR_DATA+ "/GroundItems.zip"),
                        new File(Tools.DIR_DATA + "/plugins/"));

                // LoginTimer
                Tools.copyAssetFile(this, "LoginTimer.zip",Tools.DIR_DATA, true);
                Tools.ZipTool.unzip(new File(Tools.DIR_DATA+ "/LoginTimer.zip"),
                        new File(Tools.DIR_DATA + "/plugins/"));

                // RememberMyLogin
                Tools.copyAssetFile(this, "RememberMyLogin.zip",Tools.DIR_DATA, true);
                Tools.ZipTool.unzip(new File(Tools.DIR_DATA+ "/RememberMyLogin.zip"),
                        new File(Tools.DIR_DATA + "/plugins/"));

                // SlayerTrackerPlugin
                Tools.copyAssetFile(this, "SlayerTrackerPlugin.zip",Tools.DIR_DATA, true);
                Tools.ZipTool.unzip(new File(Tools.DIR_DATA+ "/SlayerTrackerPlugin.zip"),
                        new File(Tools.DIR_DATA + "/plugins/"));

                // XPDropPlugin
                Tools.copyAssetFile(this, "XPDropPlugin.zip",Tools.DIR_DATA, true);
                Tools.ZipTool.unzip(new File(Tools.DIR_DATA+ "/XPDropPlugin.zip"),
                        new File(Tools.DIR_DATA + "/plugins/"));

                /*
                // Extract predumped sounds
                try{
                    // Unpack Music
                    int totalSongs = 644;
                    for(int i = 0; i <= totalSongs; i++){
                        Tools.copyAssetFile(this, "music/"+i+".ogg", Tools.DIR_DATA + "/music", true);
                        String str = "Song: "+i+".ogg";
                        runOnUiThread(()->{if(startupTextView!=null)startupTextView.setText(str);});
                    }
                    // Unpack Sound Effects
                    int totalsfx = 6749;
                    for(int i = 0; i <= totalsfx; i++){
                        Tools.copyAssetFile(this, "effects/"+i+".ogg", Tools.DIR_DATA + "/effects", true);
                        String str = "SFX: "+i+".ogg";
                        runOnUiThread(()->{if(startupTextView!=null)startupTextView.setText(str);});
                    }
                }
                catch (Exception e){
                    System.out.println("Error Unpacking Music.");
                }
                 */
                return true;
            }catch (IOException e) {
                Log.e("JREAuto", "Internal JRE unpack failed", e);
                return false;
            }
        }else return true; // we have at least one runtime, and it's compatible, good to go
    }

    private static boolean mkdirs(String path)
    {
        File file = new File(path);
        // check necessary???
        if(file.getParentFile().exists())
             return file.mkdir();
        else return file.mkdirs();
    }


    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
    }

    //We are calling this method to check the permission status
    private boolean isStorageAllowed() {
        //Getting the permission status
        int result1 = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        int result2 = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE);


        //If permission is granted returning true
        return result1 == PackageManager.PERMISSION_GRANTED &&
            result2 == PackageManager.PERMISSION_GRANTED;
    }

    //Requesting permission
    private void requestStoragePermission()
    {
        ActivityCompat.requestPermissions(this, new String[]{
            Manifest.permission.WRITE_EXTERNAL_STORAGE, Manifest.permission.READ_EXTERNAL_STORAGE}, REQUEST_STORAGE_REQUEST_CODE);
    }

    // This method will be called when the user will tap on allow or deny
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_STORAGE_REQUEST_CODE) {
            synchronized (mLockStoragePerm) {
                mLockStoragePerm.notifyAll();
            }
        }
    }

}
