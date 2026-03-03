package com.benjch.www.command;

import java.io.File;

import org.kohsuke.args4j.Option;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.benjch.www.cli.Command;

public class CommandIsWorking implements Command {

    private static final Logger LOGGER = LoggerFactory.getLogger(CommandIsWorking.class);

    @Option(name = "-fileConfig", metaVar = "FILE", usage = "properties file to configure CLI", required = false)
    private File fileConfig;

    @Override
    public void execute() {
        if (fileConfig != null) {
            LOGGER.info("command is working, fileConfig : {}", fileConfig.getName());
        }
    }

    @Override
    public String getName() {
        return "commandIsWorking";
    }
}
