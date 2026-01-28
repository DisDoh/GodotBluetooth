package disd.godot.plugin.android.netP5;

/**
 * Replacement for deprecated java.util.Observer.
 */
public interface NetObserver {
    void update(NetObservable observable, Object arg);
}
