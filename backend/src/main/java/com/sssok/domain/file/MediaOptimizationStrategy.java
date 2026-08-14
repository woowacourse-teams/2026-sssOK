package com.sssok.domain.file;

public final class MediaOptimizationStrategy {

    private MediaOptimizationStrategy() {
    }

    public static OptimizationPlan decide(MediaType mediaType, FileSize fileSize) {
        if (mediaType.isVideo()) {
            return OptimizationPlan.skip();
        }
        if (mediaType.preservesAnimation()) {
            return OptimizationPlan.skip();
        }
        if (fileSize.isBelowCompressionThreshold()) {
            return OptimizationPlan.skip();
        }
        return OptimizationPlan.resizeThenCompress();
    }
}
