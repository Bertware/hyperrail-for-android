/*
 * This Source Code Form is subject to the terms of the Mozilla Public
 * License, v. 2.0. If a copy of the MPL was not distributed with this
 * file, You can obtain one at http://mozilla.org/MPL/2.0/.
 */

package be.hyperrail.android;

import android.os.Bundle;
import android.support.annotation.DrawableRes;
import android.support.annotation.NonNull;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.content.ContextCompat;
import android.support.v4.view.ViewPager;
import android.support.v7.app.AppCompatActivity;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

public class FirstLaunchGuide extends AppCompatActivity {

    /**
     * The {@link android.support.v4.view.PagerAdapter} that will provide
     * fragments for each of the sections. We use a
     * {@link FragmentPagerAdapter} derivative, which will keep every
     * loaded fragment in memory. If this becomes too memory intensive, it
     * may be best to switch to a
     */
    private SectionsPagerAdapter mSectionsPagerAdapter;

    /**
     * The {@link ViewPager} that will host the section contents.
     */
    private ViewPager mViewPager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_first_launch_guide);
        // Create the adapter that will return a fragment for each of the three
        // primary sections of the activity.
        mSectionsPagerAdapter = new SectionsPagerAdapter(getSupportFragmentManager());

        // Set up the ViewPager with the sections adapter.
        mViewPager = findViewById(R.id.container);
        mViewPager.setAdapter(mSectionsPagerAdapter);
    }


    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_first_launch_guide, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }

        return super.onOptionsItemSelected(item);
    }

    /**
     * A placeholder fragment containing a simple view.
     */
    public static class FirstLaunchFragment extends android.support.v4.app.Fragment {
        /**
         * The fragment argument representing the section number for this
         * fragment.
         */
        private static final String ARG_TITLE = "title";
        private static final String ARG_IMG = "image";
        private static final String ARG_DESCRIPTION = "description";

        public FirstLaunchFragment() {
        }

        /**
         * Returns a new instance of this fragment for the given section
         * number.
         */
        public static FirstLaunchFragment newInstance(String title, @DrawableRes int illustration, String description) {
            FirstLaunchFragment fragment = new FirstLaunchFragment();
            Bundle args = new Bundle();
            args.putString(ARG_TITLE, title);
            args.putInt(ARG_IMG, illustration);
            args.putString(ARG_DESCRIPTION, description);
            fragment.setArguments(args);
            return fragment;
        }

        @Override
        public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container,
                                 Bundle savedInstanceState) {
            View rootView = inflater.inflate(R.layout.fragment_first_launch_guide, container, false);

            if (getArguments() == null) {
                return rootView;
            }

            TextView titleView = rootView.findViewById(R.id.text_title);
            titleView.setText(getArguments().getString(ARG_TITLE));

            if (getActivity() != null) {
                ImageView imageView = rootView.findViewById(R.id.image);
                imageView.setImageDrawable(ContextCompat.getDrawable(getActivity(), getArguments().getInt(ARG_IMG)));
            }

            TextView descriptionView = rootView.findViewById(R.id.text_description);
            descriptionView.setText(getArguments().getString(ARG_DESCRIPTION));

            return rootView;
        }
    }

    /**
     * A {@link FragmentPagerAdapter} that returns a fragment corresponding to
     * one of the sections/tabs/pages.
     */
    public class SectionsPagerAdapter extends FragmentPagerAdapter {

        public SectionsPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int position) {
            return FirstLaunchFragment.newInstance(getResources().getStringArray(R.array.firstlaunch_titles)[position], getResources().getIntArray(R.array.firstlaunch_images)[position], getResources().getStringArray(R.array.firstlaunch_descriptions)[position]);
        }

        @Override
        public int getCount() {
            return getResources().getStringArray(R.array.firstlaunch_titles).length;
        }

        @Override
        public CharSequence getPageTitle(int position) {
            return getResources().getStringArray(R.array.firstlaunch_titles)[position];
        }
    }
}
