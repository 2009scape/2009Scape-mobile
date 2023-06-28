package net.kdt.pojavlaunch;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.DisplayMetrics;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.Button;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.preference.PreferenceManager;

import com.google.android.material.switchmaterial.SwitchMaterial;

import net.kdt.pojavlaunch.prefs.LauncherPreferences;
import net.kdt.pojavlaunch.utils.Tools;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;

public class SettingsActivity extends Activity {

    private static final int FILE_SELECT_CODE = 0;

    private void showFileChooser() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);

        try {
            startActivityForResult(
                    Intent.createChooser(intent, "Select a File to Upload"),
                    FILE_SELECT_CODE);
        } catch (android.content.ActivityNotFoundException ex) {
            // Potentially direct the user to the Market with a Dialog
            Toast.makeText(this, "Please install a File Manager.",
                    Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == FILE_SELECT_CODE) {
            if (resultCode == RESULT_OK) {
                Uri uri = data.getData();
                File config = new File(Tools.DIR_DATA, "config.json");
                try {
                    Log.d("TAG", "Starting copy: " + uri.getPath());
                    InputStream inputStream = getContentResolver().openInputStream(uri);
                    FileOutputStream fileOutputStream = new FileOutputStream(config);
                    byte buf[] = new byte[1024];
                    int len;
                    while ((len = inputStream.read(buf)) > 0) {
                        fileOutputStream.write(buf, 0, len);
                    }
                    fileOutputStream.close();
                    inputStream.close();
                    Toast.makeText(this, "Config loaded. Please restart the app.",
                            Toast.LENGTH_SHORT).show();
                } catch (IOException e1) {
                    Log.d("error", "Error with file " + e1);
                }
            }
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.settings_menu);
        DisplayMetrics dm = new DisplayMetrics();
        getWindowManager().getDefaultDisplay().getMetrics(dm);
        int width = dm.widthPixels;
        int height = dm.heightPixels;
        getWindow().setLayout(((int)(width*0.8)),((int)(height*0.8)));

        final EditText username = findViewById(R.id.username);
        final EditText password = findViewById(R.id.password);
        final SwitchMaterial righthanded = findViewById(R.id.righthanded);
        final SeekBar mouseSpeed = findViewById(R.id.mouseslider);
        final Button loadConfig = findViewById(R.id.loadconfig);
        final Button saveLogin = findViewById(R.id.savelogin);

        //For storing string value in sharedPreference
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        SharedPreferences.Editor editor = preferences.edit();

        loadConfig.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                showFileChooser();
                return true;
            }
            return false;
        });

        saveLogin.setOnTouchListener((v, event) -> {
            if (event.getAction() == MotionEvent.ACTION_DOWN) {
                editor.putString("password",password.getText().toString());
                editor.putString("username",username.getText().toString());
                editor.commit();
                Toast.makeText(this, "Saved login data. Please restart the app.",
                        Toast.LENGTH_SHORT).show();
                return true;
            }
            return false;
        });

        // Restore from saved values
        username.setText(preferences.getString("username",""));
        password.setText(preferences.getString("password",""));
        mouseSpeed.setProgress((int)LauncherPreferences.PREF_MOUSESPEED);
        righthanded.setChecked(Boolean.parseBoolean(preferences.getString("righthanded","false")));

        username.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                System.out.println("Username: "+username.getText());
                editor.putString("username",username.getText().toString());
                editor.commit();
                return true;
            }
            return false;
        });

        password.setOnKeyListener((v, keyCode, event) -> {
            if (event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_ENTER) {
                System.out.println("Password: "+password.getText());
                editor.putString("password",password.getText().toString());
                editor.commit();
                return true;
            }
            return false;
        });

        mouseSpeed.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int i, boolean b) {
                editor.putInt("mousespeed",mouseSpeed.getProgress());
                editor.commit();
                LauncherPreferences.PREF_MOUSESPEED = mouseSpeed.getProgress()/1.5f;
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });

        righthanded.setOnCheckedChangeListener((buttonView, isChecked) -> {
            editor.putString("righthanded",""+isChecked);
            editor.commit();
            Toast.makeText(this, "Relaunch required to update GUI.",
                    Toast.LENGTH_SHORT).show();
        });

    }
}
