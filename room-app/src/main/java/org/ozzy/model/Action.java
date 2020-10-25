package org.ozzy.model;

import java.util.List;
import java.util.UUID;

public class Action {
  public final UUID uuid = UUID.randomUUID();
  private String condition;
  private List<String> instructions;
  private String user;
  private String room;

  public String getCondition() {
    return condition;
  }

  public void setCondition(String condition) {
    this.condition = condition;
  }

  public List<String> getDo() {
    return instructions;
  }

  public void setDo(List<String> instructions) {
    this.instructions = instructions;
  }

  public String getUser() {
    return user;
  }

  public void setUser(String user) {
    this.user = user;
  }

  public String getRoom() {
    return room;
  }

  public void setRoom(String room) {
    this.room = room;
  }
}
