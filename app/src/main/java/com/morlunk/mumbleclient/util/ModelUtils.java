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
