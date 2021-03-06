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

package org.eclipse.jetty.server.session;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;

/**
 * JDBCSessionDataStoreTest
 */
public class JDBCSessionDataStoreTest extends AbstractSessionDataStoreTest
{

    @BeforeEach
    public void setUp() throws Exception
    {
        JdbcTestHelper.prepareTables();
    }

    @AfterEach
    public void tearDown() throws Exception
    {
        JdbcTestHelper.shutdown(null);
    }

    @Override
    public SessionDataStoreFactory createSessionDataStoreFactory()
    {
        return JdbcTestHelper.newSessionDataStoreFactory();
    }

    @Override
    public void persistSession(SessionData data)
        throws Exception
    {
        JdbcTestHelper.insertSession(data.getId(), data.getContextPath(), data.getVhost(), data.getLastNode(),
            data.getCreated(), data.getAccessed(), data.getLastAccessed(),
            data.getMaxInactiveMs(), data.getExpiry(), data.getCookieSet(),
            data.getLastSaved(), data.getAllAttributes());
    }

    @Override
    public void persistUnreadableSession(SessionData data) throws Exception
    {
        JdbcTestHelper.insertSession(data.getId(), data.getContextPath(), data.getVhost(), data.getLastNode(),
            data.getCreated(), data.getAccessed(), data.getLastAccessed(),
            data.getMaxInactiveMs(), data.getExpiry(), data.getCookieSet(),
            data.getLastSaved(), null);
    }

    @Override
    public boolean checkSessionExists(SessionData data) throws Exception
    {
        return JdbcTestHelper.existsInSessionTable(data.getId(), false);
    }

    @Override
    public boolean checkSessionPersisted(SessionData data) throws Exception
    {
        ClassLoader old = Thread.currentThread().getContextClassLoader();
        Thread.currentThread().setContextClassLoader(_contextClassLoader);
        try
        {
            return JdbcTestHelper.checkSessionPersisted(data);
        }
        finally
        {
            Thread.currentThread().setContextClassLoader(old);
        }
    }
}
