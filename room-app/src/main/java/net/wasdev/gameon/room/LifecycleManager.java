/*******************************************************************************
 * Copyright (c) 2015 IBM Corp.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package net.wasdev.gameon.room;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArraySet;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;

import javax.enterprise.context.ApplicationScoped;
import javax.json.Json;
import javax.json.JsonArrayBuilder;
import javax.json.JsonObject;
import javax.json.JsonObjectBuilder;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.websocket.Endpoint;
import javax.websocket.HandshakeResponse;
import javax.websocket.Session;
import javax.websocket.server.HandshakeRequest;
import javax.websocket.server.ServerApplicationConfig;
import javax.websocket.server.ServerEndpointConfig;

import org.apache.http.HttpEntity;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.methods.CloseableHttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.CloseableHttpClient;
import org.apache.http.impl.client.HttpClients;
import org.apache.http.util.EntityUtils;
import org.gameontext.signed.SignedRequestHmac;
import org.gameontext.signed.SignedRequestMap;
import org.ozzy.model.Item;
import org.ozzy.model.Room;
import org.ozzy.model.Story;
import org.ozzy.runtime.RoomEngine;
import org.ozzy.runtime.RoomResponseProcessor;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;

/**
 * Manages the registration of all rooms in the Engine with the concierge
 */
@ApplicationScoped
public class LifecycleManager implements ServerApplicationConfig {
  private final Map<String, Collection<Session>> sessionMap = new ConcurrentHashMap<String, Collection<Session>>();
  private String registrationSecret;
  private String systemId;

  public static class SessionRoomResponseProcessor implements RoomResponseProcessor {
    private Collection<Session> activeSessions = new CopyOnWriteArraySet<Session>();
    private AtomicInteger counter = new AtomicInteger(0);
    private Map<String, Collection<Session>> sessionMap;

    public SessionRoomResponseProcessor(Map<String, Collection<Session>> sessionMap) {
      this.sessionMap = sessionMap;
    }

    private void generateEvent(Session session, JsonObject content, String userID, boolean selfOnly, int bookmark)
        throws IOException {
      JsonObjectBuilder response = Json.createObjectBuilder();
      response.add("type", "event");
      response.add("content", content);
      response.add("bookmark", bookmark);

      String msg = "player," + (selfOnly ? userID : "*") + "," + response.build().toString();
      Log.log(Level.FINE, this, "ROOM(PE): sending to session {0} messsage {1}", session.getId(), msg);
      synchronized(session) {
        if(session.isOpen())
          session.getBasicRemote().sendText(msg);
      }
    }

    @Override
    public void playerEvent(String senderId, String selfMessage, String othersMessage) {
      // System.out.println("Player message :: from("+senderId+")
      // onlyForSelf("+String.valueOf(selfMessage)+")
      // others("+String.valueOf(othersMessage)+")");
      JsonObjectBuilder content = Json.createObjectBuilder();
      boolean selfOnly = true;
      if (othersMessage != null && othersMessage.length() > 0) {
        content.add("*", othersMessage);
        selfOnly = false;
      }
      if (selfMessage != null && selfMessage.length() > 0) {
        content.add(senderId, selfMessage);
      }
      JsonObject json = content.build();
      int count = counter.incrementAndGet();

      String groupId = getGroupForPlayerId(senderId);
      System.out.println("Have group "+groupId+" for sender "+senderId);
      Collection<Session> sessionsForGroup = sessionMap.get(groupId);
      System.out.println("I know of "+sessionsForGroup.size()+" sessions for this group, sending to them now. ");
      for (Session s : sessionsForGroup) {
        try {
          generateEvent(s, json, senderId, selfOnly, count);
        } catch (IOException io) {
          throw new RuntimeException(io);
        }
      }
    }

    private void generateRoomEvent(Session session, JsonObject content, int bookmark) throws IOException {
      JsonObjectBuilder response = Json.createObjectBuilder();
      response.add("type", "event");
      response.add("content", content);
      response.add("bookmark", bookmark);

      String msg = "player,*," + response.build().toString();

      Log.log(Level.FINE, this, "ROOM(RE): sending to session {0} messsage {1}", session.getId(), msg);
      synchronized(session) {
        if(session.isOpen())
          session.getBasicRemote().sendText(msg);
      }
    }

