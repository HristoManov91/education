package org.education.jpms.library.internal;

/**
 * Public type in a non-exported package. Its public visibility alone does
 * not make reflective access legal across JPMS module boundaries.
 */
public final class Vault {

    private final String code = "demo-only";

    public Vault() {
    }
}
