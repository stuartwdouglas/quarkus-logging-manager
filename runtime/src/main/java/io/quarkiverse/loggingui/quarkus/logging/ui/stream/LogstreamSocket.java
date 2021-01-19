package io.quarkiverse.loggingui.quarkus.logging.ui.stream;

import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.MemoryHandler;

import javax.enterprise.context.ApplicationScoped;
import javax.enterprise.event.Observes;

import io.netty.handler.codec.http.HttpHeaderNames;
import io.quarkus.arc.Unremovable;
import io.vertx.core.AsyncResult;
import io.vertx.core.http.ServerWebSocket;
import io.vertx.ext.web.Router;
import io.vertx.ext.web.RoutingContext;

/**
 * Websocket server that can distribute log messages.
 * If there is no subscribers, the logging fall back to normal file.
 */
@Unremovable
@ApplicationScoped
public class LogstreamSocket {

    private static final Logger log = Logger.getLogger(LogstreamSocket.class.getName());

    void setup(@Observes Router router) {
        router.route("/logstream").handler(new io.vertx.core.Handler<RoutingContext>() {
            @Override
            public void handle(RoutingContext event) {
                if ("websocket".equalsIgnoreCase(event.request().getHeader(HttpHeaderNames.UPGRADE))) {
                    event.request().toWebSocket(new io.vertx.core.Handler<AsyncResult<ServerWebSocket>>() {
                        @Override
                        public void handle(AsyncResult<ServerWebSocket> event) {
                            if (event.succeeded()) {
                                ServerWebSocket socket = event.result();
                                SessionState state = new SessionState();
                                state.handler = new MemoryHandler(new JsonHandler(socket), 1000, Level.FINEST);
                                state.session = socket;
                                socket.closeHandler(new io.vertx.core.Handler<Void>() {
                                    @Override
                                    public void handle(Void event) {
                                        stop(state);
                                    }
                                });
                                socket.textMessageHandler(new io.vertx.core.Handler<String>() {
                                    @Override
                                    public void handle(String event) {
                                        onMessage(event, state);
                                    }
                                });

                            } else {
                                log.log(Level.SEVERE, "Failed to connect to log server", event.cause());
                            }

                        }
                    });
                } else {
                    event.next();
                }
            }
        });
    }

    static class SessionState {
        ServerWebSocket session;
        Handler handler;
        boolean started;
    }

    public void onMessage(String message, SessionState session) {
        if (message != null && !message.isEmpty()) {
            if (message.equalsIgnoreCase(START)) {
                start(session);
            } else if (message.equalsIgnoreCase(STOP)) {
                stop(session);
            }
        }
    }

    private void start(SessionState session) {
        if (!session.started) {
            registerHandler(session.handler);
            session.started = true;
        }
    }

    private void stop(SessionState session) {
        unregisterHandler(session.handler);
        session.started = false;
    }

    private void registerHandler(Handler handler) {
        Logger logger = Logger.getLogger("");
        if (logger != null) {
            logger.addHandler(handler);
        }
    }

    private void unregisterHandler(Handler handler) {
        if (handler != null) {
            Logger logger = Logger.getLogger("");
            if (logger != null)
                logger.removeHandler(handler);
        }
    }

    private static final String START = "start";
    private static final String STOP = "stop";
}