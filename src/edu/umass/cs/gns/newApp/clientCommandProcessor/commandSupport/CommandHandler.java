/*
 * Copyright (C) 2013
 * University of Massachusetts
 * All Rights Reserved 
 */
package edu.umass.cs.gns.newApp.clientCommandProcessor.commandSupport;

import edu.umass.cs.gns.newApp.clientCommandProcessor.commands.CommandModule;
import edu.umass.cs.gns.newApp.clientCommandProcessor.commands.GnsCommand;
import edu.umass.cs.gns.newApp.clientCommandProcessor.demultSupport.ClientRequestHandlerInterface;
import static edu.umass.cs.gns.newApp.clientCommandProcessor.commandSupport.GnsProtocolDefs.*;
import edu.umass.cs.gns.main.GNS;
import edu.umass.cs.gns.newApp.AppReconfigurableNodeOptions;
import edu.umass.cs.gns.newApp.NewApp;
import edu.umass.cs.gns.newApp.packet.CommandPacket;
import edu.umass.cs.gns.newApp.packet.CommandValueReturnPacket;
import edu.umass.cs.gns.util.CanonicalJSON;
import edu.umass.cs.gns.util.NetworkUtils;
import edu.umass.cs.nio.JSONNIOTransport;

import edu.umass.cs.utils.DelayProfiler;
import java.io.IOException;
import java.net.InetAddress;
import java.net.InetSocketAddress;

import org.json.JSONException;
import org.json.JSONObject;

import java.net.UnknownHostException;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.security.spec.InvalidKeySpecException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Handles sending and receiving of commands.
 *
 * @author westy
 */
public class CommandHandler {

  // handles command processing
  private static final CommandModule commandModule = new CommandModule();

  private static long commandCount = 0;

  /**
   * Handles command packets coming in from the client.
   *
   * @param incomingJSON
   * @param handler
   * @throws JSONException
   * @throws UnknownHostException
   */
  public static void handlePacketCommandRequest(JSONObject incomingJSON, ClientRequestHandlerInterface handler)
          throws JSONException, UnknownHostException {
    handlePacketCommandRequest(incomingJSON, handler, null);
  }

  private static final ExecutorService execPool = Executors.newFixedThreadPool(100);

  private static class WorkerTask implements Runnable {

    private final JSONObject jsonFormattedCommand;
    private final GnsCommand command;
    private final ClientRequestHandlerInterface handler;
    private final CommandPacket packet;
    private final NewApp app;
    private final long receiptTime;

    public WorkerTask(JSONObject jsonFormattedCommand, GnsCommand command, ClientRequestHandlerInterface handler, CommandPacket packet, NewApp app, long receiptTime) {
      this.jsonFormattedCommand = jsonFormattedCommand;
      this.command = command;
      this.handler = handler;
      this.packet = packet;
      this.app = app;
      this.receiptTime = receiptTime;
    }

    @Override
    public void run() {
      try {
        final Long execStart = System.currentTimeMillis(); // instrumentation
        CommandResponse returnValue = executeCommand(command, jsonFormattedCommand, handler);
        DelayProfiler.update("executeCommand", execStart);
        // the last arguments here in the call below are instrumentation that the client can use to determine LNS load
        CommandValueReturnPacket returnPacket = new CommandValueReturnPacket(packet.getClientRequestId(),
                packet.getLNSRequestId(),
                packet.getServiceName(), returnValue,
                handler.getReceivedRequests(), handler.getRequestsPerSecond(),
                System.currentTimeMillis() - receiptTime);

        if (app != null) {
          // call back the app directly
          try {
            if (handler.getParameters().isDebugMode()) {
              GNS.getLogger().info("HANDLING COMMAND REPLY : " + returnPacket.toString());
            }
            handleCommandReturnValuePacketForApp(returnPacket.toJSONObject(), app);
          } catch (IOException e) {
            GNS.getLogger().severe("Problem replying to command: " + e);
          }
        } else {
          if (handler.getParameters().isDebugMode()) {
            GNS.getLogger().info("SENDING VALUE BACK TO " + packet.getSenderAddress() + "/" + packet.getSenderPort() + ": " + returnPacket.toString());
          }
          handler.sendToAddress(returnPacket.toJSONObject(), packet.getSenderAddress(), packet.getSenderPort());
        }

      } catch (JSONException e) {
        GNS.getLogger().severe("Problem  executing command: " + e);
        e.printStackTrace();
      }
      DelayProfiler.update("handlePacketCommandRequest", receiptTime);
    }
  }

