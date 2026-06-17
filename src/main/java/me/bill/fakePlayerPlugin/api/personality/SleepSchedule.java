package me.bill.fakePlayerPlugin.api.personality;

public final class SleepSchedule {

    private final int sleepHour;
    private final int wakeHour;

    public SleepSchedule(int sleepHour, int wakeHour) {
        this.sleepHour = clampHour(sleepHour);
        this.wakeHour = clampHour(wakeHour);
    }

    public static SleepSchedule defaultSchedule() {
        return new SleepSchedule(22, 8);
    }

    public static SleepSchedule noSleep() {
        return new SleepSchedule(-1, -1);
    }

    public int getSleepHour() {
        return sleepHour;
    }

    public int getWakeHour() {
        return wakeHour;
    }

    public boolean isAwakeAt(int hour) {
        if (sleepHour == -1 || wakeHour == -1) return true;
        hour = clampHour(hour);
        if (wakeHour < sleepHour) {
            return hour >= wakeHour && hour < sleepHour;
        }
        return hour >= wakeHour || hour < sleepHour;
    }

    private static int clampHour(int hour) {
        if (hour < -1) return -1;
        if (hour > 23) return 23;
        return hour;
    }

    @Override
    public String toString() {
        return "SleepSchedule{" + "sleep=" + sleepHour + ", wake=" + wakeHour + '}';
    }
}
