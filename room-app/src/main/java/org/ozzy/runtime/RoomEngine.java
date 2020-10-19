package org.ozzy.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import org.ozzy.model.Action;
import org.ozzy.model.Command;
import org.ozzy.model.Item;
import org.ozzy.model.Room;

public class RoomEngine {

	Map<String, Object> globalVars;
	List<Command> globalCommands;
	Room room;

	public Map<String, CommandHandler> commandHandlers;
	public Map<String, Object> stateById;
	public Map<ActionFingerprint, Integer> actionMap = new HashMap<ActionFingerprint, Integer>();

	public String getId() {
		return "ozzy.test."+room.getId();
	}
	public String getName() {
		return room.getName();
	}
	
	public RoomResponseProcessor rrp = new DebugResponseProcessor();
	
    public static class DebugResponseProcessor implements RoomResponseProcessor {
        @Override
        public void playerEvent(String senderId, String selfMessage, String othersMessage) {
            System.out.println("Player message :: from(" + senderId + ") onlyForSelf(" + String.valueOf(selfMessage)
                    + ") others(" + String.valueOf(othersMessage) + ")");
        }

        @Override
        public void roomEvent(String s) {
            System.out.println("Message sent to everyone :: " + s);
        }

        @Override
        public void locationEvent(String senderId, String roomId, String roomName, String roomDescription,Map<String,String> exits,
                List<String> objects, List<String> inventory, Map<String,String> commands) {
            System.out.println("Location: " + roomName + " (For " + senderId + ") " + roomDescription);
            if (exits.isEmpty()){
                System.out.println("There are no exits.");
            }else{
                for( Entry<String, String> exit : exits.entrySet()){
                    System.out.println(" - "+exit.getKey()+" "+exit.getValue());
                }
            }
            if (!objects.isEmpty()) {
                System.out.println("You can see the following items: " + objects);
            }
            if (!inventory.isEmpty()) {
                System.out.println("You are carrying " + inventory);
            }
        }

        @Override
        public void exitEvent(String senderId, String m, String id, String exitJson) {
            System.out.println("Exit succeeded : " + m + " to " + id);
        }
    }	
	
	private static class ActionFingerprint {
		String fingerprint = "";

		public ActionFingerprint(List<Action> actions) {
			for (Action a : actions) {
				fingerprint += ":" + a.uuid.toString();
			}
		}

		@Override
		public int hashCode() {
			final int prime = 31;
			int result = 1;
			result = prime * result + ((fingerprint == null) ? 0 : fingerprint.hashCode());
			return result;
		}

		@Override
		public boolean equals(Object obj) {
			if (this == obj)
				return true;
			if (obj == null)
				return false;
			if (getClass() != obj.getClass())
				return false;
			ActionFingerprint other = (ActionFingerprint) obj;
			if (fingerprint == null) {
				if (other.fingerprint != null)
					return false;
			} else if (!fingerprint.equals(other.fingerprint))
				return false;
			return true;
		}
	}

	private static class CommandHandler {
		String command;
		List<Action> actions = new ArrayList<Action>();

		public CommandHandler(String command) {
			this.command = command;
		}
	}

	public static void verifyRoom(Map<String, Object> globalVars, List<Command> globalCommands, Room room) {
		Map<String, Object> stateById = new HashMap<String, Object>();
		if (globalVars != null) {
			stateById.putAll(globalVars); // not really state, but handy to pretend it is.
		}
		if (room.getState() != null) {
			for (Map.Entry<String, Object> kv : room.getState().entrySet()) {
				stateById.put("room.state." + kv.getKey(), kv.getValue());
			}
		}
		if (room.getItems() != null) {
			for (Item i : room.getItems()) {
				if (i.getState() != null) {
					for (Map.Entry<String, Object> kv : i.getState().entrySet()) {
						stateById.put("items." + i.getName() + "." + kv.getKey(), kv.getValue());
					}
				}
			}
		}
		List<String> failures = new ArrayList<String>();
		if (globalCommands != null) {
			for (Command c : globalCommands) {
				if (c.getActions() != null) {
					for (Action a : c.getActions()) {
						if (a.getCondition() != null) {
							ConditionParser cp = new ConditionParser();
							try {
								cp.evaluate(a.getCondition(), stateById, "x", "x", "x");
							} catch (RuntimeException pe) {
								failures.add("["+a.getCondition() + "] --> " + pe.getMessage());
							}
						}
					}
				}
			}
		}
		if (room.getCommands() != null) {
			for (Command c : room.getCommands()) {
				if (c.getActions() != null) {
					for (Action a : c.getActions()) {
						if (a.getCondition() != null) {
							ConditionParser cp = new ConditionParser();
							try {
								cp.evaluate(a.getCondition(), stateById, "x", "x", "x");
							} catch (RuntimeException pe) {
								failures.add("["+a.getCondition() + "] --> " + pe.getMessage());
							}
						}
					}
				}
			}
		}
		if (room.getItems() != null) {
			for (Item i : room.getItems()) {
				if (i.getCommands() != null) {
					for (Command c : i.getCommands()) {
						if (c.getActions() != null) {
							for (Action a : c.getActions()) {
								if (a.getCondition() != null) {
									ConditionParser cp = new ConditionParser();
									try {
										cp.evaluate(a.getCondition(), stateById, "x", "x", "x");
									} catch (RuntimeException pe) {
										failures.add("["+a.getCondition() + "] --> " + pe.getMessage());
									}
								}
							}
						}
					}
				}
			}
		}
		if(!failures.isEmpty()) {
			System.out.println("Room: ("+room.getId()+") "+room.getName()+" -- Validation pass. FAIL.");
			for(String s: failures) {
				System.out.println("  "+s);
			}
		}else {
			System.out.println("Room: ("+room.getId()+") "+room.getName()+" -- Validation pass. OK.");
		}
	}

