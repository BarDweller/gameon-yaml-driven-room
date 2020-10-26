package org.ozzy.model;

import java.util.List;
import java.util.Map;

public class Story {
  private Map<String, Object> vars;
  private Map<String, String> commanddescriptions;
  private String id;
  private String revision;
  private List<Command> commands;
  private List<Room> rooms;

  public Map<String, Object> getVars() {
    return vars;
  }

  public void setVars(Map<String, Object> vars) {
    this.vars = vars;
  }
  

  public Map<String, String> getCommanddescriptions() {
    return commanddescriptions;
  }

  public void setCommanddescriptions(Map<String, String> commanddescriptions) {
    this.commanddescriptions = commanddescriptions;
  }

  public String getId() {
    return id;
  }

  public void setId(String id) {
    this.id = id;
  }

  public String getRevision() {
    return revision;
  }

  public void setRevision(String revision) {
    this.revision = revision;
  }

  public List<Command> getCommands() {
    return commands;
  }

  public void setCommands(List<Command> commands) {
    this.commands = commands;
  }

  public List<Room> getRooms() {
    return rooms;
  }

  public void setRooms(List<Room> rooms) {
    this.rooms = rooms;
  }
}