    @Override
    public void roomEvent(String senderId, String s) {
      // System.out.println("Message sent to everyone :: "+s);
      JsonObjectBuilder content = Json.createObjectBuilder();
      content.add("*", s);
      JsonObject json = content.build();
      int count = counter.incrementAndGet();

      String groupId = getGroupForPlayerId(senderId);
      Collection<Session> sessionsForGroup = sessionMap.get(groupId);
      for (Session session : sessionsForGroup) {
        try {
          generateRoomEvent(session, json, count);
        } catch (IOException io) {
          throw new IllegalStateException(io);
        }
      }
    }

    public void chatEvent(String senderId, String username, String tmsg) {
      JsonObjectBuilder content = Json.createObjectBuilder();
      content.add("type", "chat");
      content.add("username", username);
      content.add("content", tmsg);
      content.add("bookmark", counter.incrementAndGet());
      JsonObject json = content.build();
      String msg = "player,*," + json.toString();
      
      String groupId = getGroupForPlayerId(senderId);
      Collection<Session> sessionsForGroup = sessionMap.get(groupId);
      for (Session session : sessionsForGroup) {
        try {
          Log.log(Level.FINE, this, "ROOM(CE): sending to session {0} messsage {1}", session.getId(), msg);
          synchronized(session) {
            if(session.isOpen())
              session.getBasicRemote().sendText(msg);
          }
        } catch (IOException io) {
          throw new IllegalStateException(io);
        }
      }
    }

    @Override
    public void locationEvent(String senderId, String roomId, String roomName, String roomDescription,
        Map<String, String> exits, List<String> objects, List<String> inventory, Map<String, String> commands) {
      JsonObjectBuilder content = Json.createObjectBuilder();
      content.add("type", "location");
      content.add("name", roomId);
      content.add("fullName", roomName);
      content.add("description", roomDescription);

      JsonObjectBuilder exitJson = Json.createObjectBuilder();
      for (Entry<String, String> e : exits.entrySet()) {
        if(e.getValue()!=null ) {
          exitJson.add(e.getKey(), e.getValue());
        }
      }
      content.add("exits", exitJson.build());

      JsonObjectBuilder commandJson = Json.createObjectBuilder();
      for (Entry<String, String> c : commands.entrySet()) {
        commandJson.add(c.getKey(), c.getValue());
      }
      content.add("commands", commandJson.build());

      JsonArrayBuilder inv = Json.createArrayBuilder();
      for (String i : inventory) {
        inv.add(i);
      }
      content.add("pockets", inv.build());

      JsonArrayBuilder objs = Json.createArrayBuilder();
      for (String o : objects) {
        objs.add(o);
      }
      content.add("objects", objs.build());
      content.add("bookmark", counter.incrementAndGet());

      JsonObject json = content.build();
      String msg = "player," + senderId + "," + json.toString();
      
      String groupId = getGroupForPlayerId(senderId);
      Collection<Session> sessionsForGroup = sessionMap.get(groupId);
      for (Session session : sessionsForGroup) {
        try {
          Log.log(Level.FINE, this, "ROOM(LE): sending to session {0} messsage {1}", session.getId(), msg);
          session.getBasicRemote().sendText(msg);
          synchronized(session) {
            if(session.isOpen())
              session.getBasicRemote().sendText(msg);
          }
        } catch (IOException io) {
          throw new RuntimeException(io);
        }
      }
    }

    @Override
    public void exitEvent(String senderId, String message, String exitID, String exitJson) {
      JsonObjectBuilder content = Json.createObjectBuilder();
      content.add("type", "exit");
      content.add("exitId", exitID);
      content.add("content", message);
      content.add("bookmark", counter.incrementAndGet());
      JsonObject json = content.build();
      String msg = "playerLocation," + senderId + "," + json.toString();
      
      String groupId = getGroupForPlayerId(senderId);
      Collection<Session> sessionsForGroup = sessionMap.get(groupId);
      for (Session session : sessionsForGroup) {
        try {
          
          Log.log(Level.FINE, this, "ROOM(EE): sending to session {0} messsage {1}", session.getId(), msg);
          synchronized(session) {
            if(session.isOpen())
              session.getBasicRemote().sendText(msg);
          }
        } catch (IOException io) {
          throw new RuntimeException(io);
        }
      }
    }

