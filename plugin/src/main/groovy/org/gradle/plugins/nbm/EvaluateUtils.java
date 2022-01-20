package org.gradle.plugins.nbm;

import groovy.lang.Closure;

public final class EvaluateUtils {
    public static String asString(Object obj) {
        if (obj instanceof Closure) {
            return asString(((Closure<?>) obj).call());
        }

        return obj != null ? obj.toString() : null;
    }

    private EvaluateUtils() {
        throw new AssertionError();
    }

}
