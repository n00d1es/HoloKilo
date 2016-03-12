package net.kajos.holokilo.Unity;

import android.app.Activity;
import android.app.ActivityManager;
import android.content.Context;
import android.content.pm.ConfigurationInfo;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;
import android.view.*;
import android.widget.FrameLayout;
import net.kajos.holokilo.Config;


/**
 * Created by Kajos on 17-12-2015.
 */
public class UnityARPlayerActivity extends Activity {
    private UnityPlayer unityPlayer;

    FrameLayout layout;

    public UnityPlayer getUnityPlayer() {
        return unityPlayer;
    }

    public FrameLayout getLayout() {
        return layout;
    }

    public void onCreate(Bundle var) {
        Config.DRAW_CUBES = false;
        Config.SHOW_CAMERA = true;
        Config.DO_AUTO_EXPOSURE = true;

        super.onCreate(var);

        // Unity player
        requestWindowFeature(1);
        super.onCreate(var);

        getWindow().takeSurface(null);
        getWindow().setFormat(2);
        layout = new FrameLayout(this);

        unityPlayer = new UnityPlayer(this);

        layout.addView(unityPlayer);
        setContentView(layout);
        unityPlayer.requestFocus();
    }

    public void onDestroy() {
        super.onDestroy();

        unityPlayer.quit();
    }

    public void onPause() {
        super.onPause();

        unityPlayer.pause();
    }

    public void onResume() {
        super.onResume();

        unityPlayer.resume();
    }

    public void setStereo(boolean stereo) {

    }

    @Override
    public void onConfigurationChanged(Configuration var1) {
        super.onConfigurationChanged(var1);
        unityPlayer.configurationChanged(var1);
    }

    @Override
    public void onWindowFocusChanged(boolean var1) {
        super.onWindowFocusChanged(var1);
        unityPlayer.windowFocusChanged(var1);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent var1) {
        return var1.getAction() == 2?unityPlayer.injectEvent(var1):super.dispatchKeyEvent(var1);
    }

    @Override
    public boolean onKeyUp(int var1, KeyEvent var2) {
        return unityPlayer.injectEvent(var2);
    }

    @Override
    public boolean onKeyDown(int var1, KeyEvent var2) {
        return unityPlayer.injectEvent(var2);
    }

    @Override
    public boolean onTouchEvent(MotionEvent var1) {
        unityPlayer.initBase();
        return unityPlayer.injectEvent(var1);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent var1) {
        return unityPlayer.injectEvent(var1);
    }
}