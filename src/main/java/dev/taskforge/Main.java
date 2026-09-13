package dev.taskforge;

public final class Main {

    private Main() {
        // Classe utilitaire : pas d'instanciation.
    }

    public static String applicationName() {
        return "TaskForge";
    }

    public static void main(String[] args) {
        System.out.println(applicationName() + " bootstrap OK");
        System.out.println("Java runtime: " + Runtime.version());
        System.out.println("Phase 0 initialized");
    }
}