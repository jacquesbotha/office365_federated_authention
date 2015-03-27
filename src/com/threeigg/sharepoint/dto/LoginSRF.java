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
package com.threeigg.sharepoint.dto;

@SuppressWarnings("unused")
public class LoginSRF {
	private int 	State=0;
	private int 	UserState=0;
	private String 	Login="";
	private int 	FederationGlobalVersion=-1;
	private String 	DomainName="";
	private String 	AuthURL="";
	private String 	SiteGroup="";
	private String 	NameSpaceType="";
	private String 	FederationBrandName="";
	private int 	AuthNForwardType=0;

	public String getAuthURL() {
		return AuthURL;
	}
}
