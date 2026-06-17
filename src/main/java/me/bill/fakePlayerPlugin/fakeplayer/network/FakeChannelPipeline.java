package me.bill.fakePlayerPlugin.fakeplayer.network;

import java.net.SocketAddress;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelOutboundInvoker;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.ChannelProgressivePromise;
import io.netty.channel.ChannelPromise;
import io.netty.channel.DefaultChannelPromise;
import io.netty.util.ReferenceCountUtil;
import io.netty.util.concurrent.EventExecutorGroup;

@SuppressWarnings("deprecation")
public final class FakeChannelPipeline implements ChannelPipeline {

    private final Channel channel;
    private final Map<String, ChannelHandler> handlers = new LinkedHashMap<>();
    private final Object handlersLock = new Object();

    public FakeChannelPipeline(Channel channel) {
        this.channel = channel;
    }

    @Override
    public ChannelPipeline addFirst(String name, ChannelHandler handler) {
        synchronized (handlersLock) {
            putFirst(name, handler);
        }
        return this;
    }

    @Override
    public ChannelPipeline addFirst(EventExecutorGroup group, String name, ChannelHandler handler) {
        synchronized (handlersLock) {
            putFirst(name, handler);
        }
        return this;
    }

    @Override
    public ChannelPipeline addLast(String name, ChannelHandler handler) {
        synchronized (handlersLock) {
            putLast(name, handler);
        }
        return this;
    }

    @Override
    public ChannelPipeline addLast(EventExecutorGroup group, String name, ChannelHandler handler) {
        synchronized (handlersLock) {
            putLast(name, handler);
        }
        return this;
    }

    @Override
    public ChannelPipeline addBefore(String baseName, String name, ChannelHandler handler) {
        synchronized (handlersLock) {
            putNear(baseName, name, handler, false);
        }
        return this;
    }

    @Override
    public ChannelPipeline addBefore(EventExecutorGroup group, String baseName, String name, ChannelHandler handler) {
        synchronized (handlersLock) {
            putNear(baseName, name, handler, false);
        }
        return this;
    }

    @Override
    public ChannelPipeline addAfter(String baseName, String name, ChannelHandler handler) {
        synchronized (handlersLock) {
            putNear(baseName, name, handler, true);
        }
        return this;
    }

    @Override
    public ChannelPipeline addAfter(EventExecutorGroup group, String baseName, String name, ChannelHandler handler) {
        synchronized (handlersLock) {
            putNear(baseName, name, handler, true);
        }
        return this;
    }

    @Override
    public ChannelPipeline addFirst(ChannelHandler... handlers) {
        synchronized (handlersLock) {
            for (int i = handlers.length - 1; i >= 0; i--) putFirst(null, handlers[i]);
        }
        return this;
    }

    @Override
    public ChannelPipeline addFirst(EventExecutorGroup group, ChannelHandler... handlers) {
        synchronized (handlersLock) {
            for (int i = handlers.length - 1; i >= 0; i--) putFirst(null, handlers[i]);
        }
        return this;
    }

    @Override
    public ChannelPipeline addLast(ChannelHandler... handlers) {
        synchronized (handlersLock) {
            for (ChannelHandler handler : handlers) putLast(null, handler);
        }
        return this;
    }

    @Override
    public ChannelPipeline addLast(EventExecutorGroup group, ChannelHandler... handlers) {
        synchronized (handlersLock) {
            for (ChannelHandler handler : handlers) putLast(null, handler);
        }
        return this;
    }

    @Override
    public ChannelPipeline remove(ChannelHandler handler) {
        if (handler != null) {
            synchronized (handlersLock) {
                handlers.values().removeIf(existing -> existing == handler);
            }
        }
        return this;
    }

    @Override
    public ChannelHandler remove(String name) {
        synchronized (handlersLock) {
            return name != null ? handlers.remove(name) : null;
        }
    }

    @Override
    public <T extends ChannelHandler> T remove(Class<T> handlerType) {
        if (handlerType == null) return null;
        synchronized (handlersLock) {
            for (Iterator<Map.Entry<String, ChannelHandler>> it =
                            handlers.entrySet().iterator();
                    it.hasNext(); ) {
                Map.Entry<String, ChannelHandler> entry = it.next();
                if (handlerType.isInstance(entry.getValue())) {
                    it.remove();
                    return handlerType.cast(entry.getValue());
                }
            }
        }
        return null;
    }

    @Override
    public ChannelHandler removeFirst() {
        synchronized (handlersLock) {
            Iterator<Map.Entry<String, ChannelHandler>> it = handlers.entrySet().iterator();
            if (!it.hasNext()) return null;
            ChannelHandler handler = it.next().getValue();
            it.remove();
            return handler;
        }
    }

