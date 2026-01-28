package disd.godot.plugin.android.netP5;

import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Lightweight observable base class that avoids java.util.Observable (deprecated).
 * Thread-safe for add/remove while notifying.
 */
public class NetObservableBase implements NetObservable {

    private final CopyOnWriteArrayList<NetObserver> observers = new CopyOnWriteArrayList<>();

    @Override
    public void addObserver(NetObserver o) {
        if (o == null) return;
        if (!observers.contains(o)) observers.add(o);
    }

    @Override
    public void deleteObserver(NetObserver o) {
        if (o == null) return;
        observers.remove(o);
    }

    @Override
    public void deleteObservers() {
        observers.clear();
    }

    @Override
    public int countObservers() {
        return observers.size();
    }

    @Override
    public void notifyObservers(Object arg) {
        for (NetObserver o : observers) {
            try {
                o.update(this, arg);
            } catch (Throwable ignored) {
                // Don't let a buggy observer kill the networking thread.
            }
        }
    }
}
