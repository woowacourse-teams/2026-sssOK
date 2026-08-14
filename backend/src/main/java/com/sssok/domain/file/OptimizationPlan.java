package com.sssok.domain.file;

import java.util.List;

public record OptimizationPlan(boolean skipped, List<OptimizationStep> steps) {

    private static final OptimizationStep PRIMARY = new OptimizationStep(1600, 0.8);
    private static final OptimizationStep SECONDARY = new OptimizationStep(1200, 0.7);

    public OptimizationPlan {
        steps = List.copyOf(steps);
    }

    public static OptimizationPlan skip() {
        return new OptimizationPlan(true, List.of());
    }

    public static OptimizationPlan resizeThenCompress() {
        return new OptimizationPlan(false, List.of(PRIMARY, SECONDARY));
    }

    public record OptimizationStep(int maxWidth, double quality) {
    }
}
