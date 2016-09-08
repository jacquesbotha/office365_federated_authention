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

import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HttpsURLConnection;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.threeigg.sharepoint.dto.LoginSRF;

// WAUTH Param types
// Can support: username/password, SSL & Integrated auth.
// We will force it to use username/password
// http://msdn.microsoft.com/en-gb/library/ee895365.aspx
//
public class Authenticator {
	private String site;
	private String userAgent = "Mozilla/5.0 (Windows NT 6.3; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/38.0.2125.111 Safari/537.36";

	private static final String PATTERN_STS_FORM = "<input (.*?) />";
	private static final String PATTERN_SAML_TOKEN = "<input (.*) />";
	private static final String PATTERN_MS_FORM = "<input (.*)>";
	
	private Boolean windowsIntegratedAuthentication = false;
	private String stsLocation;
	private ArrayList<String> cookieValue = new ArrayList<String>();
	private ArrayList<String> postValue = new ArrayList<String>();
	private int responseCode;
	private String responseHTML = "";
	private String responseLocation = "";
	private String error = "";
	private Boolean debug = false;
	private int readTimeout = 10000;
	private String host = "";
	private String path = "";
	
	public Authenticator(String site) {
		this.site = site;
	}
	
	private void doCallToLocation( String location, String method, Boolean followRedirects) {
		try {
			setResponseHTML("");
			if (getDebug()) {
				System.out.println("Location: " + location);
			}
			String inputLine;
			StringBuffer response = new StringBuffer();
			BufferedReader inputStream = null;
			URL url = new URL(location);
			setPath( url.getFile().substring(0, url.getFile().lastIndexOf('/') + 1) );
			setHost( url.getProtocol() + "://" + url.getHost() );
			
			HttpsURLConnection connection = (HttpsURLConnection) url.openConnection();
			connection.setReadTimeout(getReadTimeout());
			connection.setInstanceFollowRedirects(followRedirects);
			connection.setRequestMethod(method);
			connection.setRequestProperty("User-Agent", getUserAgent());
			connection.setRequestProperty("Accept",	"text/html,application/xhtml+xml,application/xml;q=0.9,image/webp,*/*;q=0.8");
			connection.setRequestProperty("Accept-Language", "en-US,en;q=0.8");
			connection.setRequestProperty("Connection", "keep-alive");
			if ( getCookie().size() > 0 ) {
				connection.setRequestProperty("Cookie", getCookiesAsString());
			}
			if (getDebug()) {
				System.out.println("Method:" + method);
			}
			
			// If method is POST then send POST values.
			if (method.equals("POST")) {
				connection.setDoOutput(true);
				DataOutputStream wr = new DataOutputStream(connection.getOutputStream());
				if (getDebug()) {
					System.out.println( "Sending POST: " + getPostValuesAsString() );
				}
				wr.writeBytes(getPostValuesAsString());
				wr.flush();
				wr.close();
			}
			setResponseCode(connection.getResponseCode());
			setResponseLocation(connection.getHeaderField("Location"));
			List<String> setCookie = connection.getHeaderFields().get("Set-Cookie");
			if (setCookie != null) {
				for (int i = 0; i < setCookie.size(); i++) {
					addCookie(setCookie.get(i));
				}
			}
			inputStream = new BufferedReader(new InputStreamReader(connection.getInputStream()));
			while ((inputLine = inputStream.readLine()) != null) {
				response.append(inputLine);
			}
			setResponseHTML(response.toString());
			
			if (getDebug()) {
				Map<String, List<String>> map = connection.getHeaderFields();
				for (Map.Entry<String, List<String>> entry : map.entrySet()) {
					System.out.println(entry.getKey() + ": " + entry.getValue());
				}
			}
			inputStream.close();
		} catch (Exception e) {
			setError(e.getMessage());
		}
	}
	
	private void doPostToLocation(String location, Boolean followRedirects) {
		doCallToLocation(location, "POST", followRedirects);
		outputResponse();
	}
	
	private void doGetToLocation(String location, Boolean followRedirects) {
		doCallToLocation(location, "GET", followRedirects);
		outputResponse();
	}
	
	
	private void getUserRealm(String username) {
		try {
			username = URLEncoder.encode(username, "UTF-8");
		} catch (UnsupportedEncodingException e) {
		}
		doGetToLocation("https://login.microsoftonline.com/GetUserRealm.srf?login=" + username + "&handler=1&extended=1", true);
		
		if (!getError().equals("")) { return; }
		
		Gson gson = new GsonBuilder().create();
		LoginSRF loginSRF = gson.fromJson(getResponseHTML(), LoginSRF.class);
		String tmpStsLocation = loginSRF.getAuthURL().replace("&username=",  "&username=" + username);
		tmpStsLocation += "&wa=wsignin1.0&wtrealm=urn:federation:MicrosoftOnline";
		try {
			tmpStsLocation += "&wctx=" + URLEncoder.encode("wa=wsignin1.0&wreply=" + URLEncoder.encode(getSite(), "UTF-8") + "&LoginOptions=3", "UTF-8");
		} catch (UnsupportedEncodingException e) {
		}
		
		if (getWindowsIntegratedAuthentication()) {
			tmpStsLocation += "urn:federation:authentication:windows";
		} else {
			// Force username/password form
			tmpStsLocation += "&wauth=urn:oasis:names:tc:SAML:1.0:am:password";
		}
		setStsLocation(tmpStsLocation);
	}


