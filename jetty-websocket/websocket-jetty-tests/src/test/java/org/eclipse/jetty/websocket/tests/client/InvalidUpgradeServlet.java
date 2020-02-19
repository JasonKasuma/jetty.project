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

package org.eclipse.jetty.websocket.tests.client;

import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.eclipse.jetty.http.HttpHeader;
import org.eclipse.jetty.http.HttpStatus;
import org.eclipse.jetty.websocket.core.internal.WebSocketCore;

public class InvalidUpgradeServlet extends HttpServlet
{
    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
    {
        String pathInfo = req.getPathInfo();
        if (pathInfo.contains("only-accept"))
        {
            // Force 200 response, no response body content, incomplete websocket response headers, no actual upgrade for this test
            resp.setStatus(HttpStatus.OK_200);
            String key = req.getHeader(HttpHeader.SEC_WEBSOCKET_KEY.toString());
            resp.setHeader(HttpHeader.SEC_WEBSOCKET_ACCEPT.toString(), WebSocketCore.hashKey(key));
        }
        else if (pathInfo.contains("close-connection"))
        {
            // Force 101 response, with invalid Connection header, invalid handshake
            resp.setStatus(HttpStatus.SWITCHING_PROTOCOLS_101);
            String key = req.getHeader(HttpHeader.SEC_WEBSOCKET_KEY.toString());
            resp.setHeader(HttpHeader.CONNECTION.toString(), "close");
            resp.setHeader(HttpHeader.SEC_WEBSOCKET_ACCEPT.toString(), WebSocketCore.hashKey(key));
        }
        else if (pathInfo.contains("missing-connection"))
        {
            // Force 101 response, with no Connection header, invalid handshake
            resp.setStatus(HttpStatus.SWITCHING_PROTOCOLS_101);
            String key = req.getHeader(HttpHeader.SEC_WEBSOCKET_KEY.toString());
            // Intentionally leave out Connection header
            resp.setHeader(HttpHeader.SEC_WEBSOCKET_ACCEPT.toString(), WebSocketCore.hashKey(key));
        }
        else if (pathInfo.contains("rubbish-accept"))
        {
            // Force 101 response, with no Connection header, invalid handshake
            resp.setStatus(HttpStatus.SWITCHING_PROTOCOLS_101);
            resp.setHeader(HttpHeader.SEC_WEBSOCKET_ACCEPT.toString(), "rubbish");
        }
        else
        {
            resp.setStatus(500);
        }
    }
}
