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
import android.util.Xml;

import com.morlunk.mumbleclient.db.PublicServer;

import org.xmlpull.v1.XmlPullParser;
import org.xmlpull.v1.XmlPullParserException;

import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.ProtocolException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by andrew on 05/05/14.
 */
class PublicServerFetchTask extends AsyncTask<Void, Void, List<PublicServer>> {

    private static final String MUMBLE_PUBLIC_URL = "http://mumble.info/list2.cgi";

    @Override
    protected List<PublicServer> doInBackground(Void... params) {
        try {
            // Fetch XML from server
            URL url = new URL(MUMBLE_PUBLIC_URL);
            HttpURLConnection connection = (HttpURLConnection) url.openConnection();
            connection.setRequestMethod("GET");
            connection.addRequestProperty("version", com.morlunk.jumble.Constants.PROTOCOL_STRING);
            connection.connect();
            InputStream stream = connection.getInputStream();

            XmlPullParser parser = Xml.newPullParser();
            parser.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES, false);
            parser.setInput(stream, "UTF-8");
            parser.nextTag();

            List<PublicServer> serverList = new ArrayList<PublicServer>();

            parser.require(XmlPullParser.START_TAG, null, "servers");
            while(parser.next() != XmlPullParser.END_TAG) {
                if (parser.getEventType() != XmlPullParser.START_TAG) {
                    continue;
                }

                serverList.add(readEntry(parser));
            }
            parser.require(XmlPullParser.END_TAG, null, "servers");

            return serverList;
        } catch (XmlPullParserException e) {
            e.printStackTrace();
        } catch (ProtocolException e) {
            e.printStackTrace();
        } catch (MalformedURLException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    private PublicServer readEntry(XmlPullParser parser) throws XmlPullParserException, IOException {
        String name = parser.getAttributeValue(null, "name");
        String ca = parser.getAttributeValue(null, "ca");
        String continentCode = parser.getAttributeValue(null, "continent_code");
        String country = parser.getAttributeValue(null, "country");
        String countryCode = parser.getAttributeValue(null, "country_code");
        String ip = parser.getAttributeValue(null, "ip");
        String port = parser.getAttributeValue(null, "port");
        String region = parser.getAttributeValue(null, "region");
        String url = parser.getAttributeValue(null, "url");

        parser.nextTag();

        PublicServer server = new PublicServer(name, ca, continentCode, country, countryCode, ip, Integer.parseInt(port), region, url);

        return server;
    }
}
