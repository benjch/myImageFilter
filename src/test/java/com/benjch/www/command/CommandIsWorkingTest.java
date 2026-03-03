package com.benjch.www.command;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.io.File;
import java.lang.reflect.Field;

import org.junit.jupiter.api.Test;

class CommandIsWorkingTest {

    @Test
    void givenNullFileConfig_whenExecute_thenNoExceptionThrown() {
        CommandIsWorking commandIsWorking = new CommandIsWorking();
        assertDoesNotThrow(commandIsWorking::execute);
    }

    @Test
    void givenValidFileConfig_whenExecute_thenNoExceptionThrown() throws Exception {
        CommandIsWorking commandIsWorking = new CommandIsWorking();
        File fileConfig = new File("config.properties");

        setFileConfig(commandIsWorking, fileConfig);

        assertDoesNotThrow(commandIsWorking::execute);
    }

    @Test
    void givenExceptionFileConfig_whenExecute_thenNoExceptionThrown_dueToImplementation() throws Exception {
        CommandIsWorking commandIsWorking = new CommandIsWorking();
        File fileConfig = new File("exception");

        setFileConfig(commandIsWorking, fileConfig);

        assertDoesNotThrow(commandIsWorking::execute);
    }

    @Test
    void whenGetName_thenReturnExpectedCommandName() {
        CommandIsWorking commandIsWorking = new CommandIsWorking();

        String name = commandIsWorking.getName();

        assertEquals("commandIsWorking", name);
    }

    private void setFileConfig(CommandIsWorking commandIsWorking, File fileConfig) throws Exception {
        Field fileConfigField = CommandIsWorking.class.getDeclaredField("fileConfig");
        fileConfigField.setAccessible(true);
        fileConfigField.set(commandIsWorking, fileConfig);
    }
}
