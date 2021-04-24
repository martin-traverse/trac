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
import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http2.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;


public class Http1to2Framing extends Http2ChannelDuplexHandler {

    private final Logger log = LoggerFactory.getLogger(getClass());

    private final Map<Integer, Http2FrameStream> streams;
    private final AtomicInteger nextSeqId;
    private int inboundSeqId;
    private int outboundSeqId;

    Http1to2Framing() {

        this.streams = new HashMap<>();
        this.nextSeqId = new AtomicInteger(0);
        this.inboundSeqId = -1;
    }

    @Override
    public void write(ChannelHandlerContext ctx, Object msg, ChannelPromise promise) throws Exception {

        log.info("Translating HTTP/1 message of type {}", msg.getClass().getSimpleName());

        if (msg instanceof HttpRequest)
            newSeqStream();

        var frames = translateRequestFrames(msg);
        var notLastFrame = frames.subList(0, frames.size() - 1);
        var lastFrame = frames.get(frames.size() - 1);

        for (var frame : notLastFrame)
            ctx.write(frame);

        promise.addListener(fut -> {

            var stream = streams.get(inboundSeqId);
            log.info("on stream  {}, {}", stream.id(), stream.state());
        });

        ctx.write(lastFrame, promise);
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        super.channelRead(ctx, msg);
    }

    private void newSeqStream() {

        inboundSeqId = nextSeqId.getAndIncrement();

        var stream = this.newStream();
        streams.put(inboundSeqId, stream);

        log.info("SEQ ID {} -> STREAM {}", inboundSeqId, stream.id());
    }

    private void deleteSeqStream() {

    }

    private List<Http2Frame> translateRequestFrames(Object http1) {

        var seqId = inboundSeqId;
        var stream = streams.get(seqId);

        if (stream == null)
            throw new EUnexpected();

        var frames = new ArrayList<Http2Frame>();

        if (http1 instanceof HttpRequest) {

            var h1Request = (HttpRequest) http1;
            var h1Headers = h1Request.headers();

            var h2Headers = new DefaultHttp2Headers()
                    .scheme(HttpScheme.HTTPS.name())
                    .method(HttpMethod.POST.name())
                    .path(h1Request.uri());

            var filterHeaders = List.of(
                    "connection",
                    "content-length");
//                    "host",
//                    "content-length",
//                    "x-user-agent",
//                    "dnt");

            for (var header : h1Headers)
                if (!filterHeaders.contains(header.getKey().toLowerCase()))
                    h2Headers.add(header.getKey().toLowerCase(), header.getValue());

            var frame = new DefaultHttp2HeadersFrame(h2Headers, false).stream(stream);
            frames.add(frame);
        }

        if (http1 instanceof HttpContent) {

            var h1Content = (HttpContent) http1;
            var contentBuf = h1Content.content();

            var MAX_DATA_SIZE = 16 * 1024;

            contentBuf.retain();

            log.info("Size of content: {}", contentBuf.readableBytes());

            while (contentBuf.readableBytes() > MAX_DATA_SIZE) {

                var slice = contentBuf.readSlice(MAX_DATA_SIZE);
                var frame = new DefaultHttp2DataFrame(slice).stream(stream);
                frames.add(frame);
            }

            var endStreamFlag = (http1 instanceof LastHttpContent);
            var slice = contentBuf.readSlice(contentBuf.readableBytes());

            log.info("Size of slice: {}", slice.readableBytes());
            log.info("end of stream: {}", endStreamFlag);

            var padding = 256 - (slice.readableBytes() % 256) % 256;

            var frame = new DefaultHttp2DataFrame(slice, endStreamFlag, padding).stream(stream);
            frames.add(frame);
        }

        if (frames.isEmpty())
            throw new EUnexpected();

        return frames;
    }
}
