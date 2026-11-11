package org.education.jpms.library.api;

public final class Greetings {

    private Greetings() {
    }

    public static String welcome(String person) {
        return "Hello, " + person;
    }
}
