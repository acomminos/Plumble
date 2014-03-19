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

import android.os.AsyncTask;
import android.util.Log;

import com.morlunk.jumble.model.Server;
import com.morlunk.mumbleclient.Constants;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.nio.ByteBuffer;

/**
 * Pings the requested server and returns a ServerInfoResponse.
 * Will return a 'dummy' ServerInfoResponse in the case of failure.
 * @author morlunk
 *
 */
public class ServerInfoTask extends AsyncTask<Server, Void, ServerInfoResponse> {

	private Server server;
	
	@Override
	protected ServerInfoResponse doInBackground(Server... params) {
		server = params[0];
		try {
			InetAddress host = InetAddress.getByName(server.getHost());
			
			// Create ping message
			ByteBuffer buffer = ByteBuffer.allocate(12);
			buffer.putInt(0); // Request type
			buffer.putLong(server.getId()); // Identifier
			DatagramPacket requestPacket = new DatagramPacket(buffer.array(), 12, host, server.getPort());
			
			// Send packet and wait for response
			DatagramSocket socket = new DatagramSocket();
			socket.setSoTimeout(1000);
			socket.setReceiveBufferSize(1024);

            long startTime = System.nanoTime();

            socket.send(requestPacket);
			
			byte[] responseBuffer = new byte[24];
			DatagramPacket responsePacket = new DatagramPacket(responseBuffer, responseBuffer.length);
			socket.receive(responsePacket);

            int latencyInMs = (int) ((System.nanoTime()-startTime)/1000000);
			
			ServerInfoResponse response = new ServerInfoResponse(server, responseBuffer, latencyInMs);
							
			Log.i(Constants.TAG, "DEBUG: Server version: "+response.getVersionString()+"\nUsers: "+response.getCurrentUsers()+"/"+response.getMaximumUsers());
			
			return response;
			
		} catch (Exception e) {
//			e.printStackTrace();
		}

		return new ServerInfoResponse(); // Return dummy in case of failure
	}
	
}