//
//  ========================================================================
//  Copyright (c) 1995-2017 Mort Bay Consulting Pty. Ltd.
//  ------------------------------------------------------------------------
//  All rights reserved. This program and the accompanying materials
//  are made available under the terms of the Eclipse Public License v1.0
//  and Apache License v2.0 which accompanies this distribution.
//
//      The Eclipse Public License is available at
//      http://www.eclipse.org/legal/epl-v10.html
//
//      The Apache License v2.0 is available at
//      http://www.opensource.org/licenses/apache2.0.php
//
//  You may elect to redistribute this code under either of these licenses.
//  ========================================================================
//

package org.eclipse.jetty.websocket.tests.client;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.instanceOf;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

import java.io.IOException;
import java.lang.reflect.Field;
import java.net.SocketTimeoutException;
import java.net.URI;
import java.nio.ByteBuffer;
import java.nio.channels.SelectableChannel;
import java.nio.channels.SelectionKey;
import java.nio.channels.SocketChannel;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.eclipse.jetty.client.HttpClient;
import org.eclipse.jetty.client.http.HttpClientTransportOverHTTP;
import org.eclipse.jetty.io.EndPoint;
import org.eclipse.jetty.io.EofException;
import org.eclipse.jetty.io.ManagedSelector;
import org.eclipse.jetty.io.SelectorManager;
import org.eclipse.jetty.io.SocketChannelEndPoint;
import org.eclipse.jetty.toolchain.test.EventQueue;
import org.eclipse.jetty.toolchain.test.TestTracker;
import org.eclipse.jetty.util.BufferUtil;
import org.eclipse.jetty.util.log.Log;
import org.eclipse.jetty.util.log.Logger;
import org.eclipse.jetty.util.log.StacklessLogging;
import org.eclipse.jetty.util.thread.Scheduler;
import org.eclipse.jetty.websocket.api.ProtocolException;
import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.StatusCode;
import org.eclipse.jetty.websocket.api.WebSocketAdapter;
import org.eclipse.jetty.websocket.client.WebSocketClient;
import org.eclipse.jetty.websocket.common.OpCode;
import org.eclipse.jetty.websocket.common.Parser;
import org.eclipse.jetty.websocket.common.WebSocketFrame;
import org.eclipse.jetty.websocket.common.WebSocketSession;
import org.eclipse.jetty.websocket.common.io.AbstractWebSocketConnection;
import org.eclipse.jetty.websocket.tests.RawFrameBuilder;
import org.eclipse.jetty.websocket.tests.UntrustedWSConnection;
import org.eclipse.jetty.websocket.tests.UntrustedWSEndpoint;
import org.eclipse.jetty.websocket.tests.UntrustedWSServer;
import org.eclipse.jetty.websocket.tests.UntrustedWSSession;
import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

public class ClientCloseTest
{
    private static final Logger LOG = Log.getLogger(ClientCloseTest.class);
    
    private static class CloseTrackingSocket extends WebSocketAdapter
    {
        private static final Logger LOG = Log.getLogger(CloseTrackingSocket.class);
        
        public int closeCode = -1;
        public String closeReason = null;
        public CountDownLatch closeLatch = new CountDownLatch(1);
        public AtomicInteger closeCount = new AtomicInteger(0);
        public CountDownLatch openLatch = new CountDownLatch(1);
        public CountDownLatch errorLatch = new CountDownLatch(1);
        
        public EventQueue<String> messageQueue = new EventQueue<>();
        public AtomicReference<Throwable> error = new AtomicReference<>();
        
        public void assertNoCloseEvent()
        {
            assertThat("Client Close Event", closeLatch.getCount(), is(1L));
            assertThat("Client Close Event Status Code ", closeCode, is(-1));
        }
        
