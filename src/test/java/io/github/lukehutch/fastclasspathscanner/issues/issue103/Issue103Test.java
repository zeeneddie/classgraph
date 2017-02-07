/*
 * This file is part of FastClasspathScanner.
 * 
 * Author: Luke Hutchison
 * 
 * Hosted at: https://github.com/lukehutch/fast-classpath-scanner
 * 
 * --
 *
 * The MIT License (MIT)
 *
 * Copyright (c) 2016 Luke Hutchison
 * 
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and associated
 * documentation files (the "Software"), to deal in the Software without restriction, including without
 * limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 * the Software, and to permit persons to whom the Software is furnished to do so, subject to the following
 * conditions:
 * 
 * The above copyright notice and this permission notice shall be included in all copies or substantial
 * portions of the Software.
 * 
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT
 * LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO
 * EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN
 * AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE
 * OR OTHER DEALINGS IN THE SOFTWARE.
 */
package io.github.lukehutch.fastclasspathscanner.issues.issue103;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.StrictAssertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

import io.github.lukehutch.fastclasspathscanner.FastClasspathScanner;
import io.github.lukehutch.fastclasspathscanner.scanner.ScanResult;
import io.github.lukehutch.fastclasspathscanner.scanner.ScanResultProcessor;

public class Issue103Test {
    private static boolean exceptionCaughtSync = false;
    private static boolean exceptionCaughtAsync = false;
    private static List<String> classesFoundSync = new ArrayList<>();
    private static List<String> classesFoundAsync = new ArrayList<>();

    static {
        // Test that synchronous scanning from class initializer is allowed (and does not deadlock)
        try {
            new FastClasspathScanner(Issue103Test.class.getPackage().getName())
                    .matchAllClasses(c -> classesFoundSync.add(c.getName())).strictWhitelist().scan();
        } catch (final RuntimeException e) {
            exceptionCaughtSync = true;
        }

        // Test that async scanning is disallowed from class initializer, to prevent deadlock
        final ExecutorService es = Executors.newSingleThreadExecutor();
        try {
            new FastClasspathScanner(Issue103Test.class.getPackage().getName())
                    .matchAllClasses(c -> classesFoundAsync.add(c.getName())).strictWhitelist().scanAsync(es, 1);
        } catch (final RuntimeException e) {
            exceptionCaughtAsync = true;
        } finally {
            es.shutdown();
        }
    }

    @Test
    public void nonInheritedAnnotation() {
        assertThat(exceptionCaughtSync).isFalse();
        assertThat(classesFoundSync).isNotEmpty();

        assertThat(exceptionCaughtAsync).isTrue();
        assertThat(classesFoundAsync).isEmpty();
    }

    @Test
    public void scanResultProcessor() {
        final ExecutorService executorService = Executors.newFixedThreadPool(4);
        final CountDownLatch latch = new CountDownLatch(1);
        try {
            new FastClasspathScanner(Issue103Test.class.getPackage().getName()).scanAsync(executorService, 4,
                    new ScanResultProcessor() {
                        @Override
                        public void processScanResult(final ScanResult scanResult) {
                            latch.countDown();
                        }
                    });
            boolean scanResultProcessorRun = false;
            try {
                scanResultProcessorRun |= latch.await(5, TimeUnit.SECONDS);
            } catch (final InterruptedException e) {
                // Fall through
            }
            if (!scanResultProcessorRun) {
                throw new RuntimeException("ScanResultProcessor was not run or timed out");
            }
        } finally {
            executorService.shutdown();
        }
    }
}