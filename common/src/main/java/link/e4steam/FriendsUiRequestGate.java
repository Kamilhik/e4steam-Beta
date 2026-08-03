package link.e4steam;

/** Rejects duplicate UI work and stale async completions after a screen is closed or rebuilt. */
public final class FriendsUiRequestGate {
    private int generation;
    private boolean open;
    private boolean busy;

    public synchronized int open() {
        open = true;
        busy = false;
        return ++generation;
    }

    public synchronized int close() {
        open = false;
        busy = false;
        return ++generation;
    }

    public synchronized int tryBegin() {
        if (!open || busy) {
            return -1;
        }
        busy = true;
        return generation;
    }

    public synchronized boolean finish(int token) {
        if (!open || token != generation) {
            return false;
        }
        busy = false;
        return true;
    }

    public synchronized boolean isBusy() {
        return busy;
    }

    public synchronized boolean isOpen() {
        return open;
    }
}
