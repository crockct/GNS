package edu.umass.cs.gns.packet;

import edu.umass.cs.gns.packet.Packet.PacketType;
import org.json.JSONException;
import org.json.JSONObject;

/**
 * This packet is sent by a replica controller to an active replica while changing the
 * set of active replicas for a name. Active replica, on receiving the packet, proposes
 * a request to stop the paxos among current set of active replicas.
 *
 * Further, once the paxos replicas are stopped, active replicas changes the packet type
 * field to <code>OLD_ACTIVE_STOP_CONFIRM_TO_PRIMARY</code> and send the same packet to
 * primaries.
 *
 *
 * Refer to the classes <link>StopActiveSetTask</link> and <code>ListenerReplicationPaxos</code>
 * for more documentation.
 */
public class OldActiveSetStopPacket extends BasicPacket
{
  private final static String REQ_ID = "reqID";

	private final static String NAME = "name";

	private final static String PRIMARY_SENDER = "primarySender";
	
	private final static String ACTIVE_RECEIVER = "activeReceiver";

	private final static String PAXOS_ID_TO_BE_STOPPED = "paxosID";

  /**
   * Unique request ID generated by the replica controller.
   */
  private int requestID;

  /**
	 * name for which the proposal is being done.
	 */
  private String name;
	
	/**
	 * primary node that sent this message 
	 */
  private int primarySender;
	
	/**
	 * active who received this message from primary
	 */
  private int activeReceiver;
	
	/**
	 * Paxos ID that is requested to be stopped.
	 */
  private String paxosIDTobeStopped;

	/**
	 * 
	 * @param name
	 * @param primarySender
	 */
	public OldActiveSetStopPacket(String name, int requestID, int primarySender, int activeReceiver, String paxosIDToBeStopped, PacketType type1) {
		this.name = name;
    this.requestID = requestID;
		//this.recordKey = recordKey;
		this.type = type1;
		this.primarySender = primarySender;
		this.activeReceiver = activeReceiver;
		this.paxosIDTobeStopped = paxosIDToBeStopped;
	}
	
	public OldActiveSetStopPacket(JSONObject json) throws JSONException {
		this.type = Packet.getPacketType(json);
    this.requestID = json.getInt(REQ_ID);
		this.name = json.getString(NAME);
		this.primarySender = json.getInt(PRIMARY_SENDER);
		this.activeReceiver = json.getInt(ACTIVE_RECEIVER);
		this.paxosIDTobeStopped = json.getString(PAXOS_ID_TO_BE_STOPPED);
	}

	/**
	 * JSON object that is implemented.
	 * @return
	 * @throws JSONException
	 */
	@Override
	public JSONObject toJSONObject() throws JSONException {
		JSONObject json = new JSONObject();
		Packet.putPacketType(json, getType());
    json.put(REQ_ID, requestID);
		json.put(NAME, name);
		json.put(PRIMARY_SENDER, primarySender);
		json.put(ACTIVE_RECEIVER, activeReceiver);
		json.put(PAXOS_ID_TO_BE_STOPPED, paxosIDTobeStopped);
		return json;
	}

  public int getRequestID() {
    return requestID;
  }

	public String getName() {
		return name;
	}
	
	public int getPrimarySender() {
		return primarySender;
	}
	
	public int getActiveReceiver() {
		return activeReceiver;
	}
	
	public String getPaxosIDToBeStopped() {
		return paxosIDTobeStopped;
	}

  /**
   * Once the paxos instance is stopped, the active replica changes the packet type
   * before sending response to primary replica. The packet type field helps the receiving node
   * to identify that this is a reply from an active replica.
   */
	public void changePacketTypeToConfirm() {
		setType(PacketType.OLD_ACTIVE_STOP_CONFIRM_TO_PRIMARY);
	}

  public void changePacketTypeToActiveRemoved() {
    setType(PacketType.ACTIVE_REMOVE_CONFIRM);
  }

  /**
   * This method is called before proposing the STOP request to active replicas.
   * Active replica changes the packet type to distinguish paxos request from the
   * original packet it received from primary.
   */
	public void changePacketTypeToPaxosStop() {
		setType(PacketType.ACTIVE_PAXOS_STOP);
	}
}
