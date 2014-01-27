/*
 * Copyright (C) 2014 Andrew Comminos
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.morlunk.mumbleclient.wizard;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentPagerAdapter;
import android.support.v4.view.ViewPager;
import android.support.v7.app.ActionBarActivity;
import android.view.View;
import android.widget.Button;

import com.morlunk.mumbleclient.R;
import com.morlunk.mumbleclient.Settings;

/**
 * A simple activity_wizard providing an easy to use interface for configuring useful settings.
 * Created by andrew on 01/11/13.
 */
public class WizardActivity extends ActionBarActivity implements WizardNavigation {
    private Settings mSettings;
    private ViewPager mViewPager;
    private WizardPagerAdapter mPagerAdapter;
    private ViewPager.OnPageChangeListener mPageListener = new ViewPager.OnPageChangeListener() {
        @Override
        public void onPageScrolled(int position, float positionOffset, int positionOffsetPixels) {

        }

        @Override
        public void onPageScrollStateChanged(int state) {

        }

        @Override
        public void onPageSelected(int position) {
            updateNavigationButtons(position);
            setTitle(mPagerAdapter.getPageTitle(position));
        }
    };
    private Button mBackButton;
    private Button mNextButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_wizard);

        getSupportActionBar().setDisplayShowHomeEnabled(false);

        mSettings = Settings.getInstance(this);
        mViewPager = (ViewPager) findViewById(R.id.wizard_pager);
        mPagerAdapter = new WizardPagerAdapter(getSupportFragmentManager());
        mViewPager.setAdapter(mPagerAdapter);
        mViewPager.setOnPageChangeListener(mPageListener);
        mBackButton = (Button) findViewById(R.id.wizard_back_button);
        mNextButton = (Button) findViewById(R.id.wizard_next_button);

        mBackButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                back();
            }
        });
        mNextButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                next();
            }
        });

        updateNavigationButtons(0);
        setTitle(mPagerAdapter.getPageTitle(0));
    }

    @Override
    public void onBackPressed() {
        back();
    }

    private void updateNavigationButtons(int pagerPosition) {
        mBackButton.setText(pagerPosition == 0 ? R.string.wizard_cancel : R.string.wizard_back);
        mNextButton.setText(pagerPosition == mPagerAdapter.getCount() - 1 ? R.string.wizard_finish : R.string.wizard_next);
    }

    private void showConfirmQuitDialog() {
        new AlertDialog.Builder(this)
                .setMessage(R.string.wizard_confirm_close)
                .setPositiveButton(R.string.confirm, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mSettings.setFirstRun(false); // Mark in settings never to run this wizard again
                        finish();
                    }
                }).setNegativeButton(android.R.string.cancel, null)
                .show();
    }

    @Override
    public void next() {
        if (mViewPager.getCurrentItem() == (mPagerAdapter.getCount() - 1)) {
            mSettings.setFirstRun(false); // Mark in settings never to run this wizard again
            finish();
        } else
            mViewPager.setCurrentItem(mViewPager.getCurrentItem() + 1, true);
    }

    @Override
    public void back() {
        if(mViewPager.getCurrentItem() == 0)
            showConfirmQuitDialog(); // If cancel is pressed at the beginning of the pager, prompt the user to quit.
        else
            mViewPager.setCurrentItem(mViewPager.getCurrentItem()-1, true);
    }

    private class WizardPagerAdapter extends FragmentPagerAdapter {

        private final Class<? extends Fragment> WIZARD_FRAGMENTS[] = new Class[] {
                WizardWelcomeFragment.class,
                WizardCertificateFragment.class,
                WizardAudioFragment.class,
                WizardCertificateFragment.class
        };

        public WizardPagerAdapter(FragmentManager fm) {
            super(fm);
        }

        @Override
        public Fragment getItem(int i) {
            return Fragment.instantiate(WizardActivity.this, WIZARD_FRAGMENTS[i].getName());
        }

        @Override
        public CharSequence getPageTitle(int position) {
            switch (position) {
                case 0:
                    return getString(R.string.wizard_welcome_title);
                case 1:
                    return getString(R.string.wizard_certificate_title);
                case 2:
                    return getString(R.string.wizard_audio_title);
                case 3:
                    return getString(R.string.wizard_general_title);
            }
            return null;
        }

        @Override
        public int getCount() {
            return WIZARD_FRAGMENTS.length;
        }
    }
}