    public String getGroupForPlayerId(String playerId) {
      // map userid to group..
      String groupId = "default";
      if (playerId.startsWith("facebook:") || playerId.startsWith("twitter:")) {
        groupId = "fbtwitter";
      } else if (playerId.startsWith("story:colab:")) {
        groupId = playerId.substring(0, playerId.lastIndexOf(':'));
      }
      System.out.println("DEBUG: playerId " + playerId + " mapped to group " + groupId);
      return groupId;
    }

    public void addSession(Session s) {
      activeSessions.add(s);
    }

    public void linkSessionWithPlayer(Session s, String playerId) {
      String groupId = getGroupForPlayerId(playerId);
      sessionMap.putIfAbsent(groupId, new CopyOnWriteArraySet<Session>());
      Collection<Session> sessions = sessionMap.get(groupId);
      sessions.add(s);
      System.out.println("Associated id "+playerId+" with session "+s.getId()+" to groupId "+groupId);
    }

    public void removeSession(Session s) {
      activeSessions.remove(s);
    }

    public void unlinkSessionFromPlayer(Session s, String playerId) {
      String groupId = getGroupForPlayerId(playerId);
      Collection<Session> sessions = sessionMap.get(groupId);
      sessions.remove(s);
      System.out.println("Unassociated id "+playerId+" with session "+s.getId()+" to groupId "+groupId);
    }

    public Collection<Session> getSessions() {
      return activeSessions;
    }
  }

  private void getConfig() {
    try {
      registrationSecret = (String) new InitialContext().lookup(Constants.JNDI_REGISTRATION_SECRET);
      systemId = (String) new InitialContext().lookup(Constants.JNDI_SYSTEM_ID);
    } catch (NamingException e) {
    }
    if (registrationSecret == null || systemId == null) {
      throw new IllegalStateException("registrationSecret(" + String.valueOf(registrationSecret) + ") or systemid("
          + String.valueOf(systemId) + ") was not found, check server.xml/server.env");
    }
  }

  private static class RoomWSConfig extends ServerEndpointConfig.Configurator {
    private final Holodeck holodeck;
    private final SessionRoomResponseProcessor srrp;
    private final String token;

    public RoomWSConfig(Holodeck holodeck, SessionRoomResponseProcessor srrp, String token) {
      this.holodeck = holodeck;
      this.srrp = srrp;
      this.holodeck.setRoomResponseProcessor(srrp);
      this.token = token;
    }

    @SuppressWarnings("unchecked")
    @Override
    public <T> T getEndpointInstance(Class<T> endpointClass) {
      RoomWS r = new RoomWS(this.holodeck, this.srrp);
      return (T) r;
    }

    @Override
    public void modifyHandshake(ServerEndpointConfig sec, HandshakeRequest request, HandshakeResponse response) {
      super.modifyHandshake(sec, request, response);

      if (token == null || token.isEmpty()) {
        Log.log(Level.FINEST, this, "No token set for room, skipping validation");
      } else {
        Log.log(Level.FINEST, this, "Validating WS handshake");
        SignedRequestHmac wsHmac = new SignedRequestHmac("", token, "", request.getRequestURI().getRawPath());

        try {
          wsHmac.checkHeaders(new SignedRequestMap.MLS_StringMap(request.getHeaders())).verifyFullSignature()
              .wsResignRequest(new SignedRequestMap.MLS_StringMap(response.getHeaders()));

          Log.log(Level.INFO, this, "validated and resigned", wsHmac);
        } catch (Exception e) {
          Log.log(Level.WARNING, this, "Failed to validate HMAC, unable to establish connection", e);

          response.getHeaders().replace(HandshakeResponse.SEC_WEBSOCKET_ACCEPT, Collections.emptyList());
        }
      }
    }
  }

