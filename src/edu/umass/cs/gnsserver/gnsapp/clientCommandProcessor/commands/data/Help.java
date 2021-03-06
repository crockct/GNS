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
package edu.umass.cs.gnsserver.gnsapp.clientCommandProcessor.commands.data;

import static edu.umass.cs.gnscommon.GNSCommandProtocol.*;
import edu.umass.cs.gnsserver.gnsapp.clientCommandProcessor.ClientRequestHandlerInterface;
import edu.umass.cs.gnsserver.gnsapp.clientCommandProcessor.commandSupport.CommandResponse;
import edu.umass.cs.gnsserver.gnsapp.clientCommandProcessor.commands.CommandDescriptionFormat;
import edu.umass.cs.gnsserver.gnsapp.clientCommandProcessor.commands.CommandModule;

import edu.umass.cs.gnscommon.CommandType;
import edu.umass.cs.gnsserver.gnsapp.clientCommandProcessor.commands.BasicCommand;
import org.json.JSONObject;

/**
 *
 * @author westy
 */
public class Help extends BasicCommand {

  /**
   *
   * @param module
   */
  public Help(CommandModule module) {
    super(module);
  }
  
  @Override
  public CommandType getCommandType() {
    return CommandType.Help;
  }

  @Override
  public String[] getCommandParameters() {
    return new String[]{};
  }

//  @Override
//  public String getCommandName() {
//    return HELP;
//  }

  @Override
  public CommandResponse<String> execute(JSONObject json, ClientRequestHandlerInterface handler) {
    if (json.has("tcp")) {
      return new CommandResponse<String>("Commands are sent as TCP packets." + NEWLINE + NEWLINE
              + "Note: We use the terms field and key interchangably below." + NEWLINE + NEWLINE
              + "Commands:" + NEWLINE
              + module.allCommandDescriptions(CommandDescriptionFormat.TCP));
    } else if (json.has("tcpwiki")) {
      return new CommandResponse<String>("Commands are sent as TCP packets." + NEWLINE + NEWLINE
              + "Note: We use the terms field and key interchangably below." + NEWLINE + NEWLINE
              + "Commands:" + NEWLINE
              + module.allCommandDescriptions(CommandDescriptionFormat.TCP_Wiki));
    } else {
      return new CommandResponse<String>("Commands are sent as HTTP GET queries." + NEWLINE + NEWLINE
              + "Note: We use the terms field and key interchangably below." + NEWLINE + NEWLINE
              + "Commands:" + NEWLINE
              + module.allCommandDescriptions(CommandDescriptionFormat.HTML));
    }
  }

  @Override
  public String getCommandDescription() {
    return "Returns this help message";
  }
}