        public void assertReceivedCloseEvent(int clientTimeoutMs, Matcher<Integer> statusCodeMatcher, Matcher<String> reasonMatcher)
                throws InterruptedException
        {
            long maxTimeout = clientTimeoutMs * 4;
            
            assertThat("Client Close Event Occurred", closeLatch.await(maxTimeout, TimeUnit.MILLISECONDS), is(true));
            assertThat("Client Close Event Count", closeCount.get(), is(1));
            assertThat("Client Close Event Status Code", closeCode, statusCodeMatcher);
            if (reasonMatcher == null)
            {
                assertThat("Client Close Event Reason", closeReason, nullValue());
            }
            else
            {
                assertThat("Client Close Event Reason", closeReason, reasonMatcher);
            }
        }
        
        public void assertReceivedErrorEvent(int clientTimeoutMs, Class<? extends Throwable> expectedCause, Matcher<String> messageMatcher) throws InterruptedException
        {
            long maxTimeout = clientTimeoutMs * 4;
            
            assertThat("Client Error Event Occurred", errorLatch.await(maxTimeout, TimeUnit.MILLISECONDS), is(true));
            assertThat("Client Error Type", error.get(), instanceOf(expectedCause));
            assertThat("Client Error Message", error.get().getMessage(), messageMatcher);
        }
        
        public void clearQueues()
        {
            messageQueue.clear();
        }
        
        @Override
        public void onWebSocketClose(int statusCode, String reason)
        {
            LOG.debug("onWebSocketClose({},{})", statusCode, reason);
            super.onWebSocketClose(statusCode, reason);
            closeCount.incrementAndGet();
            closeCode = statusCode;
            closeReason = reason;
            closeLatch.countDown();
        }
        
        @Override
        public void onWebSocketConnect(Session session)
        {
            LOG.debug("onWebSocketConnect({})", session);
            super.onWebSocketConnect(session);
            openLatch.countDown();
        }
        
        @Override
        public void onWebSocketError(Throwable cause)
        {
            LOG.warn("onWebSocketError", cause);
            assertThat("Unique Error Event", error.compareAndSet(null, cause), is(true));
            errorLatch.countDown();
        }
        
        @Override
        public void onWebSocketText(String message)
        {
            LOG.debug("onWebSocketText({})", message);
            messageQueue.offer(message);
        }
        
        public EndPoint getEndPoint() throws Exception
        {
            Session session = getSession();
            assertThat("Session type", session, instanceOf(WebSocketSession.class));
            
            WebSocketSession wssession = (WebSocketSession) session;
            Field fld = wssession.getClass().getDeclaredField("connection");
            fld.setAccessible(true);
            assertThat("Field: connection", fld, notNullValue());
            
            Object val = fld.get(wssession);
            assertThat("Connection type", val, instanceOf(AbstractWebSocketConnection.class));
            @SuppressWarnings("resource")
            AbstractWebSocketConnection wsconn = (AbstractWebSocketConnection) val;
            return wsconn.getEndPoint();
        }
    }
    
    @Rule
    public TestName testname = new TestName();
    
    @Rule
    public TestTracker tt = new TestTracker();
    
    private UntrustedWSServer server;
    private WebSocketClient client;
    
    private void confirmConnection(CloseTrackingSocket clientSocket, Future<Session> clientFuture, UntrustedWSSession serverSession) throws Exception
    {
        // Wait for client connect on via future
        clientFuture.get(30, TimeUnit.SECONDS);
        
        // Wait for client connect via client websocket
        assertThat("Client WebSocket is Open", clientSocket.openLatch.await(30, TimeUnit.SECONDS), is(true));
    
        UntrustedWSEndpoint serverEndpoint = serverSession.getUntrustedEndpoint();
        // Future<List<WebSocketFrame>> futFrames = serverEndpoint.expectedFrames(1);
        
        try
        {
            // Send message from client to server
            final String echoMsg = "echo-test";
            Future<Void> testFut = clientSocket.getRemote().sendStringByFuture(echoMsg);
            
            // Wait for send future
            testFut.get(30, TimeUnit.SECONDS);
            
            // Read Frame on server side
            WebSocketFrame frame = serverEndpoint.framesQueue.poll(10, TimeUnit.SECONDS);
            assertThat("Server received frame", frame.getOpCode(), is(OpCode.TEXT));
            assertThat("Server received frame payload", frame.getPayloadAsUTF8(), is(echoMsg));
            
            // Server send echo reply
            serverEndpoint.getRemote().sendString(echoMsg);
            
            // Wait for received echo
            clientSocket.messageQueue.awaitEventCount(1, 1, TimeUnit.SECONDS);
            
            // Verify received message
            String recvMsg = clientSocket.messageQueue.poll();
            assertThat("Received message", recvMsg, is(echoMsg));
            
            // Verify that there are no errors
            assertThat("Error events", clientSocket.error.get(), nullValue());
        }
        finally
        {
            clientSocket.clearQueues();
        }
    }
    