  public static class RegistrationRoom {
    private String id;
    private String name;

    public RegistrationRoom(String id, String name) {
      this.id = id;
      this.name = name;
    }

    public String getId() {
      return id;
    }

    public String getName() {
      return name;
    }
  }

  private Set<ServerEndpointConfig> registerRooms(Holodeck holodeck) {

    sessionMap.putIfAbsent("default", new CopyOnWriteArraySet<>());

    Set<ServerEndpointConfig> endpoints = new HashSet<ServerEndpointConfig>();

    // new plan.. only 1 room to register, and it's magic. Yes really. Magic. Do not
    // ask.
    RegistrationRoom room = new RegistrationRoom("ozzy.test.colab", "The Colab Adventure Room"); // TODO: externalise
                                                                                                 // config.

    RoomRegistrationHandler roomRegistration = new RoomRegistrationHandler(room, systemId, registrationSecret);
    try {
      roomRegistration.performRegistration();
    } catch (Exception e) {
      Log.log(Level.SEVERE, this, "Room Registration FAILED", e);
      // we keep running, maybe we were registered ok before...
    }

    // now regardless of our registration, open our websocket.
    SessionRoomResponseProcessor srrp = new SessionRoomResponseProcessor(sessionMap);
    ServerEndpointConfig.Configurator config = new RoomWSConfig(holodeck, srrp, roomRegistration.getToken());

    endpoints
        .add(ServerEndpointConfig.Builder.create(RoomWS.class, "/ws/" + room.getId()).configurator(config).build());

    return endpoints;
  }

  private InputStream readFromHttp(String url) throws ClientProtocolException, IOException {
    CloseableHttpClient httpclient = HttpClients.createDefault();
    HttpGet httpget = new HttpGet(url);
    CloseableHttpResponse response = httpclient.execute(httpget);
    byte[] data = null;
    try {
      HttpEntity entity = response.getEntity();
      if (entity != null) {
        data = EntityUtils.toByteArray(entity);
      } else {
        throw new IOException("Unable to load yaml");
      }
    } finally {
      response.close();
    }
    return new ByteArrayInputStream(data);
  }

  private Story parseYaml(InputStream inputStream) {
    Constructor c = new Constructor(Story.class);
    Yaml yaml = new Yaml(c);
    Story s = yaml.load(inputStream);
    return s;
  }

  public static class Holodeck implements RoomResponseProcessor {
    Map<String, Map<String, RoomEngine>> holodeckProgramsByGroupId;
    SessionRoomResponseProcessor srrp;
    String startId;
    Map<String, RoomEngine> activeRoomEngineByGroupId;
    Map<String, Collection<String>> useridsByGroupId;
    
    public Holodeck(Map<String, Map<String, RoomEngine>> holodeckPrograms, String startId ) {
      this.holodeckProgramsByGroupId = holodeckPrograms;
      this.startId = startId;
      
      activeRoomEngineByGroupId = new ConcurrentHashMap<>();
      for(String key : holodeckProgramsByGroupId.keySet()) {
        activeRoomEngineByGroupId.put(key, holodeckPrograms.get(key).get(startId));
      }
      
      useridsByGroupId = new ConcurrentHashMap<String, Collection<String>>();
    }
    
    public void setRoomResponseProcessor( SessionRoomResponseProcessor srrp) {
      this.srrp = srrp;
      //plug the holodeck into the rooms so they can speak =)
      for(Map.Entry<String, Map<String, RoomEngine>> e : holodeckProgramsByGroupId.entrySet()) {
        for(Map.Entry<String, RoomEngine> e2 : e.getValue().entrySet()) {
          e2.getValue().setHolodeck(this);
        }
      }
    }
    
