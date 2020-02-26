//
// ========================================================================
// Copyright (c) 1995-2020 Mort Bay Consulting Pty Ltd and others.
//
// This program and the accompanying materials are made available under
// the terms of the Eclipse Public License 2.0 which is available at
// https://www.eclipse.org/legal/epl-2.0
//
// This Source Code may also be made available under the following
// Secondary Licenses when the conditions for such availability set
// forth in the Eclipse Public License, v. 2.0 are satisfied:
// the Apache License v2.0 which is available at
// https://www.apache.org/licenses/LICENSE-2.0
//
// SPDX-License-Identifier: EPL-2.0 OR Apache-2.0
// ========================================================================
//

package org.eclipse.jetty.logging;

import java.util.Objects;

import org.slf4j.Logger;
import org.slf4j.Marker;
import org.slf4j.event.Level;
import org.slf4j.event.LoggingEvent;
import org.slf4j.helpers.SubstituteLogger;
import org.slf4j.spi.LocationAwareLogger;

public class JettyLogger implements LocationAwareLogger, Logger
{
    /**
     * The Level to set if you want this logger to be "OFF"
     */
    public static final int OFF = 999;
    /**
     * The Level to set if you want this logger to show all events from all levels.
     */
    public static final int ALL = -1;

    private final JettyLoggerFactory factory;
    private final String name;
    private final String condensedName;
    private final JettyAppender appender;
    private int level;
    private boolean hideStacks = false;

    public JettyLogger(JettyLoggerFactory factory, String name, JettyAppender appender)
    {
        this(factory, name, appender, Level.INFO.toInt(), false);
    }

    public JettyLogger(JettyLoggerFactory factory, String name, JettyAppender appender, int level, boolean hideStacks)
    {
        this.factory = factory;
        this.name = name;
        this.condensedName = JettyLoggerFactory.condensePackageString(name);
        this.appender = appender;
        this.level = level;
        this.hideStacks = hideStacks;
    }

    @Override
    public void debug(String msg)
    {
        if (isDebugEnabled())
        {
            emit(Level.DEBUG, msg);
        }
    }

    @Override
    public void debug(String format, Object arg)
    {
        if (isDebugEnabled())
        {
            emit(Level.DEBUG, format, arg);
        }
    }

    @Override
    public void debug(String format, Object arg1, Object arg2)
    {
        if (isDebugEnabled())
        {
            emit(Level.DEBUG, format, arg1, arg2);
        }
    }

    @Override
    public void debug(String format, Object... arguments)
    {
        if (isDebugEnabled())
        {
            emit(Level.DEBUG, format, arguments);
        }
    }

    @Override
    public void debug(String msg, Throwable throwable)
    {
        if (isDebugEnabled())
        {
            emit(Level.DEBUG, msg, throwable);
        }
    }

    @Override
    public boolean isDebugEnabled(Marker marker)
    {
        return isDebugEnabled();
    }

    @Override
    public void debug(Marker marker, String msg)
    {
        // TODO: do we want to support org.sfl4j.Marker?
        debug(msg);
    }

    @Override
    public void debug(Marker marker, String format, Object arg)
    {
        // TODO: do we want to support org.sfl4j.Marker?
        debug(format, arg);
    }

    @Override
    public void debug(Marker marker, String format, Object arg1, Object arg2)
    {
        // TODO: do we want to support org.sfl4j.Marker?
        debug(format, arg1, arg2);
    }

    @Override
    public void debug(Marker marker, String format, Object... arguments)
    {
        // TODO: do we want to support org.sfl4j.Marker?
        debug(format, arguments);
    }

    @Override
    public void debug(Marker marker, String msg, Throwable t)
    {
        // TODO: do we want to support org.sfl4j.Marker?
        debug(msg, t);
    }

    @Override
    public void error(String msg)
    {
        if (isErrorEnabled())
        {
            emit(Level.ERROR, msg);
        }
    }

    @Override
    public void error(String format, Object arg)
    {
        if (isErrorEnabled())
        {
            emit(Level.ERROR, format, arg);
        }
    }

