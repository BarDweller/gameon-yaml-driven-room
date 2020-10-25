package org.ozzy.runtime;

import java.util.List;
import java.util.Map;

public interface RoomResponseProcessor {

  // "Player message :: from("+senderId+")
  // onlyForSelf("+String.valueOf(selfMessage)+")
  // others("+String.valueOf(othersMessage)+")"
  public void playerEvent(String senderId, String selfMessage, String othersMessage);

  // "Message sent to everyone :: "+s
  public void roomEvent(String senderId, String s);

  public void locationEvent(String senderId, String roomId, String roomName, String roomDescription,
      Map<String, String> exits, List<String> objects, List<String> inventory, Map<String, String> commands);

  public void exitEvent(String senderId, String exitMessage, String exitID, String exitJson);

}
