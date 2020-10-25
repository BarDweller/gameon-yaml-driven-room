# Game On! YAML Driven Room Implementation

This is a room for Game On! (gameontext.org) that uses Yaml to define its room content. 

## Building

To build this project: 

    ./gradlew build
    docker build -t gameontext/yaml-room room-wlpcfg

## Notes

This room 'groups' users by some property, currently fb&twitter in one group, and everyone else in another. 

The visitors to the room are split by group, so only members of the same group can talk to each other, see each other etc.

The room is best explained as being like a Holodeck. The Holodeck is only 1 room, with 1 real door, but when you enter, there
can be many rooms inside, and the room you experience changes as you "move" around inside the Holodeck. 

This Game On room takes the same approach, allowing you to define an entire world full of rooms using Yaml, will be experienced
by players when they enter. All the time, the player will actually be stationary in the Game On map, as it is the room description
that changes around them, rather than them changing location. 

When groups are used, the entire group experiences the room change if any player triggers something causing a move. This enables
a group to share a yaml defined adventure experience. 

### TODO:
- externalise yaml url / mapping of yaml url -> group id / map key|secret
- add reset instruction
- 

## YAML Format
The yaml-room input file provides for defining a set of rooms, along with associated state, and commands. 


### [Root of yaml document]

#### vars:
A map for global vars that will be substituted into output sent to the player, and can also be used to define item names.
In addition to the global vars the following state related vars are auto populated for any request
- `{id}` unique id for user that executed a command (eg, `twitter:58222240`)
- `{name}` the character name chosen by the player executing the command (eg, `Fred`)
- `{arg}` the argument(s) to a command,
- eg 
 - `/use fish` results in `{arg}==fish`, 
 - `/use fish with donut` results in `{arg}==with donut`

#### commanddescriptions:
A map of command names to descriptions to be given when a player uses /help

#### commands:
A list of [COMMAND yaml object](#command-yaml-object) , these commands are active for all rooms. This is a handy way to provide "default" actions for commands

#### rooms:
A list of [ROOM yaml object](#room-yaml-object) , that defines the rooms. The first declared room is assumed to be the entrypoint for new guests.

---

### ROOM yaml object
This represents a room declaration to the yaml room engine. The room declaration includes the room id, needed for moving from room 
to room via `teleportAll` (see [ACTION] yaml object.), a name for display via Game On, exit descriptions for Game On, and can define 
commands (eg `/look`, `/examine` that will only work while that room is active) and items (that will allow commands, eg `/use thing`, `/examine thing` etc). 

#### name: 
The friendly name for the room, to be displayed in Game On
#### id: 
The **unique** id for this room in this yaml.
#### state: 
Like `vars:` except for this room, allowing declaration of vars for use in output, or state conditional logic. eg, 

      lightOn: false
      doorLock: locked
      
#### exits: 
A map of directions to exit descriptions, to be used by Game On for the /exits command, empty directions should be set to "", eg.

       S: The way you came in, it will not open.
       N: There appears to be a door here. 
       E: ""
       W: ""
       
#### commands:
A list of [COMMAND yaml object](#command-yaml-object), providing commands that are local to this room. 
#### items:
A lits of [ITEM yaml object](#item-yaml-object), defining the items (and item commands) for this room.

----

### ITEM yaml object
#### name:
The name for this item in the room (can contain spaces), eg 'stilettos' 
#### aliases:
A list of alternate names the item can be referred to as. eg.

     - shoes
     - heels
     - high heels
     
#### state:
Like `vars:` (or `state:` in the [ROOM] object), this can define a map of state for this item. eg,

      buckled: false
      broken: false
      wornBy: nobody

#### commands:
A list of [COMMAND yaml object](#command-yaml-object) for this item. These commands will trigger only when a player does
`/command itemname` where command is the defined command, and itemname is the name, or alias for this item.


----

### COMMAND yaml object
This defines a command to the yaml room, telling the room the name(s) to expect the command to be invoked by 
(eg, a command with a `name:` of  `examine` would be invoked in game as `/examine`). Each command defines
a list of [ACTION yaml object](#action-yaml-object), that represent possible processing the room will execute as a result of the 
command being invoked. 

See [ACTION yaml object](#action-yaml-object) for more information.
 
#### name: 
the name of the command, eg examine
#### aliases: 
a list of aliases for the command, eg 

     - look 
     - peek
 
#### actions: 
a list of [ACTION yaml object](#action-yaml-object) for the command. 


-----

### ACTION yaml object
This defines an 'action' which is something the room engine will do in response to a command. 

Multiple actions can be declared for any given command, and are evaluated to see if they are 'selectable'.

Actions considered selectable are added to a list, and used to reply to the user in a round robin fashion, eg, if you 
define 3 actions without any condition (so they are always selectable), then the room will use the first when the command is
used the first time, the 2nd for the 2nd, and so on, wrapping back to the 1st on the 4th usage of the command. This round robin index
is for  __the room__  not per player. So if there were 3 people in the room in the previous example, each would have seen a different 
action being used.  

Because actions can change state, and state can affect actions being selectable, the round robin index is "per set of actions", so 
if a selected action modifies state in a way that alters the selectable set of actions for a given command, a new index will be used for 
the new set of actions. Should the set revert to it's previous members via a subsequent modification, the index will resume for the previous
set. (see also: `/ydebug actionmap` to debug how many sets are being tracked)

#### condition: (optional)
Defines a condition that must be true for this action to be considered as selectable. 

If no condition is specified, the action is always selectable.  

Conditions can refer to global vars, these must use `{varname}` syntax. eg, `{id}=="fred"` 

Conditions can refer to room state, prefix the var name with `room.state` eg, `room.state.lightOn==true`

Conditions can refer to item state, prefix the var name with `items.itemname.state` eg, `items.stilettos.wornBy=={id}`
Always use the `name:` of the item when referring to state not an `alias:`

Conditions can use `==` and `!=` for comparisons. All comparisons are actually string comparisons. There is no typed data. 

Var names / state is substituted into the expression, and then the expression is compared. eg, `{id}==fred` becomes `john==fred` which would fail.

Conditions can be combined with && and || there is no support for () .. expression aggregation is left to right, eg, `A==1 && B==2 || C==3` becomes `(A==1 && B==2) || C==3`,  and `A==1 && B==2 || C==3 && D==4` becomes `((A==1 && B==2) || C==3) && D==4`

A special condition value `unmatched` can be used to say 'use this action, when no other actions matched'. `unmatched` cannot be combined with other expressions.
`unmatched` actions will  __ONLY__  be considered as selectable if no other actions matched. There can be multiple actions declared with 'unmatched', round robin rules apply as normal.

(see also: `/ydebug state` to view state at runtime) 

#### user: (optional)
Text to send back to the user who initiated the command. 
#### room: (optional)
Text to send back to everyone  _other than_  the user who initiated the command. 
#### do: (optional)
A list of 'instructions' for the room to execute if this action is selected. If an action has both user/room output AND a do, the user/room output is always sent **before** the instructions are executed.

Valid instructions include. 

      set varreference=value
      teleportAll roomid
      
`set` will update a state value to the supplied argument, the `varreference` format is the same as used in `condition:` expressions.
(see also: `/ydebug state` to view state at runtime)

`teleportAll` will switch the room around all the players in the group, to be based from the [ROOM] yaml object with the matching id.
  