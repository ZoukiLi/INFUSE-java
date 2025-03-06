package com.CC.Util;


import org.slf4j.LoggerFactory;

import ch.qos.logback.classic.Logger;

public interface Loggable {
    Logger logger = (Logger) LoggerFactory.getLogger(Loggable.class);
}
