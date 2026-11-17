package com.sssok.benchmark;

import java.io.PrintWriter;
import java.util.Arrays;
import org.junit.platform.engine.discovery.DiscoverySelectors;
import org.junit.platform.launcher.core.LauncherDiscoveryRequestBuilder;
import org.junit.platform.launcher.core.LauncherFactory;
import org.junit.platform.launcher.listeners.SummaryGeneratingListener;

/** Docker native-test 단계의 진입점. 누락·실패·스킵을 성공으로 처리하지 않는다. */
public class NativeImageTestRunner {
    public static void main(String[] args) {
        var listener = new SummaryGeneratingListener();
        var request = LauncherDiscoveryRequestBuilder.request().selectors(
            DiscoverySelectors.selectClass("com.sssok.renewal.outgoing.processing.image.LibvipsImageProcessorTest"),
            DiscoverySelectors.selectClass("com.sssok.renewal.outgoing.processing.image.ThumbnailatorImageProcessorTest"),
            DiscoverySelectors.selectClass("com.sssok.renewal.outgoing.processing.image.ImageProcessorConfigurationTest"),
            DiscoverySelectors.selectClass("com.sssok.renewal.outgoing.processing.video.FfmpegVideoFrameExtractorTest")
        );
        if (Arrays.asList(args).contains("--include-cli")) {
            request.selectors(DiscoverySelectors.selectClass(
                "com.sssok.renewal.outgoing.processing.image.LibvipsCliImageProcessorTest"));
        }
        LauncherFactory.create().execute(request.build(), listener);
        var summary = listener.getSummary();
        summary.printTo(new PrintWriter(System.out, true));
        summary.printFailuresTo(new PrintWriter(System.out, true));
        if (summary.getTestsFoundCount() == 0 || summary.getTestsFailedCount() > 0
            || summary.getTestsSkippedCount() > 0 || summary.getTestsAbortedCount() > 0
            || summary.getContainersFailedCount() > 0 || summary.getContainersAbortedCount() > 0) {
            throw new IllegalStateException("Image and video contract tests did not all pass");
        }
    }
}