    public static class TestClientTransportOverHTTP extends HttpClientTransportOverHTTP
    {
        @Override
        protected SelectorManager newSelectorManager(HttpClient client)
        {
            return new ClientSelectorManager(client, 1)
            {
                @Override
                protected EndPoint newEndPoint(SelectableChannel channel, ManagedSelector selector, SelectionKey key)
                {
                    TestEndPoint endPoint = new TestEndPoint(channel, selector, key, getScheduler());
                    endPoint.setIdleTimeout(client.getIdleTimeout());
                    return endPoint;
                }
            };
        }
    }
    
    public static class TestEndPoint extends SocketChannelEndPoint
    {
        public AtomicBoolean congestedFlush = new AtomicBoolean(false);
        
        public TestEndPoint(SelectableChannel channel, ManagedSelector selector, SelectionKey key, Scheduler scheduler)
        {
            super((SocketChannel) channel, selector, key, scheduler);
        }
        
        @Override
        public boolean flush(ByteBuffer... buffers) throws IOException
        {
            boolean flushed = super.flush(buffers);
            congestedFlush.set(!flushed);
            return flushed;
        }
    }
    
    @Before
    public void startClient() throws Exception
    {
        HttpClient httpClient = new HttpClient(new TestClientTransportOverHTTP(), null);
        client = new WebSocketClient(httpClient);
        client.addBean(httpClient);
        client.start();
    }
    
    @Before
    public void startServer() throws Exception
    {
        server = new UntrustedWSServer();
        server.start();
    }
    
    @After
    public void stopClient() throws Exception
    {
        client.stop();
    }
    
    @After
    public void stopServer() throws Exception
    {
        server.stop();
    }
    
    @Test
    public void testHalfClose() throws Exception
    {
        // Set client timeout
        final int timeout = 1000;
        client.setMaxIdleTimeout(timeout);
        
        URI wsURI = server.getWsUri().resolve("/untrusted/" + testname.getMethodName());
        CompletableFuture<UntrustedWSSession> serverSessionFut = new CompletableFuture<>();
        server.registerConnectFuture(wsURI, serverSessionFut);
        
        // Client connects
        CloseTrackingSocket clientSocket = new CloseTrackingSocket();
        Future<Session> clientConnectFuture = client.connect(clientSocket, wsURI);
        
        // Server accepts connect
        UntrustedWSSession serverSession = serverSessionFut.get(10, TimeUnit.SECONDS);
        
        // client confirms connection via echo
        confirmConnection(clientSocket, clientConnectFuture, serverSession);
        
        // client sends close frame (code 1000, normal)
        final String origCloseReason = "Normal Close";
        clientSocket.getSession().close(StatusCode.NORMAL, origCloseReason);
        
        // server receives close frame
        serverSession.getUntrustedEndpoint().assertCloseInfo("Server", StatusCode.NORMAL, is(origCloseReason));
    
        // server sends 2 messages
        RemoteEndpoint remote = serverSession.getRemote();
        remote.sendString("Hello");
        remote.sendString("World");
        
        // server sends close frame (code 1000, no reason)
        serverSession.close(StatusCode.NORMAL, "From Server");
        
        // client receives 2 messages
        clientSocket.messageQueue.awaitEventCount(2, 1, TimeUnit.SECONDS);
        
        // Verify received messages
        String recvMsg = clientSocket.messageQueue.poll();
        assertThat("Received message 1", recvMsg, is("Hello"));
        recvMsg = clientSocket.messageQueue.poll();
        assertThat("Received message 2", recvMsg, is("World"));
        
        // Verify that there are no errors
        assertThat("Error events", clientSocket.error.get(), nullValue());
        
        // client close event on ws-endpoint
        clientSocket.assertReceivedCloseEvent(timeout, is(StatusCode.NORMAL), containsString("From Server"));
    }
    