    @Override
    public void error(String format, Object arg1, Object arg2)
    {
        if (isErrorEnabled())
        {
            emit(Level.ERROR, format, arg1, arg2);
        }
    }

    @Override
    public void error(String format, Object... arguments)
    {
        if (isErrorEnabled())
        {
            emit(Level.ERROR, format, arguments);
        }
    }

    @Override
    public void error(String msg, Throwable throwable)
    {
        if (isErrorEnabled())
        {
            emit(Level.ERROR, msg, throwable);
        }
    }

    @Override
    public boolean isErrorEnabled(Marker marker)
    {
        return isErrorEnabled();
    }

    @Override
    public void error(Marker marker, String msg)
    {
        // TODO: do we want to support org.sfl4j.Marker?
        error(msg);
    }

    @Override
    public void error(Marker marker, String format, Object arg)
    {
        // TODO: do we want to support org.sfl4j.Marker?
        error(format, arg);
    }

    @Override
    public void error(Marker marker, String format, Object arg1, Object arg2)
    {
        // TODO: do we want to support org.sfl4j.Marker?
        error(format, arg1, arg2);
    }

    @Override
    public void error(Marker marker, String format, Object... arguments)
    {
        // TODO: do we want to support org.sfl4j.Marker?
        error(format, arguments);
    }

    @Override
    public void error(Marker marker, String msg, Throwable t)
    {
        // TODO: do we want to support org.sfl4j.Marker?
        error(msg, t);
    }

    public JettyAppender getAppender()
    {
        return appender;
    }

    /**
     * Entry point for {@link LocationAwareLogger}
     */
    @Override
    public void log(Marker marker, String fqcn, int levelInt, String message, Object[] argArray, Throwable throwable)
    {
        if (this.level <= levelInt)
        {
            long timestamp = System.currentTimeMillis();
            String threadName = Thread.currentThread().getName();
            getAppender().emit(this, intToLevel(levelInt), timestamp, threadName, throwable, message, argArray);
        }
    }

    /**
     * Dynamic (via Reflection) entry point for {@link SubstituteLogger} usage.
     *
     * @param event the logging event
     */
    @SuppressWarnings("unused")
    public void log(LoggingEvent event)
    {
        // TODO: do we want to support org.sfl4j.Marker?
        // TODO: do we want to support org.sfl4j.even.KeyValuePair?
        getAppender().emit(this, event.getLevel(), event.getTimeStamp(), event.getThreadName(), event.getThrowable(), event.getMessage(), event.getArgumentArray());
    }

    public String getCondensedName()
    {
        return condensedName;
    }

    public int getLevel()
    {
        return level;
    }

    public void setLevel(Level level)
    {
        Objects.requireNonNull(level, "Level");
        setLevel(level.toInt());
    }

    public void setLevel(int lvlInt)
    {
        this.level = lvlInt;

        // apply setLevel to children too.
        factory.walkChildLoggers(this.getName(), (logger) -> logger.setLevel(lvlInt));
    }

    @Override
    public String getName()
    {
        return name;
    }

    @Override
    public void info(String msg)
    {
        if (isInfoEnabled())
        {
            emit(Level.INFO, msg);
        }
    }

    @Override
    public void info(String format, Object arg)
    {
        if (isInfoEnabled())
        {
            emit(Level.INFO, format, arg);
        }
    }

    @Override
    public void info(String format, Object arg1, Object arg2)
    {
        if (isInfoEnabled())
        {
            emit(Level.INFO, format, arg1, arg2);
        }
    }

    @Override
    public void info(String format, Object... arguments)
    {
        if (isInfoEnabled())
        {
            emit(Level.INFO, format, arguments);
        }
    }

    @Override
    public void info(String msg, Throwable throwable)
    {
        if (isInfoEnabled())
        {
            emit(Level.INFO, msg, throwable);
        }
    }

    @Override
    public boolean isInfoEnabled(Marker marker)
    {
        return isInfoEnabled();
    }

