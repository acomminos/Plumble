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

package com.morlunk.mumbleclient.channel;

import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.RemoteException;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import com.morlunk.jumble.IJumbleService;
import com.morlunk.jumble.IJumbleSession;
import com.morlunk.jumble.JumbleService;
import com.morlunk.jumble.net.JumbleConnection;
import com.morlunk.jumble.net.JumbleUDPMessageType;
import com.morlunk.mumbleclient.R;
import com.morlunk.mumbleclient.util.JumbleServiceFragment;

import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * A fragment that displays known information from the remote server.
 * Created by andrew on 28/08/13.
 */
public class ServerInfoFragment extends JumbleServiceFragment {

    private static final int POLL_RATE = 1000;

    private ScheduledExecutorService mExecutorService = Executors.newSingleThreadScheduledExecutor();
    private Handler mHandler = new Handler(Looper.getMainLooper());
    private ScheduledFuture<?> mPollFuture;

    private TextView mProtocolView;
    private TextView mOSVersionView;
    private TextView mTCPLatencyView;
    private TextView mUDPLatencyView;
    private TextView mHostView;
    private TextView mCodecView;
    private TextView mMaxBandwidthView;
    private TextView mCurrentBandwidthView;

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_server_info, container, false);
        mProtocolView = (TextView) view.findViewById(R.id.server_info_protocol);
        mOSVersionView = (TextView) view.findViewById(R.id.server_info_os_version);
        mTCPLatencyView = (TextView) view.findViewById(R.id.server_info_tcp_latency);
        mUDPLatencyView = (TextView) view.findViewById(R.id.server_info_udp_latency);
        mHostView = (TextView) view.findViewById(R.id.server_info_host);
        mMaxBandwidthView = (TextView) view.findViewById(R.id.server_info_max_bandwidth);
        mCurrentBandwidthView = (TextView) view.findViewById(R.id.server_info_current_bandwidth);
        mCodecView = (TextView) view.findViewById(R.id.server_info_codec);
        return view;
    }

    /**
     * Updates the info from the service.
     */
    public void updateData() throws RemoteException {
        if(getService() == null || !getService().isConnected())
            return;

        IJumbleSession session = getService().getSession();

        mProtocolView.setText(getString(R.string.server_info_protocol, session.getServerRelease()));
        mOSVersionView.setText(getString(R.string.server_info_version, session.getServerOSName(), session.getServerOSVersion()));
        mTCPLatencyView.setText(getString(R.string.server_info_latency, (float)session.getTCPLatency()*Math.pow(10, -3)));
        mUDPLatencyView.setText(getString(R.string.server_info_latency, (float)session.getUDPLatency()*Math.pow(10, -3)));
        mHostView.setText(getString(R.string.server_info_host, getService().getTargetServer().getHost(), getService().getTargetServer().getPort()));

        String codecName;
        JumbleUDPMessageType codecType = session.getCodec();
        switch (codecType) {
            case UDPVoiceOpus:
                codecName = "Opus";
                break;
            case UDPVoiceCELTBeta:
                codecName = "CELT 0.11.0";
                break;
            case UDPVoiceCELTAlpha:
                codecName = "CELT 0.7.0";
                break;
            case UDPVoiceSpeex:
                codecName = "Speex";
                break;
            default:
                codecName = "???";
        }

        mMaxBandwidthView.setText(getString(R.string.server_info_max_bandwidth, (float)session.getMaxBandwidth()/1000f));
        mCurrentBandwidthView.setText(getString(R.string.server_info_current_bandwidth, (float)session.getCurrentBandwidth()/1000f));
        mCodecView.setText(getString(R.string.server_info_codec, codecName));
    }

    @Override
    public void onServiceBound(IJumbleService service) {
        // wow this is ugly
        mPollFuture = mExecutorService.scheduleAtFixedRate(new Runnable() {
            @Override
            public void run() {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        try {
                            if(isVisible()) updateData();
                        } catch (RemoteException e) {
                            e.printStackTrace();
                        }
                    }
                });
            }
        }, 0, POLL_RATE, TimeUnit.MILLISECONDS);
    }

    @Override
    public void onServiceUnbound() {
        if (mPollFuture != null) {
            mPollFuture.cancel(true);
            mPollFuture = null;
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mExecutorService.shutdownNow();
    }
}