    private void sendNewRoomText(String userid, RoomEngine re) {
      Map<String,String> exits = new HashMap<>();
      exits.put("N","No sign of an exit here...");
      exits.put("S","Nothing over here either...");
      exits.put("E","Still no sign of an exit...");
      exits.put("W","Yet another direction with no exit..");

      if(re.room.getExits()!=null) {
        exits.putAll(re.room.getExits());
      }
      
      List<String> items = new ArrayList<>();
      List<String> inventory = new ArrayList<>();
      Map<String,String> commands = new TreeMap<>(); //re.getCommandMap();
      if(re.getCommandMap()!=null) {
        for(Entry<String, String> c : re.getCommandMap().entrySet()) {
          commands.put("/"+c.getKey(), c.getValue());
        }
      }
      
      //handy for debug.. gives away too much for real use.
//      for( String e : re.commandHandlers.keySet() ) {
//        if(e.contains(":")) {
//          String parts[] = e.split(":");
//          commands.putIfAbsent(parts[0], "");
//          String current = commands.get(parts[0]);
//          if(current.length()>0) { current+=","+parts[1]; }else {current=parts[1];}
//          commands.put(parts[0], current);
//        }
//      }

      srrp.locationEvent(userid, re.getId(), re.getName(), "", exits, items, inventory, commands);
    }
    
    public void switchRoom(String userid, String newRoomId) {
      String groupId = srrp.getGroupForPlayerId(userid);
      if(holodeckProgramsByGroupId.containsKey(groupId)) {
        Map<String, RoomEngine> holodeckProgramsForGroupId = holodeckProgramsByGroupId.get(groupId);
        if(holodeckProgramsForGroupId.containsKey(newRoomId)) {
          RoomEngine re = holodeckProgramsForGroupId.get(newRoomId);
          activeRoomEngineByGroupId.put(groupId, re);
          

          
          Collection<String> userIdsForGroup = useridsByGroupId.get(groupId);
          if(userIdsForGroup!=null) {
            for(String userInGroup : userIdsForGroup) {
              sendNewRoomText(userInGroup, re);
              //re.processRoomInput("/look", userInGroup, playerName);
              command(userInGroup, "look");
            }
          }
          
        }
      }
    }
    
    public void updateExit(String userid, String direction, String description) {
      
    }
    
    public void command(String userid, String content) {
      
      String groupId = srrp.getGroupForPlayerId(userid);
      System.out.println("Command '"+content+"' for user "+userid+" assigned to groupId "+groupId);
      RoomEngine activeProgram = activeRoomEngineByGroupId.get(groupId);
      System.out.println("Obtained RoomEngine "+activeProgram.getName()+" for groupId "+groupId);
      
      if ("ydebug commands".equals(content.toLowerCase())) {
        String ymsg = "DEBUG: I know the following commands\n";
        for (String key : activeProgram.commandHandlers.keySet()) {
          ymsg += "* **" + key + "**\n";
        }
        srrp.playerEvent(userid, ymsg, null);
      } else if ("ydebug items".equals(content.toLowerCase())) {
        String ymsg = "DEBUG: I know the following items in this room\n";
        if (activeProgram.room.getItems() != null) {
          for (Item i : activeProgram.room.getItems()) {
            ymsg += "* **" + i.getName() + "**\n";
          }
        }
        srrp.playerEvent(userid, ymsg, null);
      } else if ("ydebug state".equals(content.toLowerCase())) {
        String ymsg = "DEBUG: I know the following state vars\n";
        Map<String,Object> stateMap=new TreeMap<>();
        if (activeProgram.stateById.size() > 0) {
          stateMap.putAll(activeProgram.stateById);
          for (Map.Entry<String, Object> kv : stateMap.entrySet()) {
            ymsg += "* **" + kv.getKey() + "** -> " + kv.getValue() + "\n";
          }
        }
        srrp.playerEvent(userid, ymsg, null);
      } else if ("ydebug actionmap".equals(content.toLowerCase())) {
        srrp.playerEvent(userid, "DEBUG: actionmap currently has " + activeProgram.actionMap.size() + " entries.", null);
      } else if (content.toLowerCase().startsWith("ydebug teleport ") && content.length()>"ydebug teleport ".length()) {
        String roomid = content.toLowerCase().substring("ydebug teleport ".length());
        if(holodeckProgramsByGroupId.get(groupId).containsKey(roomid)) {
          srrp.playerEvent(userid, "DEBUG: loading holodeck program with id "+roomid, null);
          switchRoom(userid, roomid);
        }else {
          String knownRooms = holodeckProgramsByGroupId.get(groupId).keySet().toString();
          srrp.playerEvent(userid, "DEBUG: teleport requested for roomid "+roomid+" known rooms "+knownRooms, null);
        }
      } else {
        activeProgram.command(userid, content);
      }
    }
    
