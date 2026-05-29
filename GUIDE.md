# Pinpoint

A craftable waypoint and teleportation system. Place blocks to mark your locations and travel between them instantly.

---

## The Two Items

**Pinpoint Block** — place it anywhere to mark that spot as a Pinpoint.

**Pinpoint Compass** — opens your list of accessible Pinpoints so you can travel.

---

## Crafting

**Pinpoint Block**
```
Q Q Q
Q E Q     Q = Quartz  |  E = Eye of Ender
Q Q Q
```

**Pinpoint Compass**
```
. E .
. C .     E = Eye of Ender  |  C = Compass
. . .
```

---

## Creating a Pinpoint

1. Craft a **Pinpoint Block**
2. Place it anywhere
3. Type a **name** in chat when prompted

That's it — the block is now your Pinpoint. Right-click it to manage it or teleport to it.

---

## Traveling

**Option 1:** Right-click your **Pinpoint Compass** to open the hub, then click any destination.

**Option 2:** Right-click any Pinpoint block you have access to.

After selecting a destination, stand still while the countdown runs. Moving or taking damage cancels it.

---

## Managing Your Pinpoint

Right-click your Pinpoint block (or click the compass icon in the hub) to open the manage menu. From there:

- **Teleport** to it
- **Toggle public/private** — public Pinpoints appear for everyone
- **Set a fee** — players pay to teleport there, you receive the money
- **Invite players** by name (for private Pinpoints)
- **Rename** it
- **Change its icon**
- **Delete** it

---

## Public vs Private

| Type | Who can see it |
|---|---|
| Public | Everyone on the server |
| Private | Only you and players you've invited |

To invite someone: open the Pinpoint manage menu and type their name.

---

## Fees

If you set a fee on your Pinpoint, other players pay that amount when they teleport there. The money goes directly to you, even if you're offline.

Fee of 0 = free for anyone (if the Pinpoint is public).

---

## Party System

Link up with a friend so you can travel together.

1. Hold your **Pinpoint Compass** in your main hand
2. **Shift+right-click** another player to send them a link request
3. They accept in the pop-up (or type `/party accept`)

Once linked, when you teleport to a Pinpoint your party members get a notification and can **follow with one click**.

To leave your party: `/party leave`

---

## Commands

| Command | What it does |
|---|---|
| `/wp` | Open your Pinpoint hub |
| `/wp list` | List all Pinpoints you can access |
| `/wp accept` | Accept a teleport invite |
| `/wp deny` | Deny a teleport invite |
| `/party` | Open party management |
| `/party leave` | Leave your party |
| `/party follow` | Follow your party's last travel |

---

## Tips

- You can have **multiple Pinpoints** in different worlds
- Pinpoints **survive server restarts** — they're permanent
- The hub remembers your page when you go into a manage menu and come back
- Bedrock players: all actions work through GUIs and right-clicks — no chat commands needed
