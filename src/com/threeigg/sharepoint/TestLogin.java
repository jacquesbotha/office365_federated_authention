/**
*   Copyright 2015 Jacques Botha
*   Licensed under the Apache License, Version 2.0 (the "License");
*   you may not use this file except in compliance with the License.
*   You may obtain a copy of the License at
*
*       http://www.apache.org/licenses/LICENSE-2.0
*
*   Unless required by applicable law or agreed to in writing, software
*   distributed under the License is distributed on an "AS IS" BASIS,
*   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
*   See the License for the specific language governing permissions and
*   limitations under the License.
*/

package com.threeigg.sharepoint;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.MalformedURLException;
import java.net.URL;

public class TestLogin {

	public static String fetchJson(String urlString, String cookie) {
		URL url = null;
		try {
			url = new URL(urlString);
		} catch (MalformedURLException e) {
			return "{'error':'Invalid URL'}";
		}
		HttpURLConnection conn;
		String content = "";
		try {
			conn = (HttpURLConnection) url.openConnection();
			conn.setConnectTimeout(10000);
			if ((cookie == null) || cookie.isEmpty() ) {
			} else {
				conn.setRequestProperty("Cookie", cookie);
			}
			conn.setRequestProperty("Accept", "application/json");
			conn.connect();
			if ( conn.getResponseCode() == 403 ) {
				return "{\"error\":\"403 Forbidden\"}";
			}
			
			if ( conn.getResponseCode() == 200 ) {
				BufferedReader in = new BufferedReader(new InputStreamReader(conn.getInputStream()));
				String inputLine;
				while ((inputLine = in.readLine()) != null)
					content += inputLine;
				in.close();
			} else {
				return "{\"error\":\"" + conn.getResponseCode() + "\"}";
			}
			conn.disconnect();
		} catch (Exception e) {
			//e.printStackTrace();
			return "{\"error\":\"Error retrieving content from server\"}";
		}
		return content;
	}
	
	public static void main(String[] args) {
		Authenticator authenticator = new Authenticator("https://<companysite>.sharepoint.com/_forms/default.aspx");
		authenticator.setDebug(false);
		authenticator.setWindowsIntegratedAuthentication(false);
		if ( !authenticator.authenticate("firstname.lastname@company.com", "<password>")) {
			System.out.println(authenticator.getError());
		} else {
			System.out.println(authenticator.getCookiesAsString());
			System.out.println( 
					fetchJson("https://<companysite>.sharepoint.com/sites/pwa/_api/ProjectData/Timesheets?$metadata&$format=json", 
							authenticator.getCookiesAsString()
					)
			);
		}
	}

}
