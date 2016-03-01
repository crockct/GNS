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
package edu.umass.cs.gnsclient.client;

import edu.umass.cs.gnsclient.client.util.GuidUtils;
import edu.umass.cs.gnsclient.client.util.JSONUtils;
import edu.umass.cs.gnsclient.client.util.ServerSelectDialog;
import edu.umass.cs.gnscommon.utils.RandomString;
import edu.umass.cs.gnscommon.exceptions.client.GnsClientException;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.util.Arrays;
import java.util.HashSet;
import static org.junit.Assert.*;
import org.junit.FixMethodOrder;
import org.junit.Test;
import org.junit.runners.MethodSorters;

/**
 * Comprehensive functionality test for the GNS.
 *
 */
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class GroupAddTest {

  private static String ACCOUNT_ALIAS = "admin@gns.name"; // REPLACE THIS WITH YOUR ACCOUNT ALIAS
  private static final String PASSWORD = "password";
  private static UniversalTcpClientExtended client;
  /**
   * The address of the GNS server we will contact
   */
  private static InetSocketAddress address = null;
  private static GuidEntry masterGuid;
  private static GuidEntry westyEntry;
  private static GuidEntry samEntry;
  private static GuidEntry mygroupEntry;

  public GroupAddTest() {
    if (client == null) {
      if (System.getProperty("host") != null
              && !System.getProperty("host").isEmpty()
              && System.getProperty("port") != null
              && !System.getProperty("port").isEmpty()) {
        address = new InetSocketAddress(System.getProperty("host"),
                Integer.parseInt(System.getProperty("port")));
      } else {
        address = ServerSelectDialog.selectServer();
      }
      client = new UniversalTcpClientExtended(address.getHostName(), address.getPort(),
              System.getProperty("disableSSL").equals("true"));
      try {
        masterGuid = GuidUtils.lookupOrCreateAccountGuid(client, ACCOUNT_ALIAS, PASSWORD, true);
      } catch (Exception e) {
        fail("Exception when we were not expecting it: " + e);
      }
    }
  }

  @Test
  public void test_01_testCreateGuids() {
    try {
      westyEntry = GuidUtils.registerGuidWithTestTag(client, masterGuid, "westy" + RandomString.randomString(6));
      samEntry = GuidUtils.registerGuidWithTestTag(client, masterGuid, "sam" + RandomString.randomString(6));
      System.out.println("Created: " + westyEntry);
      System.out.println("Created: " + samEntry);
    } catch (Exception e) {
      fail("Exception while creating guids: " + e);
    }
  }

  private static GuidEntry guidToDeleteEntry;

  @Test
  public void test_210_GroupCreate() {
    String mygroupName = "mygroup" + RandomString.randomString(6);
    try {
      try {
        client.lookupGuid(mygroupName);
        fail(mygroupName + " entity should not exist");
      } catch (GnsClientException e) {
      }
      guidToDeleteEntry = GuidUtils.registerGuidWithTestTag(client, masterGuid, "deleteMe" + RandomString.randomString(6));
      mygroupEntry = GuidUtils.registerGuidWithTestTag(client, masterGuid, mygroupName);
    } catch (Exception e) {
      fail("Exception while creating guids: " + e);
    }
  }

  @Test
  public void test_211_GroupAddWesty() {
    try {
      client.groupAddGuid(mygroupEntry.getGuid(), westyEntry.getGuid(), mygroupEntry);
    } catch (Exception e) {
      fail("Exception while adding Westy: " + e);
    }
  }
  
   @Test
  public void test_212_GroupAddSam() {
    try {
      client.groupAddGuid(mygroupEntry.getGuid(), samEntry.getGuid(), mygroupEntry);
    } catch (Exception e) {
      fail("Exception while adding Sam: " + e);
    }
  }
   @Test
  public void test_213_GroupAddGuidToDelete() {
    try {
      client.groupAddGuid(mygroupEntry.getGuid(), guidToDeleteEntry.getGuid(), mygroupEntry);
    } catch (Exception e) {
      fail("Exception while adding GuidToDelete: " + e);
    }
  }

}