  private static void handlePacketCommandRequest(JSONObject incomingJSON, ClientRequestHandlerInterface handler,
          NewApp app)
          throws JSONException, UnknownHostException {
    final Long receiptTime = System.currentTimeMillis(); // instrumentation
    if (handler.getParameters().isDebugMode()) {
      GNS.getLogger().info("<<<<<<<<<<<<<<<<< COMMAND PACKET RECEIVED: " + incomingJSON);
    }
    final CommandPacket packet = new CommandPacket(incomingJSON);
    // FIXME: Don't do this every time. 
    // Set the host field. Used by the help command and email module. 
    commandModule.setHTTPHost(handler.getNodeAddress().getHostString() + ":8080");
    final JSONObject jsonFormattedCommand = packet.getCommand();
    // Adds a field to the command to allow us to process the authentication of the signature
    addMessageWithoutSignatureToCommand(jsonFormattedCommand, handler);
    final GnsCommand command = commandModule.lookupCommand(jsonFormattedCommand);
    DelayProfiler.update("commandPreProc", receiptTime);

    execPool.submit(new WorkerTask(jsonFormattedCommand, command, handler, packet, app, receiptTime));
//    try {
//      final Long execStart = System.currentTimeMillis(); // instrumentation
//      CommandResponse returnValue = executeCommand(command, jsonFormattedCommand, handler);
//      DelayProfiler.update("executeCommand", execStart);
//      // the last arguments here in the call below are instrumentation that the client can use to determine LNS load
//      CommandValueReturnPacket returnPacket = new CommandValueReturnPacket(packet.getClientRequestId(),
//              packet.getLNSRequestId(),
//              packet.getServiceName(), returnValue,
//              handler.getReceivedRequests(), handler.getRequestsPerSecond(),
//              System.currentTimeMillis() - receiptTime);
//
//      if (app != null) {
//        // call back the app directly
//        try {
//          if (handler.getParameters().isDebugMode()) {
//            GNS.getLogger().info("HANDLING COMMAND REPLY : " + returnPacket.toString());
//          }
//          handleCommandReturnValuePacketForApp(returnPacket.toJSONObject(), app);
//        } catch (IOException e) {
//          GNS.getLogger().severe("Problem replying to command: " + e);
//        }
//      } else {
//        if (handler.getParameters().isDebugMode()) {
//          GNS.getLogger().info("SENDING VALUE BACK TO " + packet.getSenderAddress() + "/" + packet.getSenderPort() + ": " + returnPacket.toString());
//        }
//        handler.sendToAddress(returnPacket.toJSONObject(), packet.getSenderAddress(), packet.getSenderPort());
//      }
//
//    } catch (JSONException e) {
//      GNS.getLogger().severe("Problem  executing command: " + e);
//      e.printStackTrace();
//    }
//    DelayProfiler.update("handlePacketCommandRequest", receiptTime);
  }

  // this little dance is because we need to remove the signature to get the message that was signed
  // alternatively we could have the client do it but that just means a longer message
  // OR we could put the signature outside the command in the packet, but some packets don't need a signature
  private static void addMessageWithoutSignatureToCommand(JSONObject command, ClientRequestHandlerInterface handler) throws JSONException {
    if (command.has(SIGNATURE)) {
      String signature = command.getString(SIGNATURE);
      command.remove(SIGNATURE);
      String commandSansSignature = CanonicalJSON.getCanonicalForm(command);
      //String commandSansSignature = JSONUtils.getCanonicalJSONString(command);
      if (handler.getParameters().isDebugMode()) {
        GNS.getLogger().fine("########CANONICAL JSON: " + commandSansSignature);
      }
      command.put(SIGNATURE, signature);
      command.put(SIGNATUREFULLMESSAGE, commandSansSignature);
    }
  }

