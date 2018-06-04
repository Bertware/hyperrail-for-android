package be.bertmarcelis.thesis.batterytesting;

import android.os.AsyncTask;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.view.View;

import java.util.concurrent.ThreadPoolExecutor;

import be.bertmarcelis.thesis.R;
import be.bertmarcelis.thesis.irail.contracts.StationNotResolvedException;

public class BatteryTestActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_battery_test);

        findViewById(R.id.btn_benchmark_liveboards).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                benchmarkLiveboards();
            }
        });

        findViewById(R.id.btn_benchmark_routes).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                benchmarkRoutes();
            }
        });

        findViewById(R.id.btn_benchmark_trein).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                benchmarkTrains();
            }
        });
    }

    private void benchmarkLiveboards() {
        findViewById(R.id.batter_progress).setVisibility(View.VISIBLE);
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);
                findViewById(R.id.batter_progress).setVisibility(View.GONE);
            }

            @Override
            protected Void doInBackground(Void... voids) {
                try {
                    new DeparturesArrivalsBenchmark().benchmark(BatteryTestActivity.this);
                } catch (StationNotResolvedException e) {
                    e.printStackTrace();
                }
                return null;
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void benchmarkRoutes() {
        findViewById(R.id.batter_progress).setVisibility(View.VISIBLE);
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);
                findViewById(R.id.batter_progress).setVisibility(View.GONE);
            }

            @Override
            protected Void doInBackground(Void... voids) {
                try {
                    new ConnectionsBenchmark().benchmark(BatteryTestActivity.this);
                } catch (StationNotResolvedException e) {
                    e.printStackTrace();
                }
                return null;
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

    private void benchmarkTrains() {
        findViewById(R.id.batter_progress).setVisibility(View.VISIBLE);
        new AsyncTask<Void, Void, Void>() {
            @Override
            protected void onPostExecute(Void aVoid) {
                super.onPostExecute(aVoid);
                findViewById(R.id.batter_progress).setVisibility(View.GONE);
            }

            @Override
            protected Void doInBackground(Void... voids) {
                new TrainIndexBenchmark().benchmark();
                return null;
            }
        }.executeOnExecutor(AsyncTask.THREAD_POOL_EXECUTOR);
    }

}
