/*
 * Copyright (C) 2013
 * University of Massachusetts
 * All Rights Reserved 
 */
package edu.umass.cs.gns.clientsupport;

import edu.umass.cs.gns.localnameserver.LNSPacketDemultiplexer;
import edu.umass.cs.gns.localnameserver.LocalNameServer;
import edu.umass.cs.gns.main.GNS;
import edu.umass.cs.gns.nameserver.NameRecordKey;
import edu.umass.cs.gns.nameserver.ResultValue;
import edu.umass.cs.gns.nameserver.ValuesMap;
import edu.umass.cs.gns.packet.AddRecordPacket;
import edu.umass.cs.gns.packet.ConfirmUpdateLNSPacket;
import edu.umass.cs.gns.packet.DNSPacket;
import edu.umass.cs.gns.packet.NSResponseCode;
import edu.umass.cs.gns.packet.Packet;
import static edu.umass.cs.gns.packet.Packet.getPacketType;
import edu.umass.cs.gns.packet.RemoveRecordPacket;
import edu.umass.cs.gns.packet.Transport;
import edu.umass.cs.gns.packet.UpdateAddressPacket;
import edu.umass.cs.gns.util.ConfigFileInfo;
import java.util.Arrays;
import java.util.Date;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * One of a number of class that implement client support in the GNS server. 
 * 
 * The intercessor is the primary liason class between the servers (HTTP and new
 * TCP) and the Command Module which handles incoming requests from the servers
 * and the the Local Name Server.
 * It provides support for the AccountAccess, Field Access,
 * FieldMetaData, GroupAccess, and SelectHandler classes.
 * 
 * Provides basic methods for reading and writing fields in the GNS. Used 
 * by the various classes in the client package to implement writing of fields 
 * (for both user data and system data), meta data, groups and perform more
 * sophisticated queries (the select queries).
 * 
 * @author westy
 */
public class Intercessor {

  private static int localServerID = 0;
  /* Used by the wait/notify calls */
  private static final Object monitor = new Object();
  /* Used by update confirmation */
  private static final Object monitorUpdate = new Object();
  /**
   * We use a ValuesMap for return values even when returning a single value. This lets us use the same structure for single and
   * multiple value returns.
   */
  private static ConcurrentMap<Integer, QueryResult> queryResultMap;
  private static Random randomID;
  /* Used for sending updates and getting confirmations */
  public static Transport transport;
  private static ConcurrentMap<Integer, NSResponseCode> updateSuccessResult;
  // Instrumentation
  private static ConcurrentMap<Integer, Date> queryTimeStamp;

  public static int getLocalServerID() {
    return localServerID;
  }

  public static void setLocalServerID(int localServerID) {
    Intercessor.localServerID = localServerID;

    GNS.getLogger().info("Local server id: " + localServerID
            + " Address: " + ConfigFileInfo.getIPAddress(localServerID)
            + " LNS TCP Port: " + ConfigFileInfo.getLNSTcpPort(localServerID));
  }

  static {
    randomID = new Random();
    queryResultMap = new ConcurrentHashMap<Integer, QueryResult>(10, 0.75f, 3);
    queryTimeStamp = new ConcurrentHashMap<Integer, Date>(10, 0.75f, 3);
    updateSuccessResult = new ConcurrentHashMap<Integer, NSResponseCode>(10, 0.75f, 3);
  }

