/*
 * Copyright (C) 2013
 * University of Massachusetts
 * All Rights Reserved 
 */
package edu.umass.cs.gns.client;

//import edu.umass.cs.gns.packet.QueryResultValue;
import edu.umass.cs.gns.main.GNS;
import edu.umass.cs.gns.nameserver.ResultValue;
import java.util.HashSet;
import java.util.Set;

/**
 * Implements metadata on fields.
 *
 * @author westy
 */
public class FieldMetaData {

  public static String makeFieldMetaDataKey(MetaDataTypeName metaDataType, String key) {
    return GNS.makeInternalField(metaDataType.name() + "_" + key);
  }

  /**
   * Grabs the metadata indexed by type from the field from the guid.
   * 
   * @param type
   * @param guidInfo
   * @param key
   * @return 
   */
  public static Set<String> lookup(MetaDataTypeName type, GuidInfo guidInfo, String key) {
    return lookup(type, guidInfo.getGuid(), key);
  }

  /**
   * Grabs the metadata indexed by type from the field from the guid.
   * 
   * @param type
   * @param guid
   * @param key
   * @return 
   */
  public static Set<String> lookup(MetaDataTypeName type, String guid, String key) {
    String metaDataKey = makeFieldMetaDataKey(type, key);
    QueryResult result = Intercessor.sendQueryBypassingAuthentication(guid, metaDataKey);
    if (!result.isError()) {
      return new HashSet<String>(result.get(metaDataKey).toStringSet());
    } else {
      return new HashSet<String>();
    }
  }

  /**
   * Adds a value to the metadata of the field in the guid.
   * 
   * @param type
   * @param userInfo
   * @param key
   * @param value 
   */
  public static void add(MetaDataTypeName type, GuidInfo userInfo, String key, String value) {
    add(type, userInfo.getGuid(), key, value);
  }

  /**
   * Adds a value to the metadata of the field in the guid.
   * 
   * @param type
   * @param guid
   * @param key
   * @param value 
   */
  public static void add(MetaDataTypeName type, String guid, String key, String value) {

    String metaDataKey = makeFieldMetaDataKey(type, key);
    Intercessor.sendUpdateRecordBypassingAuthentication(guid, metaDataKey, value, null, UpdateOperation.APPEND_OR_CREATE);
  }

  /**
   * Removes a value from the metadata of the field in the guid.
   * 
   * @param type
   * @param userInfo
   * @param key
   * @param value 
   */
  public static void remove(MetaDataTypeName type, GuidInfo userInfo, String key, String value) {
    remove(type, userInfo.getGuid(), key, value);
  }

  public static void remove(MetaDataTypeName type, String guid, String key, String value) {

    String metaDataKey = makeFieldMetaDataKey(type, key);
    Intercessor.sendUpdateRecordBypassingAuthentication(guid, metaDataKey, value, null, UpdateOperation.REMOVE);
  }
  //
  public static String Version = "$Revision$";
}
