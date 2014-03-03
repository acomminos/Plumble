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

package com.morlunk.mumbleclient.servers;

import com.morlunk.jumble.model.Server;

import java.nio.ByteBuffer;

/**
 * Response from server pings.
 * @see http://mumble.sourceforge.net/Protocol
 * @author morlunk
 */
public class ServerInfoResponse {

	private long mIdentifier;
	private int mVersion;
	private int mCurrentUsers;
	private int mMaximumUsers;
	private int mAllowedBandwidth;
	private int mLatency;
    private Server mServer;

	/**
	 * Whether or not this server info response represents a failure to retrieve a response. Used to efficiently denote failed responses.
	 */
	private boolean mDummy = false;
		
	/**
	 * Creates a ServerInfoResponse object with the bytes obtained from the server.
	 * @param response The response to the UDP pings sent by the server.
	 * @see http://mumble.sourceforge.net/Protocol
	 */
	public ServerInfoResponse(Server server, byte[] response, int latency) {
		ByteBuffer buffer = ByteBuffer.wrap(response);
		mVersion = buffer.getInt();
		mIdentifier = buffer.getLong();
		mCurrentUsers = buffer.getInt();
		mMaximumUsers = buffer.getInt();
		mAllowedBandwidth = buffer.getInt();
        mLatency = latency;
        mServer = server;
	}

	/**
	 * Instantiating a ServerInfoResponse with no data will cause it to be considered a 'dummy' response by its handler.
	 */
	public ServerInfoResponse() {
		this.mDummy = true;
	}
	
	public long getIdentifier() {
		return mIdentifier;
	}

	public int getVersion() {
		return mVersion;
	}
	
	public String getVersionString() {
		byte[] versionBytes = ByteBuffer.allocate(4).putInt(mVersion).array();
		return String.format("%d.%d.%d", (int)versionBytes[1], (int)versionBytes[2], (int)versionBytes[3]);
	}

	public int getCurrentUsers() {
		return mCurrentUsers;
	}

	public int getMaximumUsers() {
		return mMaximumUsers;
	}

	public int getAllowedBandwidth() {
		return mAllowedBandwidth;
	}

    public int getLatency() {
        return mLatency;
    }

    public Server getServer() {
        return mServer;
    }

    public boolean isDummy() {
		return mDummy;
	}
}
