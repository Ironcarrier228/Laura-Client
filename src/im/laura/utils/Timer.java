package im.laura.utils;

public class Timer {
    private long time;

    public Timer() {
        reset();
    }

    public void reset() {
        this.time = System.currentTimeMillis();
    }

    public boolean hasPassed(long ms) {
        return System.currentTimeMillis() - this.time >= ms;
    }

    public boolean passedMs(long ms) {
        return System.currentTimeMillis() - this.time >= ms;
    }

    public long getTime() {
        return this.time;
    }

    public void setTime(long time) {
        this.time = time;
    }
}