    @Override
    public void info(Marker marker, String msg)
    {
        // TODO: do we want to support org.sfl4j.Marker?
        info(msg);
    }

    @Override
    public void info(Marker marker, String format, Object arg)
    {
        // TODO: do we want to support org.sfl4j.Marker?
        info(format, arg);
    }

    @Override
    public void info(Marker marker, String format, Object arg1, Object arg2)
    {
        // TODO: do we want to support org.sfl4j.Marker?
        info(format, arg1, arg2);
    }

    @Override
    public void info(Marker marker, String format, Object... arguments)
    {
        // TODO: do we want to support org.sfl4j.Marker?
        info(format, arguments);
    }

    @Override
    public void info(Marker marker, String msg, Throwable t)
    {
        // TODO: do we want to support org.sfl4j.Marker?
        info(msg, t);
    }

    @Override
    public boolean isDebugEnabled()
    {
        return level <= Level.DEBUG.toInt();
    }

    @Override
    public boolean isErrorEnabled()
    {
        return level <= Level.ERROR.toInt();
    }

    public boolean isHideStacks()
    {
        return hideStacks;
    }

    public void setHideStacks(boolean hideStacks)
    {
        this.hideStacks = hideStacks;

        // apply setHideStacks to children too.
        factory.walkChildLoggers(this.getName(), (logger) -> logger.setHideStacks(hideStacks));
    }

    @Override
    public boolean isInfoEnabled()
    {
        return level <= Level.INFO.toInt();
    }

    @Override
    public boolean isTraceEnabled()
    {
        return level <= Level.TRACE.toInt();
    }

    @Override
    public boolean isWarnEnabled()
    {
        return level <= Level.WARN.toInt();
    }

    @Override
    public void trace(String msg)
    {
        if (isTraceEnabled())
        {
            emit(Level.TRACE, msg);
        }
    }

    @Override
    public void trace(String format, Object arg)
    {
        if (isTraceEnabled())
        {
            emit(Level.TRACE, format, arg);
        }
    }

    @Override
    public void trace(String format, Object arg1, Object arg2)
    {
        if (isTraceEnabled())
        {
            emit(Level.TRACE, format, arg1, arg2);
        }
    }

    @Override
    public void trace(String format, Object... arguments)
    {
        if (isTraceEnabled())
        {
            emit(Level.TRACE, format, arguments);
        }
    }

    @Override
    public void trace(String msg, Throwable throwable)
    {
        if (isTraceEnabled())
        {
            emit(Level.TRACE, msg, throwable);
        }
    }

    @Override
    public boolean isTraceEnabled(Marker marker)
    {
        return isTraceEnabled();
    }

    @Override
    public void trace(Marker marker, String msg)
    {
        // TODO: do we want to support org.sfl4j.Marker?
        trace(msg);
    }

    @Override
    public void trace(Marker marker, String format, Object arg)
    {
        // TODO: do we want to support org.sfl4j.Marker?
        trace(format, arg);
    }

    @Override
    public void trace(Marker marker, String format, Object arg1, Object arg2)
    {
        // TODO: do we want to support org.sfl4j.Marker?
        trace(format, arg1, arg2);
    }

    @Override
    public void trace(Marker marker, String format, Object... argArray)
    {
        // TODO: do we want to support org.sfl4j.Marker?
        trace(format, argArray);
    }

    @Override
    public void trace(Marker marker, String msg, Throwable t)
    {
        // TODO: do we want to support org.sfl4j.Marker?
        trace(msg, t);
    }

    @Override
    public void warn(String msg)
    {
        if (isWarnEnabled())
        {
            emit(Level.WARN, msg);
        }
    }

    @Override
    public void warn(String format, Object arg)
    {
        if (isWarnEnabled())
        {
            emit(Level.WARN, format, arg);
        }
    }

    @Override
    public void warn(String format, Object... arguments)
    {
        if (isWarnEnabled())
        {
            emit(Level.WARN, format, arguments);
        }
    }

