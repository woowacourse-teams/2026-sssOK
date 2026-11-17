package com.sssok.benchmark;

import java.io.PrintWriter;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

/** Java 25 비교 이미지에서만 컴파일·실행한다. */
public class FfmComparisonRunner {
    public static void main(String[] args) {
        var listener = new SummaryGeneratingListener();
        var request = LauncherDiscoveryRequestBuilder.request().selectors(
            DiscoverySelectors.selectClass("com.sssok.renewal.outgoing.processing.image.LibvipsFfmImageProcessorTest"),
            DiscoverySelectors.selectClass("com.sssok.renewal.outgoing.processing.image.LibvipsImageProcessorTest"),
            DiscoverySelectors.selectClass("com.sssok.renewal.outgoing.processing.image.ThumbnailatorImageProcessorTest")
        ).build();
        LauncherFactory.create().execute(request, listener);
        var summary = listener.getSummary();
        summary.printTo(new PrintWriter(System.out, true));
        summary.printFailuresTo(new PrintWriter(System.out, true));
        if (summary.getTestsFoundCount() == 0 || summary.getTestsFailedCount() > 0
            || summary.getTestsSkippedCount() > 0 || summary.getTestsAbortedCount() > 0
            || summary.getContainersFailedCount() > 0 || summary.getContainersAbortedCount() > 0) {
            throw new IllegalStateException("Image contract tests did not all pass");
        }
    }
}
