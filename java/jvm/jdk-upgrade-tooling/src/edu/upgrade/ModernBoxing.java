package edu.upgrade;

public final class ModernBoxing {
    private ModernBoxing() {
    }

    public static int value() {
        return Integer.valueOf(42).intValue();
    }

    public static void main(String[] args) {
        System.out.println("VALUE=" + value());
    }
}