    @Override
    public void warn(String format, Object arg1, Object arg2)
    {
        if (isWarnEnabled())
        {
            emit(Level.WARN, format, arg1, arg2);
        }
    }

    @Override
    public void warn(String msg, Throwable throwable)
    {
        if (isWarnEnabled())
        {
            emit(Level.WARN, msg, throwable);
        }
    }

    @Override
    public boolean isWarnEnabled(Marker marker)
    {
        return isWarnEnabled();
    }

    @Override
    public void warn(Marker marker, String msg)
    {
        // TODO: do we want to support org.sfl4j.Marker?
        warn(msg);
    }

    @Override
    public void warn(Marker marker, String format, Object arg)
    {
        // TODO: do we want to support org.sfl4j.Marker?
        warn(format, arg);
    }

    @Override
    public void warn(Marker marker, String format, Object arg1, Object arg2)
    {
        // TODO: do we want to support org.sfl4j.Marker?
        warn(format, arg1, arg2);
    }

    @Override
    public void warn(Marker marker, String format, Object... arguments)
    {
        // TODO: do we want to support org.sfl4j.Marker?
        warn(format, arguments);
    }

    @Override
    public void warn(Marker marker, String msg, Throwable t)
    {
        // TODO: do we want to support org.sfl4j.Marker?
        warn(msg, t);
    }

    private void emit(Level level, String msg)
    {
        long timestamp = System.currentTimeMillis();
        String threadName = Thread.currentThread().getName();
        getAppender().emit(this, level, timestamp, threadName, null, msg);
    }

    private void emit(Level level, String format, Object arg)
    {
        long timestamp = System.currentTimeMillis();
        String threadName = Thread.currentThread().getName();
        if (arg instanceof Throwable)
            getAppender().emit(this, level, timestamp, threadName, (Throwable)arg, format);
        else
            getAppender().emit(this, level, timestamp, threadName, null, format, arg);
    }

    private void emit(Level level, String format, Object arg1, Object arg2)
    {
        long timestamp = System.currentTimeMillis();
        String threadName = Thread.currentThread().getName();
        if (arg2 instanceof Throwable)
            getAppender().emit(this, level, timestamp, threadName, (Throwable)arg2, format, arg1);
        else
            getAppender().emit(this, level, timestamp, threadName, null, format, arg1, arg2);
    }

    private void emit(Level level, String format, Object... arguments)
    {
        long timestamp = System.currentTimeMillis();
        String threadName = Thread.currentThread().getName();
        getAppender().emit(this, level, timestamp, threadName, null, format, arguments);
    }

    private void emit(Level level, String msg, Throwable throwable)
    {
        long timestamp = System.currentTimeMillis();
        String threadName = Thread.currentThread().getName();
        getAppender().emit(this, level, timestamp, threadName, throwable, msg);
    }

    public static Level intToLevel(int level)
    {
        if (level >= JettyLogger.OFF)
            return Level.ERROR;
        if (level >= Level.ERROR.toInt())
            return Level.ERROR;
        if (level >= Level.WARN.toInt())
            return Level.WARN;
        if (level >= Level.INFO.toInt())
            return Level.INFO;
        if (level >= Level.DEBUG.toInt())
            return Level.DEBUG;
        if (level >= Level.TRACE.toInt())
            return Level.TRACE;
        return Level.TRACE; // everything else
    }

    public static String levelToString(int level)
    {
        if (level >= JettyLogger.OFF)
            return "OFF";
        if (level >= Level.ERROR.toInt())
            return "ERROR";
        if (level >= Level.WARN.toInt())
            return "WARN";
        if (level >= Level.INFO.toInt())
            return "INFO";
        if (level >= Level.DEBUG.toInt())
            return "DEBUG";
        if (level >= Level.TRACE.toInt())
            return "TRACE";
        return "OFF"; // everything else
    }

    @Override
    public String toString()
    {
        return String.format("%s:%s:LEVEL=%s", JettyLogger.class.getSimpleName(), name, levelToString(level));
    }
}