    @Test
    public void testNetworkCongestion() throws Exception
    {
        // Set client timeout
        final int timeout = 1000;
        client.setMaxIdleTimeout(timeout);
        
        URI wsURI = server.getWsUri().resolve("/untrusted/" + testname.getMethodName());
        CompletableFuture<UntrustedWSSession> serverSessionFut = new CompletableFuture<>();
        server.registerConnectFuture(wsURI, serverSessionFut);
        
        // Client connects
        CloseTrackingSocket clientSocket = new CloseTrackingSocket();
        Future<Session> clientConnectFuture = client.connect(clientSocket, wsURI);
        
        // Server accepts connect
        UntrustedWSSession serverSession = serverSessionFut.get(10, TimeUnit.SECONDS);
        
        // client confirms connection via echo
        confirmConnection(clientSocket, clientConnectFuture, serverSession);
        
        // client sends BIG frames (until it cannot write anymore)
        // server must not read (for test purpose, in order to congest connection)
        // when write is congested, client enqueue close frame
        // client initiate write, but write never completes
        EndPoint endp = clientSocket.getEndPoint();
        assertThat("EndPoint is testable", endp, instanceOf(TestEndPoint.class));
        TestEndPoint testendp = (TestEndPoint) endp;
        
        char msg[] = new char[10240];
        int writeCount = 0;
        long writeSize = 0;
        int i = 0;
        while (!testendp.congestedFlush.get())
        {
            int z = i - ((i / 26) * 26);
            char c = (char) ('a' + z);
            Arrays.fill(msg, c);
            clientSocket.getRemote().sendStringByFuture(String.valueOf(msg));
            writeCount++;
            writeSize += msg.length;
        }
        LOG.info("Wrote {} frames totalling {} bytes of payload before congestion kicked in", writeCount, writeSize);
        
        // Verify timeout error
        assertThat("OnError Latch", clientSocket.errorLatch.await(2, TimeUnit.SECONDS), is(true));
        assertThat("OnError", clientSocket.error.get(), instanceOf(SocketTimeoutException.class));
    }
    
    @Test
    public void testProtocolException() throws Exception
    {
        // Set client timeout
        final int timeout = 1000;
        client.setMaxIdleTimeout(timeout);
        
        URI wsURI = server.getWsUri().resolve("/untrusted/" + testname.getMethodName());
        CompletableFuture<UntrustedWSSession> serverSessionFut = new CompletableFuture<>();
        server.registerConnectFuture(wsURI, serverSessionFut);
        
        // Client connects
        CloseTrackingSocket clientSocket = new CloseTrackingSocket();
        Future<Session> clientConnectFuture = client.connect(clientSocket, wsURI);
        
        // Server accepts connect
        UntrustedWSSession serverSession = serverSessionFut.get(10, TimeUnit.SECONDS);
        
        // client confirms connection via echo
        confirmConnection(clientSocket, clientConnectFuture, serverSession);
        
        // client should not have received close message (yet)
        clientSocket.assertNoCloseEvent();
        
        // server sends bad close frame (too big of a reason message)
        byte msg[] = new byte[400];
        Arrays.fill(msg, (byte) 'x');
        ByteBuffer bad = ByteBuffer.allocate(500);
        RawFrameBuilder.putOpFin(bad, OpCode.CLOSE, true);
        RawFrameBuilder.putLength(bad, msg.length + 2, false);
        bad.putShort((short) StatusCode.NORMAL);
        bad.put(msg);
        BufferUtil.flipToFlush(bad, 0);
        try (StacklessLogging ignored = new StacklessLogging(Parser.class))
        {
            serverSession.getUntrustedConnection().writeRaw(bad);
            
            // client should have noticed the error
            assertThat("OnError Latch", clientSocket.errorLatch.await(2, TimeUnit.SECONDS), is(true));
            assertThat("OnError", clientSocket.error.get(), instanceOf(ProtocolException.class));
            assertThat("OnError", clientSocket.error.get().getMessage(), containsString("Invalid control frame"));
            
            // client parse invalid frame, notifies server of close (protocol error)
            serverSession.getUntrustedEndpoint().assertCloseInfo("Server", StatusCode.PROTOCOL, allOf(containsString("Invalid control frame"), containsString("length")));
        }
        
        // server disconnects
        serverSession.disconnect();
        
        // client triggers close event on client ws-endpoint
        clientSocket.assertReceivedCloseEvent(timeout, is(StatusCode.PROTOCOL), allOf(containsString("Invalid control frame"), containsString("length")));
    }
    
