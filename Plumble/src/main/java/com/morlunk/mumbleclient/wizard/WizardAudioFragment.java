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

import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.Spinner;
import android.widget.SpinnerAdapter;
import android.widget.TextView;

import com.morlunk.mumbleclient.R;

/**
 * Created by andrew on 04/11/13.
 */
public class WizardAudioFragment extends Fragment {

    private Spinner mInputSpinner;
    private AudioInputSpinnerAdapter mSpinnerAdapter;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_wizard_audio, container, false);
        mSpinnerAdapter = new AudioInputSpinnerAdapter();
        mInputSpinner = (Spinner) view.findViewById(R.id.wizard_audio_input_spinner);
        mInputSpinner.setAdapter(mSpinnerAdapter);
        return view;
    }

    private class AudioInputSpinnerAdapter extends BaseAdapter implements SpinnerAdapter {

        @Override
        public int getCount() {
            return 3;
        }

        @Override
        public Object getItem(int position) {
            switch (position) {
                case 0:
                    return "Voice Activity";
                case 1:
                    return "Push to Talk";
                case 2:
                    return "Continuous";
            }
            return null;
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if(view == null) {
                LayoutInflater inflater = getActivity().getLayoutInflater();
                view = inflater.inflate(android.R.layout.simple_spinner_item, parent, false);
            }
            TextView textView = (TextView) view.findViewById(android.R.id.text1);
            textView.setText((CharSequence) getItem(position));
            return view;
        }

        @Override
        public View getDropDownView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if(view == null) {
                LayoutInflater inflater = getActivity().getLayoutInflater();
                view = inflater.inflate(android.R.layout.simple_spinner_dropdown_item, parent, false);
            }
            TextView textView = (TextView) view.findViewById(android.R.id.text1);
            textView.setText((CharSequence) getItem(position));
            return view;
        }
    }
}