	public RoomEngine(Map<String, Object> globalVars, List<Command> globalCommands, Room room) {
		this.globalVars = globalVars;
		this.globalCommands = globalCommands;
		this.room = room;
		this.commandHandlers = new HashMap<String, CommandHandler>();
		this.stateById = new HashMap<String, Object>();

		// figure out which commands we support.
		if (globalCommands != null) {
			for (Command c : globalCommands) {
				// build handler if we've not seen this one yet
				if (!commandHandlers.containsKey(c.getName())) {
					commandHandlers.put(c.getName(), new CommandHandler(c.getName()));
				}
				CommandHandler ch = commandHandlers.get(c.getName());
				// add handler under it's aliases if any are present
				if (c.getAliases() != null) {
					for (String alias : c.getAliases()) {
						if (!commandHandlers.containsKey(alias)) {
							commandHandlers.put(alias, ch);
						}
					}
				}
				// add the actions to the handler
				ch.actions.addAll(c.getActions());
			}
		}
		// add room commands
		if (room.getCommands() != null) {
			for (Command c : room.getCommands()) {
				// build handler if we've not seen this one yet
				if (!commandHandlers.containsKey(c.getName())) {
					commandHandlers.put(c.getName(), new CommandHandler(c.getName()));
				}
				CommandHandler ch = commandHandlers.get(c.getName());
				// add handler under it's aliases if any are present
				if (c.getAliases() != null) {
					for (String alias : c.getAliases()) {
						if (!commandHandlers.containsKey(alias)) {
							commandHandlers.put(alias, ch);
						}
					}
				}
				// add the actions to the handler
				ch.actions.addAll(c.getActions());
			}
		}
		// add the item commands
		if (room.getItems() != null) {
			for (Item i : room.getItems()) {
				for (Command c : i.getCommands()) {
					String fixedName = i.getName().replace(' ', '-');
					String key = c.getName() + ":" + fixedName;
					// build handler if we've not seen this one yet
					if (!commandHandlers.containsKey(key)) {
						commandHandlers.put(key, new CommandHandler(c.getName()));
					}
					CommandHandler ch = commandHandlers.get(key);
					// add handler under it's aliases if any are present
					if (c.getAliases() != null) {
						for (String alias : c.getAliases()) {
							String aliaskey = alias + ":" + fixedName;
							if (!commandHandlers.containsKey(aliaskey)) {
								commandHandlers.put(aliaskey, ch);
							}
						}
					}
					// add the actions to the handler
					if(c.getActions()==null) {
						System.err.println("ERROR ROOM: "+room.getId()+" ITEM: "+i.getName()+" COMMAND: "+c.getName()+" missing actions");
					}else {
						ch.actions.addAll(c.getActions());
					}
				}
				// now add again as the aliases for the item..
				if (i.getAliases() != null) {
					for (String ialias : i.getAliases()) {
						for (Command c : i.getCommands()) {
							String fixedName = ialias.replace(' ', '-');
							String key = c.getName() + ":" + fixedName;
							// build handler if we've not seen this one yet
							if (!commandHandlers.containsKey(key)) {
								commandHandlers.put(key, new CommandHandler(c.getName()));
							}
							CommandHandler ch = commandHandlers.get(key);
							// add handler under it's aliases if any are present
							if (c.getAliases() != null) {
								for (String alias : c.getAliases()) {
									;
									String aliaskey = alias + ":" + fixedName;
									if (!commandHandlers.containsKey(aliaskey)) {
										commandHandlers.put(aliaskey, ch);
									}
								}
							}
							// add the actions to the handler
							ch.actions.addAll(c.getActions());
						}
					}
				}
			}
		}
		// add the state
		if (globalVars != null) {
			stateById.putAll(globalVars); // not really state, but handy to pretend it is.
		}
		if (room.getState() != null) {
			for (Map.Entry<String, Object> kv : room.getState().entrySet()) {
				stateById.put("room.state." + kv.getKey(), kv.getValue());
			}
		}
		if (room.getItems() != null) {
			for (Item i : room.getItems()) {
				if (i.getState() != null) {
					for (Map.Entry<String, Object> kv : i.getState().entrySet()) {
						stateById.put("items." + i.getName() + "." + kv.getKey(), kv.getValue());
					}
				}
			}
		}

	}