    @Test
    public void testReadEOF() throws Exception
    {
        // Set client timeout
        final int timeout = 1000;
        client.setMaxIdleTimeout(timeout);
        
        URI wsURI = server.getWsUri().resolve("/untrusted/" + testname.getMethodName());
        CompletableFuture<UntrustedWSSession> serverSessionFut = new CompletableFuture<>();
        server.registerConnectFuture(wsURI, serverSessionFut);
        
        // Client connects
        CloseTrackingSocket clientSocket = new CloseTrackingSocket();
        Future<Session> clientConnectFuture = client.connect(clientSocket, wsURI);
        
        // Server accepts connect
        UntrustedWSSession serverSession = serverSessionFut.get(10, TimeUnit.SECONDS);
        
        // client confirms connection via echo
        confirmConnection(clientSocket, clientConnectFuture, serverSession);
        
        try (StacklessLogging ignored = new StacklessLogging(CloseTrackingSocket.class))
        {
            // client sends close frame
            final String origCloseReason = "Normal Close";
            clientSocket.getSession().close(StatusCode.NORMAL, origCloseReason);
            
            // server receives close frame
            serverSession.getUntrustedEndpoint().assertCloseInfo("Server", StatusCode.NORMAL, is(origCloseReason));
    
            // client should not have received close message (yet)
            clientSocket.assertNoCloseEvent();
            
            // server shuts down connection (no frame reply)
            serverSession.disconnect();
            
            // client reads -1 (EOF)
            clientSocket.assertReceivedErrorEvent(timeout, IOException.class, containsString("EOF"));
            // client triggers close event on client ws-endpoint
            clientSocket.assertReceivedCloseEvent(timeout, is(StatusCode.ABNORMAL), containsString("Disconnected"));
        }
    }
    
    @Test
    public void testServerNoCloseHandshake() throws Exception
    {
        // Set client timeout
        final int timeout = 1000;
        client.setMaxIdleTimeout(timeout);
        
        URI wsURI = server.getWsUri().resolve("/untrusted/" + testname.getMethodName());
        CompletableFuture<UntrustedWSSession> serverSessionFut = new CompletableFuture<>();
        server.registerConnectFuture(wsURI, serverSessionFut);
        
        // Client connects
        CloseTrackingSocket clientSocket = new CloseTrackingSocket();
        Future<Session> clientConnectFuture = client.connect(clientSocket, wsURI);
        
        // Server accepts connect
        UntrustedWSSession serverSession = serverSessionFut.get(10, TimeUnit.SECONDS);
        UntrustedWSConnection serverConn = serverSession.getUntrustedConnection();
        
        // client confirms connection via echo
        confirmConnection(clientSocket, clientConnectFuture, serverSession);
        
        // client sends close frame
        final String origCloseReason = "Normal Close";
        clientSocket.getSession().close(StatusCode.NORMAL, origCloseReason);
        
        // server receives close frame
        serverSession.getUntrustedEndpoint().assertCloseInfo("Server", StatusCode.NORMAL, is(origCloseReason));
    
        // client should not have received close message (yet)
        clientSocket.assertNoCloseEvent();
        
        // server never sends close frame handshake
        // server sits idle
        
        // client idle timeout triggers close event on client ws-endpoint
        assertThat("OnError Latch", clientSocket.errorLatch.await(2, TimeUnit.SECONDS), is(true));
        assertThat("OnError", clientSocket.error.get(), instanceOf(SocketTimeoutException.class));
        assertThat("OnError", clientSocket.error.get().getMessage(), containsString("Timeout on Read"));
    }
    
