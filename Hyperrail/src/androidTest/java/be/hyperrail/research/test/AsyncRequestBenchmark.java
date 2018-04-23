package be.hyperrail.research.test;

import android.support.annotation.NonNull;
import android.support.test.InstrumentationRegistry;
import android.support.test.runner.AndroidJUnit4;
import android.util.Log;

import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.sql.SQLOutput;

import be.hyperrail.research.irail.contracts.IRailErrorResponseListener;
import be.hyperrail.research.irail.contracts.IRailSuccessResponseListener;
import be.hyperrail.research.irail.contracts.IrailStationProvider;
import be.hyperrail.research.irail.implementation.linkedconnections.LinkedConnections;
import be.hyperrail.research.irail.implementation.linkedconnections.LinkedConnectionsProvider;

/**
 * Created in be.hyperrail.android.test on 27/03/2018.
 */

@RunWith(AndroidJUnit4.class)
public class AsyncRequestBenchmark {

    private static boolean hasAsyncFinished = false;
    private static long duration[][] = new long[144][24];

    /**
     * Measure how long it takes to load linked connections pages asynchronously
     */
    @Test
    public void benchmark() {

        LinkedConnectionsProvider provider = new LinkedConnectionsProvider(InstrumentationRegistry.getTargetContext());
        // Runs should not interfere with eachother
        provider.setCacheEnabled(false);

        for (int i = 1; i <= 24 * 6; i++) {
            // take an average of each hour, to exclude variatons depending on
            for (int j = 0; j < 24; j++) {
                DateTime startTime = new DateTime(2018, 3, 26, j, 0, 0);
                DateTime endTime = startTime.plusMinutes(i * 10);

                hasAsyncFinished = false;
                final long start = System.currentTimeMillis();

                final int interval = i * 10;
                final int finalI = i;
                final int finalJ = j;
                provider.getLinkedConnectionsByDateForTimeSpan(startTime, endTime, new IRailSuccessResponseListener<LinkedConnections>() {
                    @Override
                    public void onSuccessResponse(@NonNull LinkedConnections data, Object tag) {
                        hasAsyncFinished = true;
                        long timeRequired = System.currentTimeMillis() - start;
                        duration[finalI - 1][finalJ] = timeRequired;
                        Log.i("Benchmark", timeRequired / 1000 + " seconds for an interval of " + interval);
                    }
                }, new IRailErrorResponseListener() {
                    @Override
                    public void onErrorResponse(@NonNull Exception e, Object tag) {
                        hasAsyncFinished = true;
                    }
                }, null);

                while (!hasAsyncFinished) {
                    try {
                        Thread.sleep(500);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }

        for (int i = 1; i <= 24 * 6; i++) {
            // take an average of each hour
            for (int j = 0; j < 24; j++) {
                System.out.print(duration[i-1][j] + ",");
            }
            System.out.println(" ");
        }

    }
}