  /**
   * This is invoked to receive packets. It updates the appropriate map
   * for the id and notifies the  appropriate monitor to wake the 
   * original caller. 
   * 
   * @param json 
   */
  public static void handleIncomingPackets(JSONObject json) {
    try {
      switch (getPacketType(json)) {
        case CONFIRM_UPDATE_LNS:
        case CONFIRM_ADD_LNS:
        case CONFIRM_REMOVE_LNS:
          ConfirmUpdateLNSPacket packet = new ConfirmUpdateLNSPacket(json);
          int id = packet.getRequestID();
          //Packet is a response and does not have a response error
          GNS.getLogger().fine((packet.isSuccess() ? "Successful" : "Error") + " Update (" + id + ") ");// + packet.getName() + "/" + packet.getRecordKey().getName());
          synchronized (monitorUpdate) {
            updateSuccessResult.put(id, packet.getResponseCode());
            monitorUpdate.notifyAll();
          }
          break;
        case DNS:
          DNSPacket dnsResponsePacket = new DNSPacket(json);
          id = dnsResponsePacket.getQueryId();
          if (dnsResponsePacket.isResponse() && !dnsResponsePacket.containsAnyError()) {
            //Packet is a response and does not have a response error
            GNS.getLogger().fine("Query (" + id + "): "
                    + dnsResponsePacket.getGuid() + "/" + dnsResponsePacket.getKey()
                    + " Successful Received");//  + nameRecordPacket.toJSONObject().toString());
            synchronized (monitor) {
              queryResultMap.put(id, new QueryResult(dnsResponsePacket.getRecordValue()));
              monitor.notifyAll();
            }
          } else {
            GNS.getLogger().fine("Intercessor: Query (" + id + "): "
                    + dnsResponsePacket.getGuid() + "/" + dnsResponsePacket.getKey()
                    + " Error Received: " + dnsResponsePacket.getHeader().getResponseCode().name());// + nameRecordPacket.toJSONObject().toString());
            synchronized (monitor) {
              queryResultMap.put(id, new QueryResult(dnsResponsePacket.getHeader().getResponseCode()));
              monitor.notifyAll();
            }
          }
          break;
        case SELECT_RESPONSE:
          SelectHandler.processSelectResponsePackets(json);
      }
    } catch (JSONException e) {
      GNS.getLogger().severe("JSON error: " + e);
    }
  }

  /**
   * This one performs signature and acl checks at the NS unless you set reader (and sig, message) to null).
   */
  public static QueryResult sendQuery(String name, String key, String reader, String signature, String message) {
    GNS.getLogger().fine("Sending query: " + name + " " + key);
    int id = nextQueryRequestID();


    DNSPacket queryrecord = new DNSPacket(id, name, new NameRecordKey(key), LocalNameServer.nodeID, reader, signature, message);
    JSONObject json;
    try {
      json = queryrecord.toJSONObjectQuestion();
      queryTimeStamp.put(id, new Date()); // rtt instrumentation
      injectPacketIntoLNSQueue(json);

    } catch (JSONException e) {
      e.printStackTrace();
      return null;
    }

    // now we wait until the correct packet comes back
    try {
      GNS.getLogger().finer("Waiting for query id: " + id);
      synchronized (monitor) {
        while (!queryResultMap.containsKey(id)) {
          monitor.wait();
        }
      }
      GNS.getLogger().fine("Query id response received: " + id);
    } catch (InterruptedException x) {
      GNS.getLogger().severe("Wait for return packet was interrupted " + x);

    }
    Date receiptTime = new Date(); // instrumentation
    QueryResult result = queryResultMap.get(id);
    queryResultMap.remove(id);
    Date sentTime = queryTimeStamp.get(id); // instrumentation
    queryTimeStamp.remove(id); // instrumentation
    long rtt = receiptTime.getTime() - sentTime.getTime();
    GNS.getLogger().fine("Query (" + id + ") RTT = " + rtt + "ms");
    GNS.getLogger().finer("Query (" + id + "): " + name + "/" + key + "\n  Returning: " + result.toString());
    result.setRoundTripTime(rtt);
    return result;
  }

  /**
   * This version bypasses any signature checks and is meant for "system" use.
   */
  public static QueryResult sendQueryBypassingAuthentication(String name, String key) {
    return sendQuery(name, key, null, null, null);
  }

  /**
   * Sends an AddRecord packet to the Local Name Server with an initial value.
   * 
   * @param name
   * @param key
   * @param value
   * @return 
   */
  public static NSResponseCode sendAddRecord(String name, String key, ResultValue value) {
    int id = nextUpdateRequestID();
    GNS.getLogger().finer("Sending add: " + name + "->" + value);
    AddRecordPacket pkt = new AddRecordPacket(id, name, new NameRecordKey(key), value, localServerID, GNS.DEFAULT_TTL_SECONDS);
    try {
      JSONObject json = pkt.toJSONObject();
      injectPacketIntoLNSQueue(json);

    } catch (JSONException e) {
      e.printStackTrace();
    }
    waitForUpdateConfirmationPacket(id);
    NSResponseCode result = updateSuccessResult.get(id);
    updateSuccessResult.remove(id);
    GNS.getLogger().finer("Add (" + id + "): " + name + "/" + key + "\n  Returning: " + result);
    return result;
  }

  public static NSResponseCode sendRemoveRecord(String name) {
    int id = nextUpdateRequestID();
    GNS.getLogger().finer("Sending remove: " + name);
    RemoveRecordPacket pkt = new RemoveRecordPacket(id, name, localServerID);
    try {
      JSONObject json = pkt.toJSONObject();
      injectPacketIntoLNSQueue(json);
    } catch (JSONException e) {
      e.printStackTrace();
    }
    waitForUpdateConfirmationPacket(id);
    NSResponseCode result = updateSuccessResult.get(id);
    updateSuccessResult.remove(id);
    GNS.getLogger().finer("Remove (" + id + "): " + name + "\n  Returning: " + result);
    return result;
  }

