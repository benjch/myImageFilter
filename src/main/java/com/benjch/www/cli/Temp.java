package com.benjch.www.cli;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Temp {

    private static final Logger logger = LoggerFactory.getLogger(Temp.class);

    public static void main(String[] args) {
        for (int i = 0; i < 100; i++) {
            logger.info("toto");
        }
    }
}
