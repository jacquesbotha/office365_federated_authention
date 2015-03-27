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

# office365_federated_authention
Java Class for SharePoint (Office 365) Authentication using Federated Authentication


Usage:

```javascript
		
		Authenticator authenticator = new Authenticator("https://<company_site>.sharepoint.com/_forms/default.aspx");
		authenticator.setDebug(false);
		authenticator.setWindowsIntegratedAuthentication(false);
		if ( !authenticator.authenticate("<username>", "<password>")) {
			System.out.println(authenticator.getError());
		} else {
			System.out.println(authenticator.getCookiesAsString());
			System.out.println( 
					fetchJson("https://<company_site>.sharepoint.com/<sharepoint_site>/_api/Web/Lists/GetByTitle('<list name>')/items", 
							authenticator.getCookiesAsString()
					)
			);
		}
```