    public void addUserToRoom(String userid, String username) {
      String groupId = srrp.getGroupForPlayerId(userid);
      RoomEngine activeProgram = activeRoomEngineByGroupId.get(groupId);
      activeProgram.addUserToRoom(userid, username);
      
      useridsByGroupId.putIfAbsent(groupId, new CopyOnWriteArraySet<>());
      Collection<String> userids = useridsByGroupId.get(groupId);
      userids.add(userid);
      
      sendNewRoomText(userid, activeProgram);
      command(userid, "look");
    }

    public void removeUserFromRoom(String userid) {
      String groupId = srrp.getGroupForPlayerId(userid);
      RoomEngine activeProgram = activeRoomEngineByGroupId.get(groupId);
      activeProgram.removeUserFromRoom(userid);
      
      Collection<String> userids = useridsByGroupId.get(groupId);
      if(userids!=null) {
        userids.add(userid);
      }
    }
    
    public void playerEvent(String senderId, String selfMessage, String othersMessage) {
      srrp.playerEvent(senderId, selfMessage, othersMessage);
    }

    // "Message sent to everyone :: "+s
    public void roomEvent(String senderId, String s) {
      srrp.roomEvent(senderId, s);
    }

    public void locationEvent(String senderId, String roomId, String roomName, String roomDescription,
        Map<String, String> exits, List<String> objects, List<String> inventory, Map<String, String> commands) {
      srrp.locationEvent(senderId, roomId, roomName, roomDescription, exits, objects, inventory, commands);
    }

    public void exitEvent(String senderId, String exitMessage, String exitID, String exitJson) {
      srrp.exitEvent(senderId, exitMessage, exitID, exitJson);
    }
  }

  private Holodeck buildHolodeck() {
    // load the rooms..
    try {
      
      Map<String, Map<String, RoomEngine>> roomenginesbygroupid = new ConcurrentHashMap<>();
      
      String firstId = null;
      for(String g: Constants.ACTIVE_GROUPS) {
        //TODO: cache this to databuffer
        InputStream is = readFromHttp(
            // "https://raw.githubusercontent.com/BarDweller/gameon-yaml-driven-room/patch-3/roomyaml-all-br");
            "https://raw.githubusercontent.com/suehle/gameon-yaml-driven-room/main/roomyaml-all-br");        
        Story s = parseYaml(is);
        if(firstId == null) {
          firstId = s.getRooms().get(0).getId();
        }
        Map<String, RoomEngine> rooms = new HashMap<>();
        if (s.getRooms() != null) {
          for (Room r : s.getRooms()) {
            RoomEngine re = new RoomEngine(s.getVars(), s.getCommands(), s.getCommanddescriptions(), r);
            rooms.put(r.getId(), re);
          }
        }
        roomenginesbygroupid.put(g, rooms);
      }
      Holodeck h = new Holodeck(roomenginesbygroupid,firstId);
      return h;
    } catch (IOException io) {
      io.printStackTrace();
      throw new RuntimeException(io);
    }
  }

  @Override
  public Set<ServerEndpointConfig> getEndpointConfigs(Set<Class<? extends Endpoint>> endpointClasses) {
    try {
      if (registrationSecret == null)
        getConfig();

      return registerRooms(buildHolodeck());
    } catch (IllegalStateException e) {
      Log.log(Level.SEVERE, this, "Error building endpoint configs for room", e);
      // getEndpointConfigs is defined by ServerApplicationConfig, and doesn't allow
      // for failure..
      // so this is the best we can do..
      throw e;
    }
  }

  @Override
  public Set<Class<?>> getAnnotatedEndpointClasses(Set<Class<?>> scanned) {
    return null;
  }

}
