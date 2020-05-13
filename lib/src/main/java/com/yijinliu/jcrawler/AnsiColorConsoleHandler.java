package com.yijinliu.jcrawler;

import java.util.logging.ConsoleHandler;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

public class AnsiColorConsoleHandler extends ConsoleHandler {
    public static void replaceDefault() {
        Logger logger = Logger.getLogger("");
        for (Handler handler : logger.getHandlers()) {
            if (handler instanceof ConsoleHandler) {
                logger.removeHandler(handler);
                logger.addHandler(new AnsiColorConsoleHandler());
                break;
            }
        }
    }

    @Override
    public void publish(LogRecord record) {
        System.err.print(logRecordToString(record));
        System.err.flush();
    }

    public String logRecordToString(LogRecord record) {
        Formatter f = getFormatter();
        String msg = f.format(record);
        Level level = record.getLevel();
        if (level == Level.SEVERE) {
            return "\u001b[91m" + msg + "\u001b[0m";
        } else if (level == Level.WARNING) { 
            return "\u001b[93m" + msg + "\u001b[0m";
        } else if (level == Level.FINE) {
            return "\u001b[36m" + msg + "\u001b[0m";
        } else if (level == Level.FINER) {
            return "\u001b[90m" + msg + "\u001b[0m";
        } else if (level == Level.FINEST) {
            return "\u001b[37m" + msg + "\u001b[0m";
        }
        return msg;
    }
}
