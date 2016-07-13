package com.github.mtakelemen.hellodocker.build

import java.util.function.Consumer
import java.util.function.Function

class GroovyUtils {
    public static String join(List<String> strs, String joinStr) {
        return strs.join(joinStr)
    }

    public static void forEachLine(String str, Consumer<String> lineProcessor) {
        str.eachLine { String line ->
            lineProcessor.accept(line)
        }
    }

    public static Closure<?> toClosure(Function<?, ?> action) {
        return { arg ->
            return action.apply(arg)
        }
    }
}

