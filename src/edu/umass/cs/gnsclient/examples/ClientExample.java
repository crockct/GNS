/*
 *
 *  Copyright (c) 2016 University of Massachusetts
 *
 *  Licensed under the Apache License, Version 2.0 (the "License"); you
 *  may not use this file except in compliance with the License. You
 *  may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or
 *  implied. See the License for the specific language governing
 *  permissions and limitations under the License.
 *
 *  Initial developer(s): Westy
 *
 */
package edu.umass.cs.gnsclient.examples;

import edu.umass.cs.gnsclient.client.GuidEntry;
import edu.umass.cs.gnsclient.client.GNSClientCommands;
import edu.umass.cs.gnsclient.client.util.GuidUtils;
import edu.umass.cs.gnscommon.exceptions.client.ClientException;
import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Arrays;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * In this example we create an account to write and read back JSON Objects to the GNS.
 * <p>
 * Note: This example cheats during account guid creation in that it creates the account 
 * guid and then uses the known secret to verify the account instead of making the user 
 * verify the account manually deal with the private key.
 *
 *
 * @author westy
 */
public class ClientExample {

  private static String ACCOUNT_ALIAS = "admin@gns.name"; // REPLACE THIS WITH YOUR ACCOUNT ALIAS
  private static GNSClientCommands client;
  private static GuidEntry guid;

  public static void main(String[] args) throws IOException,
          InvalidKeySpecException, NoSuchAlgorithmException, ClientException,
          InvalidKeyException, SignatureException, Exception {

    // Create the client. Connects to a default reconfigurator as specified in gigapaxos.properties file.
    client = new GNSClientCommands();
    try {
      // Create an account guid if one doesn't already exists.
      // The true makes it verbosely print out what it is doing.
      // The password is for future use.
      // Note that lookupOrCreateAccountGuid "cheats" by bypassing the account verification
      // mechanisms.
      guid = GuidUtils.lookupOrCreateAccountGuid(client, ACCOUNT_ALIAS, "password", true);
    } catch (Exception e) {
      System.out.println("Exception during accountGuid creation: " + e);
      System.exit(1);
    }
    System.out.println("Client connected to GNS.");

    // Create a JSON Object to initialize our guid record
    JSONObject json = new JSONObject("{\"occupation\":\"busboy\","
            + "\"friends\":[\"Joe\",\"Sam\",\"Billy\"],"
            + "\"gibberish\":{\"meiny\":\"bloop\",\"einy\":\"floop\"},"
            + "\"location\":\"work\",\"name\":\"frank\"}");

    // Write out the JSON Object
    client.update(guid, json);
    System.out.println("Wrote JSONObject :" + json);

    // and read the entire object back in
    JSONObject result = client.read(guid);
    System.out.println("Read JSON: " + result.toString());

    // Change a field
    client.update(guid, new JSONObject("{\"occupation\":\"rocket scientist\"}"));
    System.out.println("Updated \"occupation\" to \"rocket scientist\"");

    // and read the entire object back in
    result = client.read(guid);
    System.out.println("Retrieved JSON from guid: " + result.toString());

    // Add a field
    client.update(guid, new JSONObject("{\"ip address\":\"127.0.0.1\"}"));
    System.out.println("Added field \"ip address\" with value \"127.0.0.1\"");

    // and read the entire object back in
    result = client.read(guid);
    System.out.println("Retrieved JSON from guid: " + result.toString());

    // Remove a field
    client.fieldRemove(guid.getGuid(), "gibberish", guid);
    System.out.println("Removed field \"gibberish\"");

    // and read the entire object back in
    result = client.read(guid);
    System.out.println("Retrieved JSON from guid: " + result.toString());

    // Add some more stuff to read back
    JSONObject newJson = new JSONObject();
    JSONObject subJson = new JSONObject();
    subJson.put("sally", "red");
    subJson.put("sammy", "green");
    JSONObject subsubJson = new JSONObject();
    subsubJson.put("right", "seven");
    subsubJson.put("left", "eight");
    subJson.put("sally", subsubJson);
    newJson.put("flapjack", subJson);
    client.update(guid, newJson);
    System.out.println("Added field \"flapjack\" with value " + newJson.getJSONObject("flapjack"));
    
    // Read a single field using dot notation
    String resultString = client.fieldRead(guid, "flapjack.sally.right");
    System.out.println("Retrieved field \"flapjack.sally.right\" from guid: " + resultString);
    
    // Read a single field at the top level
    resultString = client.fieldRead(guid, "flapjack");
    System.out.println("Retrieved field \"flapjack\" from guid: " + resultString);
    
    // Update a field using dot notation
    JSONArray newValue = new JSONArray(Arrays.asList("One", "Ready", "Frap"));
    client.fieldUpdate(guid, "flapjack.sammy", newValue);
    System.out.println("Changed value of \"flapjack.sammy\" field to " + newValue);
    
    // Read the same field using dot notation
    resultString = client.fieldRead(guid, "flapjack.sammy");
    System.out.println("Retrieved field \"flapjack.sammy\" from guid: " + resultString);
    
    // Read the entire object back in
    result = client.read(guid);
    System.out.println("Retrieved JSON from guid: " + result.toString());
    
    // Read two fields at a time
    resultString = client.fieldRead(guid, new ArrayList<String>(Arrays.asList("name", "occupation")));
    System.out.println("Retrieved field \"name\" and \"occupation\" fields from guid: " + resultString);

    System.exit(0);
  }
  
}