	private void getViewStates() {
		if (getDebug()) { System.out.println("Get view states of STS page:\n============================"); }
		doGetToLocation(stsLocation, true);
	}

	private void postUsernamePassword(String username, String password) {
		if (getDebug()) { System.out.println("Post username/password:\n======================="); }
		parsePostValues(PATTERN_STS_FORM);
		try {
			//addPostValue("ctl00%24ContentPlaceHolder1%24UsernameTextBox=" + URLEncoder.encode(username, "UTF-8") );
			addPostValue("ctl00%24ContentPlaceHolder1%24PasswordTextBox=" + URLEncoder.encode(password, "UTF-8") );
		} catch (UnsupportedEncodingException e) {
		}
		doPostToLocation(stsLocation, false);
	}

	private void getSAMLRequst() {
		// Get SAML Request from Company STS Server
		if (getDebug()) { System.out.println("Get Generated SAML Request:\n============================"); }
		doGetToLocation(getFullResponseLocation(), false);
	}

	private void postSAMLRequest(String location) {
		if (getDebug()) { System.out.println("POST SAML Request:\n===================="); }
		// Post to MS Login Server
		clearPostValues();
		parsePostValues(PATTERN_SAML_TOKEN);
		
		if (getDebug()) {
			System.out.println("SAML Request:");
			System.out.println(getPostValuesAsString());
		}
		clearCookies();
		doPostToLocation(location, false);
	}
	
	private void postSAMLResponse(String location) {
		if (getDebug()) { System.out.println("POST SAML Response to MS Login Server\n===================================="); }
		// Post to MS Server (SharePoint)
		clearPostValues();
		parsePostValues(PATTERN_MS_FORM);
		clearCookies();
		doPostToLocation(location, false);

		if (getDebug()) { 
			System.out.println("Auth Cookies:");
			System.out.println(getCookiesAsString());
		}

	}
	
	private void outputResponse() {
		if (getDebug()) {
			System.out.println("Response Code: " + getResponseCode());
			System.out.println("Location: " + getResponseLocation());
			for (int i = 0; i < getCookie().size(); i++) {
				System.out.println("Response Cookie: " + getCookie().get(i));
			}
			System.out.println("URL Content:\n" + getResponseHTML());
		}
	}


	public Boolean authenticate(String username, String password) {
		
		if (getStsLocation().equals("")) {
			getUserRealm(username);
			if (!getError().equals("")) { return false; }
		}

		// At this stage - you might get a login page if you outside the network, or the SAML request token in the form if you internal...
		getViewStates();
		
		if (!getError().equals("")) { return false; }

		if (getResponseHTML().indexOf("RequestSecurityTokenResponse") == -1) {
			// If you calling from a non NTML PC etc, then you should post your username/password to the STS Login Page
			
			postUsernamePassword(username, password);
			if (!getError().equals("")) { return false; }

			// Valid response should be a 302 & "MSISAuthenticated=" cookie should be set
			if ((getResponseCode() != 302) || (getCookiesAsString().indexOf("MSISAuthenticated=") == -1)) {
				setError(findErrorInSTSForm());
				return false;
			}
			
			// Get SAML Request from STS Server
			// The passed cookies will generate the SAML Request - contents will be in the Form.
			getSAMLRequst();
			if (!getError().equals("")) { return false; }

		}
		
		if (getResponseHTML().indexOf("RequestSecurityTokenResponse") != -1) {
			// Post SAML Request to Microsoft Login
			postSAMLRequest(findFormAction());
		} else {
			setError("Error: RequestSecurityTokenResponse not found!");
		}
		if (!getError().equals("")) { return false; }
		
		if (getResponseHTML().indexOf("name=\"t\"") != -1 ) {
			// Post SAML Response to STS MS handler
			postSAMLResponse(findFormAction());
		} else {
			setError("Error: SAML response token not found!");
		}
		if (!getError().equals("")) { return false; }
			
		if ((getResponseCode() != 302) || (getCookiesAsString().indexOf("FedAuth") == -1) || (getCookiesAsString().indexOf("rtFa") == -1)) {
			setError("Error: No valid FedAuth set.");
			return false;
		}
		
		// You should now have the Authentication cookies...
		// FedAuth & rtFa
		return true;
	}
	