	/**
	 * Replace instances of item names with spaces, with item names with hyphens
	 * 
	 * @param input
	 * @return
	 */
	private String fixItemNameSpaces(String input) {
		String out = input;
		if (room.getItems() != null) {
			for (Item i : room.getItems()) {
				if (i.getName().contains(" ")) {
					out = out.replace(i.getName(), i.getName().replace(' ', '-'));
				}
				if (i.getAliases() != null) {
					for (String alias : i.getAliases()) {
						if (alias.contains(" ")) {
							out = out.replace(alias, alias.replace(' ', '-'));
						}
					}
				}
			}
		}
		return out;
	}

	/**
	 * Evaluate a 'condition:' statement against current state.
	 * 
	 * @param condition
	 * @param args
	 * @param playerId
	 * @param playerName
	 * @return
	 */
	public boolean evaluateCondition(String condition, String args, String playerId, String playerName) {
		ConditionParser cp = new ConditionParser();
		return cp.evaluate(condition, stateById, args, playerId, playerName);
	}

	/**
	 * Execute a 'do:' block.
	 * 
	 * @param instructions
	 */
	private void processInstructions(List<String> instructions, String args, String playerId, String playerName) {
		if (instructions != null) {
			for (String i : instructions) {
				i = i.trim();
				if (!i.contains(" ")) {
					System.out.println("ERROR: all instructions in do block are of form \"instruction arg\"");
				} else {
					String parts[] = i.split(" ");
					if ("set".equals(parts[0])) {
						parts = i.substring(4).split("=");
						if (!stateById.containsKey(parts[0].trim())) {
							System.out.println("ERROR: set must refer to existing var");
						} else {
							String fixed = substituteVarsInOutput(parts[1].trim(), args, playerId, playerName);
							stateById.put(parts[0].trim(), fixed);
						}
					} else if ("teleportAll".equals(parts[0])) {
						System.out.println("TELEPORT: '" + parts[1] + "'");
					}
				}
			}
		}
	}

	/**
	 * Process a string going back to the user, and inject any vars or state values
	 * requested.
	 * 
	 * @param output
	 * @param args
	 * @param playerId
	 * @param playerName
	 * @return
	 */
	private String substituteVarsInOutput(String output, String args, String playerId, String playerName) {
		if (!output.contains("{")) {
			return output;
		} else {
			String fixed = output;
			// sub in state/variable references
			for (Map.Entry<String, Object> kv : stateById.entrySet()) {
				fixed = fixed.replace("{" + kv.getKey() + "}", kv.getValue().toString());
			}
			fixed = fixed.replace("{arg}", args);
			fixed = fixed.replace("{id}", playerId);
			fixed = fixed.replace("{name}", playerName);
			fixed = fixed.replace("\\n", "\n");
			return fixed;
		}
	}

