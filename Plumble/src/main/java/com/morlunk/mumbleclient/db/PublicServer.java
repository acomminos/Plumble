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

package com.morlunk.mumbleclient.db;


import com.morlunk.jumble.model.Server;

public class PublicServer extends Server {

	private String mCA;
	private String mContinentCode;
	private String mCountry;
	private String mCountryCode;
	private String mRegion;
	private String mUrl;
	
	public PublicServer(String name, String ca, String continentCode, String country, String countryCode, String ip, Integer port, String region, String url) {
        super(-1, name, ip, port, "", "");
		mCA = ca;
		mContinentCode = continentCode;
		mCountry = country;
		mCountryCode = countryCode;
		mRegion = region;
		mUrl = url;
	}
	
	public String getCA() {
		return mCA;
	}

	public String getContinentCode() {
		return mContinentCode;
	}

	public String getCountry() {
		return mCountry;
	}

	public String getCountryCode() {
		return mCountryCode;
	}

	public String getRegion() {
		return mRegion;
	}

	public String getUrl() {
		return mUrl;
	}
}