	/** Getters and setter **/
	public Boolean getWindowsIntegratedAuthentication() {
		return windowsIntegratedAuthentication;
	}

	public void setWindowsIntegratedAuthentication(
			Boolean windowsIntegratedAuthentication) {
		this.windowsIntegratedAuthentication = windowsIntegratedAuthentication;
	}

	
	private String getSite() {
		return site;
	}
	
	private String getStsLocation() {
		if ( stsLocation == null ) { 
			return "";
		} else { 
			return stsLocation;
		}
	}

	private void setStsLocation(String stsLocation) {
		this.stsLocation = stsLocation;
	}

	private String getHost() {
		return host;
	}

	private void setHost(String host) {
		this.host = host;
	}

	private String getPath() {
		return path;
	}

	private void setPath(String path) {
		this.path = path;
	}

	private int getReadTimeout() {
		return readTimeout;
	}
	
	public void setReadTimeout(int rt) {
		readTimeout = rt;
	}

	private Boolean getDebug() {
		return debug;
	}
	
	public void setDebug( Boolean d ) {
		debug = d;
	}
	
	private void setError(String errorString) {
		error = errorString;
	}
	
	public String getError() {
		return error;
	}

	private String getUserAgent() {
		return userAgent;
	}

	public ArrayList<String> getCookie() {
		return cookieValue;
	}

	private void clearCookies() {
		cookieValue.clear();
	}
	
	private void addCookie(String cookieToAdd) {
		cookieValue.add(cookieToAdd);
	}
	
	public String getCookiesAsString() {
		String cookieValue = "";
		if (getCookie() != null) {
			for (int i = 0; i < getCookie().size(); i++) {
				cookieValue += "; " + getCookie().get(i).substring(0, getCookie().get(i).indexOf(";"));
			}
		}
		if (cookieValue.length() > 0) { 
			cookieValue = cookieValue.substring(2);
		}
		return cookieValue;
	}

	private int getResponseCode() {
		return responseCode;
	}

	private String getResponseHTML() {
		if (responseHTML == null) {
			responseHTML = "";
		}
		return responseHTML;
	}
	
	private String getResponseLocation() {
		return responseLocation;
	}
	
	private String getFullResponseLocation() {
		if ((getResponseLocation().indexOf("https://") == 0) || (getResponseLocation().indexOf("httpss://") == 0)) {
			return getResponseLocation();
		}
		if (getResponseLocation().indexOf("/") == 0) {
			return getHost() + getResponseLocation();
		}
		return getHost() + getPath() + getResponseLocation();
	}

	private void setResponseLocation(String responseLocation) {
		this.responseLocation = responseLocation;
	}

	private void setResponseCode(int responseCode) {
		this.responseCode = responseCode;
	}

	private void setResponseHTML(String responseHTML) {
		this.responseHTML = responseHTML;
	}
	
	private String findErrorInSTSForm() {
		Pattern p1 = Pattern.compile("<span (.*?)ErrorTextLabel\">(.*?)</span>");
	    Matcher m1 = p1.matcher(getResponseHTML());
	    while(m1.find()) {
			return m1.group(2);
	    }
		return "";
	}

	private String findFormAction() {
		Pattern p1 = Pattern.compile("<form (.*?)action=\"(.*?)\"");
	    Matcher m1 = p1.matcher(getResponseHTML());
	    while(m1.find()) {
	    	return m1.group(2);
	    }
		return "";
	}
	
	private void parsePostValues(String pattern) {
		// TODO:
		// Test net Pattern
		Pattern p1 = Pattern.compile(pattern);
	    Matcher m1 = p1.matcher(getResponseHTML());
	    while(m1.find()) {
	    	Pattern p2 = Pattern.compile("name=\"([^\"]+)\"(.*?)value=\"([^\"]+)\"");
		    Matcher m2 = p2.matcher(m1.group(0));
		    while( m2.find() ) {
		    	String v = m2.group(3);
		    	v = v.replace("&lt;", "<");
		    	v = v.replace("&gt;", ">");
		    	v = v.replace("&quot;", "\"");
		    	v = v.replace("&amp;", "&");
		    	try {
					addPostValue(m2.group(1) + "=" + URLEncoder.encode( v, "UTF-8" ));
				} catch (UnsupportedEncodingException e) {
				}
		    }
	    }
	}
	
	private void clearPostValues(){
		postValue.clear();
	}
	
	private String getPostValuesAsString(){
		String r = "";
		for (int i = 0; i < postValue.size(); i++) {
			r += "&" + postValue.get(i);
		}
		if (r.length()>0) {
			r = r.substring(1);
		}
		return r;
	}
	private void addPostValue(String postValueToAdd) {
		postValue.add(postValueToAdd);
	}

}