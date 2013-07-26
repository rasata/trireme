/**
 * Copyright (C) 2013 Apigee Corp. and other Noderunner contributors.
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in
 * all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
 * AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
 * LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
 * SOFTWARE.
 */
package com.apigee.noderunner.container.netty;

import com.apigee.noderunner.net.spi.HttpFuture;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;

import java.nio.channels.ClosedChannelException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class NettyHttpFuture
    extends HttpFuture
    implements ChannelFutureListener
{
    private final ChannelFuture channel;

    public NettyHttpFuture(ChannelFuture channel)
    {
        this.channel = channel;
    }

    @Override
    public boolean cancel(boolean b)
    {
        // TODO!
        return false;
    }

    @Override
    public boolean isCancelled()
    {
        return false;
    }

    @Override
    public boolean isDone()
    {
        return channel.isDone();
    }

    @Override
    public Boolean get()
        throws InterruptedException, ExecutionException
    {
        channel.await();
        return channel.isSuccess();
    }

    @Override
    public Boolean get(long l, TimeUnit timeUnit)
        throws InterruptedException, ExecutionException, TimeoutException
    {
        channel.await(timeUnit.toMillis(l));
        return channel.isSuccess();
    }

    @Override
    protected void listenerRegistered()
    {
        channel.addListener(this);
    }

    @Override
    public void operationComplete(ChannelFuture future)
    {
        Throwable cause = future.cause();
        if ((cause != null) || (cause instanceof ClosedChannelException)) {
            invokeListener(false, true, cause);
        } else {
            invokeListener(future.isSuccess(), false, future.cause());
        }
    }
}
