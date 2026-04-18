package com.diamondfinding;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class MbdmCommand implements CommandExecutor, TabCompleter {

    private final DiamondCommands cmds;

    private static final List<String> SUBCOMMANDS = Arrays.asList(
            "add", "remove", "scan", "surround", "reset", "setgoal", "status", "item",
            "hit3", "drop", "die", "dragon", "warden", "golem", "time", "lava", "tnt"
    );

    public MbdmCommand(DiamondCommands cmds) {
        this.cmds = cmds;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§b[DiamondFinding] §7Available subcommands:");
            sender.sendMessage("§e /mbdm add <amount> §7- Add diamonds to players");
            sender.sendMessage("§e /mbdm remove <amount> §7- Remove diamonds from players");
            sender.sendMessage("§e /mbdm scan [seconds] §7- Highlight diamond ores nearby");
            sender.sendMessage("§e /mbdm surround [seconds] §7- Turn blocks to diamonds temporarily");
            sender.sendMessage("§e /mbdm setgoal <amount> §7- Set diamond goal");
            sender.sendMessage("§e /mbdm reset §7- Reset all diamonds to 0");
            sender.sendMessage("§e /mbdm status §7- Show current progress");
            sender.sendMessage("§e /mbdm item §7- Give starter items (skips already owned)");
            sender.sendMessage("§e /mbdm hit3 <on|off> §7- Toggle 3x3 mining");
            sender.sendMessage("§e /mbdm drop <on|off> §7- Toggle auto-pickup diamond");
            sender.sendMessage("§e /mbdm die <number> §7- Set death diamond penalty");
            sender.sendMessage("§e /mbdm dragon §7- Ender Dragon explosion (-3000◆)");
            sender.sendMessage("§e /mbdm warden §7- Spawn Warden (-1000◆)");
            sender.sendMessage("§e /mbdm golem §7- Iron Golem aura (+1000◆)");
            sender.sendMessage("§e /mbdm time <seconds> §7- Set countdown duration");
            sender.sendMessage("§e /mbdm lava <number> §7- Spawn lava blocks around players");
            sender.sendMessage("§e /mbdm tnt <number> §7- Spawn primed TNT around players");
            return true;
        }

        String sub = args[0].toLowerCase();
        String[] subArgs = Arrays.copyOfRange(args, 1, args.length);

        return switch (sub) {
            case "add" -> cmds.add(sender, subArgs);
            case "remove" -> cmds.remove(sender, subArgs);
            case "scan" -> cmds.scan(sender, subArgs);
            case "surround" -> cmds.surround(sender, subArgs);
            case "reset" -> cmds.reset(sender);
            case "setgoal" -> cmds.setGoal(sender, subArgs);
            case "status" -> cmds.status(sender);
            case "item" -> cmds.item(sender);
            case "hit3" -> cmds.hit3(sender, subArgs);
            case "drop" -> cmds.drop(sender, subArgs);
            case "die" -> cmds.die(sender, subArgs);
            case "dragon" -> cmds.dragon(sender);
            case "warden" -> cmds.warden(sender);
            case "golem" -> cmds.golem(sender);
            case "time" -> cmds.time(sender, subArgs);
            case "lava" -> cmds.lava(sender, subArgs);
            case "tnt" -> cmds.tnt(sender, subArgs);
            default -> {
                sender.sendMessage("§c[DiamondFinding] Unknown subcommand: " + sub);
                yield true;
            }
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return SUBCOMMANDS.stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return new ArrayList<>();
    }
}