	/**
	 * Process a recognised command, matching valid actions, and choosing one
	 * appropriately.
	 * 
	 * @param ch
	 * @param parts
	 * @param args
	 * @param playerId
	 * @param playerName
	 */
	private void processCommand(CommandHandler ch, String[] parts, String args, String playerId, String playerName) {
		// identify potential actions..
		List<Action> actions = new ArrayList<Action>();
		List<Action> unmatched = new ArrayList<Action>();
		if (ch.actions != null) {
			for (Action a : ch.actions) {
				// if there's no condition, or it's empty string, it's auto approved.
				if (a.getCondition() == null || a.getCondition().trim().equals("")) {
					actions.add(a);
				} else {
					// process condition
					if (a.getCondition().trim().equals("unmatched")) {
						unmatched.add(a);
					} else {
						// implement condition logic ;)
						if (evaluateCondition(a.getCondition(), args, playerId, playerName)) {
							actions.add(a);
						}
					}
				}
			}
			// only if we didn't match anything specific will we resort to the 'unmatched'
			// fallbacks.
			if (actions.size() == 0) {
				actions.addAll(unmatched);
			}
		}
		// still nothing? that means there were no fallbacks for this command, and we
		// have no matching
		// actions to take.. this is usually an error, we always want SOMETHING to send
		// back to the user.
		if (actions.size() == 0) {
			System.out.println("ERROR: no matching actions?? utoh");
			System.out.println("command: " + ch.command);
			System.out.println("ch.actions.length: " + ch.actions.size());
			if (ch.actions != null && ch.actions.size() > 0) {
				for (Action a : ch.actions) {
					String c = a.getCondition();
					if (c == null)
						c = "No Condition Required";
					System.out.println(" - " + c);
				}
			}
			System.out.println("{arg}==" + args);
			return;
		}

		// if there are multiple actions, we need to rotate through them,
		// but the action set may change if state does, so lets remember where
		// we were on a per set basis. hacky.. but functional.
		ActionFingerprint af = new ActionFingerprint(actions);
		if (!actionMap.containsKey(af)) {
			actionMap.put(af, 0);
		}
		Integer i = actionMap.get(new ActionFingerprint(actions));

		// have we advanced past the end of this set?
		if (i > (actions.size() - 1)) {
			i = 0;
		}

		// select the indicated action
		Action chosen = actions.get(i);

		// bump the choice, and store back into the map for next time.
		i++;
		actionMap.put(af, i);

		String userOut = null;
		String roomOut = null;
		
		// any user bound messages?
		String user = chosen.getUser();
		if (user != null) {
			user = substituteVarsInOutput(user, args, playerId, playerName);
			userOut = user;
			System.out.println("USER: " + user);
		}
		// any room bound messages?
		String room = chosen.getRoom();
		if (room != null) {
			room = substituteVarsInOutput(room, args, playerId, playerName);
			roomOut = room;
			System.out.println("ROOM: " + room);
		}
		
		rrp.playerEvent(playerId, userOut, roomOut);
		
		// execute any do instructions (after issuing messages, in case the instruction
		// is a 'leave room'
		processInstructions(chosen.getDo(), args, playerId, playerName);

	}
	
	public void addUserToRoom(String userid, String username) {
		
	}
	public void removeUserFromRoom(String userid) {
		
	}
	public void command(String userid, String command) {
		this.processRoomInput("/"+command, userid, "todo");
	}
	public void setRoomResponseProcessor(RoomResponseProcessor rrp) {
		this.rrp=rrp;
	}

	/**
	 * Accept input from a user, process it appropriately
	 * 
	 * TODO: non sys-out output handling =)
	 * 
	 * @param roomInput
	 * @param playerId
	 * @param playerName
	 */
	public void processRoomInput(String roomInput, String playerId, String playerName) {
		if (!roomInput.startsWith("/")) {
			System.out.println("SAY: " + roomInput);
		} else {
			// swap item names with spaces for item names with hyphens
			String fixedInput = fixItemNameSpaces(roomInput);
			// remove /, drop to lowercase
			fixedInput = fixedInput.substring(1).toLowerCase();
			// split by space
			String parts[] = fixedInput.split(" ");

			// do we know this command?
			CommandHandler ch = null;
			String args = "";
			if (parts.length == 1) {
				// no args means we can just look for the command
				ch = commandHandlers.get(parts[0]);
			} else {
				// if we have an arg, see if we have a handler registered for the item/command
				// combo.
				ch = commandHandlers.get(parts[0] + ":" + parts[1]);
				if (ch != null) {
					// if we knew the item, we remove it from the args string, to make room logic
					// simpler.
					args = fixedInput.substring(fixedInput.indexOf(' ') + 1);
				} else {
					// if no item/command combos matched, fall back to seeing if any command
					// handlers exist.
					ch = commandHandlers.get(parts[0]);
					args = fixedInput.substring(fixedInput.indexOf(' ') + 1);
				}
			}
			// did we find something to use?
			if (ch == null) {
				System.out.println("USER: I'm sorry, I don't understand '" + roomInput + "'");
			} else {
				// yes! send the input to the handler.
				processCommand(ch, parts, args, playerId, playerName);
			}

		}
	}
}
