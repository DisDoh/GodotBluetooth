package disd.godot.plugin.android.netP5;

/**
 * Replacement for deprecated java.util.Observable.
 */
public interface NetObservable {
    void addObserver(NetObserver o);
    void deleteObserver(NetObserver o);
    void deleteObservers();
    int countObservers();
    void notifyObservers(Object arg);
}
