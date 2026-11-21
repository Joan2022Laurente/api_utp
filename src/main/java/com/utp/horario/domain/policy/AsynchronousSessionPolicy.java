package com.utp.horario.domain.policy;

import java.util.Set;

/**
 * Política canónica para identificar asignaturas asíncronas (Virtual 24/7).
 *
 * <p>Regla reducida por Ley de Absorción Booleana (Checkpoint UTP §18.1):
 * la condición original del bundle oficial
 * {@code VT || (SP && classType∈{3,4}) || classType∈{3,4}} se simplifica a
 * {@code VT || classType∈{3,4}} porque {@code (B∧C)∨C ≡ C}.</p>
 *
 * <p>{@code classType} se compara como {@code String} contra {@code "3"} y {@code "4"},
 * respetando el tipo primitivo con el que el AST de GraphQL serializa el campo.</p>
 */
public final class AsynchronousSessionPolicy {

    private static final Set<String> ASYNC_CLASS_TYPES = Set.of("3", "4");

    private AsynchronousSessionPolicy() {
    }

    public static boolean isAsynchronous(String instructionMode, String classType) {
        if ("VT".equalsIgnoreCase(instructionMode)) {
            return true;
        }
        return classType != null && ASYNC_CLASS_TYPES.contains(classType.trim());
    }
}