    @Override
    public ChannelHandler removeLast() {
        synchronized (handlersLock) {
            String last = null;
            for (String name : handlers.keySet()) last = name;
            return last != null ? handlers.remove(last) : null;
        }
    }

    @Override
    public ChannelPipeline replace(ChannelHandler old, String name, ChannelHandler handler) {
        if (old != null) {
            synchronized (handlersLock) {
                for (String key : List.copyOf(handlers.keySet())) {
                    if (handlers.get(key) == old) {
                        handlers.remove(key);
                        handlers.put(resolveName(name, handler), handler);
                        break;
                    }
                }
            }
        }
        return this;
    }

    @Override
    public ChannelHandler replace(String old, String name, ChannelHandler handler) {
        synchronized (handlersLock) {
            ChannelHandler removed = old != null ? handlers.remove(old) : null;
            if (handler != null) handlers.put(resolveName(name, handler), handler);
            return removed;
        }
    }

    @Override
    public <T extends ChannelHandler> T replace(Class<T> old, String name, ChannelHandler handler) {
        synchronized (handlersLock) {
            T removed = remove(old);
            if (handler != null) handlers.put(resolveName(name, handler), handler);
            return removed;
        }
    }

    @Override
    public ChannelHandler first() {
        synchronized (handlersLock) {
            return handlers.isEmpty() ? null : handlers.values().iterator().next();
        }
    }

    @Override
    public ChannelHandlerContext firstContext() {
        return null;
    }

    @Override
    public ChannelHandler last() {
        synchronized (handlersLock) {
            ChannelHandler last = null;
            for (ChannelHandler handler : handlers.values()) last = handler;
            return last;
        }
    }

    @Override
    public ChannelHandlerContext lastContext() {
        return null;
    }

    @Override
    public ChannelHandler get(String name) {
        synchronized (handlersLock) {
            return name != null ? handlers.get(name) : null;
        }
    }

    @Override
    public <T extends ChannelHandler> T get(Class<T> handlerType) {
        if (handlerType == null) return null;
        synchronized (handlersLock) {
            for (ChannelHandler handler : handlers.values()) {
                if (handlerType.isInstance(handler)) return handlerType.cast(handler);
            }
        }
        return null;
    }

    @Override
    public ChannelHandlerContext context(ChannelHandler handler) {
        return null;
    }

    @Override
    public ChannelHandlerContext context(String name) {
        return null;
    }

    @Override
    public ChannelHandlerContext context(Class<? extends ChannelHandler> handlerType) {
        return null;
    }

    @Override
    public Channel channel() {
        return this.channel;
    }

    @Override
    public List<String> names() {
        synchronized (handlersLock) {
            return List.copyOf(handlers.keySet());
        }
    }

    @Override
    public Map<String, ChannelHandler> toMap() {
        synchronized (handlersLock) {
            return Map.copyOf(handlers);
        }
    }

    @Override
    public ChannelPipeline fireChannelRegistered() {
        return this;
    }

    @Override
    public ChannelPipeline fireChannelUnregistered() {
        return this;
    }

    @Override
    public ChannelPipeline fireChannelActive() {
        return this;
    }

    @Override
    public ChannelPipeline fireChannelInactive() {
        return this;
    }

    @Override
    public ChannelPipeline fireExceptionCaught(Throwable cause) {
        return this;
    }

    @Override
    public ChannelPipeline fireUserEventTriggered(Object event) {
        return this;
    }

    @Override
    public ChannelPipeline fireChannelRead(Object msg) {
        ReferenceCountUtil.release(msg);
        return this;
    }

    @Override
    public ChannelPipeline fireChannelReadComplete() {
        return this;
    }

    @Override
    public ChannelPipeline fireChannelWritabilityChanged() {
        return this;
    }

    @Override
    public ChannelFuture bind(SocketAddress addr) {
        return newSucceededFuture();
    }

    @Override
    public ChannelFuture connect(SocketAddress remote) {
        return newSucceededFuture();
    }

    @Override
    public ChannelFuture connect(SocketAddress remote, SocketAddress local) {
        return newSucceededFuture();
    }

    @Override
    public ChannelFuture disconnect() {
        return newSucceededFuture();
    }

    @Override
    public ChannelFuture close() {
        return newSucceededFuture();
    }

    @Override
    public ChannelFuture deregister() {
        return newSucceededFuture();
    }