  /**
   * Executes the given command with the parameters supplied in the JSONObject.
   *
   * @param command
   * @param json
   * @return
   */
  public static CommandResponse executeCommand(GnsCommand command, JSONObject json, ClientRequestHandlerInterface handler) {
    try {
      if (command != null) {
        //GNS.getLogger().info("Executing command: " + command.toString());
        GNS.getLogger().fine("Executing command: " + command.toString() + " with " + json);
        return command.execute(json, handler);
      } else {
        return new CommandResponse(BADRESPONSE + " " + OPERATIONNOTSUPPORTED + " - Don't understand " + json.toString());
      }
    } catch (JSONException e) {
      e.printStackTrace();
      return new CommandResponse(BADRESPONSE + " " + JSONPARSEERROR + " " + e + " while executing command.");
    } catch (NoSuchAlgorithmException e) {
      return new CommandResponse(BADRESPONSE + " " + QUERYPROCESSINGERROR + " " + e);
    } catch (InvalidKeySpecException e) {
      return new CommandResponse(BADRESPONSE + " " + QUERYPROCESSINGERROR + " " + e);
    } catch (SignatureException e) {
      return new CommandResponse(BADRESPONSE + " " + QUERYPROCESSINGERROR + " " + e);
    } catch (InvalidKeyException e) {
      return new CommandResponse(BADRESPONSE + " " + QUERYPROCESSINGERROR + " " + e);
    }
  }

  //
  // Code for handling commands at the app
  //
  static class CommandRequestInfo {

    private final String host;
    private final int port;
    // For debugging
    private final String command;
    private final String guid;

    public CommandRequestInfo(String host, int port, String command, String guid) {
      this.host = host;
      this.port = port;
      this.command = command;
      this.guid = guid;
    }

    public String getHost() {
      return host;
    }

    public int getPort() {
      return port;
    }

    public String getCommand() {
      return command;
    }

    public String getGuid() {
      return guid;
    }

  }

  private static final ConcurrentMap<Integer, CommandRequestInfo> outStandingQueries = new ConcurrentHashMap<>(10, 0.75f, 3);

  private static InetSocketAddress ccpAddress;

  static {
    try {
      ccpAddress = new InetSocketAddress(NetworkUtils.getLocalHostLANAddress().getHostAddress(), GNS.DEFAULT_CCP_TCP_PORT);
      GNS.getLogger().info("CCP Address is " + ccpAddress);
    } catch (UnknownHostException e) {
      GNS.getLogger().severe("Unabled to determine CCP address: " + e + "; using loopback address");
      ccpAddress = new InetSocketAddress(InetAddress.getLoopbackAddress(), GNS.DEFAULT_CCP_TCP_PORT);
    }
  }

  public static void handleCommandPacketForApp(JSONObject json, NewApp app) throws JSONException, IOException {
    CommandPacket packet = new CommandPacket(json);
    // Squirrel away the host and port so we know where to send the command return value
    // A little unnecessary hair for debugging... also peek inside the command.
    JSONObject command;
    String commandString = null;
    String guid = null;
    if ((command = packet.getCommand()) != null) {
      commandString = command.optString(COMMANDNAME, null);
      guid = command.optString(GUID, command.optString(NAME, null));
    }
    outStandingQueries.put(packet.getClientRequestId(),
            new CommandRequestInfo(packet.getSenderAddress(), packet.getSenderPort(),
                    commandString, guid));
    // Send it to the client command handler
    if (app.getClientCommandProcessor() != null) { // new version with CPP running as part of app
      handlePacketCommandRequest(json, app.getClientCommandProcessor().getRequestHandler(), app);
    } else { // old code that will not be executed and will be going away soon
      // remove these so the stamper will put new ones in so the packet will find it's way back here
      json.remove(JSONNIOTransport.DEFAULT_IP_FIELD);
      json.remove(JSONNIOTransport.DEFAULT_PORT_FIELD);
      app.getNioServer().sendToAddress(ccpAddress, json);
    }
  }

  public static void handleCommandReturnValuePacketForApp(JSONObject json, NewApp app) throws JSONException, IOException {
    CommandValueReturnPacket returnPacket = new CommandValueReturnPacket(json);
    int id = returnPacket.getClientRequestId();
    CommandRequestInfo sentInfo;
    if ((sentInfo = outStandingQueries.get(id)) != null) {
      outStandingQueries.remove(id);
      if (AppReconfigurableNodeOptions.debuggingEnabled) {
        GNS.getLogger().info("&&&&&&& For " + sentInfo.getCommand() + " | " + sentInfo.getGuid() + " APP IS SENDING VALUE BACK TO "
                + sentInfo.getHost() + "/" + sentInfo.getPort() + ": " + returnPacket.toString());
      }
      app.getNioServer().sendToAddress(new InetSocketAddress(sentInfo.getHost(), sentInfo.getPort()),
              json);
      if (commandCount++ % 500 == 0) {
        System.out.println("8888888888888888888888888888>>>> " + DelayProfiler.getStats());
      }
    } else {
      GNS.getLogger().severe("Command packet info not found for " + id + ": " + json);
    }
  }
}
