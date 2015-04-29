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

import android.text.TextUtils;

import java.net.URI;

public class HtmlUtils {
    /**
     * Tries to get the link's hostname, returns `null` if not a valid URL.
     * @param link link to get the hostname of
     * @return String?
     */
    public static String getHostnameFromLink(String link) {
        // Cheap pre-check
        if (link.contains("://")) {
            try {
                URI maybeURI = URI.create(link);
                if (!TextUtils.isEmpty(maybeURI.getHost())) {
                    return maybeURI.getHost();
                }
            } catch (IllegalArgumentException e) {
                // not a valid URI
            }
        }
        return null;
    }
}
