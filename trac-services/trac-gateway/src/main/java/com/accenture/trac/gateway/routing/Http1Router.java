/*
 * Copyright 2021 Accenture Global Solutions Limited
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.accenture.trac.gateway.routing;

import com.accenture.trac.common.exception.EUnexpected;
import com.accenture.trac.gateway.proxy.http.Http1ProxyBuilder;
import com.accenture.trac.gateway.proxy.grpc.GrpcProxyBuilder;

import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.*;
import io.netty.util.concurrent.Future;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URI;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;


public class Http1Router extends SimpleChannelInboundHandler<HttpObject> {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private static final AtomicInteger nextRouterId = new AtomicInteger();
    private final int routerId = nextRouterId.getAndIncrement();

    private Bootstrap bootstrap;
    private final Map<String, ClientState> clients;
    private final List<Route> routes;

    private long currentInboundRequest;
    private long currentOutboundRequest;
    private final Map<Long, RequestState> requests;

    public Http1Router() {

        this.clients = new HashMap<>();
        this.requests = new HashMap<>();
        this.routes = new ArrayList<>();

        addHardCodedRoutes();
    }

    private void addHardCodedRoutes() {

        var metaApiRoute = new Route();
        metaApiRoute.clientKey = "API: Metadata";
        metaApiRoute.matcher = (uri, method, headers) -> uri
                .getPath()
                .matches("^/trac.api.TracMetadataApi/.+");
        metaApiRoute.host = "localhost";
        metaApiRoute.port = 8081;
        metaApiRoute.initializer = GrpcProxyBuilder::new;

        var defaultRoute = new Route();
        defaultRoute.clientKey = "Static content";
        defaultRoute.matcher = (uri, method, headers) -> true;;
        defaultRoute.host = "localhost";
        defaultRoute.port = 8090;
        defaultRoute.initializer = Http1ProxyBuilder::new;

        routes.add(0, metaApiRoute);
        routes.add(1, defaultRoute);
    }

    @Override
    public void handlerAdded(ChannelHandlerContext ctx) throws Exception {

        log.info("HTTP 1.1 router added for ID {}", routerId);

        // Bootstrap is used to create proxy channels for this router channel
        // In an HTTP 1 world, browser clients will normally make several connections,
        // so there will be multiple router instances per client

        // Proxy channels will run on the same event loop as the router channel they belong to
        // Proxy channels will use the same ByteBuf allocator as the router they belong to

        var eventLoop = ctx.channel().eventLoop();
        var allocator = ctx.alloc();

        this.bootstrap = new Bootstrap()
                .group(eventLoop)
                .channel(NioSocketChannel.class)
                .option(ChannelOption.ALLOCATOR, allocator);

        super.handlerAdded(ctx);
    }

    @Override
    public void handlerRemoved(ChannelHandlerContext ctx) {

        log.info("HTTP 1.1 router removed for ID {}", routerId);
    }

    @Override
    public void channelRegistered(ChannelHandlerContext ctx) throws Exception {

        log.info("HTTP 1.1 channel registered");

        super.channelRegistered(ctx);
    }

    @Override
    protected void channelRead0(ChannelHandlerContext ctx, HttpObject msg) throws Exception {

        processInbound(ctx, msg);
    }

    public void processInbound(ChannelHandlerContext ctx, HttpObject msg) throws Exception {

        if (msg instanceof HttpRequest) {
            processNewRequest(ctx, (HttpRequest) msg);
        }
        else if (msg instanceof HttpContent) {
            processRequestContent(ctx, (HttpContent) msg);
        }
        else {
            throw new EUnexpected();
        }

        if (msg instanceof LastHttpContent) {
            processEndOfRequest(ctx, (LastHttpContent) msg);
        }
    }

    public void processOutbound(ChannelHandlerContext ctx, HttpObject msg, long requestId) {

        if (requestId == currentOutboundRequest) {

        }
        else {
            // queue
        }

        if (msg instanceof LastHttpContent) {
            currentOutboundRequest++;
        }
    }


    private void processNewRequest(ChannelHandlerContext ctx, HttpRequest msg) throws Exception {

        // Increment counter for inbound requests
        currentInboundRequest++;

        // Set up a new request state with initial values
        var request = new RequestState();
        request.requestId = currentInboundRequest;
        request.status = RequestStatus.RECEIVING;
        requests.put(request.requestId, request);

        // Look for a matching route for this request
        var uri = URI.create(msg.uri());
        var method = msg.method();
        var headers = msg.headers();

        Route selectedRoute = null;

        for (var route : this.routes) {
            if (route.matcher.matches(uri, method, headers)) {

                log.info("{} {} -> {} ({})", method, uri, route.clientKey, routerId);
                selectedRoute = route;
                break;
            }
        }

        // If there is no matching route, fail the request and respond with 404
        if (selectedRoute == null) {

            // No route available, send a 404 back to the client
            log.warn("No route available: " + method + " " + uri);

            var protocolVersion = msg.protocolVersion();
            var response = new DefaultHttpResponse(protocolVersion, HttpResponseStatus.NOT_FOUND);
            ctx.writeAndFlush(response);

            request.status = RequestStatus.FAILED;

            // No need to release, base class does it automatically
            // ReferenceCountUtil.release(msg);

            return;
        }

        request.clientKey = selectedRoute.clientKey;

        var existingClient = clients.getOrDefault(request.clientKey, null);
        var client = existingClient != null ? existingClient : new ClientState();

        if (existingClient == null)
            clients.put(request.clientKey, client);

        if (client.channel == null) {

            var channelActiveFuture = ctx.newPromise();
            var channelInactiveFuture = ctx.newPromise();

            var channelInit = selectedRoute
                    .initializer
                    .makeInitializer(ctx, channelActiveFuture);

            var channelOpenFuture = bootstrap
                    .handler(channelInit)
                    .connect(selectedRoute.host, selectedRoute.port);

            var channelCloseFuture = channelOpenFuture
                    .channel()
                    .closeFuture();

            channelOpenFuture.addListener(future -> proxyChannelOpen(ctx, client, future));
            channelCloseFuture.addListener(future -> proxyChannelClosed(ctx, client, future));
            channelActiveFuture.addListener(future -> proxyChannelActive(ctx, client, future));
            channelInactiveFuture.addListener(future -> proxyChannelInactive(ctx, client, future));

            client.channel = channelOpenFuture.channel();
            client.channelOpenFuture = channelOpenFuture;
            client.channelCloseFuture = channelCloseFuture;
            client.channelActiveFuture = channelActiveFuture;
            client.channelInactiveFuture = channelInactiveFuture;
        }

        if (client.channel.isActive())
            client.channel.write(msg);
        else
            client.outboundQueue.add(msg);
    }

    private void processRequestContent(ChannelHandlerContext ctx, HttpContent msg) {

        var requestState = requests.getOrDefault(currentInboundRequest, null);

        if (requestState == null)
            throw new EUnexpected();

        var client = clients.getOrDefault(requestState.clientKey, null);

        if (client == null)
            throw new EUnexpected();

        msg.retain();

        if (client.channel.isActive())
            client.channel.write(msg);
        else
            client.outboundQueue.add(msg);
    }

    private void processEndOfRequest(ChannelHandlerContext ctx, LastHttpContent msg) {

        var requestState = requests.getOrDefault(currentInboundRequest, null);

        if (requestState == null)
            throw new EUnexpected();

        var client = clients.getOrDefault(requestState.clientKey, null);

        if (client == null)
            throw new EUnexpected();

        if (client.channel.isActive())
            client.channel.flush();
    }

    private void proxyChannelOpen(ChannelHandlerContext ctx, ClientState client, Future<?> future) {

        if (!future.isSuccess()) {

            var response = new DefaultFullHttpResponse(
                    HttpVersion.HTTP_1_1, HttpResponseStatus.
                    SERVICE_UNAVAILABLE);

            ctx.writeAndFlush(response);
        }
    }

    private void proxyChannelActive(ChannelHandlerContext ctx, ClientState client, Future<?> future) {

        if (future.isSuccess()) {
            log.info("Connection to server ok");

            var outboundHead = client.outboundQueue.poll();

            while (outboundHead != null) {
                client.channel.write(outboundHead);
                outboundHead = client.outboundQueue.poll();
            }

            client.channel.flush();
        }
        else
            log.error("Connection to server failed", future.cause());
    }

    private void proxyChannelInactive(ChannelHandlerContext ctx, ClientState client, Future<?> future) {

    }

    private void proxyChannelClosed(ChannelHandlerContext ctx, ClientState client, Future<?> future) {

    }


    private enum RequestStatus {
        RECEIVING,
        RECEIVING_BIDI,
        WAITING_FOR_RESPONSE,
        RESPONDING,
        SUCCEEDED,
        FAILED
    }

    private static class RequestState {

        long requestId;
        RequestStatus status;
        String clientKey;
    }

    private static class ClientState {

        Channel channel;
        ChannelFuture channelOpenFuture;
        ChannelFuture channelCloseFuture;
        ChannelFuture channelActiveFuture;
        ChannelFuture channelInactiveFuture;

        Queue<Object> outboundQueue = new LinkedList<>();
    }

    private static class Route {

        IRouteMatcher matcher;
        String clientKey;

        String host;
        int port;
        ProxyInitializer initializer;
    }

    @FunctionalInterface
    private interface ProxyInitializer {

        ChannelInitializer<Channel> makeInitializer(
                ChannelHandlerContext routerCtx,
                ChannelPromise routeActivePromise);
    }
}