  /**
   * Sends an update request for a single value.
   * 
   * @param name
   * @param key
   * @param newValue
   * @param oldValue
   * @param operation
   * @return 
   */
  public static NSResponseCode sendUpdateRecord(String name, String key, String newValue, String oldValue, UpdateOperation operation,
          String writer, String signature, String message) {
    return sendUpdateRecord(name, key,
            new ResultValue(Arrays.asList(newValue)),
            oldValue != null ? new ResultValue(Arrays.asList(oldValue)) : null,
            operation,
            writer, signature, message);
  }

  /**
   * Sends an update request for a list.
   * 
   * @param name
   * @param key
   * @param newValue
   * @param oldValue
   * @param operation
   * @return 
   */
  public static NSResponseCode sendUpdateRecord(String name, String key, ResultValue newValue, ResultValue oldValue, UpdateOperation operation,
          String writer, String signature, String message) {
    int id = nextUpdateRequestID();
    sendUpdateRecordHelper(name, key, newValue, oldValue, id, operation, writer, signature, message);
    // now we wait until the correct packet comes back
    waitForUpdateConfirmationPacket(id);
    NSResponseCode result = updateSuccessResult.get(id);
    updateSuccessResult.remove(id);
    GNS.getLogger().finer("Update (" + id + "): " + name + "/" + key + "\n  Returning: " + result);
    return result;
  }

  /**
   * Used internally by the system to send update requests for lists. Ignores signatures and access.
   * 
   * @param name
   * @param key
   * @param newValue
   * @param oldValue
   * @param operation
   * @return 
   */
  public static NSResponseCode sendUpdateRecordBypassingAuthentication(String name, String key, ResultValue newValue,
          ResultValue oldValue, UpdateOperation operation) {
    return sendUpdateRecord(name, key, newValue, oldValue, operation, null, null, null);
  }

  /**
   * Used internally by the system to send update requests. Ignores signatures and access.
   * 
   * @param name
   * @param key
   * @param newValue
   * @param oldValue
   * @param operation
   * @return 
   */
  public static NSResponseCode sendUpdateRecordBypassingAuthentication(String name, String key, String newValue,
          String oldValue, UpdateOperation operation) {
    return sendUpdateRecord(name, key, newValue, oldValue, operation, null, null, null);
  }

  private static void sendUpdateRecordHelper(String name, String key, ResultValue newValue,
          ResultValue oldValue, int id, UpdateOperation operation,
          String writer, String signature, String message) {

    GNS.getLogger().finer("Sending update: " + name + " : " + key + " newValue: " + newValue + " oldValue: " + oldValue);
    UpdateAddressPacket pkt = new UpdateAddressPacket(Packet.PacketType.UPDATE_ADDRESS_LNS,
            id,
            name, new NameRecordKey(key),
            newValue,
            oldValue,
            operation, localServerID, GNS.DEFAULT_TTL_SECONDS,
            writer, signature, message);
    try {
      JSONObject json = pkt.toJSONObject();
      injectPacketIntoLNSQueue(json);

    } catch (JSONException e) {
      e.printStackTrace();
    }
  }

  private static int nextUpdateRequestID() {
    int id;
    do {
      id = randomID.nextInt();
    } while (updateSuccessResult.containsKey(id));
    return id;
  }

  private static int nextQueryRequestID() {
    int id;
    do {
      id = randomID.nextInt();
    } while (queryResultMap.containsKey(id));
    return id;
  }

  private static void waitForUpdateConfirmationPacket(int sequenceNumber) {
    try {
      synchronized (monitorUpdate) {
        while (!updateSuccessResult.containsKey(sequenceNumber)) {
          monitorUpdate.wait();
        }
      }
    } catch (InterruptedException x) {
      GNS.getLogger().severe("Wait for update success confirmation packet was interrupted " + x);
    }
  }

  /**
   * Helper function for sending JSON packets to the Local Name Server. 
   * This does not require a socket based send (just a dispatch)
   * as the LNS runs in the same process as the HTTP server.
   * 
   * @param jsonObject 
   */
  public static void injectPacketIntoLNSQueue(JSONObject jsonObject) {
    LNSPacketDemultiplexer.demultiplexLNSPackets(jsonObject);
  }
}