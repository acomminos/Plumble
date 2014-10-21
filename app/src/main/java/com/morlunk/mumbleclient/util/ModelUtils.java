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

package com.morlunk.mumbleclient.util;

import android.os.RemoteException;

import com.morlunk.jumble.IJumbleService;
import com.morlunk.jumble.model.Channel;

import java.util.LinkedList;
import java.util.List;

/**
 * Tools for dealing with the recursive user-channel hierarchy.
 * Created by andrew on 18/10/14.
 */
public class ModelUtils {
    /**
     * Flattens the channel hierarchy, returning an array of channels in hierarchical order.
     * @param channel The root channel to flatten from.
     * @param service The service to fetch subchannel information from.
     * @return A list of channels.
     */
    public static List<Channel> getChannelList(Channel channel,
                                               IJumbleService service) throws RemoteException {
        LinkedList<Channel> channels = new LinkedList<Channel>();
        getChannelList(channel, service, channels);
        return channels;
    }

    private static void getChannelList(Channel channel,
                                       IJumbleService service,
                                       List<Channel> channels) throws RemoteException {
        channels.add(channel);
        for (int cid : channel.getSubchannels()) {
            Channel subc = service.getChannel(cid);
            if (subc != null) {
                getChannelList(subc, service, channels);
            }
        }
    }
}
