package com.cleanroommc.groovyscript.sandbox;

import com.cleanroommc.groovyscript.GroovyScript;
import com.cleanroommc.groovyscript.api.GroovyLog;
import net.minecraftforge.fml.common.FMLCommonHandler;
import net.minecraftforge.fml.common.Loader;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.intellij.lang.annotations.Flow;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.kohsuke.groovy.sandbox.impl.Checker;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Date;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class GroovyLogImpl implements GroovyLog {

    public static final GroovyLogImpl LOG = new GroovyLogImpl();

    private static final Logger logger = LogManager.getLogger("GroovyLog");
    private final Path logFilePath;
    private final PrintWriter printWriter;
    private final DateFormat timeFormat = new SimpleDateFormat("[HH:mm:ss]");

    private GroovyLogImpl() {
        File logFile = new File(Loader.instance().getConfigDir().toPath().getParent().toString() + File.separator + "groovy.log");
        logFilePath = logFile.toPath();
        PrintWriter tempWriter;
        try {
            // delete file if it exists
            if (logFile.exists() && !logFile.isDirectory()) {
                Files.delete(logFilePath);
            }
            // create file
            Files.createFile(logFilePath);
            // create writer which automatically flushes on write
            tempWriter = new PrintWriter(new BufferedWriter(new OutputStreamWriter(new FileOutputStream(logFile))), true);
        } catch (IOException e) {
            e.printStackTrace();
            tempWriter = new PrintWriter(System.out);
        }
        this.printWriter = tempWriter;
        DateFormat dateFormat = new SimpleDateFormat("dd.MM.yyyy");
        writeLogLine("============  GroovyLog  ====  " + dateFormat.format(new Date()) + "  ============");
        writeLogLine("GroovyScript version: " + GroovyScript.VERSION);
    }

    @Override
    public boolean isDebug() {
        return GroovyScript.getRunConfig().isDebug();
    }

    @Override
    public PrintWriter getWriter() {
        return printWriter;
    }

    @Override
    public Path getLogFilerPath() {
        return logFilePath;
    }

    @Override
    public void log(GroovyLog.Msg msg) {
        if (msg.getLevel() == Level.OFF) return;
        if (msg.getLevel() == Level.DEBUG && !isDebug()) return;
        String level = msg.getLevel().name();
        String main = msg.getMainMsg();
        List<String> messages = msg.getSubMessages();
        if (messages.isEmpty()) {
            // has no sub messages -> log in a single line
            writeLogLine(formatLine(level, main));
            if (msg.shouldLogToMc()) {
                logger.log(msg.getLevel(), main + " in line " + Checker.getLineNumber());
            }
        } else if (messages.size() == 1 && main.length() + messages.get(0).length() < 100) {
            // has one sub message and the main message and the sub message have less than 100 characters ->
            // log in a single line
            writeLogLine(formatLine(level, main + ": - " + messages.get(0)));
            if (msg.shouldLogToMc()) {
                logger.log(msg.getLevel(), main + ": - " + messages.get(0) + "  in line " + Checker.getLineNumber());
            }
        } else {
            // has multiple log lines or the main message and the first sub message are to long ->
            // log each sub message in a single line, starting with the main message
            writeLogLine(formatLine(level, main + ": "));
            for (int i = 1; i < messages.size(); i++) {
                writeLogLine(formatLine(level, " - " + messages.get(i)));
            }
            if (msg.shouldLogToMc()) {
                logger.log(msg.getLevel(), main + " in line " + Checker.getLineNumber() + " : - ");
                for (int i = 1; i < messages.size(); i++) {
                    logger.log(msg.getLevel(), " - " + messages.get(i));
                }
            }
        }
        Throwable throwable = msg.getException();
        if (throwable != null) {
            exception(throwable);
        }
    }

    public Path getPath() {
        return logFilePath;
    }

    /**
     * Logs a info msg to the groovy log AND Minecraft's log
     *
     * @param msg  message
     * @param args arguments
     */
    public void infoMC(String msg, Object... args) {
        info(msg, args);
        logger.info(msg, args);
    }

    /**
     * Logs a info msg to the groovy log
     *
     * @param msg  message
     * @param args arguments
     */
    public void info(String msg, Object... args) {
        writeLogLine(formatLine("INFO", GroovyLog.format(msg, args)));
    }

    /**
     * Logs a debug msg to the groovy log AND Minecraft's log
     *
     * @param msg  message
     * @param args arguments
     */
    public void debugMC(String msg, Object... args) {
        if (isDebug()) {
            debug(msg, args);
            logger.info(msg, args);
        }
    }

    /**
     * Logs a debug msg to the groovy log
     *
     * @param msg  message
     * @param args arguments
     */
    public void debug(String msg, Object... args) {
        if (isDebug()) {
            writeLogLine(formatLine("DEBUG", GroovyLog.format(msg, args)));
        }
    }

    /**
     * Logs a warn msg to the groovy log AND Minecraft's log
     *
     * @param msg  message
     * @param args arguments
     */
    public void warnMC(String msg, Object... args) {
        warn(msg, args);
        logger.warn(msg, args);
    }

    @Override
    public void fatal(String msg, Object... args) {
        writeLogLine(formatLine("FATAL", GroovyLog.format(msg, args)));
    }

    @Override
    public void fatalMC(String msg, Object... args) {
        fatal(msg, args);
        logger.fatal(msg, args);
    }

    /**
     * Logs a warn msg to the groovy log
     *
     * @param msg  message
     * @param args arguments
     */
    public void warn(String msg, Object... args) {
        writeLogLine(formatLine("WARN", GroovyLog.format(msg, args)));
    }

    /**
     * Logs a error msg to the groovy log AND Minecraft's log
     *
     * @param msg  message
     * @param args arguments
     */
    public void error(String msg, Object... args) {
        writeLogLine(formatLine("ERROR", GroovyLog.format(msg, args)));
    }

    @Override
    public void errorMC(String msg, Object... args) {
        error(msg, args);
        logger.error(msg, args);
    }

    /**
     * Logs an exception to the groovy log AND Minecraft's log.
     * It does NOT throw the exception!
     * The stacktrace for the groovy log will be stripped for better readability.
     *
     * @param throwable exception
     */
    public void exception(Throwable throwable) {
        writeLogLine(formatLine("ERROR", "An exception occurred while running scripts. Look at latest.log for a full stacktrace:"));
        writeLogLine("\t" + throwable.toString());
        throwable.printStackTrace();
        for (String line : prepareStackTrace(throwable.getStackTrace())) {
            writeLogLine("\t\tat " + line);
        }
    }

    private List<String> prepareStackTrace(StackTraceElement[] stackTrace) {
        // TODO figure out what can be stripped out since those stacktrace get pretty large
        List<String> lines = Arrays.stream(stackTrace).map(StackTraceElement::toString).collect(Collectors.toList());
        String engineCause = "groovy.util.GroovyScriptEngine.run";
        int i = 0;
        for (String line : lines) {
            i++;
            if (line.startsWith(engineCause)) {
                break;
            }
        }
        if (i > 0 && i <= lines.size()) {
            lines = lines.subList(0, i);
        }
        String groovyInternal = "org.codehaus.groovy.runtime";
        String groovySandbox = "org.kohsuke";
        lines.removeIf(s -> s.startsWith(groovyInternal) || s.startsWith(groovySandbox));
        return lines;
    }

    private String formatLine(String level, String msg) {
        return timeFormat.format(new Date()) +
                (FMLCommonHandler.instance().getEffectiveSide().isClient() ? " [CLIENT/" : " [SERVER/") +
                level + "]" +
                " [" + getSource() + "]: " +
                msg;
    }

    private String getSource() {
        String source = Checker.getSource();
        if (source == Checker.UNKNOWN_SOURCE) {
            return Loader.instance().activeModContainer().getModId();
        }
        return GroovyScriptSandbox.relativizeSource(source) + ":" + Checker.getLineNumber();
    }

    private void writeLogLine(String line) {
        this.printWriter.println(line);
    }

    public static GroovyLog.Msg msg(String msg, Object... data) {
        return new MsgImpl(msg, data);
    }

    public static class MsgImpl implements GroovyLog.Msg {

        private final String mainMsg;
        private final List<String> messages = new ArrayList<>();
        private Level level = Level.INFO;
        private boolean logToMcLog = false;
        @Nullable
        private Throwable throwable;

        private MsgImpl(String msg, Object... data) {
            this.mainMsg = GroovyLog.format(msg, data);
        }

        @Flow(source = "this.level")
        public boolean isValid() {
            return level != null;
        }

        public Msg add(String msg, Object... data) {
            this.messages.add(GroovyLog.format(msg, data));
            return this;
        }

        @Override
        public Msg add(boolean condition, String msg, Object... args) {
            if (condition) {
                return add(msg, args);
            }
            return this;
        }

        public Msg add(boolean condition, Supplier<String> msg) {
            if (condition) {
                return add(msg.get());
            }
            return this;
        }

        @Override
        public Msg add(boolean condition, Consumer<Msg> msgBuilder) {
            if (condition) {
                msgBuilder.accept(this);
            }
            return this;
        }

        @Override
        public Msg exception(Throwable throwable) {
            this.throwable = throwable;
            return this;
        }

        private MsgImpl level(Level level) {
            this.level = level;
            return this;
        }

        public Msg info() {
            return level(Level.ERROR);
        }

        public Msg debug() {
            return level(Level.DEBUG);
        }

        public Msg warn() {
            return level(Level.WARN);
        }

        public Msg fatal() {
            return level(Level.FATAL);
        }

        public Msg error() {
            return level(Level.ERROR);
        }

        @Override
        public Msg logToMc(boolean logToMC) {
            this.logToMcLog = logToMC;
            return this;
        }

        @Override
        public @NotNull String getMainMsg() {
            return mainMsg;
        }

        @Override
        public @NotNull List<String> getSubMessages() {
            return messages;
        }

        @Override
        public @Nullable Throwable getException() {
            return throwable;
        }

        @Override
        public Level getLevel() {
            return level;
        }

        @Override
        public boolean shouldLogToMc() {
            return logToMcLog;
        }

        public boolean hasMessages() {
            return !this.messages.isEmpty();
        }
    }
}
