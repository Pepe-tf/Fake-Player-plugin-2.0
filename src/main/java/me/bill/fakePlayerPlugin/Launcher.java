package me.bill.fakePlayerPlugin;

public final class Launcher {

    private Launcher() {}

    public static void main(String[] args) {
        try {
            if (java.awt.Desktop.isDesktopSupported()) {
                java.awt.Desktop desktop = java.awt.Desktop.getDesktop();
                if (desktop.isSupported(java.awt.Desktop.Action.BROWSE)) {
                    desktop.browse(new java.net.URI("https://fpp.wtf"));
                }
            }
        } catch (Exception e) {
            System.err.println("Could not open browser: " + e.getMessage());
        }
        System.exit(0);
    }
}
