package org.godotengine.godotvibration;

import android.app.Activity;
import android.content.Context;
import android.os.Build;
import android.os.Vibrator;

import android.os.VibrationEffect;

import org.godotengine.godot.Godot;
import org.godotengine.godot.plugin.GodotPlugin;
import org.godotengine.godot.plugin.SignalInfo;

import androidx.annotation.NonNull;
import androidx.collection.ArraySet;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

/**
 * Created by Rodrigo Favarete, Mad Forest Games' Lead Game Developer, on September 8, 2017
 */

public class GodotVibration extends GodotPlugin {
    private Vibrator vibrator;
    protected Activity activity = null;


    /**
     * Constructor
     */

    public GodotVibration(Godot godot) {
        super(godot);
        this.activity = godot;
        this.vibrator = (Vibrator) this.activity.getSystemService(Context.VIBRATOR_SERVICE);

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

    @NonNull
    @Override
    public Set<SignalInfo> getPluginSignals() {
        Set<SignalInfo> signals = new ArraySet<>();
        return signals;
    }
    /* Methods
     * ********************************************************************** */

    public void vibrate(int duration, int amplitude) {
        if (this.vibrator.hasVibrator()) {
            if (Build.VERSION.SDK_INT >= 26) {
                this.vibrator.vibrate(VibrationEffect.createOneShot(duration, amplitude));
            } else {
                this.vibrator.vibrate(duration);
            }
        }
    }
}