package me.bill.fakePlayerPlugin.fakeplayer;

public final class BotPathfinder {
    private BotPathfinder() {}

    public record PathOptions(
            boolean parkour, boolean breakBlocks, boolean placeBlocks, boolean avoidWater, boolean avoidLava) {
        public PathOptions(boolean parkour, boolean breakBlocks, boolean placeBlocks) {
            this(parkour, breakBlocks, placeBlocks, false, false);
        }

        public static final PathOptions DEFAULT = new PathOptions(false, false, false, false, false);

        public boolean anyEnabled() {
            return parkour || breakBlocks || placeBlocks;
        }
    }
}
