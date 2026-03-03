package com.benjch.www.cli;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.kohsuke.args4j.CmdLineParser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.benjch.www.utils.UtilsIntrospection;

@SuppressWarnings("unchecked")
public class Main {
    private static final Logger LOGGER = LoggerFactory.getLogger(Main.class);

    final static Map<String, Class<? extends Command>> mapCommandByName = new HashMap<String, Class<? extends Command>>();

    static {
        try {
            String packageName = "com.benjch.www.command";
            List<Class<?>> classes = UtilsIntrospection.scanPackage(packageName);
            for (@SuppressWarnings("rawtypes")
            Class clazz : classes) {
                if (Command.class.isAssignableFrom(clazz) && !clazz.getName().equals(Command.class.getName())) {
                    @SuppressWarnings("deprecation")
                    Command command = (Command) Class.forName(clazz.getName()).newInstance();
                    mapCommandByName.put(command.getName(), clazz);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        {
            if (args.length < 1) {
                usage("Command name required !");
                System.exit(1);
            }

            String commandName = args[0];
            Class<? extends Command> commandClass = mapCommandByName.get(commandName);
            if (commandClass == null) {
                usage("No such command: " + commandName);
                System.exit(1);
            }

            String[] arguments = new String[args.length - 1];
            System.arraycopy(args, 1, arguments, 0, arguments.length);

            try {
                LOGGER.info("Command " + commandName + " start!");
                long nanoTimeStart = System.nanoTime();
                @SuppressWarnings("deprecation")
                Command command = (Command) Class.forName(commandClass.getName()).newInstance();
                if (args.length > 0) {
                    new CmdLineParser(command).parseArgument(arguments);
                }
                command.execute();
                long l = System.nanoTime() - nanoTimeStart;
                LOGGER.info("Command '" + commandName + "' finished : " + l / 1000000000D + " secs");
            } catch (Exception e) {
                LOGGER.error(e.getMessage(), e);
                e.printStackTrace();
                System.exit(2);
            }
        }

    }

    @SuppressWarnings("deprecation")
    private static void usage(String msg) {
        LOGGER.info(msg);
        LOGGER.info("Command list:");
        for (Class<? extends Command> class1 : mapCommandByName.values()) {
            try {
                Command command = (Command) Class.forName(class1.getName()).newInstance();
                CmdLineParser cmdLineParser = new CmdLineParser(command);
                final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                cmdLineParser.printUsage(byteArrayOutputStream);
                LOGGER.info("COMMANDS :\r\n" + command.getName() + "\r\n " + new String(byteArrayOutputStream.toByteArray(), StandardCharsets.UTF_8));
            } catch (Exception e) {
                e.printStackTrace();
            }

        }
    }
}