    @Override
    public ChannelFuture bind(SocketAddress addr, ChannelPromise p) {
        p.setSuccess();
        return p;
    }

    @Override
    public ChannelFuture connect(SocketAddress remote, ChannelPromise p) {
        p.setSuccess();
        return p;
    }

    @Override
    public ChannelFuture connect(SocketAddress remote, SocketAddress local, ChannelPromise p) {
        p.setSuccess();
        return p;
    }

    @Override
    public ChannelFuture disconnect(ChannelPromise p) {
        p.setSuccess();
        return p;
    }

    @Override
    public ChannelFuture close(ChannelPromise p) {
        p.setSuccess();
        return p;
    }

    @Override
    public ChannelFuture deregister(ChannelPromise p) {
        p.setSuccess();
        return p;
    }

    @Override
    public ChannelOutboundInvoker read() {
        return null;
    }

    @Override
    public ChannelPipeline flush() {
        return this;
    }

    private void notifyListener(Object msg) {
        if (channel instanceof FakeChannel fc) {
            Consumer<Object> listener = fc.getPacketListener();
            if (listener != null) listener.accept(msg);
        }
    }

    @Override
    public ChannelFuture write(Object msg) {
        notifyListener(msg);
        ReferenceCountUtil.release(msg);
        return newSucceededFuture();
    }

    @Override
    public ChannelFuture write(Object msg, ChannelPromise promise) {
        notifyListener(msg);
        ReferenceCountUtil.release(msg);
        promise.setSuccess();
        return promise;
    }

    @Override
    public ChannelFuture writeAndFlush(Object msg) {
        notifyListener(msg);
        ReferenceCountUtil.release(msg);
        return newSucceededFuture();
    }

    @Override
    public ChannelFuture writeAndFlush(Object msg, ChannelPromise promise) {
        notifyListener(msg);
        ReferenceCountUtil.release(msg);
        promise.setSuccess();
        return promise;
    }

    @Override
    public ChannelPromise newPromise() {
        return new DefaultChannelPromise(this.channel);
    }

    @Override
    public ChannelProgressivePromise newProgressivePromise() {
        return null;
    }

    @Override
    public ChannelFuture newSucceededFuture() {
        DefaultChannelPromise p = new DefaultChannelPromise(this.channel);
        p.setSuccess(null);
        return p;
    }

    @Override
    public ChannelFuture newFailedFuture(Throwable cause) {
        DefaultChannelPromise p = new DefaultChannelPromise(this.channel);
        p.setFailure(cause);
        return p;
    }

    @Override
    public ChannelPromise voidPromise() {
        DefaultChannelPromise p = new DefaultChannelPromise(this.channel);
        p.setSuccess(null);
        return p;
    }

    @Override
    public Iterator<Map.Entry<String, ChannelHandler>> iterator() {
        synchronized (handlersLock) {
            return Collections.unmodifiableMap(handlers).entrySet().iterator();
        }
    }

    private void putFirst(String name, ChannelHandler handler) {
        if (handler == null) return;
        String key = resolveName(name, handler);
        LinkedHashMap<String, ChannelHandler> copy = new LinkedHashMap<>();
        copy.put(key, handler);
        for (Map.Entry<String, ChannelHandler> entry : handlers.entrySet()) {
            if (!entry.getKey().equals(key)) copy.put(entry.getKey(), entry.getValue());
        }
        handlers.clear();
        handlers.putAll(copy);
    }

    private void putLast(String name, ChannelHandler handler) {
        if (handler == null) return;
        handlers.put(resolveName(name, handler), handler);
    }

    private void putNear(String baseName, String name, ChannelHandler handler, boolean after) {
        if (handler == null || baseName == null || !handlers.containsKey(baseName)) {
            putLast(name, handler);
            return;
        }
        String key = resolveName(name, handler);
        LinkedHashMap<String, ChannelHandler> copy = new LinkedHashMap<>();
        for (Map.Entry<String, ChannelHandler> entry : handlers.entrySet()) {
            if (!after && entry.getKey().equals(baseName)) copy.put(key, handler);
            if (!entry.getKey().equals(key)) copy.put(entry.getKey(), entry.getValue());
            if (after && entry.getKey().equals(baseName)) copy.put(key, handler);
        }
        handlers.clear();
        handlers.putAll(copy);
    }

    private String resolveName(String name, ChannelHandler handler) {
        if (name != null && !name.isBlank()) return name;
        String base = handler.getClass().getName();
        String candidate = base;
        int index = 0;
        while (handlers.containsKey(candidate)) candidate = base + "#" + (++index);
        return candidate;
    }
}
