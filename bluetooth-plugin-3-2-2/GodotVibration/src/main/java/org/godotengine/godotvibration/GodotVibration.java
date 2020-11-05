package org.godotengine.godotvibration;

import android.app.Activity;
import android.os.Build;
import android.os.Vibrator;

import android.os.VibrationEffect;

import org.godotengine.godot.Godot;
import org.godotengine.godot.plugin.GodotPlugin;

import androidx.annotation.NonNull;

import java.util.Arrays;
import java.util.List;

/**
 * Created by Rodrigo Favarete, Mad Forest Games' Lead Game Developer, on September 8, 2017
 */

public class GodotVibration extends GodotPlugin {
    protected Activity activity = null;
    private Vibrator vibrator;


    /**
     * Constructor
     */

    public GodotVibration(Godot godot) {
        super(godot);
        activity = godot;
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "GodotVibration";
    }

    @NonNull
    @Override
    public List<String> getPluginMethods() {
        return Arrays.asList(
                "vibrate");
    }

    /* Methods
     * ********************************************************************** */

    public void vibrate(int duration, int amplitude)
    {
        if (this.vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= 26) {
                this.vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude));
            } else {
                this.vibrator.vibrate(duration);
            }
        }
    }




//    @Override
//    public void this.onResume()
//    {
//        super.onResume();
//        GodotLib.calldeferred(instanceId, "_on_resume", new Object[]{});
//    }
//    @Override
//    public void onPause()
//    {
//        super.onPause();
//        GodotLib.calldeferred(instanceId, "_on_pause", new Object[]{});
//    }

    /* Definitions
     * ********************************************************************** */


}