    @Test(timeout = 5000L)
    public void testStopLifecycle() throws Exception
    {
        // Set client timeout
        final int timeout = 1000;
        client.setMaxIdleTimeout(timeout);
        
        int clientCount = 3;
        CloseTrackingSocket clientSockets[] = new CloseTrackingSocket[clientCount];
        UntrustedWSSession serverSessions[] = new UntrustedWSSession[clientCount];
        
        // Connect Multiple Clients
        for (int i = 0; i < clientCount; i++)
        {
            URI wsURI = server.getWsUri().resolve("/untrusted/" + testname.getMethodName() + "/" + i);
            CompletableFuture<UntrustedWSSession> serverSessionFut = new CompletableFuture<>();
            server.registerConnectFuture(wsURI, serverSessionFut);
            
            // Client Request Upgrade
            clientSockets[i] = new CloseTrackingSocket();
            Future<Session> clientConnectFuture = client.connect(clientSockets[i], wsURI);
            
            // Server accepts connection
            serverSessions[i] = serverSessionFut.get(10, TimeUnit.SECONDS);
            
            // client confirms connection via echo
            confirmConnection(clientSockets[i], clientConnectFuture, serverSessions[i]);
        }
        
        // client lifecycle stop
        client.stop();
        
        // clients send close frames (code 1001, shutdown)
        for (int i = 0; i < clientCount; i++)
        {
            // server receives close frame
            serverSessions[i].getUntrustedEndpoint().assertCloseInfo("Server", StatusCode.SHUTDOWN, containsString("Shutdown"));
        }
        
        // clients disconnect
        for (int i = 0; i < clientCount; i++)
        {
            clientSockets[i].assertReceivedCloseEvent(timeout, is(StatusCode.SHUTDOWN), containsString("Shutdown"));
        }
    }
    
    @Test
    public void testWriteException() throws Exception
    {
        // Set client timeout
        final int timeout = 1000;
        client.setMaxIdleTimeout(timeout);
        
        URI wsURI = server.getWsUri().resolve("/untrusted/" + testname.getMethodName());
        CompletableFuture<UntrustedWSSession> serverSessionFut = new CompletableFuture<UntrustedWSSession>()
        {
            @Override
            public boolean complete(UntrustedWSSession session)
            {
                // echo back text as-well
                session.getUntrustedEndpoint().setOnTextFunction((serverSession, text) -> text);
                return super.complete(session);
            }
        };
        server.registerConnectFuture(wsURI, serverSessionFut);
        
        // Client connects
        CloseTrackingSocket clientSocket = new CloseTrackingSocket();
        Future<Session> clientConnectFuture = client.connect(clientSocket, wsURI);
        
        // Server accepts connect
        UntrustedWSSession serverSession = serverSessionFut.get(10, TimeUnit.SECONDS);
        
        // client confirms connection via echo
        confirmConnection(clientSocket, clientConnectFuture, serverSession);
        
        // setup client endpoint for write failure (test only)
        EndPoint endp = clientSocket.getEndPoint();
        endp.shutdownOutput();
        
        // client enqueue close frame
        // client write failure
        final String origCloseReason = "Normal Close";
        clientSocket.getSession().close(StatusCode.NORMAL, origCloseReason);
        
        assertThat("OnError Latch", clientSocket.errorLatch.await(2, TimeUnit.SECONDS), is(true));
        assertThat("OnError", clientSocket.error.get(), instanceOf(EofException.class));
        
        // client triggers close event on client ws-endpoint
        // assert - close code==1006 (abnormal)
        // assert - close reason message contains (write failure)
        clientSocket.assertReceivedCloseEvent(timeout, is(StatusCode.ABNORMAL), containsString("EOF"));
    }
}