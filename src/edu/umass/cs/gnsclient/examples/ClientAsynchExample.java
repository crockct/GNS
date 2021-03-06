/*
 *
 *  Copyright (c) 2015 University of Massachusetts
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

import edu.umass.cs.gigapaxos.interfaces.Request;
import edu.umass.cs.gnsclient.client.AbstractGNSClient;
import edu.umass.cs.gnsclient.client.GuidEntry;
import edu.umass.cs.gnsserver.gnsapp.packet.CommandPacket;
import edu.umass.cs.gnsserver.gnsapp.packet.CommandValueReturnPacket;
import edu.umass.cs.gnsclient.client.deprecated.CommandResult;
import edu.umass.cs.gnsclient.client.util.GuidUtils;
import edu.umass.cs.gnscommon.exceptions.client.ClientException;
import static edu.umass.cs.gnscommon.GNSCommandProtocol.FIELD;
import static edu.umass.cs.gnscommon.GNSCommandProtocol.GUID;
import static edu.umass.cs.gnscommon.GNSCommandProtocol.READER;
import static edu.umass.cs.gnscommon.GNSCommandProtocol.USER_JSON;
import static edu.umass.cs.gnscommon.GNSCommandProtocol.WRITER;
import edu.umass.cs.gnscommon.utils.ThreadUtils;
import static edu.umass.cs.gnsclient.client.CommandUtils.*;

import java.io.IOException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.util.Collections;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import edu.umass.cs.gnsclient.client.GNSClientCommands;
import edu.umass.cs.gnscommon.CommandType;
import edu.umass.cs.gnscommon.GNSResponseCode;

import org.json.JSONObject;

/**
 * In this example we demonstrate the asynchronous client.
 *
 * It sends read or update requests for one field in a guid.
 * If you supply the -write arg it updates otherwise reads.
 * You’ll want to run it once with the -write arg before running
 * it with read to actually put a value in the field.
 * It runs forever so hit CTRL-C to stop it.
 * <p>
 * Invoke it like this:
 * <p>
 * ./scripts/client/runClient edu.umass.cs.gnsclient.examples.ClientAsynchExample -write
 * <p>
 * It prints out the latency seen by the client.
 *
 * @author westy
 */
public class ClientAsynchExample {

  private static final String ACCOUNT_ALIAS = "gnstest@cs.umass.edu"; // REPLACE THIS WITH YOUR ACCOUNT ALIAS

  public static void main(String[] args) throws IOException,
          InvalidKeySpecException, NoSuchAlgorithmException, ClientException,
          InvalidKeyException, SignatureException, Exception {

    // Create the client
    GNSClientCommands client = new GNSClientCommands(null);
    GuidEntry accountGuidEntry = null;
    try {
      // Create a guid (which is also an account guid)
      accountGuidEntry = GuidUtils.lookupOrCreateAccountGuid(client, ACCOUNT_ALIAS, "password", true);
    } catch (Exception e) {
      System.out.println("Exception during accountGuid creation: " + e);
      e.printStackTrace();
      System.exit(1);
    }
    System.out.println("Client connected to GNS");

    JSONObject command;
    if (args.length > 0 && args[0].equals("-write")) {
      JSONObject json = new JSONObject("{\"occupation\":\"busboy\","
              + "\"friends\":[\"Joe\",\"Sam\",\"Billy\"],"
              + "\"gibberish\":{\"meiny\":\"bloop\",\"einy\":\"floop\"},"
              + "\"location\":\"work\",\"name\":\"frank\"}");
      command = createAndSignCommand(CommandType.ReplaceUserJSON,
              accountGuidEntry.getPrivateKey(),
              GUID, accountGuidEntry.getGuid(), USER_JSON, json.toString(),
              WRITER, accountGuidEntry.getGuid());
    } else {
      command = createAndSignCommand(CommandType.Read,
              accountGuidEntry.getPrivateKey(),
              GUID, accountGuidEntry.getGuid(), FIELD, "occupation",
              READER, accountGuidEntry.getGuid());
    }
    // Create the command packet with a bogus id
    CommandPacket commandPacket = new CommandPacket(-1, command);
    // Keep track of what we've sent for the other thread to look at.
    Set<Long> pendingIds = Collections.newSetFromMap(new ConcurrentHashMap<Long, Boolean>());
    // Create and run another thread to pick up the responses
    Runnable companion = () -> {
      lookForResponses(client, pendingIds);
    };
    new Thread(companion).start();
    while (true) {
      long id = client.generateNextRequestID();
      // Important to set the new request id each time
      commandPacket.setClientRequestId(id);
      // Record what we're sending
      pendingIds.add(id);
      // Actually send out the packet
      client.sendCommandPacketAsynch(commandPacket);
      ThreadUtils.sleep(100); // if you generate them too fast you'll clog things up 
    }
  }

  // Not saying this is the best way to handle responses, but it works for this example.
  private static void lookForResponses(AbstractGNSClient client, Set<Long> pendingIds) {
    while (true) {
      ThreadUtils.sleep(10);
      // Loop through all the ones we've sent
      for (Long id : pendingIds) {
        if (client.isAsynchResponseReceived(id)) {
          pendingIds.remove(id);
          Request removed = client.removeAsynchResponse(id);
          if(removed instanceof CommandValueReturnPacket) {
        	  CommandValueReturnPacket commandResult = ((CommandValueReturnPacket)removed);
          System.out.println("commandResult for  " + id + " is "
                  + (commandResult.getErrorCode().equals(GNSResponseCode.NO_ERROR)
                  ? commandResult.getReturnValue()
                  : commandResult.getErrorCode().toString())
//                  + "\n"
//                  + "Latency is " + commandResult.getClientLatency()
          );
          }
        }
      }
    }
  }

}
