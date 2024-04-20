package ch.disd.godotToast;

import android.app.Activity;

import java.util.Set;
import org.godotengine.godot.Godot;
import org.godotengine.godot.plugin.GodotPlugin;
import org.godotengine.godot.plugin.SignalInfo;
import org.godotengine.godot.plugin.UsedByGodot;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.collection.ArraySet;

public class GodotToast extends GodotPlugin {

    private Activity activity = null; // The main activity of the game

    public static final String TAG = "GodotToast";

    private Godot godot = null;




    public GodotToast(Godot godot) {
        super(godot);
        this.godot = godot;
        activity = getActivity();
    }

    @NonNull
    @Override
    public String getPluginName() {
        return "GodotToast";
    }

//    @NonNull
//    @Override
//    public List<String> getPluginMethods() {
//        return Arrays.asList(
//                "vibrate");
//    }

    @NonNull
    @Override
    public Set<SignalInfo> getPluginSignals() {
        Set<SignalInfo> signals = new ArraySet<>();
        return signals;
    }

    /* Methods
     * ********************************************************************** */
    @UsedByGodot
    public void print(String text) {
        activity.runOnUiThread(new Runnable() {
            public void run() {
                Toast.makeText(activity, text, Toast.LENGTH_SHORT).show();
            }
        });
    }
}