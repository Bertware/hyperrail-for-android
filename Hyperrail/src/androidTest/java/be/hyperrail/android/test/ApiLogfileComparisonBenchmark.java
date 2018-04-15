package be.hyperrail.android.test;

import android.support.test.runner.AndroidJUnit4;

import org.junit.BeforeClass;
import org.junit.runner.RunWith;

import static android.support.test.InstrumentationRegistry.getInstrumentation;

/**
 * Compare APIs using data from iRail log files
 */

@RunWith(AndroidJUnit4.class)
public class ApiLogfileComparisonBenchmark {

    @BeforeClass
    public void setup(){
        getInstrumentation().getContext().getResources().openRawResource(be.hyperrail.android.test.R.raw.irailapi_20180326);
    }


    class LogEntry {

    }
}
